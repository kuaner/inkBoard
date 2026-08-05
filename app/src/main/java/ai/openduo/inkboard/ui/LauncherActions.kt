package ai.openduo.inkboard.ui

import ai.openduo.inkboard.data.AppInfo
import ai.openduo.inkboard.data.EpdProfile
import ai.openduo.inkboard.data.KoboyoIcon
import ai.openduo.inkboard.data.KoboyoIconCategory
import ai.openduo.inkboard.util.OrientationMode

/** User intents shared by the root compositor and feature pages. */
class LauncherActions(
    val onLaunch: (AppInfo) -> Unit,
    val onAssignSlot: (Int, AppInfo) -> Unit,
    val onClearSlot: (Int) -> Unit,
    val onSetMotto: (String) -> Unit,
    val onLoadIconPage: (KoboyoIconCategory, Int) -> Unit,
    val onSetSlotIcon: (Int, KoboyoIcon) -> Unit,
    val onLoadEpdProfile: (String) -> Unit,
    val onSaveEpdProfile: (EpdProfile) -> Unit,
    val onApplyInkBoardEpdProfile: (EpdProfile) -> Unit,
    val onLoadSystemEpdProfile: () -> Unit,
    val onApplySystemEpdProfile: (EpdProfile) -> Unit,
    val onRefreshEpdScreen: () -> Unit,
    val onOrientation: (OrientationMode) -> Unit,
    val onToggleAdb: () -> Unit,
    val onOpenSystemSettings: () -> Unit,
    val onStartSender: () -> Unit,
    val onStopSender: () -> Unit
)
