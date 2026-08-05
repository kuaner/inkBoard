package ai.openduo.inkboard.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri

/**
 * The per-app EPD profile stored by the tablet's SystemUI provider.
 *
 * These names and ranges deliberately mirror the vendor's own “应用优化”
 * dialog, rather than introducing a second, launcher-specific display policy.
 */
data class EpdProfile(
    val packageName: String,
    val dpiEnabled: Boolean = false,
    val dpi: Int = 260,
    val refreshEnabled: Boolean = false,
    val refreshMode: EpdRefreshMode = EpdRefreshMode.COMMON,
    val refreshFrequency: Int = 20,
    val contrastEnabled: Boolean = false,
    val contrast: Int = 0,
    val bleachEnabled: Boolean = false,
    val bleachTextPlus: Boolean = false,
    val bleachIconColor: Int = 0,
    val bleachCoverColor: Int = 0,
    val bleachBackgroundColor: Int = 0
)

/**
 * Fixed density choices for S11A's per-app EPD setting.
 *
 * Android Settings calculates the tablet's display-size values as
 * 220 / 260 / 302 / 346 / 390 dpi. The vendor EPD dialog itself starts at
 * the native 260 dpi, so the launcher exposes the native default and the
 * three larger system values. This keeps the choices readable and avoids
 * presenting a second 10-dpi-at-a-time slider.
 */
enum class EpdDpiPreset(
    val value: Int,
    val title: String,
    val description: String
) {
    DEFAULT(260, "默认", "系统原生显示大小"),
    LARGE(302, "大", "系统显示大小：大"),
    LARGER(346, "较大", "系统显示大小：较大"),
    LARGEST(390, "最大", "系统显示大小：最大");

    companion object {
        fun fromValue(value: Int): EpdDpiPreset? =
            entries.firstOrNull { it.value == value }
    }
}

/** Values accepted by this Rockchip/Yitoa EPD driver. */
enum class EpdRefreshMode(
    val value: Int,
    val label: String,
    val description: String
) {
    AUTO(0, "AUTO", "由系统平衡清晰度与响应"),
    COMMON(7, "普通", "最稳定、最适合桌面与阅读"),
    A2(12, "A2", "最快速，允许更多残影"),
    A2_DITHER(13, "A2 抖动", "快速刷新并保留灰阶细节"),
    DU(14, "DU", "强对比、快速黑白更新"),
    DU4(15, "DU4", "更激进的快速黑白更新");

    companion object {
        fun fromValue(value: Int): EpdRefreshMode =
            entries.firstOrNull { it.value == value } ?: COMMON
    }
}

/**
 * Curated combinations for the two controls that most affect day-to-day
 * e-ink feel: waveform and the number of partial updates before a full
 * refresh.  Keep the raw values in one place so the UI does not ask users to
 * reason about six modes multiplied by eleven threshold values.
 */
enum class EpdRefreshPreset(
    val title: String,
    val modeLabel: String,
    val description: String,
    val mode: EpdRefreshMode,
    val frequency: Int
) {
    AUTO(
        title = "自动",
        modeLabel = "AUTO",
        description = "交给系统平衡速度与残影",
        mode = EpdRefreshMode.AUTO,
        frequency = 20
    ),
    CLEAN(
        title = "清晰",
        modeLabel = "普通",
        description = "静态桌面最稳，黑白边缘干净",
        mode = EpdRefreshMode.COMMON,
        frequency = 20
    ),
    READING(
        title = "灰阶",
        modeLabel = "A2 抖动",
        description = "保留灰阶细节，允许少量残影",
        mode = EpdRefreshMode.A2_DITHER,
        frequency = 30
    ),
    QUICK(
        title = "快速",
        modeLabel = "A2",
        description = "滑动与操作更快，残影明显增加",
        mode = EpdRefreshMode.A2,
        frequency = 50
    ),
    BLACK_WHITE(
        title = "黑白极速",
        modeLabel = "DU4",
        description = "纯黑白响应最快，不适合灰阶内容",
        mode = EpdRefreshMode.DU4,
        frequency = 70
    );

    fun matches(profile: EpdProfile): Boolean =
        profile.refreshEnabled &&
            profile.refreshMode == mode &&
            profile.refreshFrequency == frequency

    fun applyTo(profile: EpdProfile): EpdProfile = profile.copy(
        refreshEnabled = true,
        refreshMode = mode,
        refreshFrequency = frequency
    )

    companion object {
        fun fromProfile(profile: EpdProfile): EpdRefreshPreset? =
            entries.firstOrNull { it.matches(profile) }
    }
}

