package ai.openduo.inkboard.ui.home

import ai.openduo.inkboard.ui.LauncherActions
import ai.openduo.inkboard.ui.components.AppGlyph
import ai.openduo.inkboard.ui.components.EditorialHeader
import ai.openduo.inkboard.ui.components.Hairline
import ai.openduo.inkboard.ui.components.HomeLayoutSpec
import ai.openduo.inkboard.ui.components.PaperFrame
import ai.openduo.inkboard.ui.components.ShortcutColumns
import ai.openduo.inkboard.ui.components.VerticalRule
import ai.openduo.inkboard.ui.components.homeGridRowsFor
import ai.openduo.inkboard.ui.components.homeLayoutSpec

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openduo.inkboard.LauncherUiState
import ai.openduo.inkboard.data.AppInfo
import ai.openduo.inkboard.ui.components.HomeDateParts
import ai.openduo.inkboard.ui.components.inkClickable
import ai.openduo.inkboard.ui.epd.EpdEntryPoints
import ai.openduo.inkboard.ui.theme.InkBlack
import ai.openduo.inkboard.ui.theme.InkMid
import ai.openduo.inkboard.ui.theme.InkSoft
import ai.openduo.inkboard.util.SystemMetrics
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun HomeSurface(
    state: LauncherUiState,
    actions: LauncherActions,
    epd: EpdEntryPoints,
    time: String,
    dateParts: HomeDateParts,
    metrics: SystemMetrics,
    motto: String,
    onOpenApps: () -> Unit,
    onEditSlot: (Int) -> Unit,
    onOpenMotto: () -> Unit,
    onOpenSender: () -> Unit,
    onOpenControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    val slotCount = state.homeSlotCount
    val rows = homeGridRowsFor(slotCount)
    val layout = homeLayoutSpec(slotCount)

    PaperFrame(modifier) { wide ->
        EditorialHeader(
            section = "HOME",
            onEdit = onOpenApps,
            onEpd = epd.open,
            onSender = onOpenSender,
            onMenu = onOpenControls
        )
        Spacer(Modifier.height(if (wide) 12.dp else 10.dp))

        val gridModifier = if (layout.gridHeight != null) {
            Modifier
                .fillMaxWidth()
                .height(if (wide) layout.gridHeight else layout.gridHeight + 10.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .weight(layout.gridWeight)
        }

        // Classic 8-slot: hero fills remaining space (weight 1). Dense: shared weights.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (layout.gridHeight != null) Modifier.weight(1f)
                    else Modifier.weight(layout.heroWeight)
                ),
            contentAlignment = if (wide) Alignment.CenterStart else Alignment.Center
        ) {
            HomeHero(
                modifier = Modifier.fillMaxSize(),
                dateParts = dateParts,
                time = time,
                metrics = metrics,
                motto = motto,
                onOpenMotto = onOpenMotto,
                wide = wide,
                layout = layout
            )
        }

        Spacer(Modifier.height(if (wide) 12.dp else 10.dp))

        AppColumns(
            slots = state.slots.take(slotCount),
            rows = rows,
            layout = layout,
            onLaunch = actions.onLaunch,
            onEditSlot = onEditSlot,
            compact = !wide,
            modifier = gridModifier
        )
    }
}

