package ai.openduo.inkboard

import ai.openduo.inkboard.data.AppInfo
import ai.openduo.inkboard.data.IconPickerState
import ai.openduo.inkboard.data.KoboyoIconCategory
import ai.openduo.inkboard.data.KoboyoIconGroup
import ai.openduo.inkboard.data.KoboyoIconRepository
import ai.openduo.inkboard.data.EpdModeSummary
import ai.openduo.inkboard.data.EpdProfile
import ai.openduo.inkboard.data.EpdRefreshPreset
import ai.openduo.inkboard.data.PreferencesRepository
import ai.openduo.inkboard.util.OrientationMode

/**
 * The complete read-only state exposed to Compose.
 *
 * Repositories and vendor-facing services stay behind LauncherViewModel; UI
 * pages receive this snapshot and callbacks only.
 */
data class LauncherUiState(
    /** Visible home shortcuts; length always equals [homeSlotCount]. */
    val slots: List<AppInfo?> = List(PreferencesRepository.DEFAULT_SLOT_COUNT) { null },
    /** Active density: 8 / 12 / 16 / 24. Default 8. */
    val homeSlotCount: Int = PreferencesRepository.DEFAULT_SLOT_COUNT,
    val allApps: List<AppInfo> = emptyList(),
    val motto: String = PreferencesRepository.DEFAULT_MOTTO,
    val orientation: OrientationMode = OrientationMode.LANDSCAPE,
    val adbEnabled: Boolean = false,
    val iconGroups: List<KoboyoIconGroup> = KoboyoIconRepository.GROUPS,
    val iconCategories: List<KoboyoIconCategory> = KoboyoIconRepository.CATEGORIES,
    val iconPicker: IconPickerState = IconPickerState(),
    val epdProfile: EpdProfile? = null,
    val epdLoading: Boolean = false,
    val epdError: String? = null,
    val systemEpdProfile: EpdProfile? = null,
    val systemEpdLoading: Boolean = false,
    val systemEpdError: String? = null,
    /** Mode-first EPD hub summaries (counts + which mode is system default). */
    val epdModeSummaries: List<EpdModeSummary> = emptyList(),
    val epdModesLoading: Boolean = false,
    /** Apps currently assigned to the open refresh mode. */
    val epdModeMembers: List<AppInfo> = emptyList(),
    val epdModeMembersLoading: Boolean = false,
    val epdModeMembersPreset: EpdRefreshPreset? = null,
    /**
     * True only on devices whose firmware exposes the SystemUI EPD stack
     * (probed once on first launch and cached). Controls the unified EPD hub
     * and write paths.
     */
    val epdCustomizationEnabled: Boolean = false,
    val sender: SenderSnapshot = SenderSnapshot(),
    val ready: Boolean = false
)

/** Read-only lifecycle state for the one-shot local file server. */
data class SenderSnapshot(
    val running: Boolean = false,
    val loading: Boolean = false,
    val port: Int? = null,
    val url: String? = null,
    val error: String? = null
)
