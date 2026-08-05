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
 */
class PreferencesRepository(private val context: Context) {

    companion object {
        const val SLOT_COUNT = 8
        /** Reading-themed default — two clauses for lead/trail type scale. */
        const val DEFAULT_MOTTO = "书读百遍，其义自见。一页有声，世界便慢。"
        private val LEGACY_DEFAULT_MOTTOS = setOf(
            "安静的，保持清醒。",
            "读完一页，再走一步。",
            "书读百遍，其义自见 · 一页有声，世界便慢",
            "书读百遍，其义自见。一页有声，世界便慢。今日所读，皆有回响。"
        )
        private const val SLOT_SCHEMA_VERSION = "5a"
        private val SLOT_SCHEMA = stringPreferencesKey("slot_schema")
        private val MOTTO = stringPreferencesKey("motto")
        private val SYSTEM_EPD_PACKAGES = stringSetPreferencesKey("epd_system_packages")
        private val EPD_CUSTOM_PROBED = booleanPreferencesKey("epd_custom_probed")
        private val EPD_CUSTOM_SUPPORTED = booleanPreferencesKey("epd_custom_supported")
        private val EPD_CUSTOM_PROBE_VERSION = intPreferencesKey("epd_custom_probe_version")

        fun slotKey(index: Int) = stringPreferencesKey("slot_$index")
        fun slotIconKey(index: Int) = stringPreferencesKey("slot_icon_$index")
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

    val slotKeys: Flow<List<String?>> = context.dataStore.data.map { prefs ->
        (0 until SLOT_COUNT).map { i ->
            prefs[slotKey(i)]?.takeIf { it.isNotBlank() }
        }
    }

    val slotIconPaths: Flow<List<String?>> = context.dataStore.data.map { prefs ->
        (0 until SLOT_COUNT).map { i ->
            prefs[slotIconKey(i)]?.takeIf { it.isNotBlank() }
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
        if (index !in 0 until SLOT_COUNT) return
        context.dataStore.edit { prefs ->
            prefs.remove(slotIconKey(index))
            if (appKey.isNullOrBlank()) {
                prefs.remove(slotKey(index))
            } else {
                prefs[slotKey(index)] = appKey
            }
        }
    }

    suspend fun setSlotIcon(index: Int, path: String?) {
        if (index !in 0 until SLOT_COUNT) return
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

    /** Seed eight launcher slots once, while preserving a user's choices. */
    suspend fun ensureDefaults(preferredKeys: List<String>) {
        context.dataStore.edit { prefs ->
            val schema = prefs[SLOT_SCHEMA]
            val existingKeys = (0 until SLOT_COUNT).map { index -> prefs[slotKey(index)] }
            if (schema != SLOT_SCHEMA_VERSION) {
                // The previous build could seed Onyx Launcher itself as a shortcut.
                // Remove only that known legacy default; preserve other choices.
                val containsLegacyLauncher = existingKeys.any { key ->
                    key?.startsWith("com.onyx/com.onyx.StartupActivity") == true
                }
                val containsLegacySystemApp = existingKeys.any { key ->
                    key?.let(::isSystemAppKey) == true
                }
                if (containsLegacyLauncher || containsLegacySystemApp || existingKeys.all { it == null }) {
                    for (i in 0 until SLOT_COUNT) {
                        prefs.remove(slotKey(i))
                    }
                    preferredKeys.take(SLOT_COUNT).forEachIndexed { i, key ->
                        prefs[slotKey(i)] = key
                    }
                }
                prefs[SLOT_SCHEMA] = SLOT_SCHEMA_VERSION
                return@edit
            }
            val anySet = existingKeys.any { it != null }
            if (anySet) return@edit
            preferredKeys.take(SLOT_COUNT).forEachIndexed { i, key ->
                prefs[slotKey(i)] = key
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
