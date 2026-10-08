/*
 * SPDX-FileCopyrightText: 2026 The yaap-asteroids Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.island

import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager
import com.android.systemui.statusbar.chips.notification.domain.interactor.StatusBarNotificationChipsInteractor
import com.android.systemui.statusbar.quickactions.media.domain.interactor.MediaControlChipInteractor
import com.android.systemui.statusbar.quickactions.media.ui.viewmodel.MediaControlPopupViewModel
import javax.inject.Inject

/** What the dynamic island needs from the rest of SystemUI. */
class DynamicIslandDependencies
@Inject
constructor(
    /** The playing media. */
    val mediaControlChipInteractor: MediaControlChipInteractor,
    /** The expanded media card, AOSP's status bar media popup. */
    val mediaPopupViewModelFactory: MediaControlPopupViewModel.Factory,
    /** Moves the media player into the popup while it shows. */
    val mediaHierarchyManager: MediaHierarchyManager,
    /** Incoming notifications popping out of the island. */
    val islandNotifications: IslandNotificationRepository,
    /** Shows a notification's full heads up, as tapping its status bar chip does. */
    val notificationChipsInteractor: StatusBarNotificationChipsInteractor,
)
