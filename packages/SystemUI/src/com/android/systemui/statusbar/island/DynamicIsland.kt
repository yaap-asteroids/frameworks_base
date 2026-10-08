/*
 * SPDX-FileCopyrightText: 2026 The yaap-asteroids Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.island

import android.app.ActivityOptions
import android.app.PendingIntent
import android.graphics.Color
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.oneui.OneUiToggles
import com.android.systemui.oneui.rememberSecureInt
import com.android.systemui.oneui.rememberSecureToggle
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.ui.compose.OngoingActivityChip
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.NotificationIconContainerViewBinder
import com.android.systemui.statusbar.quickactions.media.shared.model.MediaControlChipModel
import com.android.systemui.statusbar.quickactions.media.ui.compose.MediaControlPopup
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The dynamic island: [state]'s primary content as a black pill wrapped around the camera cutout,
 * the icon left of the camera and the text right of it, with its secondary content as a small
 * circle beside it.
 *
 * Live activities are the status bar's own [OngoingActivityChip], recoloured, so taps, long
 * presses, accessibility and the return-to-app transitions are AOSP's; a promoted notification
 * opens its heads up notification with its actions. Media opens its app on tap and expands into
 * AOSP's status bar media card on long press.
 */
@Composable
fun DynamicIsland(
    state: IslandState?,
    dependencies: DynamicIslandDependencies,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
    cutout: IslandCutout,
    modifier: Modifier = Modifier,
) {
    // Keep showing the last state while the island animates out.
    var shown by remember { mutableStateOf(state) }
    if (state != null) shown = state

    // Slides the pill so its gap sits on the cutout. The gap reports where it landed and the
    // offset corrects by the difference, so it settles after one layout pass.
    var offsetX by remember { mutableFloatStateOf(0f) }
    var aligned by remember { mutableStateOf(false) }
    val gap = with(LocalDensity.current) { cutout.width.toDp() } + CutoutClearance
    val onGapPositioned = { gapCenterX: Float ->
        val delta = cutout.centerX - gapCenterX
        if (abs(delta) > 0.5f) offsetX += delta
        aligned = true
    }

    // Where the island sits once settled, so it can grow out of and shrink back into the cutout
    // instead of its own geometric centre, which drifts with whatever else is sharing the pill.
    var rowLeftX by remember { mutableFloatStateOf(0f) }
    var rowWidth by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val nudgeXDp by rememberSecureInt(ISLAND_OFFSET_X_DP, default = 0)
    val nudgeYDp by rememberSecureInt(ISLAND_OFFSET_Y_DP, default = 0)
    val nudgeXPx = with(density) { nudgeXDp.dp.toPx() }
    val nudgeYPx = with(density) { nudgeYDp.dp.toPx() }
    val appearScale = rememberSecureInt(ISLAND_APPEAR_SCALE_PERCENT, default = 60).value / 100f

    val cutoutPivot =
        if (aligned && rowWidth > 0f) {
            TransformOrigin(((cutout.centerX - rowLeftX) / rowWidth).coerceIn(0f, 1f), 0.5f)
        } else {
            TransformOrigin.Center
        }

    val pop by dependencies.islandNotifications.pop.collectAsState()
    var shownPop by remember { mutableStateOf(pop) }
    if (pop != null) shownPop = pop
    val scope = rememberCoroutineScope()

    var mediaExpanded by remember { mutableStateOf(false) }
    val media = state?.media
    LaunchedEffect(media == null) { if (media == null) mediaExpanded = false }
    val expand = { mediaExpanded = true }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        AnimatedVisibility(
            visible = state != null || pop != null,
            enter =
                fadeIn(IslandMotion.smooth()) +
                    scaleIn(IslandMotion.snappy(), appearScale, cutoutPivot),
            exit =
                fadeOut(IslandMotion.smooth()) +
                    scaleOut(IslandMotion.snappy(), appearScale, cutoutPivot),
        ) {
            val targetIsland = shown
            val targetPop = shownPop.takeIf { pop != null || state == null }
            // Morph between contents: the pill keeps its place and springs to the new size while
            // the content cross-fades, rather than one pill leaving and another arriving.
            AnimatedContent(
                targetState = targetPop to targetIsland,
                contentKey = { (incoming, island) -> incoming?.key ?: island?.primary?.key },
                transitionSpec = {
                    val pops = targetState.first != null && initialState.first == null
                    (fadeIn(IslandMotion.smooth()) togetherWith fadeOut(IslandMotion.smooth()))
                        .using(
                            SizeTransform(clip = false) { _, _ ->
                                if (pops) IslandMotion.bouncy() else IslandMotion.snappy()
                            }
                        )
                },
                modifier =
                    Modifier.offset {
                            IntOffset((offsetX + nudgeXPx).roundToInt(), nudgeYPx.roundToInt())
                        }
                        .graphicsLayer { alpha = if (aligned) 1f else 0f }
                        .onGloballyPositioned {
                            rowLeftX = it.positionInWindow().x
                            rowWidth = it.size.width.toFloat()
                        },
                label = "island",
            ) { (notification, current) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (notification != null) {
                        // A new notification takes the pill; what was there waits beside it.
                        NotificationPill(
                            notification = notification,
                            gap = gap,
                            onGapPositioned = onGapPositioned,
                            onOpen = {
                                notification.contentIntent?.let(::openApp)
                                dependencies.islandNotifications.dismiss()
                            },
                            onExpand = {
                                scope.launch {
                                    dependencies.notificationChipsInteractor
                                        .onPromotedNotificationChipTapped(notification.key)
                                }
                                dependencies.islandNotifications.dismiss()
                            },
                        )
                        current?.primary?.let {
                            Spacer(Modifier.width(SecondarySpacing))
                            Circle(it, iconViewStore, expand)
                        }
                    } else if (current != null) {
                        Pill(current.primary, iconViewStore, gap, onGapPositioned, expand)
                        current.secondary?.let {
                            Spacer(Modifier.width(SecondarySpacing))
                            Circle(it, iconViewStore, expand)
                        }
                    }
                }
            }
        }

        // Kept composed while it shrinks back, so the card can animate out too.
        val card = remember { MutableTransitionState(false) }
        card.targetState = mediaExpanded
        if (card.currentState || card.targetState) {
            MediaCard(dependencies, card, onDismiss = { mediaExpanded = false })
        }
    }
}

