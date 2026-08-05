package ai.openduo.inkboard.ui.epd

import ai.openduo.inkboard.data.AppInfo

internal enum class EpdSection {
    REFRESH,
    DISPLAY,
    FILTER
}

internal data class EpdTarget(
    val label: String,
    val packageName: String,
    val app: AppInfo? = null
)

/**
 * Single UI choke point for EPD entry points.
 *
 * Feature pages never read `epdCustomizationEnabled` themselves: they only
 * receive this bag of optional callbacks. When the device is not S11A-family,
 * every field is null and the pages simply hide those controls.
 */
internal data class EpdEntryPoints(
    val openSystem: (() -> Unit)? = null,
    val openApp: ((AppInfo) -> Unit)? = null,
    val openInkBoard: (() -> Unit)? = null,
    val refresh: (() -> Unit)? = null
) {
    val available: Boolean get() = openSystem != null

    companion object {
        val None = EpdEntryPoints()

        fun of(
            enabled: Boolean,
            openSystem: () -> Unit,
            openApp: (AppInfo) -> Unit,
            openInkBoard: () -> Unit,
            refresh: () -> Unit
        ): EpdEntryPoints = if (!enabled) {
            None
        } else {
            EpdEntryPoints(
                openSystem = openSystem,
                openApp = openApp,
                openInkBoard = openInkBoard,
                refresh = refresh
            )
        }
    }
}

