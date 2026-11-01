package ai.openduo.inkboard.ui.home

import ai.openduo.inkboard.ui.LauncherActions
import ai.openduo.inkboard.ui.apps.AppDrawer
import ai.openduo.inkboard.ui.apps.AppLauncherPage
import ai.openduo.inkboard.ui.components.formatCurrentTime
import ai.openduo.inkboard.ui.components.formatHomeDateParts
import ai.openduo.inkboard.util.readSystemMetrics
import ai.openduo.inkboard.ui.controls.ControlPage
import ai.openduo.inkboard.ui.epd.EpdEntryPoints
import ai.openduo.inkboard.ui.epd.EpdHomePage
import ai.openduo.inkboard.ui.epd.EpdListTarget
import ai.openduo.inkboard.ui.epd.EpdModeChoice
import ai.openduo.inkboard.ui.epd.EpdRoute
import ai.openduo.inkboard.ui.icons.IconPicker
import ai.openduo.inkboard.ui.sender.SenderPage

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ai.openduo.inkboard.LauncherUiState
import ai.openduo.inkboard.data.AppInfo
import ai.openduo.inkboard.data.EpdProfile
import ai.openduo.inkboard.data.EpdRefreshPreset
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val GLOBAL_ID = "global"
private const val INKBOARD_ID = "inkboard"