/** The main pill, with a gap over the cutout between its icon and its text. */
@Composable
private fun Pill(
    content: IslandContent,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
    gap: Dp,
    onGapPositioned: (Float) -> Unit,
    onExpandMedia: () -> Unit,
) {
    when (content) {
        is IslandContent.Activity ->
            OngoingActivityChip(
                model = content.chip.copy(colors = islandColors(content.chip.colors)),
                iconViewStore = iconViewStore,
                cutoutGap = gap,
                onCutoutGapPositioned = onGapPositioned,
                modifier = Modifier.islandGlow(content.chip.glowColor()),
            )
        is IslandContent.Media -> MediaPill(content.media, gap, onGapPositioned, onExpandMedia)
    }
}

/** The second thing going on, as a small circle with just its icon. */
@Composable
private fun Circle(
    content: IslandContent,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
    onExpandMedia: () -> Unit,
) {
    when (content) {
        is IslandContent.Activity ->
            OngoingActivityChip(
                model =
                    content.chip.copy(
                        colors = islandColors(content.chip.colors),
                        content = OngoingActivityChipModel.Content.IconOnly,
                    ),
                iconViewStore = iconViewStore,
                modifier = Modifier.islandGlow(content.chip.glowColor()),
            )
        is IslandContent.Media ->
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.size(dimensionResource(R.dimen.ongoing_appops_chip_height))
                        .islandGlow(MaterialTheme.colorScheme.primary)
                        .clip(CircleShape)
                        .background(ComposeColor.Black)
                        .combinedClickable(
                            onClickLabel = content.media.appName,
                            onClick = { content.media.clickIntent?.let(::openApp) },
                            onLongClick = onExpandMedia,
                        ),
            ) {
                MediaAppIcon(content.media)
            }
    }
}

