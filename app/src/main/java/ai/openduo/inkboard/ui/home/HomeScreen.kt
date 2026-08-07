package ai.openduo.inkboard.ui.home

import ai.openduo.inkboard.ui.LauncherActions
import ai.openduo.inkboard.ui.components.AppGlyph
import ai.openduo.inkboard.ui.components.EditorialHeader
import ai.openduo.inkboard.ui.components.Hairline
import ai.openduo.inkboard.ui.components.PaperFrame
import ai.openduo.inkboard.ui.components.ShortcutColumns
import ai.openduo.inkboard.ui.components.ShortcutRows
import ai.openduo.inkboard.ui.components.VerticalRule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openduo.inkboard.LauncherUiState
import ai.openduo.inkboard.data.AppInfo
import ai.openduo.inkboard.data.BuiltInShortcut
import ai.openduo.inkboard.ui.components.HomeDateParts
import ai.openduo.inkboard.ui.components.inkClickable
import ai.openduo.inkboard.ui.epd.EpdEntryPoints
import ai.openduo.inkboard.ui.theme.InkBlack
import ai.openduo.inkboard.ui.theme.InkMid
import ai.openduo.inkboard.ui.theme.InkSoft
import ai.openduo.inkboard.util.SystemMetrics

@Composable
internal fun HomeSurface(
    state: LauncherUiState,
    actions: LauncherActions,
    epd: EpdEntryPoints,
    time: String,
    dateParts: HomeDateParts,
    metrics: SystemMetrics,
    motto: String,
    onEditApps: () -> Unit,
    onEditSlot: (Int) -> Unit,
    onOpenMotto: () -> Unit,
    onOpenSender: () -> Unit,
    onOpenControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    PaperFrame(modifier) { wide ->
        EditorialHeader(
            section = "HOME",
            onEdit = onEditApps,
            onEpd = epd.openSystem,
            onSender = onOpenSender,
            onMenu = onOpenControls
        )
        Spacer(Modifier.height(if (wide) 12.dp else 10.dp))

        // Hero takes all space above the fixed app grid, with large type so
        // the band feels full (not a thin strip + void).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = if (wide) Alignment.CenterStart else Alignment.Center
        ) {
            HomeHero(
                modifier = Modifier.fillMaxWidth(),
                dateParts = dateParts,
                time = time,
                metrics = metrics,
                motto = motto,
                onOpenMotto = onOpenMotto,
                wide = wide
            )
        }

        Spacer(Modifier.height(if (wide) 12.dp else 10.dp))

        // Keep the app grid at the size that already felt right.
        AppColumns(
            slots = state.slots,
            onLaunch = actions.onLaunch,
            onEditSlot = onEditSlot,
            onOpenEpd = epd.openApp,
            compact = !wide,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (wide) 320.dp else 330.dp)
        )
    }
}

/**
 * Landing-style hero (no game, no floating ornaments).
 *
 * Landscape — classic split landing:
 *   left  : kicker + headline (motto) with type hierarchy
 *   right : oversized time as the visual anchor + meta
 *
 * Portrait — stacked landing:
 *   time → headline → meta strip
 */
@Composable
private fun HomeHero(
    modifier: Modifier,
    dateParts: HomeDateParts,
    time: String,
    metrics: SystemMetrics,
    motto: String,
    onOpenMotto: () -> Unit,
    wide: Boolean
) {
    if (wide) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroHeadline(
                motto = motto,
                onOpenMotto = onOpenMotto,
                wide = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 36.dp)
            )
            HeroTimePanel(
                dateParts = dateParts,
                time = time,
                metrics = metrics,
                timeSizeSp = 108,
                compact = false
            )
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            HeroTimePanel(
                dateParts = dateParts,
                time = time,
                metrics = metrics,
                timeSizeSp = 92,
                compact = true,
                centered = true
            )
            Spacer(Modifier.height(24.dp))
            HeroHeadline(
                motto = motto,
                onOpenMotto = onOpenMotto,
                wide = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HeroHeadline(
    motto: String,
    onOpenMotto: () -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier
) {
    val parts = splitMottoLeadTrail(motto)
    Column(
        modifier = modifier.inkClickable(onClick = onOpenMotto),
        horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally
    ) {
        if (parts.size >= 2) {
            Text(
                text = parts[0],
                color = InkBlack,
                fontSize = if (wide) 58.sp else 40.sp,
                lineHeight = if (wide) 68.sp else 48.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.1).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (wide) TextAlign.Start else TextAlign.Center
            )
            Spacer(Modifier.height(if (wide) 14.dp else 10.dp))
            Text(
                text = parts[1],
                color = InkBlack,
                fontSize = if (wide) 32.sp else 22.sp,
                lineHeight = if (wide) 40.sp else 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (wide) TextAlign.Start else TextAlign.Center
            )
        } else {
            Text(
                text = motto,
                color = InkBlack,
                fontSize = if (wide) 54.sp else 38.sp,
                lineHeight = if (wide) 64.sp else 46.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.9).sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (wide) TextAlign.Start else TextAlign.Center
            )
        }
    }
}

