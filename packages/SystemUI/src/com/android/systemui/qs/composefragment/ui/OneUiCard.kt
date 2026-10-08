/*
 * SPDX-FileCopyrightText: 2026 The yaap-asteroids Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.composefragment.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.integerResource
import com.android.systemui.oneui.OneUiToggles
import com.android.systemui.oneui.rememberSecureToggle
import com.android.systemui.res.R

/**
 * A One UI style card: a rounded, translucent surface that groups Quick Settings content. Draws
 * just the content with [OneUiToggles.QS_CARD] off.
 */
@Composable
fun OneUiCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val enabled by rememberSecureToggle(OneUiToggles.QS_CARD)
    if (!enabled) {
        Box(modifier) { content() }
        return
    }
    val shape = RoundedCornerShape(dimensionResource(R.dimen.oneui_qs_card_corner_radius))
    val alpha = integerResource(R.integer.oneui_qs_card_alpha_percent) / 100f
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha))
                .padding(dimensionResource(R.dimen.oneui_qs_card_padding))
    ) {
        content()
    }
}
