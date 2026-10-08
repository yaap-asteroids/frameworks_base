/*
 * SPDX-FileCopyrightText: 2026 The yaap-asteroids Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.island

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.PI
import kotlin.math.pow

/**
 * The island's springs. Every island animation is a spring, so it keeps its momentum when
 * interrupted instead of restarting.
 *
 * A spring here is described by how long it takes to settle, its response, and how far it
 * overshoots, its bounce from 0 to 1. With a unit mass that is a stiffness of (2π / response)²
 * and a damping ratio of 1 - bounce.
 */
internal object IslandMotion {
    /** Settles without overshoot: fades and content changes. */
    fun <T> smooth(): SpringSpec<T> = spring(response = 0.5f, bounce = 0f)

    /** A slight overshoot: growing, shrinking, appearing and resizing. */
    fun <T> snappy(): SpringSpec<T> = spring(response = 0.5f, bounce = 0.15f)

    /** A visible bounce, for what needs attention: an incoming notification. */
    fun <T> bouncy(): SpringSpec<T> = spring(response = 0.5f, bounce = 0.3f)

    private fun <T> spring(response: Float, bounce: Float): SpringSpec<T> =
        spring(dampingRatio = 1f - bounce, stiffness = (2 * PI / response).pow(2).toFloat())

    /** Whether the user turned animations off, in which case nothing should loop. */
    fun isReduced(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) ==
            0f
}
