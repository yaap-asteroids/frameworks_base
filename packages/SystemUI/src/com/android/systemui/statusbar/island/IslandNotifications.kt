/*
 * SPDX-FileCopyrightText: 2026 The yaap-asteroids Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.island

import android.app.Notification
import android.app.PendingIntent
import android.app.StatusBarManager.WINDOW_STATE_SHOWING
import android.app.StatusBarManager.WINDOW_STATUS_BAR
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.view.Display
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.StatusBarState
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.interruption.VisualInterruptionDecisionProvider
import com.android.systemui.statusbar.notification.interruption.VisualInterruptionFilter
import com.android.systemui.statusbar.notification.interruption.VisualInterruptionType
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Settings.Secure toggle for showing incoming notifications in the island, on unless 0. */
const val ISLAND_NOTIFICATIONS_SETTING = "systemui_island_notifications"

/** A notification popping out of the island in place of its heads up notification. */
data class IslandNotification(
    val key: String,
    val appIcon: Drawable?,
    val title: CharSequence?,
    val text: CharSequence?,
    /** The app's colour, which the island glows in. */
    val color: Int,
    val contentIntent: PendingIntent?,
)

/** The notification currently popping out of the island, cleared after [POP_DURATION_MS]. */
@SysUISingleton
class IslandNotificationRepository
@Inject
constructor(@Application private val scope: CoroutineScope) {
    private val _pop = MutableStateFlow<IslandNotification?>(null)
    val pop: StateFlow<IslandNotification?> = _pop.asStateFlow()

    private var clearJob: Job? = null

    fun show(notification: IslandNotification) {
        _pop.value = notification
        clearJob?.cancel()
        clearJob =
            scope.launch {
                delay(POP_DURATION_MS)
                if (_pop.value?.key == notification.key) _pop.value = null
            }
    }

    fun dismiss() {
        clearJob?.cancel()
        _pop.value = null
    }

    private companion object {
        const val POP_DURATION_MS = 4000L
    }
}

/**
 * Turns heads up notifications into island pops.
 *
 * It sits in the heads up decision as a filter. For each notification it asks the decision again
 * with itself left out, so a notification pops exactly when it would have shown a heads up: every
 * other rule (DND, importance, snoozing, avalanche, ...) still applies. Calls and full screen
 * intents keep their heads up for their answer buttons, and so does everything while the island
 * cannot be seen: on the lock screen, with the status bar hidden, or with the setting off.
 */
@SysUISingleton
class IslandNotificationFilter
@Inject
constructor(
    private val context: Context,
    private val decisionProvider: VisualInterruptionDecisionProvider,
    private val repository: IslandNotificationRepository,
    private val statusBarStateController: StatusBarStateController,
    private val commandQueue: CommandQueue,
) :
    VisualInterruptionFilter(
        types = setOf(VisualInterruptionType.PEEK),
        reason = "shown in the dynamic island",
    ),
    CoreStartable {

    @Volatile private var statusBarShowing = true
    private val evaluating = ThreadLocal.withInitial { false }
    private val lastPopTime = mutableMapOf<String, Long>()

    override fun start() {
        commandQueue.addCallback(
            object : CommandQueue.Callbacks {
                override fun setWindowState(displayId: Int, window: Int, state: Int) {
                    if (displayId == Display.DEFAULT_DISPLAY && window == WINDOW_STATUS_BAR) {
                        statusBarShowing = state == WINDOW_STATE_SHOWING
                    }
                }
            }
        )
        decisionProvider.addFilter(this)
    }

    override fun shouldSuppress(entry: NotificationEntry): Boolean {
        if (evaluating.get() || !canPop() || keepsHeadsUp(entry.sbn.notification)) return false

        // Would this notification show a heads up if the island were not here?
        evaluating.set(true)
        val wouldPeek =
            try {
                decisionProvider.makeUnloggedHeadsUpDecision(entry).shouldInterrupt
            } finally {
                evaluating.set(false)
            }
        if (!wouldPeek) return false

        // The decision is asked again on updates; pop only for a newly posted alert.
        val postTime = entry.sbn.postTime
        synchronized(lastPopTime) {
            if (lastPopTime.size > MAX_TRACKED_KEYS) lastPopTime.clear()
            if (lastPopTime.put(entry.key, postTime) != postTime) {
                repository.show(entry.toIslandNotification())
            }
        }
        return true
    }

    private fun canPop(): Boolean =
        statusBarShowing &&
            statusBarStateController.state == StatusBarState.SHADE &&
            isOn(DYNAMIC_ISLAND_SETTING) &&
            isOn(ISLAND_NOTIFICATIONS_SETTING)

    private fun isOn(setting: String) =
        Settings.Secure.getInt(context.contentResolver, setting, 1) != 0

    /** Calls and full screen intents need their heads up for its buttons. */
    private fun keepsHeadsUp(notification: Notification) =
        notification.fullScreenIntent != null ||
            notification.category == Notification.CATEGORY_CALL

    private fun NotificationEntry.toIslandNotification(): IslandNotification {
        val notification = sbn.notification
        val extras = notification.extras
        val appInfo =
            extras.getParcelable(
                Notification.EXTRA_BUILDER_APPLICATION_INFO,
                ApplicationInfo::class.java,
            )
        val appIcon = appInfo?.let { context.packageManager.getApplicationIcon(it) }
        val color =
            notification.color.takeIf { it != Notification.COLOR_DEFAULT && it != 0 }
                ?: appIcon?.averageColor()
                ?: Color.WHITE
        return IslandNotification(
            key = key,
            appIcon = appIcon,
            title =
                extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                    ?: extras.getCharSequence(Notification.EXTRA_TITLE),
            text = extras.getCharSequence(Notification.EXTRA_TEXT),
            color = color,
            contentIntent = notification.contentIntent,
        )
    }
}

/** The icon's average opaque colour, for apps that do not set a notification colour. */
private fun Drawable.averageColor(): Int? {
    val bitmap = Bitmap.createBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888)
    val bounds = copyBounds()
    setBounds(0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
    draw(Canvas(bitmap))
    setBounds(bounds)
    var r = 0L
    var g = 0L
    var b = 0L
    var count = 0
    for (pixel in IntArray(SAMPLE_SIZE * SAMPLE_SIZE).also {
        bitmap.getPixels(it, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
    }) {
        if (Color.alpha(pixel) < 128) continue
        r += Color.red(pixel)
        g += Color.green(pixel)
        b += Color.blue(pixel)
        count++
    }
    bitmap.recycle()
    if (count == 0) return null
    return Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
}

private const val SAMPLE_SIZE = 16

/** Notifications remembered for de-duplicating pops; old ones are dropped past this. */
private const val MAX_TRACKED_KEYS = 200
