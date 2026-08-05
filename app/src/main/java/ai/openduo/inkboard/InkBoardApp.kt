package ai.openduo.inkboard

import android.app.Application
import android.util.Log
import ai.openduo.inkboard.data.AppInfo
import ai.openduo.inkboard.data.AppRepository
import ai.openduo.inkboard.data.EpdCapability
import ai.openduo.inkboard.data.MonoIconCache
import ai.openduo.inkboard.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

/**
 * Starts resolving the eight home shortcuts as soon as the process exists,
 * before [MainActivity] has finished composing an empty grid.
 *
 * Also resolves EPD customization support once (firmware/service probe, cached
 * forever in DataStore) so the first UI frame already knows whether to show
 * SYSTEM EPD / per-app EPD.
 */
class InkBoardApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _homeSlots = MutableStateFlow<List<AppInfo?>>(
        List(PreferencesRepository.SLOT_COUNT) { null }
    )
    private val _motto = MutableStateFlow(PreferencesRepository.DEFAULT_MOTTO)
    private val _homeReady = MutableStateFlow(false)
    private val _epdCustomizationEnabled = MutableStateFlow(false)
    private val _epdCapabilityReady = MutableStateFlow(false)

    val homeSlots: StateFlow<List<AppInfo?>> = _homeSlots.asStateFlow()
    val motto: StateFlow<String> = _motto.asStateFlow()
    val homeReady: StateFlow<Boolean> = _homeReady.asStateFlow()
    val epdCustomizationEnabled: StateFlow<Boolean> = _epdCustomizationEnabled.asStateFlow()
    val epdCapabilityReady: StateFlow<Boolean> = _epdCapabilityReady.asStateFlow()

    private val bootstrapMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        // Kick off before any activity collects UI state.
        appScope.launch { bootstrapHomeSlots() }
    }

    suspend fun awaitHomeBootstrap(): List<AppInfo?> {
        bootstrapHomeSlots()
        return _homeSlots.value
    }

    suspend fun awaitEpdCapability(): Boolean {
        bootstrapHomeSlots()
        return _epdCustomizationEnabled.value
    }

    private suspend fun bootstrapHomeSlots() = withContext(Dispatchers.IO) {
        bootstrapMutex.withLock {
            if (_homeReady.value && _epdCapabilityReady.value) return@withLock
            val prefs = PreferencesRepository(this@InkBoardApp)
            val appRepo = AppRepository(this@InkBoardApp)

            val elapsed = measureTimeMillis {
                if (!_epdCapabilityReady.value) {
                    val supported = prefs.getOrProbeEpdCustomization {
                        EpdCapability.probe(this@InkBoardApp)
                    }
                    _epdCustomizationEnabled.value = supported
                    _epdCapabilityReady.value = true
                    Log.i(TAG, "epd customization enabled=$supported")
                }

                if (!_homeReady.value) {
                    val keys = prefs.slotKeys.first()
                    val iconPaths = prefs.slotIconPaths.first()
                    val motto = prefs.motto.first()
                    val resolved = appRepo.resolveByKeys(keys.filterNotNull())
                    val slots = keys.mapIndexed { index, key ->
                        key?.let { packageKey ->
                            resolved[packageKey]?.copy(customIconPath = iconPaths.getOrNull(index))
                        }
                    }
                    // Decode monochrome bitmaps off the main thread so the first home
                    // composition can paint icons from cache instead of converting them.
                    val density = resources.displayMetrics.density
                    val warmPx = (68f * density).toInt().coerceAtLeast(48)
                    slots.forEach { slotApp ->
                        if (slotApp != null && slotApp.builtInShortcut == null) {
                            MonoIconCache.prewarm(slotApp, warmPx, this@InkBoardApp)
                        }
                    }
                    _motto.value = motto
                    _homeSlots.value = slots
                    _homeReady.value = true
                }
            }
            Log.i(
                TAG,
                "home bootstrap ${elapsed}ms ready=${slotsReadySummary(_homeSlots.value)} " +
                    "epd=${_epdCustomizationEnabled.value}"
            )
        }
    }

    fun publishHomeSlots(slots: List<AppInfo?>, motto: String? = null) {
        if (motto != null) _motto.value = motto
        _homeSlots.value = slots
        _homeReady.value = true
    }

    private fun slotsReadySummary(slots: List<AppInfo?>): String {
        val filled = slots.count { it != null }
        return "$filled/${slots.size}"
    }

    companion object {
        private const val TAG = "InkBoardBoot"
    }
}
