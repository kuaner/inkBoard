package ai.openduo.inkboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "inkboard_prefs")

/**
 * Ordered home slots: slot_0 … slot_N store "package/activity" keys.
 * Empty string = vacant slot.
 *
 * Physical storage covers [MAX_SLOT_COUNT]; the visible desktop uses
 * [homeSlotCount] (8 / 12 / 16 / 24). Keys beyond the active count are kept
 * so shrinking density does not wipe user choices.
 */
class PreferencesRepository(private val context: Context) {

    companion object {
        /** Physical slot storage ceiling. */
        const val MAX_SLOT_COUNT = 24
        /** Default visible shortcuts (legacy layout). */
        const val DEFAULT_SLOT_COUNT = 8
        /** MENU density options. Default selection is [DEFAULT_SLOT_COUNT]. */
        val SLOT_COUNT_OPTIONS = listOf(4, 8, 12, 16, 24)

        /**
         * Backward-compatible alias used by older call sites that still mean
         * "how many slots the home grid shows by default". Prefer
         * [DEFAULT_SLOT_COUNT] / [MAX_SLOT_COUNT] / live [homeSlotCount].
         */
        const val SLOT_COUNT = DEFAULT_SLOT_COUNT

        /** Reading-themed default — two clauses for lead/trail type scale. */
        const val DEFAULT_MOTTO = "书读百遍，其义自见。一页有声，世界便慢。"
        private val LEGACY_DEFAULT_MOTTOS = setOf(
            "安静的，保持清醒。",
            "读完一页，再走一步。",
            "书读百遍，其义自见 · 一页有声，世界便慢",
            "书读百遍，其义自见。一页有声，世界便慢。今日所读，皆有回响。"
        )
        private const val SLOT_SCHEMA_VERSION = "6a"
        private val SLOT_SCHEMA = stringPreferencesKey("slot_schema")
        private val MOTTO = stringPreferencesKey("motto")
        private val HOME_SLOT_COUNT = intPreferencesKey("home_slot_count")
        private val SYSTEM_EPD_PACKAGES = stringSetPreferencesKey("epd_system_packages")
        private val EPD_CUSTOM_PROBED = booleanPreferencesKey("epd_custom_probed")
        private val EPD_CUSTOM_SUPPORTED = booleanPreferencesKey("epd_custom_supported")
        private val EPD_CUSTOM_PROBE_VERSION = intPreferencesKey("epd_custom_probe_version")

        fun slotKey(index: Int) = stringPreferencesKey("slot_$index")
        fun slotIconKey(index: Int) = stringPreferencesKey("slot_icon_$index")
        fun slotLabelKey(index: Int) = stringPreferencesKey("slot_label_$index")

        fun normalizeSlotCount(raw: Int?): Int {
            if (raw == null) return DEFAULT_SLOT_COUNT
            return SLOT_COUNT_OPTIONS.minByOrNull { kotlin.math.abs(it - raw) }
                ?: DEFAULT_SLOT_COUNT
        }
    }

    /**
     * Return the cached EPD-customization flag. [probe] runs only when this
     * install has never been probed, or when [EpdCapability.PROBE_VERSION]
     * advanced (detection rules changed). Later starts just read DataStore.
     */
    suspend fun getOrProbeEpdCustomization(probe: () -> Boolean): Boolean {
        val current = context.dataStore.data.first()
        val cachedVersion = current[EPD_CUSTOM_PROBE_VERSION] ?: 0
        if (current[EPD_CUSTOM_PROBED] == true &&
            cachedVersion == EpdCapability.PROBE_VERSION
        ) {
            return current[EPD_CUSTOM_SUPPORTED] == true
        }
        val supported = probe()
        context.dataStore.edit { prefs ->
            prefs[EPD_CUSTOM_PROBED] = true
            prefs[EPD_CUSTOM_SUPPORTED] = supported
            prefs[EPD_CUSTOM_PROBE_VERSION] = EpdCapability.PROBE_VERSION
        }
        return supported
    }

    /** Cached value only; null if this install has never been probed. */
    suspend fun cachedEpdCustomization(): Boolean? {
        val current = context.dataStore.data.first()
        if (current[EPD_CUSTOM_PROBED] != true) return null
        if ((current[EPD_CUSTOM_PROBE_VERSION] ?: 0) != EpdCapability.PROBE_VERSION) return null
        return current[EPD_CUSTOM_SUPPORTED] == true
    }

    /** Visible home shortcut count (8 / 12 / 16 / 24). Default 8. */
    val homeSlotCount: Flow<Int> = context.dataStore.data.map { prefs ->
        normalizeSlotCount(prefs[HOME_SLOT_COUNT])
    }

    suspend fun setHomeSlotCount(count: Int) {
        val normalized = normalizeSlotCount(count)
        context.dataStore.edit { prefs ->
            prefs[HOME_SLOT_COUNT] = normalized
        }
    }

    /**
     * All stored slot keys up to [MAX_SLOT_COUNT]. Callers that render the
     * home grid should take only the first [homeSlotCount] entries.
     */
    val slotKeys: Flow<List<String?>> = context.dataStore.data.map { prefs ->
        (0 until MAX_SLOT_COUNT).map { i ->
            prefs[slotKey(i)]?.takeIf { it.isNotBlank() }
        }
    }

    val slotIconPaths: Flow<List<String?>> = context.dataStore.data.map { prefs ->
        (0 until MAX_SLOT_COUNT).map { i ->
            prefs[slotIconKey(i)]?.takeIf { it.isNotBlank() }
        }
    }