@Composable
fun LauncherHome(
    state: LauncherUiState,
    actions: LauncherActions,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var appLauncher by rememberSaveable { mutableStateOf(false) }
    var shortcutEditor by rememberSaveable { mutableStateOf(false) }
    var controls by rememberSaveable { mutableStateOf(false) }
    var sender by rememberSaveable { mutableStateOf(false) }
    var mottoEditor by rememberSaveable { mutableStateOf(false) }
    var iconPicker by rememberSaveable { mutableStateOf(false) }
    var renameEditor by rememberSaveable { mutableStateOf(false) }
    var editingSlot by rememberSaveable { mutableIntStateOf(0) }
    var drawerPage by rememberSaveable { mutableIntStateOf(0) }
    var launcherPage by rememberSaveable { mutableIntStateOf(0) }
    var currentTime by rememberSaveable { mutableStateOf(formatCurrentTime()) }
    var currentDateParts by remember { mutableStateOf(formatHomeDateParts()) }
    var systemMetrics by remember { mutableStateOf(readSystemMetrics(context)) }

    // EPD single page: list + modes. Drafts flush on leave / target change.
    var epdRoute by remember { mutableStateOf<EpdRoute?>(null) }
    var epdListPage by rememberSaveable { mutableIntStateOf(0) }
    var selectedTargetId by rememberSaveable { mutableStateOf(GLOBAL_ID) }
    var modeChoice by remember { mutableStateOf(EpdModeChoice.of(EpdRefreshPreset.CLEAN)) }
    var modeDirty by remember { mutableStateOf(false) }
    // Track which target the current draft belongs to.
    var draftTargetId by remember { mutableStateOf(GLOBAL_ID) }

    val epdTargets = remember(state.allApps, context.packageName) {
        buildEpdTargets(state.allApps, context)
    }

    val epd = EpdEntryPoints.of(
        enabled = state.epdCustomizationEnabled,
        open = {
            epdRoute = EpdRoute.Home
            epdListPage = 0
            selectedTargetId = GLOBAL_ID
            draftTargetId = GLOBAL_ID
            modeDirty = false
            modeChoice = EpdModeChoice.of(EpdRefreshPreset.CLEAN)
            actions.onLoadSystemEpdProfile()
            actions.onRefreshApps()
        },
        refresh = actions.onRefreshEpdScreen
    )

    val globalFrequency = state.systemEpdProfile?.refreshFrequency
        ?: EpdRefreshPreset.CLEAN.frequency
    val globalPreset = state.systemEpdProfile
        ?.let { EpdRefreshPreset.fromProfile(it) }
        ?: EpdRefreshPreset.CLEAN

    // Seed mode panel when system profile or selected app profile loads.
    LaunchedEffect(state.systemEpdProfile, selectedTargetId, epdRoute) {
        if (epdRoute == null || modeDirty) return@LaunchedEffect
        if (selectedTargetId == GLOBAL_ID) {
            state.systemEpdProfile?.let {
                modeChoice = EpdModeChoice.fromProfile(it)
                draftTargetId = GLOBAL_ID
            }
        } else if (modeChoice.followGlobal) {
            // Keep the inherited chip value in sync while still following.
            modeChoice = EpdModeChoice.follow(globalFrequency)
        }
    }

    LaunchedEffect(state.epdProfile, selectedTargetId, epdRoute, globalFrequency) {
        if (epdRoute == null || modeDirty) return@LaunchedEffect
        if (selectedTargetId == GLOBAL_ID) return@LaunchedEffect
        val target = epdTargets.firstOrNull { it.id == selectedTargetId } ?: return@LaunchedEffect
        val pkg = target.packageName ?: return@LaunchedEffect
        val profile = state.epdProfile?.takeIf { it.packageName == pkg } ?: return@LaunchedEffect
        modeChoice = if (!profile.refreshEnabled) {
            EpdModeChoice.follow(globalFrequency)
        } else {
            EpdModeChoice.fromProfile(profile)
        }
        draftTargetId = selectedTargetId
    }

    fun flushModeDraft(targetId: String, choice: EpdModeChoice) {
        val target = epdTargets.firstOrNull { it.id == targetId } ?: return
        if (target.isGlobal) {
            val preset = choice.preset ?: EpdRefreshPreset.CLEAN
            val base = state.systemEpdProfile
                ?: EpdProfile(
                    packageName = "ai.openduo.inkboard.system-default",
                    refreshEnabled = true
                )
            actions.onApplySystemEpdProfile(preset.applyTo(base, choice.frequency))
        } else {
            val app = target.app ?: AppInfo(
                label = target.label,
                packageName = target.packageName ?: return,
                activityName = "",
                icon = null
            )
            if (choice.followGlobal) {
                actions.onRemoveAppFromEpdMode(app)
            } else {
                val preset = choice.preset ?: EpdRefreshPreset.CLEAN
                actions.onAssignAppToEpdMode(app, preset, choice.frequency)
            }
        }
    }

    val closeEpd: () -> Unit = {
        if (modeDirty) {
            flushModeDraft(draftTargetId, modeChoice)
        }
        modeDirty = false
        epdRoute = null
    }

    val selectTarget: (EpdListTarget) -> Unit = { target ->
        if (target.id != selectedTargetId) {
            if (modeDirty) {
                flushModeDraft(draftTargetId, modeChoice)
                modeDirty = false
            }
            selectedTargetId = target.id
            draftTargetId = target.id
            // Reset choice until load arrives (avoid flashing wrong mode).
            modeChoice = if (target.isGlobal) {
                state.systemEpdProfile?.let { EpdModeChoice.fromProfile(it) }
                    ?: EpdModeChoice.of(EpdRefreshPreset.CLEAN)
            } else {
                EpdModeChoice.follow(
                    state.systemEpdProfile?.refreshFrequency
                        ?: EpdRefreshPreset.CLEAN.frequency
                )
            }
            if (!target.isGlobal) {
                val pkg = target.packageName
                if (pkg != null) actions.onLoadEpdProfile(pkg)
            } else {
                actions.onLoadSystemEpdProfile()
            }
        }
    }

    val closeSender: () -> Unit = {
        sender = false
        actions.onStopSender()
    }

    val openShortcutEditor: (Int) -> Unit = { slot ->
        editingSlot = slot
        drawerPage = 0
        shortcutEditor = true
        actions.onRefreshApps()
    }

    LaunchedEffect(sender) {
        if (sender) actions.onStartSender()
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            currentTime = formatCurrentTime()
            currentDateParts = formatHomeDateParts()
            systemMetrics = readSystemMetrics(context)
            val untilNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(untilNextMinute.coerceAtLeast(1_000L))
        }
    }

    val showEpd = epd.available && epdRoute != null

    BackHandler(
        enabled = sender || showEpd || appLauncher || shortcutEditor ||
            controls || mottoEditor || iconPicker || renameEditor
    ) {
        when {
            sender -> closeSender()
            showEpd -> closeEpd()
            renameEditor -> renameEditor = false
            iconPicker -> iconPicker = false
            mottoEditor -> mottoEditor = false
            shortcutEditor -> shortcutEditor = false
            appLauncher -> appLauncher = false
            controls -> controls = false
        }
    }

    when {
        sender -> SenderPage(
            snapshot = state.sender,
            onBack = closeSender,
            onRetry = actions.onStartSender,
            onStop = actions.onStopSender,
            modifier = modifier
        )

        showEpd -> EpdHomePage(
            targets = epdTargets,
            selectedId = selectedTargetId,
            listPage = epdListPage,
            onListPageChange = { epdListPage = it },
            onSelectTarget = selectTarget,
            modeChoice = modeChoice,
            onModeChoice = { choice ->
                modeChoice = choice
                modeDirty = true
                draftTargetId = selectedTargetId
            },
            globalPreset = globalPreset,
            globalFrequency = globalFrequency,
            onRefreshScreen = actions.onRefreshEpdScreen,
            onBack = closeEpd,
            error = state.systemEpdError ?: state.epdError,
            modifier = modifier
        )

        renameEditor -> RenameEditor(
            slot = editingSlot,
            originalLabel = state.slots.getOrNull(editingSlot)?.let { slotApp ->
                state.allApps.firstOrNull { it.key == slotApp.key }?.label ?: slotApp.label
            }.orEmpty(),
            initial = state.slots.getOrNull(editingSlot)?.label.orEmpty(),
            onSave = { name ->
                actions.onSetSlotLabel(editingSlot, name)
                renameEditor = false
            },
            onBack = { renameEditor = false },
            modifier = modifier
        )

        iconPicker -> IconPicker(
            state = state,
            slot = editingSlot,
            onCategory = { category -> actions.onLoadIconPage(category, 0) },
            onPage = { page ->
                if (state.iconPicker.query.isNotBlank()) {
                    actions.onSearchIcons(state.iconPicker.query, page)
                } else {
                    actions.onLoadIconPage(state.iconPicker.category, page)
                }
            },
            onSearch = { q -> actions.onSearchIcons(q, 0) },
            onSelectIcon = { icon ->
                actions.onSetSlotIcon(editingSlot, icon)
                iconPicker = false
            },
            onBack = { iconPicker = false },
            modifier = modifier
        )

        mottoEditor -> MottoEditor(
            initial = state.motto,
            onSave = { motto ->
                actions.onSetMotto(motto)
                mottoEditor = false
            },
            onBack = { mottoEditor = false },
            modifier = modifier
        )

        appLauncher -> AppLauncherPage(
            apps = state.allApps,
            page = launcherPage,
            onPageChange = { launcherPage = it },
            onLaunch = { app ->
                appLauncher = false
                actions.onLaunch(app)
            },
            onBack = { appLauncher = false },
            modifier = modifier
        )

        shortcutEditor -> AppDrawer(
            slots = state.slots,
            apps = state.allApps,
            selectedSlot = editingSlot,
            onSelectSlot = { editingSlot = it },
            onLaunch = { app ->
                shortcutEditor = false
                actions.onLaunch(app)
            },
            onAddShortcut = { app -> actions.onAssignSlot(editingSlot, app) },
            onClearSlot = { actions.onClearSlot(editingSlot) },
            onRenameSlot = { renameEditor = true },
            onOpenIconPicker = {
                iconPicker = true
                actions.onLoadIconPage(state.iconPicker.category, 0)
            },
            page = drawerPage,
            onPageChange = { drawerPage = it },
            onBack = { shortcutEditor = false },
            modifier = modifier
        )

        controls -> ControlPage(
            state = state,
            actions = actions,
            onOpenMotto = { mottoEditor = true },
            onOpenApps = { openShortcutEditor(0) },
            onBack = { controls = false },
            modifier = modifier
        )

        else -> HomeSurface(
            state = state,
            actions = actions,
            epd = epd,
            time = currentTime,
            dateParts = currentDateParts,
            metrics = systemMetrics,
            motto = state.motto,
            onOpenApps = {
                launcherPage = 0
                appLauncher = true
                actions.onRefreshApps()
            },
            onEditSlot = { openShortcutEditor(it) },
            onOpenMotto = { mottoEditor = true },
            onOpenSender = { sender = true },
            onOpenControls = { controls = true },
            modifier = modifier
        )
    }
}

