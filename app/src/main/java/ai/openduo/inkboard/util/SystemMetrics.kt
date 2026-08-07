package ai.openduo.inkboard.util

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Lightweight host metrics for the home clock panel.
 * Read at most once per minute; never polled continuously.
 */
data class SystemMetrics(
    /** Used RAM as 0–100. */
    val memoryUsedPercent: Int,
    /** e.g. "1.8" */
    val memoryUsedGb: String,
    /** e.g. "3.5" */
    val memoryTotalGb: String,
    /** InkBoard process CPU usage over the last sampling interval. */
    val processCpuPercent: String
) {
    val memorySummary: String
        get() = "$memoryUsedPercent%  ·  ${memoryUsedGb}/${memoryTotalGb}G"
}

fun readSystemMetrics(context: Context): SystemMetrics {
    val memory = readMemory(context)
    return SystemMetrics(
        memoryUsedPercent = memory.percent,
        memoryUsedGb = memory.usedGb,
        memoryTotalGb = memory.totalGb,
        processCpuPercent = ProcessCpuSampler.readPercent()
    )
}

private data class MemoryReading(
    val percent: Int,
    val usedGb: String,
    val totalGb: String
)

private fun readMemory(context: Context): MemoryReading {
    val am = context.getSystemService(ActivityManager::class.java)
    val info = ActivityManager.MemoryInfo()
    am?.getMemoryInfo(info)
    val total = info.totalMem.coerceAtLeast(1L)
    val avail = info.availMem.coerceIn(0L, total)
    val used = (total - avail).coerceAtLeast(0L)
    val percent = ((used * 100.0) / total).roundToInt().coerceIn(0, 100)
    return MemoryReading(
        percent = percent,
        usedGb = formatGb(used),
        totalGb = formatGb(total)
    )
}

private fun formatGb(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f", gb)
}

/**
 * Public Android process CPU accounting, sampled at the same cadence as the
 * home metrics. Android does not expose the host's load average to ordinary
 * apps, so the UI labels this honestly as CPU rather than LOAD.
 */
private object ProcessCpuSampler {
    private var previousCpuMs: Long? = null
    private var previousWallMs: Long? = null

    @Synchronized
    fun readPercent(): String {
        val nowCpuMs = runCatching { Process.getElapsedCpuTime() }.getOrNull()
            ?: return "—"
        val nowWallMs = SystemClock.elapsedRealtime()
        val oldCpuMs = previousCpuMs
        val oldWallMs = previousWallMs
        previousCpuMs = nowCpuMs
        previousWallMs = nowWallMs

        if (oldCpuMs == null || oldWallMs == null) return "—"
        val cpuDeltaMs = nowCpuMs - oldCpuMs
        val wallDeltaMs = nowWallMs - oldWallMs
        if (cpuDeltaMs < 0L || wallDeltaMs <= 0L) return "—"

        val cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val percent = (cpuDeltaMs.toDouble() / (wallDeltaMs * cpuCount) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
        return String.format(Locale.US, "%d%%", percent)
    }
}