    val slotLabels: Flow<List<String?>> = context.dataStore.data.map { prefs ->
        (0 until MAX_SLOT_COUNT).map { i ->
            prefs[slotLabelKey(i)]?.takeIf { it.isNotBlank() }
        }
    }

    suspend fun setSlotLabel(index: Int, label: String?) {
        if (index !in 0 until MAX_SLOT_COUNT) return
        context.dataStore.edit { prefs ->
            val value = label?.trim().orEmpty()
            if (value.isEmpty()) prefs.remove(slotLabelKey(index)) else prefs[slotLabelKey(index)] = value
        }
    }

    val motto: Flow<String> = context.dataStore.data.map { prefs ->
        val stored = prefs[MOTTO]?.trim().orEmpty()
        when {
            stored.isBlank() -> DEFAULT_MOTTO
            stored in LEGACY_DEFAULT_MOTTOS -> DEFAULT_MOTTO
            else -> stored
        }
    }

    suspend fun setSlot(index: Int, appKey: String?) {
        if (index !in 0 until MAX_SLOT_COUNT) return
        context.dataStore.edit { prefs ->
            prefs.remove(slotIconKey(index))
            prefs.remove(slotLabelKey(index))
            if (appKey.isNullOrBlank()) {
                prefs.remove(slotKey(index))
            } else {
                prefs[slotKey(index)] = appKey
            }
        }
    }

    suspend fun setSlotIcon(index: Int, path: String?) {
        if (index !in 0 until MAX_SLOT_COUNT) return
        context.dataStore.edit { prefs ->
            if (path.isNullOrBlank()) {
                prefs.remove(slotIconKey(index))
            } else {
                prefs[slotIconKey(index)] = path
            }
        }
    }

    suspend fun setMotto(value: String) {
        context.dataStore.edit { prefs ->
            val motto = value.trim()
            if (motto.isBlank() || motto == DEFAULT_MOTTO || motto in LEGACY_DEFAULT_MOTTOS) {
                prefs.remove(MOTTO)
            } else {
                prefs[MOTTO] = motto
            }
        }
    }

    /** Packages whose refresh strategy follows InkBoard's global baseline. */
    suspend fun getSystemEpdPackages(): Set<String> =
        context.dataStore.data.first()[SYSTEM_EPD_PACKAGES].orEmpty()

    suspend fun addSystemEpdPackages(packages: Set<String>): Set<String> {
        if (packages.isEmpty()) return getSystemEpdPackages()
        val result = context.dataStore.edit { prefs ->
            prefs[SYSTEM_EPD_PACKAGES] = (prefs[SYSTEM_EPD_PACKAGES].orEmpty() + packages)
        }
        return result[SYSTEM_EPD_PACKAGES].orEmpty()
    }

    suspend fun setSystemEpdPackage(packageName: String, followsSystem: Boolean) {
        context.dataStore.edit { prefs ->
            val packages = prefs[SYSTEM_EPD_PACKAGES].orEmpty().toMutableSet()
            if (followsSystem) packages += packageName else packages -= packageName
            prefs[SYSTEM_EPD_PACKAGES] = packages
        }
    }

    /**
     * Seed launcher slots once, while preserving a user's choices.
     * Seeds only the default visible count; extra density slots stay empty
     * until the user assigns them.
     */
    suspend fun ensureDefaults(preferredKeys: List<String>) {
        context.dataStore.edit { prefs ->
            val schema = prefs[SLOT_SCHEMA]
            val existingKeys = (0 until MAX_SLOT_COUNT).map { index -> prefs[slotKey(index)] }
            val seedCount = DEFAULT_SLOT_COUNT
            if (schema != SLOT_SCHEMA_VERSION) {
                // The previous build could seed Onyx Launcher itself as a shortcut.
                // Remove only that known legacy default; preserve other choices.
                val containsLegacyLauncher = existingKeys.any { key ->
                    key?.startsWith("com.onyx/com.onyx.StartupActivity") == true
                }
                val containsLegacySystemApp = existingKeys.any { key ->
                    key?.let(::isSystemAppKey) == true
                }
                val firstEightEmpty = existingKeys.take(seedCount).all { it == null }
                if (containsLegacyLauncher || containsLegacySystemApp || firstEightEmpty) {
                    for (i in 0 until seedCount) {
                        prefs.remove(slotKey(i))
                    }
                    preferredKeys.take(seedCount).forEachIndexed { i, key ->
                        prefs[slotKey(i)] = key
                    }
                }
                // Existing installs without home_slot_count keep default 8.
                if (prefs[HOME_SLOT_COUNT] == null) {
                    prefs[HOME_SLOT_COUNT] = DEFAULT_SLOT_COUNT
                }
                prefs[SLOT_SCHEMA] = SLOT_SCHEMA_VERSION
                return@edit
            }
            val anySet = existingKeys.take(seedCount).any { it != null }
            if (anySet) return@edit
            preferredKeys.take(seedCount).forEachIndexed { i, key ->
                prefs[slotKey(i)] = key
            }
            if (prefs[HOME_SLOT_COUNT] == null) {
                prefs[HOME_SLOT_COUNT] = DEFAULT_SLOT_COUNT
            }
        }
    }

    private fun isSystemAppKey(key: String): Boolean {
        val packageName = key.substringBefore('/')
        return runCatching {
            val flags = context.packageManager.getApplicationInfo(packageName, 0).flags
            flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
        }.getOrDefault(false)
    }
}
