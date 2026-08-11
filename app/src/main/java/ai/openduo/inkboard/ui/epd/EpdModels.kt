package ai.openduo.inkboard.ui.epd

import ai.openduo.inkboard.data.AppInfo
import ai.openduo.inkboard.data.EpdProfile
import ai.openduo.inkboard.data.EpdRefreshPreset

internal enum class EpdSection {
    REFRESH,
    DISPLAY,
    FILTER
}

internal data class EpdTarget(
    val label: String,
    val packageName: String,
    val app: AppInfo? = null
) {
    val isSystemDefault: Boolean
        get() = packageName == SYSTEM_DEFAULT_PACKAGE

    val isInkBoard: Boolean
        get() = app == null && !isSystemDefault

    val editorMeta: String
        get() = when {
            isSystemDefault -> "SYSTEM"
            isInkBoard -> "INKBOARD"
            else -> "APP"
        }

    companion object {
        const val SYSTEM_DEFAULT_PACKAGE = "ai.openduo.inkboard.system-default"
    }
}

/** Single-page EPD: no nested routes. */
internal sealed class EpdRoute {
    data object Home : EpdRoute()
}

/**
 * One cell in the EPD target grid.
 * [isGlobal] is always first; InkBoard is second; then other apps.
 * [iconSvgPath] is used for global (bundled SVG); [app] carries drawable icons.
 */
internal data class EpdListTarget(
    val id: String,
    val label: String,
    val packageName: String?,
    val app: AppInfo?,
    val isGlobal: Boolean,
    /** Asset-relative path under assets/, e.g. koboyo/svg/admin-cog.svg */
    val iconSvgPath: String? = null
)

/**
 * Choice for the mode panel. Global has no “follow”.
 * [frequency] is the full-refresh threshold (partial updates → 1 full frame);
 * presets only supply a recommended default when first selected.
 *
 * When [followGlobal] is true, [frequency] still carries the effective global
 * threshold so the chip row can show what the app inherits — and a chip tap
 * can break free into an independent profile.
 */
internal data class EpdModeChoice(
    val followGlobal: Boolean,
    val preset: EpdRefreshPreset?,
    val frequency: Int = preset?.frequency ?: EpdRefreshPreset.CLEAN.frequency
) {
    companion object {
        fun follow(
            frequency: Int = EpdRefreshPreset.CLEAN.frequency
        ) = EpdModeChoice(
            followGlobal = true,
            preset = null,
            frequency = frequency.coerceIn(
                EpdRefreshPreset.MIN_FREQUENCY,
                EpdRefreshPreset.MAX_FREQUENCY
            )
        )

        fun of(
            preset: EpdRefreshPreset,
            frequency: Int = preset.frequency
        ) = EpdModeChoice(
            followGlobal = false,
            preset = preset,
            frequency = frequency.coerceIn(
                EpdRefreshPreset.MIN_FREQUENCY,
                EpdRefreshPreset.MAX_FREQUENCY
            )
        )

        fun fromProfile(profile: EpdProfile): EpdModeChoice {
            val preset = EpdRefreshPreset.fromProfile(profile) ?: EpdRefreshPreset.CLEAN
            return of(preset, profile.refreshFrequency)
        }
    }
}

internal data class EpdEntryPoints(
    val open: (() -> Unit)? = null,
    val refresh: (() -> Unit)? = null
) {
    val available: Boolean get() = open != null

    companion object {
        val None = EpdEntryPoints()

        fun of(
            enabled: Boolean,
            open: () -> Unit,
            refresh: () -> Unit
        ): EpdEntryPoints = if (!enabled) {
            None
        } else {
            EpdEntryPoints(open = open, refresh = refresh)
        }
    }
}
