package ai.openduo.inkboard.ui.epd

import ai.openduo.inkboard.data.AppInfo
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

/** Choice for the mode panel. Global has no “follow”. */
internal data class EpdModeChoice(
    val followGlobal: Boolean,
    val preset: EpdRefreshPreset?
) {
    companion object {
        fun follow() = EpdModeChoice(followGlobal = true, preset = null)
        fun of(preset: EpdRefreshPreset) = EpdModeChoice(followGlobal = false, preset = preset)
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