/** Prefer sentence end, then softer pauses — type hierarchy for landing copy. */
private fun splitMottoLeadTrail(motto: String): List<String> {
    val text = motto.trim()
    if (text.isEmpty()) return emptyList()
    val preferred = listOf('。', '！', '？', '!', '?', '；', ';', '·', '，', '、', ',')
    for (mark in preferred) {
        val index = text.indexOf(mark)
        if (index in 1 until text.lastIndex) {
            val lead = text.substring(0, index + 1).trim()
            val trail = text.substring(index + 1).trim().trimStart('·', ' ', '　')
            if (lead.isNotEmpty() && trail.isNotEmpty()) return listOf(lead, trail)
        }
    }
    return listOf(text)
}

@Composable
private fun HeroTimePanel(
    dateParts: HomeDateParts,
    time: String,
    metrics: SystemMetrics,
    timeSizeSp: Int,
    compact: Boolean,
    centered: Boolean = false
) {
    val hours = time.substringBefore(":")
    val minutes = time.substringAfter(":")
    val timeSize = timeSizeSp.sp
    val weekdayShort = dateParts.weekday.take(3)
    val dateLine = "$weekdayShort · ${dateParts.dayOfMonth} ${dateParts.monthYear.substringBefore(' ')}"
    val statsLine = "MEM ${metrics.memoryUsedPercent}%  ·  CPU ${metrics.processCpuPercent}"
    val align = if (centered) Alignment.CenterHorizontally else Alignment.End
    val textAlign = if (centered) TextAlign.Center else TextAlign.End

    Column(
        horizontalAlignment = align,
        modifier = Modifier.wrapContentWidth()
    ) {
        Text(
            text = "$hours:$minutes",
            color = InkBlack,
            fontFamily = FontFamily.Monospace,
            fontSize = timeSize,
            lineHeight = timeSize,
            fontWeight = FontWeight.Black,
            letterSpacing = (-3.2).sp,
            textAlign = textAlign
        )
        Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
        Text(
            text = dateLine,
            color = InkBlack,
            fontSize = if (compact) 15.sp else 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            textAlign = textAlign
        )
        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
        Text(
            text = statsLine,
            color = InkBlack,
            fontFamily = FontFamily.Monospace,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            textAlign = textAlign
        )
    }
}

@Composable
private fun AppColumns(
    slots: List<AppInfo?>,
    onLaunch: (AppInfo) -> Unit,
    onEditSlot: (Int) -> Unit,
    onOpenEpd: ((AppInfo) -> Unit)?,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Hairline()
        Column(modifier = Modifier.weight(1f)) {
            for (row in 0 until ShortcutRows) {
                if (row > 0) Hairline()
                Row(modifier = Modifier.weight(1f)) {
                    for (column in 0 until ShortcutColumns) {
                        if (column > 0) VerticalRule()
                        val index = row * ShortcutColumns + column
                        AppColumn(
                            index = index,
                            app = slots.getOrNull(index),
                            compact = compact,
                            onClick = {
                                slots.getOrNull(index)?.let(onLaunch) ?: onEditSlot(index)
                            },
                            onLongClick = { onEditSlot(index) },
                            onOpenEpd = onOpenEpd,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppColumn(
    index: Int,
    app: AppInfo?,
    compact: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenEpd: ((AppInfo) -> Unit)?,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .inkClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "0${index + 1}",
                color = InkMid,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.7.sp
            )
            Spacer(Modifier.weight(1f))
            if (onOpenEpd != null && app != null && app.builtInShortcut == null) {
                Text(
                    text = "EPD",
                    color = InkBlack,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier
                        .inkClickable(onClick = { onOpenEpd(app) })
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (app == null) {
            Text(
                text = "+",
                color = InkSoft,
                fontSize = if (compact) 36.sp else 46.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AppGlyph(app = app, size = if (compact) 56.dp else 68.dp)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = app?.label ?: "ADD APP",
            color = if (app == null) InkSoft else InkBlack,
            fontSize = if (compact) 14.sp else 16.sp,
            fontWeight = if (app == null) FontWeight.Medium else FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