/**
 * Thin adapter over the exported SystemUI provider used by the stock EPD
 * “应用优化” dialog. Saving a profile changes only that app's row; the vendor
 * framework reads the row when the app becomes foreground.
 */
class EpdSettingsRepository(
    private val context: Context,
    /**
     * Device product gate (S11A family). When false every public call is a
     * no-op so callers never need scattered `if (epdEnabled)` checks.
     */
    private val isEnabled: () -> Boolean = { true }
) {

    val enabled: Boolean get() = isEnabled()

    private val resolver get() = context.contentResolver
    private val baseDpi: Int
        get() = context.resources.displayMetrics.densityDpi.coerceIn(MIN_DPI, MAX_DPI)

    fun load(packageName: String): EpdProfile {
        if (!enabled) return defaultProfile(packageName)
        return loadStored(packageName) ?: defaultProfile(packageName)
    }

    private fun loadStored(packageName: String): EpdProfile? = runCatching {
        val fallback = defaultProfile(packageName)
        resolver.query(
            SETTINGS_URI,
            PROJECTION,
            "$COLUMN_PACKAGE = ?",
            arrayOf(packageName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.toProfile(packageName, fallback) else null
        }
    }.getOrNull()

    fun save(profile: EpdProfile): Boolean {
        if (!enabled) return false
        return runCatching {
            val values = profile.toContentValues()
            val updated = resolver.update(
                SETTINGS_URI,
                values,
                "$COLUMN_PACKAGE = ?",
                arrayOf(profile.packageName)
            )
            if (updated > 0) {
                true
            } else {
                values.put(COLUMN_PACKAGE, profile.packageName)
                resolver.insert(SETTINGS_URI, values)
                // The vendor insert() implementation returns null even when the
                // SQLite insert succeeds, so verify through the normal read URI.
                resolver.query(
                    SETTINGS_URI,
                    arrayOf(COLUMN_PACKAGE),
                    "$COLUMN_PACKAGE = ?",
                    arrayOf(profile.packageName),
                    null
                )?.use { it.moveToFirst() } == true
            }
        }.getOrDefault(false)
    }

    /**
     * Runs the vendor's activation query for the supplied package.
     *
     * This is deliberately a query with a null projection. On S11A the
     * einksettingsupdate implementation reads every column from the returned
     * row; a narrow projection makes the vendor code fail. The query also
     * requires a non-null selectionArgs[0], which is why this cannot be
     * replaced with an adb content-query example.
     */
    fun activate(profile: EpdProfile): Boolean {
        if (!enabled) return false
        return runCatching {
            val applied = resolver.query(
                SETTINGS_UPDATE_URI,
                null,
                "$COLUMN_PACKAGE = ?",
                arrayOf(profile.packageName),
                null
            )?.use { cursor -> cursor.count > 0 } ?: false

            if (!applied) return@runCatching false

            // The provider handles refresh, contrast and bleaching itself. DPI is
            // delivered separately by the stock vendor broadcast, so only mirror
            // it when this app actually requested a DPI override.
            if (profile.dpiEnabled) {
                context.sendBroadcast(
                    Intent(ACTION_APP_CUSTOM).putExtra(EXTRA_CONTROL_TYPE, CONTROL_DPI)
                )
            }
            applied
        }.getOrDefault(false)
    }

    /** A one-shot clean frame; failure is harmless on builds that block hidden APIs. */
    fun requestFullRefresh(): Boolean {
        if (!enabled) return false
        return runCatching {
            val manager = context.getSystemService(EINK_SERVICE) ?: return@runCatching false
            val method = manager.javaClass.methods.firstOrNull {
                it.name == "sendOneFullFrame" && it.parameterCount == 0
            } ?: return@runCatching false
            method.invoke(manager)
            true
        }.getOrDefault(false)
    }

    fun defaultProfile(packageName: String) = EpdProfile(packageName = packageName, dpi = baseDpi)

    /**
     * Read the driver's current global baseline. The normal app cannot read
     * SystemProperties directly, so use the same public-hidden EinkManager
     * surface and a reflective property fallback used by the vendor SystemUI.
     */
    fun loadSystemProfile(): EpdProfile {
        if (!enabled) {
            return EpdProfile(
                packageName = SYSTEM_CONTROL_PACKAGE,
                refreshEnabled = true
            )
        }
        // The current hardware properties describe whichever app is in the
        // foreground. InkBoard itself intentionally has an independent row,
        // so those properties are not the stored DEFAULT after returning to
        // the launcher. Prefer the dedicated control row and use properties
        // only as a migration/fallback path for older installs.
        loadStored(SYSTEM_CONTROL_PACKAGE)?.takeIf { it.refreshEnabled }?.let { return it }

        val mode = readEinkMode()
        val frequency = readProperty("persist.vendor.fullmode_cnt", "20")
            .toIntOrNull()
            ?.coerceIn(0, 100)
            ?: 20
        return EpdProfile(
            packageName = SYSTEM_CONTROL_PACKAGE,
            refreshEnabled = true,
            refreshMode = EpdRefreshMode.fromValue(mode),
            refreshFrequency = frequency
        )
    }

    /**
     * The vendor Provider has no exported global-write API. A private control
     * row lets its own privileged activation path set both the waveform and
     * full-refresh threshold without granting InkBoard a fake system service.
     */
    fun applySystemProfile(profile: EpdProfile): Boolean {
        if (!enabled) return false
        val control = profile.copy(
            packageName = SYSTEM_CONTROL_PACKAGE,
            refreshEnabled = true,
            dpiEnabled = false,
            contrastEnabled = false,
            bleachEnabled = false,
            bleachTextPlus = false
        )
        return save(control) && activate(control)
    }

    /** Store a global refresh choice in a real app row while preserving its other EPD settings. */
    fun applySystemRefreshToApp(packageName: String, systemProfile: EpdProfile): Boolean {
        if (!enabled) return false
        val existing = load(packageName)
        return save(
            existing.copy(
                refreshEnabled = true,
                refreshMode = systemProfile.refreshMode,
                refreshFrequency = systemProfile.refreshFrequency
            )
        )
    }

    private fun readEinkMode(): Int {
        val managerMode = runCatching {
            val manager = context.getSystemService(EINK_SERVICE) ?: return@runCatching null
            val method = manager.javaClass.methods.firstOrNull {
                it.name == "getMode" && it.parameterCount == 0
            } ?: return@runCatching null
            (method.invoke(manager) as? String)?.toIntOrNull()
        }.getOrNull()
        return managerMode ?: readProperty("persist.sys.eink.mode", "7")
            .toIntOrNull()
            ?: EpdRefreshMode.COMMON.value
    }

    private fun readProperty(name: String, fallback: String): String = runCatching {
        val properties = Class.forName("android.os.SystemProperties")
        val get = properties.getMethod("get", String::class.java, String::class.java)
        get.invoke(null, name, fallback) as? String ?: fallback
    }.getOrDefault(fallback)

    private fun EpdProfile.toContentValues() = ContentValues().apply {
        put(COLUMN_DPI, dpi.coerceIn(MIN_DPI, MAX_DPI))
        put(COLUMN_DPI_ENABLED, dpiEnabled.asInt())
        put(COLUMN_REFRESH_ENABLED, refreshEnabled.asInt())
        put(COLUMN_REFRESH_MODE, refreshMode.value)
        put(COLUMN_REFRESH_FREQUENCY, refreshFrequency.coerceIn(0, 100))
        put(COLUMN_CONTRAST_ENABLED, contrastEnabled.asInt())
        put(COLUMN_CONTRAST, contrast.coerceIn(0, 80))
        // app_anim_filter is intentionally omitted: the vendor code reads it
        // but has no execution path, so exposing it would create a dead switch.
        put(COLUMN_BLEACH_ENABLED, bleachEnabled.asInt())
        put(COLUMN_BLEACH_TEXT_PLUS, bleachTextPlus.asInt())
        put(COLUMN_BLEACH_ICON, bleachIconColor.coerceIn(0, 255))
        put(COLUMN_BLEACH_COVER, bleachCoverColor.coerceIn(0, 150))
        put(COLUMN_BLEACH_BACKGROUND, bleachBackgroundColor.coerceIn(0, 255))
    }

    private fun Cursor.toProfile(packageName: String, fallback: EpdProfile): EpdProfile = EpdProfile(
        packageName = packageName,
        dpiEnabled = int(COLUMN_DPI_ENABLED, fallback.dpiEnabled.asInt()) == 1,
        dpi = int(COLUMN_DPI, fallback.dpi).let { if (it < MIN_DPI) fallback.dpi else it.coerceIn(MIN_DPI, MAX_DPI) },
        refreshEnabled = int(COLUMN_REFRESH_ENABLED, fallback.refreshEnabled.asInt()) == 1,
        refreshMode = EpdRefreshMode.fromValue(int(COLUMN_REFRESH_MODE, fallback.refreshMode.value)),
        refreshFrequency = int(COLUMN_REFRESH_FREQUENCY, fallback.refreshFrequency)
            .let { if (it < 0) fallback.refreshFrequency else it.coerceIn(0, 100) },
        contrastEnabled = int(COLUMN_CONTRAST_ENABLED, fallback.contrastEnabled.asInt()) == 1,
        contrast = int(COLUMN_CONTRAST, fallback.contrast).coerceIn(0, 80),
        bleachEnabled = int(COLUMN_BLEACH_ENABLED, fallback.bleachEnabled.asInt()) == 1,
        bleachTextPlus = int(COLUMN_BLEACH_TEXT_PLUS, fallback.bleachTextPlus.asInt()) == 1,
        bleachIconColor = int(COLUMN_BLEACH_ICON, fallback.bleachIconColor).coerceIn(0, 255),
        bleachCoverColor = int(COLUMN_BLEACH_COVER, fallback.bleachCoverColor).coerceIn(0, 150),
        bleachBackgroundColor = int(COLUMN_BLEACH_BACKGROUND, fallback.bleachBackgroundColor).coerceIn(0, 255)
    )

    private fun Cursor.int(column: String, fallback: Int): Int {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) fallback else getInt(index)
    }

    private fun Boolean.asInt() = if (this) 1 else 0

    companion object {
        const val MIN_DPI = 260
        const val MAX_DPI = 500

        private const val AUTHORITY = "com.android.systemui.eink"
        private const val PATH_SETTINGS = "einksettings"
        private const val PATH_UPDATE = "einksettingsupdate"
        private val SETTINGS_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_SETTINGS")
        private val SETTINGS_UPDATE_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_UPDATE")

        private const val EINK_SERVICE = "eink"
        private const val SYSTEM_CONTROL_PACKAGE = "ai.openduo.inkboard.system-default"
        private const val ACTION_APP_CUSTOM = "com.rockchip.eink.appcustom"
        private const val EXTRA_CONTROL_TYPE = "control_type"
        private const val CONTROL_DPI = "dpi"

        private const val COLUMN_PACKAGE = "package_name"
        private const val COLUMN_DPI = "app_dpi"
        private const val COLUMN_DPI_ENABLED = "is_dpi_setting"
        private const val COLUMN_REFRESH_ENABLED = "is_refresh_setting"
        private const val COLUMN_REFRESH_MODE = "refresh_mode"
        private const val COLUMN_REFRESH_FREQUENCY = "refresh_frequency"
        private const val COLUMN_CONTRAST_ENABLED = "is_contrast_setting"
        private const val COLUMN_CONTRAST = "app_contrast"
        private const val COLUMN_BLEACH_ENABLED = "app_bleach_mode"
        private const val COLUMN_BLEACH_TEXT_PLUS = "app_bleach_text_plus"
        private const val COLUMN_BLEACH_ICON = "app_bleach_icon_color"
        private const val COLUMN_BLEACH_COVER = "app_bleach_cover_color"
        private const val COLUMN_BLEACH_BACKGROUND = "app_bleach_bg_color"

        private val PROJECTION = arrayOf(
            COLUMN_PACKAGE,
            COLUMN_DPI,
            COLUMN_DPI_ENABLED,
            COLUMN_REFRESH_ENABLED,
            COLUMN_REFRESH_MODE,
            COLUMN_REFRESH_FREQUENCY,
            COLUMN_CONTRAST_ENABLED,
            COLUMN_CONTRAST,
            COLUMN_BLEACH_ENABLED,
            COLUMN_BLEACH_TEXT_PLUS,
            COLUMN_BLEACH_ICON,
            COLUMN_BLEACH_COVER,
            COLUMN_BLEACH_BACKGROUND
        )
    }
}