@Composable
private fun HomeHero(
    modifier: Modifier,
    dateParts: HomeDateParts,
    time: String,
    metrics: SystemMetrics,
    motto: String,
    onOpenMotto: () -> Unit,
    wide: Boolean,
    layout: HomeLayoutSpec
) {
    val timeSp = if (wide) layout.timeSpWide else layout.timeSpCompact
    val leadSp = if (wide) layout.mottoLeadSpWide else layout.mottoLeadSpCompact
    val trailSp = if (wide) layout.mottoTrailSpWide else layout.mottoTrailSpCompact

    if (wide) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroHeadline(
                motto = motto,
                onOpenMotto = onOpenMotto,
                wide = true,
                leadSp = leadSp,
                trailSp = trailSp,
                maxLines = layout.mottoMaxLines,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 36.dp)
            )
            HeroTimePanel(
                dateParts = dateParts,
                time = time,
                metrics = metrics,
                timeSizeSp = timeSp,
                compact = false,
                showStats = layout.showStats,
                centered = false
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
                timeSizeSp = timeSp,
                compact = true,
                showStats = layout.showStats,
                centered = true
            )
            Spacer(Modifier.height(if (layout.mottoMaxLines <= 1) 10.dp else 24.dp))
            HeroHeadline(
                motto = motto,
                onOpenMotto = onOpenMotto,
                wide = false,
                leadSp = leadSp,
                trailSp = trailSp,
                maxLines = layout.mottoMaxLines,
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
    leadSp: Int,
    trailSp: Int,
    maxLines: Int,
    modifier: Modifier = Modifier
) {
    val parts = if (maxLines <= 1) emptyList() else splitMottoLeadTrail(motto)
    Column(
        modifier = modifier.inkClickable(onClick = onOpenMotto),
        horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally
    ) {
        if (parts.size >= 2 && maxLines >= 2) {
            Text(
                text = parts[0],
                color = InkBlack,
                fontSize = leadSp.sp,
                lineHeight = (leadSp * 1.18f).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.0).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (wide) TextAlign.Start else TextAlign.Center
            )
            Spacer(Modifier.height(if (wide) 12.dp else 8.dp))
            Text(
                text = parts[1],
                color = InkBlack,
                fontSize = trailSp.sp,
                lineHeight = (trailSp * 1.2f).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                maxLines = (maxLines - 1).coerceAtLeast(1),
                overflow = TextOverflow.Ellipsis,
                textAlign = if (wide) TextAlign.Start else TextAlign.Center
            )
        } else {
            Text(
                text = motto,
                color = InkBlack,
                fontSize = leadSp.sp,
                lineHeight = (leadSp * 1.15f).sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.8).sp,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (wide) TextAlign.Start else TextAlign.Center
            )
        }
    }
}

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
    showStats: Boolean,
    centered: Boolean
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
        if (showStats) {
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
}

@Composable
private fun AppColumns(
    slots: List<AppInfo?>,
    rows: Int,
    layout: HomeLayoutSpec,
    onLaunch: (AppInfo) -> Unit,
    onEditSlot: (Int) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Hairline()
        Column(modifier = Modifier.weight(1f)) {
            for (row in 0 until rows) {
                if (row > 0) Hairline()
                Row(modifier = Modifier.weight(1f)) {
                    for (column in 0 until ShortcutColumns) {
                        if (column > 0) VerticalRule()
                        val index = row * ShortcutColumns + column
                        AppColumn(
                            index = index,
                            app = slots.getOrNull(index),
                            layout = layout,
                            compact = compact,
                            onClick = {
                                slots.getOrNull(index)?.let(onLaunch) ?: onEditSlot(index)
                            },
                            onLongClick = { onEditSlot(index) },
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
    layout: HomeLayoutSpec,
    compact: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .inkClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(
                horizontal = if (compact) 10.dp else 16.dp,
                vertical = if (compact) 8.dp else 12.dp
            )
    ) {
        val density = LocalDensity.current
        val cellMinPx = min(constraints.maxWidth, constraints.maxHeight).toFloat()
        val iconDp: Dp = with(density) {
            (cellMinPx * layout.iconRatio).toDp().coerceIn(layout.iconMinDp, layout.iconMaxDp)
        }
        val labelSp = with(density) {
            (cellMinPx * 0.10f).toSp().value.roundToInt().coerceIn(11, 16)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (layout.showSlotIndex) {
                Text(
                    text = (index + 1).toString().padStart(2, '0'),
                    color = InkMid,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.7.sp
                )
            }
            Spacer(Modifier.weight(1f))
            if (app == null) {
                Text(
                    text = "+",
                    color = InkSoft,
                    fontSize = if (compact) 32.sp else 42.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AppGlyph(app = app, size = iconDp)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = app?.label ?: "ADD APP",
                color = if (app == null) InkSoft else InkBlack,
                fontSize = labelSp.sp,
                fontWeight = if (app == null) FontWeight.Medium else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
