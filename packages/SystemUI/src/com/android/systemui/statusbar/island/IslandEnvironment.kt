/*
 * SPDX-FileCopyrightText: 2026 The yaap-asteroids Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.island

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.android.systemui.oneui.rememberSecureToggle

/** Settings.Secure toggle for the dynamic island, on unless set to 0. */
const val DYNAMIC_ISLAND_SETTING = "systemui_dynamic_island"

/**
 * Settings.Secure keys to manually nudge the island's position, in dp, both 0 unless set.
 *
 * The island aligns itself to the camera cutout it reads from the status bar's own layout; these
 * exist only as an escape hatch for a device whose cutout geometry doesn't read back correctly,
 * not something most devices need to touch.
 */
const val ISLAND_OFFSET_X_DP = "systemui_island_offset_x_dp"
const val ISLAND_OFFSET_Y_DP = "systemui_island_offset_y_dp"

/** Settings.Secure key for how large the media card pops out of the island, 40 (%) unless set. */
const val ISLAND_MEDIA_CARD_SCALE_PERCENT = "systemui_island_media_card_scale_percent"

/** Settings.Secure key for the island's own appear/disappear starting scale, 60 (%) unless set. */
const val ISLAND_APPEAR_SCALE_PERCENT = "systemui_island_appear_scale_percent"

/** Where the camera cutout sits in the status bar window. */
data class IslandCutout(val centerX: Float, val width: Float) {
    companion object {
        /** Reads the cutout from the status bar's cutout space, or the screen centre without one. */
        fun of(cutoutSpace: View): IslandCutout {
            if (cutoutSpace.width <= 0) return IslandCutout(cutoutSpace.rootView.width / 2f, 0f)
            val location = IntArray(2)
            cutoutSpace.getLocationInWindow(location)
            return IslandCutout(location[0] + cutoutSpace.width / 2f, cutoutSpace.width.toFloat())
        }
    }
}

/** Tracks the camera cutout as the status bar lays out. */
@Composable
fun rememberIslandCutout(cutoutSpace: View): State<IslandCutout> {
    val state = remember(cutoutSpace) { mutableStateOf(IslandCutout.of(cutoutSpace)) }
    DisposableEffect(cutoutSpace) {
        val listener =
            View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                state.value = IslandCutout.of(view)
            }
        cutoutSpace.addOnLayoutChangeListener(listener)
        onDispose { cutoutSpace.removeOnLayoutChangeListener(listener) }
    }
    return state
}

/** Whether the dynamic island is on, following [DYNAMIC_ISLAND_SETTING]. */
@Composable
fun rememberDynamicIslandEnabled(): State<Boolean> = rememberSecureToggle(DYNAMIC_ISLAND_SETTING)
