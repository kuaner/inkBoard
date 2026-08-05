package ai.openduo.inkboard.util

import android.app.ActivityManager
import android.content.Context
import java.io.File
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
    /** 1-minute load average, e.g. "0.35", or "—" if unavailable. */
    val load1m: String
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
        load1m = readLoadAverage1m()
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

/** First field of `/proc/loadavg` (1-minute load). */
private fun readLoadAverage1m(): String {
    return runCatching {
        val raw = File("/proc/loadavg").readText().trim().substringBefore(' ')
        val value = raw.toDoubleOrNull() ?: return@runCatching "—"
        String.format(Locale.US, "%.2f", value)
    }.getOrDefault("—")
}
