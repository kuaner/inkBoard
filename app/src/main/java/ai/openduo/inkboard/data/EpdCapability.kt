package ai.openduo.inkboard.data

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Whether this device may use InkBoard's custom EPD UI and writes.
 *
 * Gate is the **product identity** of the 云思智学 S11A line — not “any e-ink
 * tablet” and not uninstallable app packages. On the connected unit:
 *
 * | prop / field              | value    |
 * | ------------------------- | -------- |
 * | `ro.product.model`        | `S11A`   |
 * | `ro.product.device`       | `EB1004P`|
 * | `ro.product.name`         | `EB1004P`|
 * | `ro.build.product`        | `EB1004P`|
 * | `ro.vendor.ota.model`     | `S11A`   |
 *
 * After the model matches, we still require the SystemUI EPD provider that
 * InkBoard writes to — so a renamed / incomplete port does not enable UI that
 * cannot save. Result is probed once (versioned) and cached by
 * [PreferencesRepository].
 */
object EpdCapability {

    /** Bump when the detection rules change so existing installs re-probe once. */
    const val PROBE_VERSION = 2

    private const val TAG = "InkBoardEpd"
    private const val PROVIDER_AUTHORITY = "com.android.systemui.eink"
    private const val PROVIDER_CLASS = "EinkSettingsProvider"

    /** Marketing / OTA model name on this product line. */
    private val MODEL_MARKERS = setOf("S11A")

    /** `ro.product.device` / `ro.product.name` / `ro.build.product`. */
    private val DEVICE_MARKERS = setOf("EB1004P")

    /**
     * Live probe of the current device. Prefer
     * [PreferencesRepository.getOrProbeEpdCustomization] so this only runs when
     * the cache is missing or [PROBE_VERSION] has advanced.
     */
    fun probe(context: Context): Boolean {
        val model = Build.MODEL.orEmpty()
        val device = Build.DEVICE.orEmpty()
        val product = Build.PRODUCT.orEmpty()
        val boardProduct = systemProperty("ro.build.product")
        val otaModel = systemProperty("ro.vendor.ota.model")
        val swVersion = systemProperty("ro.product.sw.version")

        val identityHits = buildList {
            if (matchesMarker(model, MODEL_MARKERS)) add("model=$model")
            if (matchesMarker(device, DEVICE_MARKERS)) add("device=$device")
            if (matchesMarker(product, DEVICE_MARKERS)) add("product=$product")
            if (matchesMarker(boardProduct, DEVICE_MARKERS)) add("build.product=$boardProduct")
            if (matchesMarker(otaModel, MODEL_MARKERS)) add("ota.model=$otaModel")
            if (MODEL_MARKERS.any { swVersion.contains(it, ignoreCase = true) }) {
                add("sw.version=$swVersion")
            }
        }

        // Product line first: generic Rockchip e-ink boards must not match.
        val isS11aFamily = identityHits.isNotEmpty()
        val provider = context.packageManager.resolveContentProvider(PROVIDER_AUTHORITY, 0)
        val hasProvider = provider != null &&
            (provider.name?.contains(PROVIDER_CLASS) == true ||
                provider.authority == PROVIDER_AUTHORITY)

        val supported = isS11aFamily && hasProvider
        Log.i(
            TAG,
            "probe v$PROBE_VERSION supported=$supported " +
                "identity=${identityHits.ifEmpty { listOf("none") }} " +
                "provider=${provider?.name ?: "none"}"
        )
        return supported
    }

    private fun matchesMarker(value: String, markers: Set<String>): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        return markers.any { marker -> trimmed.equals(marker, ignoreCase = true) }
    }

    private fun systemProperty(key: String): String {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java, String::class.java)
            get.invoke(null, key, "") as? String
        }.getOrNull().orEmpty()
    }
}
