package ai.openduo.inkboard

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.openduo.inkboard.data.AppInfo
import ai.openduo.inkboard.data.AppRepository
import ai.openduo.inkboard.data.BuiltInShortcut
import ai.openduo.inkboard.data.EpdProfile
import ai.openduo.inkboard.data.EpdSettingsRepository
import ai.openduo.inkboard.data.IconPickerState
import ai.openduo.inkboard.data.KoboyoIcon
import ai.openduo.inkboard.data.KoboyoIconCategory
import ai.openduo.inkboard.data.KoboyoIconRepository
import ai.openduo.inkboard.data.MonoIconCache
import ai.openduo.inkboard.data.PreferencesRepository
import ai.openduo.inkboard.util.OrientationMode
import ai.openduo.inkboard.util.SenderServer
import ai.openduo.inkboard.util.SystemControls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val app = getApplication<InkBoardApp>()
    private val appRepo = AppRepository(application)
    private val prefs = PreferencesRepository(application)
    private val iconRepo = KoboyoIconRepository(application)
    private val epdCustomizationFlow = MutableStateFlow(app.epdCustomizationEnabled.value)
    /** All EPD I/O self-gates on this flag — no per-call checks in ViewModel. */
    private val epdRepo = EpdSettingsRepository(application) { epdCustomizationFlow.value }
    private val senderServer = SenderServer(application)

    private val appsFlow = MutableStateFlow<List<AppInfo>>(emptyList())
    /** Fully resolved home row; never published half-empty from key/apps races. */
    private val homeSlotsFlow = MutableStateFlow(app.homeSlots.value)
    private val mottoFlow = MutableStateFlow(app.motto.value)
    private val statusFlow = MutableStateFlow(StatusSnapshot())
    private val iconPickerFlow = MutableStateFlow(IconPickerState())
    private val epdFlow = MutableStateFlow(EpdSnapshot())
    private val systemEpdFlow = MutableStateFlow(EpdSnapshot())
    private val senderFlow = MutableStateFlow(SenderSnapshot())
    private val epdWriteMutex = Mutex()
    private val appsRefreshMutex = Mutex()
    private var iconLoadGeneration = 0
    private var senderRequestGeneration = 0

    private data class StatusSnapshot(
        val orientation: OrientationMode = OrientationMode.LANDSCAPE,
        val adbEnabled: Boolean = false
    )

    private data class EpdSnapshot(
        val profile: EpdProfile? = null,
        val loading: Boolean = false,
        val error: String? = null
    )

    private val baseUiStateFlow = combine(
        combine(homeSlotsFlow, appsFlow, mottoFlow) { slots, apps, motto ->
            Triple(slots, apps, motto)
        },
        combine(statusFlow, epdCustomizationFlow) { status, epdCustom -> status to epdCustom },
        iconPickerFlow,
        combine(epdFlow, systemEpdFlow) { epd, systemEpd -> epd to systemEpd }
    ) { home, statusEpd, iconPicker, epdStates ->
        val (slots, apps, motto) = home
        val (status, epdCustom) = statusEpd
        val (epd, systemEpd) = epdStates
        LauncherUiState(
            slots = slots,
            allApps = apps,
            motto = motto,
            orientation = status.orientation,
            adbEnabled = status.adbEnabled,
            iconPicker = iconPicker,
            epdProfile = epd.profile,
            epdLoading = epd.loading,
            epdError = epd.error,
            systemEpdProfile = systemEpd.profile,
            systemEpdLoading = systemEpd.loading,
            systemEpdError = systemEpd.error,
            epdCustomizationEnabled = epdCustom,
            ready = slots.any { it != null } || apps.isNotEmpty() || app.homeReady.value
        )
    }

    val uiState: StateFlow<LauncherUiState> = combine(
        baseUiStateFlow,
        senderFlow
    ) { state, sender ->
        state.copy(sender = sender)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LauncherUiState(
            slots = app.homeSlots.value,
            motto = app.motto.value,
            epdCustomizationEnabled = app.epdCustomizationEnabled.value,
            ready = app.homeReady.value
        )
    )

    init {
        // Mirror process-level bootstrap as soon as Application finishes it.
        viewModelScope.launch {
            app.homeSlots.collect { slots ->
                if (app.homeReady.value && homeSlotsFlow.value.all { it == null } && slots.any { it != null }) {
                    homeSlotsFlow.value = slots
                    mottoFlow.value = app.motto.value
                }
            }
        }
        viewModelScope.launch {
            app.epdCustomizationEnabled.collect { epdCustomizationFlow.value = it }
        }
        viewModelScope.launch {
            // Keep motto in sync when edited elsewhere.
            prefs.motto.collect { mottoFlow.value = it }
        }
        refreshApps()
        refreshStatus()
    }

    fun refreshApps() {
        viewModelScope.launch {
            if (!appsRefreshMutex.tryLock()) return@launch
            try {
                // 1) Atomic home-slot snapshot first — never paint keys without apps.
                val homeMs = measureTimeMillis {
                    withContext(Dispatchers.IO) {
                        val bootstrapped = app.awaitHomeBootstrap()
                        if (bootstrapped.any { it != null }) {
                            homeSlotsFlow.value = bootstrapped
                            mottoFlow.value = app.motto.value
                        } else {
                            publishResolvedHomeSlots()
                        }
                    }
                }
                Log.i(TAG, "home slots ready in ${homeMs}ms filled=${homeSlotsFlow.value.count { it != null }}")

                // 2) Full catalog for the app drawer; home already visible.
                val fullMs = measureTimeMillis {
                    val apps = appRepo.loadLaunchableApps()
                    appsFlow.value = apps
                    // Refresh home from the richer catalog without clearing first.
                    withContext(Dispatchers.IO) {
                        publishResolvedHomeSlots(appsByKey = apps.associateBy { it.key })
                    }
                }
                Log.i(TAG, "full app scan ${fullMs}ms count=${appsFlow.value.size}")

                // 3) Slow migrations stay off the critical path.
                withContext(Dispatchers.IO) {
                    val apps = appsFlow.value
                    prefs.ensureDefaults(appRepo.defaultSlotKeys(apps))
                    // No-ops on non-S11A: epdRepo is self-gated.
                    syncSystemDefaultApps(apps)
                    // Defaults may have filled empty first-run slots.
                    publishResolvedHomeSlots(appsByKey = apps.associateBy { it.key })
                }
            } finally {
                appsRefreshMutex.unlock()
            }
        }
    }

    private suspend fun publishResolvedHomeSlots(
        appsByKey: Map<String, AppInfo>? = null
    ) {
        val keys = prefs.slotKeys.first()
        val iconPaths = prefs.slotIconPaths.first()
        val motto = prefs.motto.first()
        val needed = keys.filterNotNull().filter { appsByKey?.containsKey(it) != true }
        val resolved = if (needed.isEmpty()) {
            emptyMap()
        } else {
            appRepo.resolveByKeys(needed)
        }
        val slots = keys.mapIndexed { index, key ->
            key?.let { packageKey ->
                val base = appsByKey?.get(packageKey) ?: resolved[packageKey]
                base?.copy(customIconPath = iconPaths.getOrNull(index))
            }
        }
        val density = getApplication<Application>().resources.displayMetrics.density
        val warmPx = (68f * density).toInt().coerceAtLeast(48)
        withContext(Dispatchers.IO) {
            slots.forEach { slotApp ->
                if (slotApp != null && slotApp.builtInShortcut == null) {
                    MonoIconCache.prewarm(slotApp, warmPx, getApplication())
                }
            }
        }
        mottoFlow.value = motto
        homeSlotsFlow.value = slots
        app.publishHomeSlots(slots, motto)
    }

    /**
     * The vendor framework treats “system” as a fixed COMMON/20 fallback.
     * Mirror InkBoard's chosen global baseline into apps that have not been
     * given an explicit override, so a user-selected default remains real
     * after the framework's foreground-app callback.
     */
    private suspend fun syncSystemDefaultApps(apps: List<AppInfo>) {
        val candidates = apps.filter { it.builtInShortcut == null }
        val knownSystemPackages = prefs.getSystemEpdPackages()
        val newlySystem = candidates.asSequence()
            .filter { it.packageName !in knownSystemPackages }
            .filter { !epdRepo.load(it.packageName).refreshEnabled }
            .map { it.packageName }
            .toSet()
        if (newlySystem.isEmpty()) return

        val allSystem = prefs.addSystemEpdPackages(newlySystem)
        val global = epdRepo.loadSystemProfile()
        newlySystem.forEach { packageName ->
            epdRepo.applySystemRefreshToApp(packageName, global)
        }
        // Keep the local snapshot read so future migrations can safely use the
        // same set without changing the provider's per-app data source.
        check(allSystem.containsAll(newlySystem))
    }

    fun refreshStatus() {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            statusFlow.value = StatusSnapshot(
                orientation = SystemControls.getOrientationMode(ctx),
                adbEnabled = SystemControls.isAdbEnabled(ctx)
            )
        }
    }

    /**
     * Launch the app and let the vendor framework apply its stored SystemUI
     * EPD profile when that app becomes foreground. Keeping activation in the
     * framework path avoids applying the target profile once while InkBoard is
     * still visible and then applying it a second time after the activity
     * switch.
     */
    fun launchApp(appInfo: AppInfo) {
        val ctx = getApplication<Application>()
        when (appInfo.builtInShortcut) {
            BuiltInShortcut.CLEAR_BACKGROUND -> {
                viewModelScope.launch(Dispatchers.IO) {
                    SystemControls.clearBackgroundApps(ctx)
                }
                return
            }
            BuiltInShortcut.LOCK_SCREEN -> {
                SystemControls.lockScreen(ctx)
                return
            }
            null -> Unit
        }
        appRepo.launchApp(appInfo)
    }

    fun assignSlot(index: Int, appInfo: AppInfo) {
        viewModelScope.launch {
            prefs.setSlot(index, appInfo.key)
            val iconPaths = prefs.slotIconPaths.first()
            val next = homeSlotsFlow.value.toMutableList()
            if (index in next.indices) {
                next[index] = appInfo.copy(customIconPath = iconPaths.getOrNull(index))
                homeSlotsFlow.value = next
                app.publishHomeSlots(next)
            }
            // Also ensure it exists in the drawer catalog.
            if (appsFlow.value.none { it.key == appInfo.key }) {
                appsFlow.value = appsFlow.value + appInfo
            }
        }
    }

    fun clearSlot(index: Int) {
        viewModelScope.launch {
            val previous = homeSlotsFlow.value.getOrNull(index)
            prefs.setSlot(index, null)
            val next = homeSlotsFlow.value.toMutableList()
            if (index in next.indices) {
                next[index] = null
                homeSlotsFlow.value = next
                app.publishHomeSlots(next)
            }
            previous?.let { MonoIconCache.invalidate(it.key) }
        }
    }

    fun setMotto(value: String) {
        viewModelScope.launch {
            prefs.setMotto(value)
            mottoFlow.value = value.trim().ifBlank { PreferencesRepository.DEFAULT_MOTTO }
        }
    }

    fun loadIconPage(category: KoboyoIconCategory, page: Int) {
        val requestGeneration = ++iconLoadGeneration
        val previous = iconPickerFlow.value
        viewModelScope.launch {
            // Keep the existing grid and pager in place while the next manual
            // page is read. On e-ink, replacing it with a loading screen is a
            // conspicuous flash and makes the navigation controls jump away.
            iconPickerFlow.value = previous.copy(
                category = category,
                page = page.coerceAtLeast(0),
                loading = true,
                error = null
            )
            val loaded = runCatching { iconRepo.loadPage(category, page) }
            if (requestGeneration != iconLoadGeneration) return@launch
            iconPickerFlow.value = loaded.getOrElse {
                previous.copy(
                    category = category,
                    page = page.coerceAtLeast(0),
                    loading = false,
                    error = "无法加载图标"
                )
            }
        }
    }

    fun setSlotIcon(index: Int, icon: KoboyoIcon) {
        viewModelScope.launch {
            // Bundled asset path only — no network download.
            val localPath = icon.localPath ?: iconRepo.resolveIconPath(icon)
            if (localPath.isNullOrBlank()) return@launch
            prefs.setSlotIcon(index, localPath)
            val current = homeSlotsFlow.value.getOrNull(index) ?: return@launch
            MonoIconCache.invalidate(current.key)
            val updated = current.copy(customIconPath = localPath)
            val density = getApplication<Application>().resources.displayMetrics.density
            val warmPx = (68f * density).toInt().coerceAtLeast(48)
            withContext(Dispatchers.IO) {
                MonoIconCache.prewarm(updated, warmPx, getApplication())
            }
            val next = homeSlotsFlow.value.toMutableList()
            if (index in next.indices) {
                next[index] = updated
                homeSlotsFlow.value = next
                app.publishHomeSlots(next)
            }
        }
    }

    fun loadEpdProfile(packageName: String) {
        if (!epdRepo.enabled) return
        val existing = epdFlow.value.profile?.takeIf { it.packageName == packageName }
        epdFlow.value = EpdSnapshot(profile = existing, loading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val stored = epdRepo.load(packageName)
            val followsSystem = prefs.getSystemEpdPackages().contains(packageName)
            val profile = if (followsSystem) stored.copy(refreshEnabled = false) else stored
            epdFlow.value = EpdSnapshot(profile = profile)
        }
    }

    fun saveEpdProfile(profile: EpdProfile) {
        if (!epdRepo.enabled) return
        // Render the selected value immediately.  This deliberately writes
        // only the stored SystemUI profile: activating the profile for the
        // foreground launcher makes this firmware recreate its window.
        epdFlow.value = EpdSnapshot(profile = profile)
        viewModelScope.launch(Dispatchers.IO) {
            epdWriteMutex.withLock {
                val followsSystem = !profile.refreshEnabled
                prefs.setSystemEpdPackage(profile.packageName, followsSystem)
                val storedProfile = if (followsSystem) {
                    val global = epdRepo.loadSystemProfile()
                    // Keep the just-edited DPI/contrast/bleach fields even
                    // when only the refresh strategy follows DEFAULT. The
                    // provider row still needs an explicit refresh profile on
                    // this firmware, so replace only those two fields with
                    // the current global baseline.
                    profile.copy(
                        refreshEnabled = true,
                        refreshMode = global.refreshMode,
                        refreshFrequency = global.refreshFrequency
                    )
                } else {
                    profile
                }
                val saved = epdRepo.save(storedProfile)
                if (!saved) {
                    epdFlow.value = EpdSnapshot(
                        profile = profile,
                        error = "无法写入系统 EPD 档案"
                    )
                }
            }
        }
    }

    fun loadSystemEpdProfile() {
        if (!epdRepo.enabled) return
        val existing = systemEpdFlow.value.profile
        systemEpdFlow.value = EpdSnapshot(profile = existing, loading = true)
        viewModelScope.launch(Dispatchers.IO) {
            systemEpdFlow.value = EpdSnapshot(profile = epdRepo.loadSystemProfile())
        }
    }

    fun applySystemEpdProfile(profile: EpdProfile) {
        if (!epdRepo.enabled) return
        systemEpdFlow.value = EpdSnapshot(profile = profile, loading = true)
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            epdWriteMutex.withLock {
                val applied = epdRepo.applySystemProfile(profile)
                val systemPackages = prefs.getSystemEpdPackages()
                val synced = applied && systemPackages.all { packageName ->
                    epdRepo.applySystemRefreshToApp(packageName, profile)
                }
                val inkBoard = epdRepo.load(ctx.packageName)
                val inkBoardFollowsSystem = ctx.packageName in systemPackages
                if (synced && !inkBoardFollowsSystem && inkBoard.refreshEnabled &&
                    (inkBoard.refreshMode != profile.refreshMode ||
                        inkBoard.refreshFrequency != profile.refreshFrequency)
                ) {
                    // The launcher is itself an app override. Restore its
                    // profile after updating the global baseline so changing
                    // the default does not silently change the desktop.
                    epdRepo.activate(inkBoard)
                }
                systemEpdFlow.value = if (synced) {
                    EpdSnapshot(profile = profile)
                } else {
                    EpdSnapshot(profile = profile, error = "无法应用系统 EPD 策略")
                }
            }
        }
    }

    /**
     * The vendor provider applies InkBoard's active EPD profile by broadcasting
     * DPI/bleach changes.  That causes the current launcher activity to lose
     * its window briefly, so this is called once only after its EPD page has
     * been dismissed.  Re-save inside the mutex to ensure the final fast-tapped
     * value is the one the hardware receives.
     */
    fun applyInkBoardEpdProfile(profile: EpdProfile) {
        if (!epdRepo.enabled) return
        val ctx = getApplication<Application>()
        if (profile.packageName != ctx.packageName) return

        epdFlow.value = EpdSnapshot(profile = profile)
        viewModelScope.launch(Dispatchers.IO) {
            epdWriteMutex.withLock {
                val saved = epdRepo.save(profile)
                val applied = saved && epdRepo.activate(profile)
                if (!saved || !applied) {
                    epdFlow.value = EpdSnapshot(
                        profile = profile,
                        error = "无法应用系统 EPD 档案"
                    )
                }
            }
        }
    }

    fun refreshEpdScreen() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!epdRepo.requestFullRefresh()) {
                // requestFullRefresh no-ops off S11A; only surface an error when
                // the stack is present but the call failed.
                if (epdRepo.enabled) {
                    epdFlow.value = epdFlow.value.copy(error = "系统未响应全刷请求")
                }
            }
        }
    }

    fun setOrientation(mode: OrientationMode) {
        val ctx = getApplication<Application>()
        SystemControls.setOrientationMode(ctx, mode)
        refreshStatus()
    }

    fun toggleAdb() {
        val ctx = getApplication<Application>()
        val enabled = !statusFlow.value.adbEnabled
        if (SystemControls.setAdbEnabled(ctx, enabled)) {
            // The privileged Yitoa service applies the broadcast shortly after
            // it is received, so reflect the requested state immediately and
            // reconcile it with Settings.Global after the service has settled.
            statusFlow.value = statusFlow.value.copy(adbEnabled = enabled)
            viewModelScope.launch {
                delay(400)
                refreshStatus()
            }
        }
    }

    fun openSystemSettings() = SystemControls.openSystemSettings(getApplication())

    fun startSender() {
        val request = ++senderRequestGeneration
        if (senderFlow.value.running || senderFlow.value.loading) return
        senderFlow.value = SenderSnapshot(loading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { senderServer.start() }
            if (request != senderRequestGeneration) {
                senderServer.stop()
                return@launch
            }
            senderFlow.value = result.fold(
                onSuccess = { info ->
                    SenderSnapshot(
                        running = true,
                        port = info.port,
                        url = info.url
                    )
                },
                onFailure = { error ->
                    SenderSnapshot(error = error.message ?: "无法启动文件传输服务")
                }
            )
        }
    }

    fun stopSender() {
        senderRequestGeneration += 1
        senderServer.stop()
        senderFlow.value = SenderSnapshot()
    }

    override fun onCleared() {
        senderRequestGeneration += 1
        senderServer.stop()
        super.onCleared()
    }

    companion object {
        private const val TAG = "InkBoardBoot"
    }
}