private fun buildEpdTargets(
    apps: List<AppInfo>,
    context: android.content.Context
): List<EpdListTarget> {
    val inkBoardPackage = context.packageName
    val launchable = apps.filter { it.builtInShortcut == null }
    val others = launchable
        .filter { it.packageName != inkBoardPackage }
        .sortedBy { it.label.lowercase() }
    val inkBoardApp = resolveSelfAppInfo(context)

    return buildList {
        add(
            EpdListTarget(
                id = GLOBAL_ID,
                label = "全局缺省",
                packageName = null,
                app = null,
                isGlobal = true,
                iconSvgPath = "koboyo/svg/aperture.svg"
            )
        )
        add(
            EpdListTarget(
                id = INKBOARD_ID,
                label = "InkBoard",
                packageName = inkBoardPackage,
                app = inkBoardApp,
                isGlobal = false
            )
        )
        others.forEach { app ->
            add(
                EpdListTarget(
                    id = app.key,
                    label = app.label,
                    packageName = app.packageName,
                    app = app,
                    isGlobal = false
                )
            )
        }
    }
}

/** Load this process's launcher icon (self is filtered out of the app catalog). */
private fun resolveSelfAppInfo(context: android.content.Context): AppInfo {
    val pm = context.packageManager
    val packageName = context.packageName
    val appInfo = runCatching {
        pm.getApplicationInfo(packageName, 0)
    }.getOrNull()
    val label = appInfo?.let { pm.getApplicationLabel(it)?.toString() }?.trim().orEmpty()
        .ifBlank { "InkBoard" }
    val icon = appInfo?.let { pm.getApplicationIcon(it) }
        ?: runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
    val activityName = runCatching {
        pm.getLaunchIntentForPackage(packageName)?.component?.className
    }.getOrNull().orEmpty().ifBlank { "$packageName.MainActivity" }
    return AppInfo(
        label = label,
        packageName = packageName,
        activityName = activityName,
        icon = icon
    )
}
