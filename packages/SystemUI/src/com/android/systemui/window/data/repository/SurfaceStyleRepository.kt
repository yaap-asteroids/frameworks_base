/*
 * SPDX-FileCopyrightText: 2026 The yaap-asteroids Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.window.data.repository

import android.content.Context
import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * How the shade and other SystemUI surfaces sit over what is behind them, like One UI's
 * transparency and blur options:
 *  - [STYLE_BLUR]: translucent surfaces over a blur, the default.
 *  - [STYLE_TRANSLUCENT]: the same translucent surfaces, without the blur.
 *  - [STYLE_SOLID]: opaque surfaces, AOSP's path for devices without blur.
 */
@SysUISingleton
class SurfaceStyleRepository
@Inject
constructor(
    secureSettingsRepository: SecureSettingsRepository,
    @Application scope: CoroutineScope,
) {
    val style: StateFlow<Int> =
        secureSettingsRepository
            .intSetting(SETTING, STYLE_BLUR)
            .stateIn(scope, SharingStarted.Eagerly, STYLE_BLUR)

    /** Whether surfaces should blur what is behind them. */
    val blursBehind: Boolean
        get() = style.value == STYLE_BLUR

    companion object {
        /** Settings.Secure key holding one of the STYLE_ values. */
        const val SETTING = "systemui_surface_style"

        const val STYLE_BLUR = 0
        const val STYLE_TRANSLUCENT = 1
        const val STYLE_SOLID = 2

        /** [blursBehind] for code outside dependency injection, read fresh each call. */
        @JvmStatic
        fun blursBehind(context: Context): Boolean =
            Settings.Secure.getInt(context.contentResolver, SETTING, STYLE_BLUR) == STYLE_BLUR
    }
}
