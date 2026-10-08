/*
 * SPDX-FileCopyrightText: 2026 The yaap-asteroids Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.island

import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.quickactions.media.shared.model.MediaControlChipModel

/** One thing the island can show. */
sealed interface IslandContent {
    /** Identifies the content across updates, so changes to it animate in place. */
    val key: String

    /** A live activity: a call, timer, screen recording or Live Update notification. */
    data class Activity(val chip: OngoingActivityChipModel.Active) : IslandContent {
        override val key: String
            get() = chip.key
    }

    /** The playing media. */
    data class Media(val media: MediaControlChipModel) : IslandContent {
        override val key: String
            get() = "media"
    }
}

/** The island's pill, and the small circle beside it when two things are going on. */
data class IslandState(val primary: IslandContent, val secondary: IslandContent?) {
    /** Keys of the live activities shown here, which the start of the status bar leaves out. */
    val activityKeys: Set<String>
        get() =
            listOfNotNull(primary, secondary)
                .filterIsInstance<IslandContent.Activity>()
                .mapTo(mutableSetOf()) { it.chip.key }

    val media: MediaControlChipModel?
        get() =
            listOfNotNull(primary, secondary)
                .filterIsInstance<IslandContent.Media>()
                .firstOrNull()
                ?.media

    companion object {
        /**
         * What the island shows: the top two live activities, with the playing media after them,
         * or null with the island off or nothing going on.
         */
        fun of(
            active: List<OngoingActivityChipModel.Active>,
            media: MediaControlChipModel?,
            enabled: Boolean,
        ): IslandState? {
            if (!enabled) return null
            val items =
                active.filter { !it.isHidden }.take(2).map { IslandContent.Activity(it) } +
                    listOfNotNull(
                        media?.takeIf { !it.songName.isNullOrEmpty() }?.let { IslandContent.Media(it) }
                    )
            val primary = items.firstOrNull() ?: return null
            return IslandState(primary, items.getOrNull(1))
        }
    }
}
