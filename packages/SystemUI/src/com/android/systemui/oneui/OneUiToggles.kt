/*
 * SPDX-FileCopyrightText: 2026 The yaap-asteroids Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.oneui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Settings.Secure switches for the One UI style extras. All are on unless set to 0. */
object OneUiToggles {
    /** Quick Settings tiles on a rounded card. */
    const val QS_CARD = "oneui_qs_card"

    /** Labels under the round Quick Settings icon tiles. */
    const val QS_TILE_LABELS = "oneui_qs_tile_labels"

    /** Edit, settings and power buttons at the top of Quick Settings. */
    const val QS_BUTTONS_ON_TOP = "oneui_qs_buttons_on_top"

    /** The thick brightness pill without a thumb line. */
    const val BRIGHTNESS_PILL = "oneui_brightness_pill"

    /** The glow around the dynamic island. */
    const val ISLAND_GLOW = "systemui_island_glow"
}

/** Follows the Settings.Secure switch [name], which is on unless set to 0. */
@Composable
fun rememberSecureToggle(name: String): State<Boolean> {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Secure.getInt(resolver, name, 1) != 0
    val state = remember(resolver, name) { mutableStateOf(read()) }
    DisposableEffect(resolver, name) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    state.value = read()
                }
            }
        resolver.registerContentObserver(Settings.Secure.getUriFor(name), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return state
}
