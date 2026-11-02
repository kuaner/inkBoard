package ai.openduo.inkboard.ui

import ai.openduo.inkboard.data.AppInfo
import ai.openduo.inkboard.data.EpdProfile
import ai.openduo.inkboard.data.EpdRefreshPreset
import ai.openduo.inkboard.data.KoboyoIcon
import ai.openduo.inkboard.data.KoboyoIconCategory
import ai.openduo.inkboard.util.OrientationMode

/** User intents shared by the root compositor and feature pages. */
class LauncherActions(
    val onLaunch: (AppInfo) -> Unit,
    val onAssignSlot: (Int, AppInfo) -> Unit,
    val onClearSlot: (Int) -> Unit,
    val onSetMotto: (String) -> Unit,
    /** Home density: 4 / 8 / 12 / 16 / 24 visible shortcut slots. */
    val onSetHomeSlotCount: (Int) -> Unit,
    val onLoadIconPage: (KoboyoIconCategory, Int) -> Unit,
    val onSetSlotIcon: (Int, KoboyoIcon) -> Unit,
    val onSetSlotLabel: (Int, String) -> Unit,
    val onSearchIcons: (String, Int) -> Unit,
    val onLoadEpdProfile: (String) -> Unit,
    val onSaveEpdProfile: (EpdProfile) -> Unit,
    val onApplyInkBoardEpdProfile: (EpdProfile) -> Unit,
    val onLoadSystemEpdProfile: () -> Unit,
    val onApplySystemEpdProfile: (EpdProfile) -> Unit,
    val onLoadEpdModeSummaries: () -> Unit,
    val onLoadEpdModeMembers: (EpdRefreshPreset) -> Unit,
    val onAssignAppToEpdMode: (AppInfo, EpdRefreshPreset, Int) -> Unit,
    val onRemoveAppFromEpdMode: (AppInfo) -> Unit,
    val onSetEpdModeAsSystemDefault: (EpdRefreshPreset) -> Unit,
    val onRefreshEpdScreen: () -> Unit,
    /** Rescan launchable apps when opening APPS; no-op UI update if unchanged. */
    val onRefreshApps: () -> Unit,
    val onOrientation: (OrientationMode) -> Unit,
    val onToggleAdb: () -> Unit,
    val onOpenSystemSettings: () -> Unit,
    val onStartSender: () -> Unit,
    val onStopSender: () -> Unit
)