/**
 * The playing app's icon left of the camera and a level meter right of it, the same width, so
 * both sides balance. The song is in the expanded card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaPill(
    media: MediaControlChipModel,
    gap: Dp,
    onGapPositioned: (Float) -> Unit,
    onExpand: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.islandGlow(MaterialTheme.colorScheme.primary)
                .clip(CircleShape)
                .background(ComposeColor.Black)
                .combinedClickable(
                    onClickLabel = media.appName,
                    onClick = { media.clickIntent?.let(::openApp) },
                    onLongClick = onExpand,
                )
                .heightIn(min = dimensionResource(R.dimen.ongoing_appops_chip_height))
                .padding(horizontal = 8.dp),
    ) {
        MediaAppIcon(media)
        Spacer(
            Modifier.width(gap).onGloballyPositioned {
                onGapPositioned(it.positionInWindow().x + it.size.width / 2f)
            }
        )
        LevelMeter(playing = media.isPlaying, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Bars rising and falling while media plays; still when paused or with animations off.
 *
 * The levels are read only while drawing, so the meter redraws each frame without recomposing or
 * measuring the island.
 */
@Composable
private fun LevelMeter(playing: Boolean, color: ComposeColor) {
    val context = LocalContext.current
    val animate = playing && !remember { IslandMotion.isReduced(context) }
    val transition = rememberInfiniteTransition(label = "levels")
    val levels =
        LevelBarPeriodsMs.mapIndexed { index, periodMs ->
            if (animate) {
                transition.animateFloat(
                    initialValue = LevelLow,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(
                            tween(periodMs, easing = FastOutSlowInEasing),
                            RepeatMode.Reverse,
                            StartOffset(index * LevelBarStaggerMs),
                        ),
                    label = "level$index",
                )
            } else {
                StillLevel
            }
        }
    Canvas(Modifier.size(MediaIconSize)) {
        val spacing = LevelBarSpacing.toPx()
        val barWidth = (size.width - spacing * (levels.size - 1)) / levels.size
        levels.forEachIndexed { index, level ->
            val barHeight = size.height * level.value
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + spacing), (size.height - barHeight) / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

/** An incoming notification: the app's icon left of the camera, sender and message right. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationPill(
    notification: IslandNotification,
    gap: Dp,
    onGapPositioned: (Float) -> Unit,
    onOpen: () -> Unit,
    onExpand: () -> Unit,
) {
    val message = buildAnnotatedString {
        notification.title?.let { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it) } }
        if (notification.title != null && notification.text != null) append("  ")
        notification.text?.let { append(it) }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.islandGlow(ComposeColor(notification.color))
                .clip(CircleShape)
                .background(ComposeColor.Black)
                .combinedClickable(onClick = onOpen, onLongClick = onExpand)
                .heightIn(min = dimensionResource(R.dimen.ongoing_appops_chip_height))
                .padding(horizontal = 6.dp),
    ) {
        notification.appIcon?.let {
            Image(
                painter = rememberDrawablePainter(it),
                contentDescription = null,
                modifier = Modifier.size(NotificationIconSize).clip(CircleShape),
            )
        }
        Spacer(
            Modifier.width(gap).onGloballyPositioned {
                onGapPositioned(it.positionInWindow().x + it.size.width / 2f)
            }
        )
        BasicText(
            text = message,
            style = MaterialTheme.typography.labelLarge.copy(color = ComposeColor.White),
            maxLines = 1,
            modifier = Modifier.widthIn(max = NotificationTextMaxWidth).basicMarquee(),
        )
    }
}

/** A soft glow in [color] around a pill, unless [OneUiToggles.ISLAND_GLOW] is off. */
@Composable
private fun Modifier.islandGlow(color: ComposeColor): Modifier {
    val enabled by rememberSecureToggle(OneUiToggles.ISLAND_GLOW)
    if (!enabled) return this
    return shadow(
        elevation = GlowElevation,
        shape = CircleShape,
        clip = false,
        ambientColor = color,
        spotColor = color,
    )
}

/** The colour the chip had in the status bar, which the island glows in. */
@Composable
private fun OngoingActivityChipModel.Active.glowColor(): ComposeColor =
    ComposeColor(colors.background(LocalContext.current).defaultColor)

@Composable
private fun MediaAppIcon(media: MediaControlChipModel) {
    val context = LocalContext.current
    val icon: Icon =
        when (media) {
            is MediaControlChipModel.Legacy ->
                media.appIcon?.loadDrawable(context)?.let { Icon.Loaded(it, null) }
            is MediaControlChipModel.Compose -> media.appIcon
        } ?: return
    Icon(icon = icon, tint = ComposeColor.White, modifier = Modifier.size(MediaIconSize))
}

/**
 * AOSP's status bar media card, dropped below the island in a popup window: the status bar
 * window is only as tall as the status bar. Tapping outside or back closes it.
 */
@Composable
private fun MediaCard(
    dependencies: DynamicIslandDependencies,
    visible: MutableTransitionState<Boolean>,
    onDismiss: () -> Unit,
) {
    val viewModel = remember { dependencies.mediaPopupViewModelFactory.create() }

    // The media player only moves into the popup's host while this is set.
    DisposableEffect(Unit) {
        dependencies.mediaHierarchyManager.isMediaControlPopupShowing = true
        onDispose { dependencies.mediaHierarchyManager.isMediaControlPopupShowing = false }
    }

    val statusBarHeight = dimensionResource(R.dimen.status_bar_height)
    val offsetY = with(LocalDensity.current) { statusBarHeight.roundToPx() }
    val cardScale = rememberSecureInt(ISLAND_MEDIA_CARD_SCALE_PERCENT, default = 40).value / 100f
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, offsetY),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        // Grow out of the island, and shrink back into it.
        AnimatedVisibility(
            visibleState = visible,
            enter =
                fadeIn(IslandMotion.smooth()) +
                    scaleIn(IslandMotion.snappy(), cardScale, TopCenterOrigin),
            exit =
                fadeOut(IslandMotion.smooth()) +
                    scaleOut(IslandMotion.snappy(), cardScale, TopCenterOrigin),
        ) {
            MediaControlPopup(viewModel, Modifier.padding(horizontal = 8.dp))
        }
    }
}

/** Opens the media's app; SystemUI may start it from the background. */
private fun openApp(intent: PendingIntent) {
    val options =
        ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            .toBundle()
    try {
        intent.send(null, 0, null, null, null, null, options)
    } catch (e: PendingIntent.CanceledException) {
        // The session ended between showing and tapping.
    }
}

/** Black with white content, except the red privacy chips, which stay red. */
private fun islandColors(colors: ColorsModel): ColorsModel =
    if (colors is ColorsModel.Red) colors else ColorsModel.Custom(Color.BLACK, Color.WHITE)

/** Space either side of the camera: just enough that content never touches it. */
private val CutoutClearance = 2.dp
private const val LevelLow = 0.3f
private val StillLevel: State<Float> = mutableFloatStateOf(LevelLow)
private val LevelBarPeriodsMs = listOf(420, 560, 470)
private const val LevelBarStaggerMs = 90
private val LevelBarSpacing = 2.dp
private val SecondarySpacing = 6.dp
private val MediaIconSize = 16.dp
private val NotificationIconSize = 18.dp
private val NotificationTextMaxWidth = 180.dp
private val GlowElevation = 10.dp
private val TopCenterOrigin = TransformOrigin(0.5f, 0f)
