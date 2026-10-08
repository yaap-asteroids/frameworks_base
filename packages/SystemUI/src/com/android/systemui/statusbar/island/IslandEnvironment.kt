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
