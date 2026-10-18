package com.example.unifiedreader.feature.settings

import androidx.compose.runtime.saveable.Saver
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName

/** One page reachable from the settings hub. `null` in the shell means the hub itself. */
sealed interface SettingsPage {
    val key: String
    val title: String

    data object Theme : SettingsPage {
        override val key: String = "theme"
        override val title: String = "Theme"
    }

    data class Source(val sourceType: SourceType) : SettingsPage {
        override val key: String get() = "$SourceKeyPrefix${sourceType.name}"
        override val title: String get() = sourceType.displayName()
    }

    data object FilterChips : SettingsPage {
        override val key: String = "filter_chips"
        override val title: String = "Filter chips"
    }

    data object DrawerOrder : SettingsPage {
        override val key: String = "drawer_order"
        override val title: String = "Drawer order"
    }

    data object SyncStatus : SettingsPage {
        override val key: String = "sync_status"
        override val title: String = "Reading progress sync"
    }

    data object Licenses : SettingsPage {
        override val key: String = "licenses"
        override val title: String = "Open-source licenses"
    }

    companion object {
        private const val SourceKeyPrefix = "source:"

        fun fromKey(key: String?): SettingsPage? = when {
            key.isNullOrEmpty() -> null
            key == Theme.key -> Theme
            key == FilterChips.key -> FilterChips
            key == DrawerOrder.key -> DrawerOrder
            key == SyncStatus.key -> SyncStatus
            key == Licenses.key -> Licenses
            key.startsWith(SourceKeyPrefix) -> SourceType.entries
                .firstOrNull { sourceType -> sourceType.name == key.removePrefix(SourceKeyPrefix) }
                ?.let(::Source)
            else -> null
        }
    }
}

/** Saves the open page across configuration changes and process death. */
val SettingsPageSaver: Saver<SettingsPage?, String> = Saver(
    save = { page -> page?.key.orEmpty() },
    restore = { key -> SettingsPage.fromKey(key) },
)
