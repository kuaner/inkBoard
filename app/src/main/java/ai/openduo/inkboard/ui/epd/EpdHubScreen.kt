package ai.openduo.inkboard.ui.epd

import ai.openduo.inkboard.data.EpdRefreshPreset
import ai.openduo.inkboard.ui.components.AppGlyph
import ai.openduo.inkboard.ui.components.Hairline
import ai.openduo.inkboard.ui.components.MonochromeIcon
import ai.openduo.inkboard.ui.components.PageHeader
import ai.openduo.inkboard.ui.components.PageNavigation
import ai.openduo.inkboard.ui.components.PaperFrame
import ai.openduo.inkboard.ui.components.inkClickable
import ai.openduo.inkboard.ui.theme.InkBlack
import ai.openduo.inkboard.ui.theme.InkDark
import ai.openduo.inkboard.ui.theme.InkPaper
import ai.openduo.inkboard.ui.theme.InkWhite
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt

/** Same rhythm as home shortcuts: 4 columns. */
private const val EpdGridColumns = 4
private const val EpdGridRows = 3
internal const val EpdListPageSize = EpdGridColumns * EpdGridRows // 12

/**
 * EPD single page layout (landscape-first):
 *
 * ```
 * header
 * app grid 4×3 + pager   ← takes remaining height (icons scale with cell)
 * ────
 * mode cards             ← fixed band, ~3 lines, never stretched
 * frequency chips        ← fixed control row, same visual family
 * ```
 *
 * Bottom blocks use natural heights so they stay compact and readable;
 * the app grid only fills whatever is left. Stretching either end looks wrong.
 */
@Composable
internal fun EpdHomePage(
    targets: List<EpdListTarget>,
    selectedId: String,
    listPage: Int,
    onListPageChange: (Int) -> Unit,
    onSelectTarget: (EpdListTarget) -> Unit,
    modeChoice: EpdModeChoice,
    onModeChoice: (EpdModeChoice) -> Unit,
    /** Current global baseline — used when an app leaves “跟随全局”. */
    globalPreset: EpdRefreshPreset = EpdRefreshPreset.CLEAN,
    globalFrequency: Int = EpdRefreshPreset.CLEAN.frequency,
    onRefreshScreen: () -> Unit,
    onBack: () -> Unit,
    error: String? = null,
    modifier: Modifier = Modifier
) {
    val pageCount = maxOf(1, (targets.size + EpdListPageSize - 1) / EpdListPageSize)
    val activePage = listPage.coerceIn(0, pageCount - 1)
    val pageTargets = targets.drop(activePage * EpdListPageSize).take(EpdListPageSize)
    val selected = targets.firstOrNull { it.id == selectedId } ?: targets.firstOrNull()
    val showFollow = selected != null && !selected.isGlobal

    PaperFrame(modifier, bottomPadding = 12.dp) { wide ->
        val modeColumns = if (wide) {
            if (showFollow) 6 else 5
        } else {
            2
        }

        PageHeader(
            title = "EPD.",
            meta = selected?.label ?: "REFRESH",
            actionLabel = "FULL",
            onBack = onBack,
            onAction = onRefreshScreen
        )
        Spacer(Modifier.height(if (wide) 10.dp else 8.dp))

        // Apps + pager fill leftover space after the fixed bottom controls.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            EpdTargetGrid(
                pageTargets = pageTargets,
                selectedId = selectedId,
                onSelectTarget = onSelectTarget,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Spacer(Modifier.height(6.dp))
            PageNavigation(
                page = activePage,
                pageCount = pageCount,
                onPrevious = { onListPageChange((activePage - 1).coerceAtLeast(0)) },
                onNext = { onListPageChange((activePage + 1).coerceAtMost(pageCount - 1)) }
            )
        }

        Spacer(Modifier.height(if (wide) 10.dp else 8.dp))
        Hairline()
        Spacer(Modifier.height(if (wide) 10.dp else 8.dp))

        // Fixed-height mode band: tall enough for 3 text lines, never billboard-tall.
        EpdPresetCardGrid(
            selected = modeChoice.preset,
            selectedFrequency = modeChoice.frequency,
            showFollow = showFollow,
            followSelected = modeChoice.followGlobal,
            columns = modeColumns,
            onSelectPreset = { onModeChoice(EpdModeChoice.of(it)) },
            onSelectFollow = { onModeChoice(EpdModeChoice.follow(globalFrequency)) },
            modifier = Modifier.fillMaxWidth()
        )

        // Always show the threshold row. Global / independent apps edit in place;
        // apps still on “跟随全局” show the inherited value — tapping a chip
        // switches that app to an independent profile (global waveform + chip).
        Spacer(Modifier.height(if (wide) 10.dp else 8.dp))
        EpdFrequencyRow(
            frequency = modeChoice.frequency,
            followingGlobal = modeChoice.followGlobal,
            onSelect = { next ->
                if (modeChoice.followGlobal) {
                    onModeChoice(EpdModeChoice.of(globalPreset, next))
                } else {
                    val preset = modeChoice.preset ?: globalPreset
                    onModeChoice(EpdModeChoice.of(preset, next))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(text = error, color = InkBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EpdTargetGrid(
    pageTargets: List<EpdListTarget>,
    selectedId: String,
    onSelectTarget: (EpdListTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(EpdGridRows) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(EpdGridColumns) { col ->
                    val target = pageTargets.getOrNull(row * EpdGridColumns + col)
                    if (target == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        EpdTargetCell(
                            target = target,
                            selected = target.id == selectedId,
                            onClick = { onSelectTarget(target) },
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
private fun EpdTargetCell(
    target: EpdListTarget,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fg = if (selected) InkWhite else InkBlack
    BoxWithConstraints(
        modifier = modifier
            .background(if (selected) InkBlack else InkPaper)
            .border(1.dp, InkBlack)
            .inkClickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val cellMin = min(constraints.maxWidth, constraints.maxHeight).toFloat()
        // Medium icons: readable in a 4×3 grid without starving the bottom band.
        val iconDp = with(density) {
            (cellMin * 0.42f).toDp().coerceIn(24.dp, 44.dp)
        }
        val labelSp = with(density) {
            (cellMin * 0.11f).toSp().value.roundToInt().coerceIn(11, 14)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                target.iconSvgPath != null -> MonochromeIcon(
                    drawable = null,
                    size = iconDp,
                    darkBackground = selected,
                    svgPath = target.iconSvgPath,
                    cacheKey = "epd-${target.id}"
                )
                target.app != null -> AppGlyph(
                    app = target.app,
                    size = iconDp,
                    darkBackground = selected
                )
                else -> MonochromeIcon(
                    drawable = null,
                    size = iconDp,
                    darkBackground = selected,
                    svgPath = "koboyo/svg/aperture.svg",
                    cacheKey = "epd-fallback"
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = target.label,
                color = fg,
                fontSize = labelSp.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Fixed-height mode band. Wide: one row of 5–6 cards. Portrait: 2-col stack.
 * Height is content-driven so cards never become empty billboards.
 */
@Composable
internal fun EpdPresetCardGrid(
    selected: EpdRefreshPreset?,
    selectedFrequency: Int,
    showFollow: Boolean,
    followSelected: Boolean,
    columns: Int,
    onSelectPreset: (EpdRefreshPreset) -> Unit,
    onSelectFollow: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val items: List<Any?> = buildList {
        if (showFollow) add(null)
        addAll(EpdRefreshPreset.entries)
    }
    val cols = columns.coerceIn(1, 6)
    val rows = items.chunked(cols)
    // Comfortable for title + meta + description; same family as frequency chips.
    val rowHeight = if (cols >= 5) 92.dp else 96.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                rowItems.forEach { item ->
                    if (item == null) {
                        EpdFollowCard(
                            selected = followSelected,
                            onClick = { onSelectFollow?.invoke() },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    } else {
                        val preset = item as EpdRefreshPreset
                        val isSelected = !followSelected && preset == selected
                        EpdPresetCard(
                            preset = preset,
                            frequency = if (isSelected) selectedFrequency else preset.frequency,
                            selected = isSelected,
                            onClick = { onSelectPreset(preset) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
                if (rowItems.size < cols) {
                    repeat(cols - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Full-refresh threshold chips. Unit is partial-update count, not seconds.
 * Sized as a peer control row to the mode cards above (not a thin footer).
 *
 * [followingGlobal] only changes the caption — chips stay interactive so an
 * app can leave “跟随” by picking an interval.
 */
@Composable
internal fun EpdFrequencyRow(
    frequency: Int,
    onSelect: (Int) -> Unit,
    followingGlobal: Boolean = false,
    modifier: Modifier = Modifier
) {
    val choices = EpdRefreshPreset.frequencyChoices(frequency)
    val caption = if (followingGlobal) {
        "跟随全局 · 每 ${frequency} 次局部 → 全屏 1 次 · 点选即独立"
    } else {
        "每 ${frequency} 次局部 → 全屏 1 次"
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "全刷间隔",
                color = InkBlack,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = caption,
                color = InkDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            choices.forEach { value ->
                val selected = value == frequency
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (selected) InkBlack else InkPaper)
                        .border(1.dp, InkBlack)
                        .inkClickable(onClick = { onSelect(value) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = value.toString(),
                        color = if (selected) InkWhite else InkBlack,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun EpdFollowCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fg = if (selected) InkWhite else InkBlack
    val sub = if (selected) InkWhite else InkDark
    Column(
        modifier = modifier
            .background(if (selected) InkBlack else InkPaper)
            .border(1.dp, InkBlack)
            .inkClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "跟随全局",
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "全局缺省波形与全刷",
            color = sub,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "改全局时一起变",
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EpdPresetCard(
    preset: EpdRefreshPreset,
    frequency: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fg = if (selected) InkWhite else InkBlack
    val sub = if (selected) InkWhite else InkDark
    Column(
        modifier = modifier
            .background(if (selected) InkBlack else InkPaper)
            .border(1.dp, InkBlack)
            .inkClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = preset.title,
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${preset.modeLabel} · 每${frequency}次→全刷",
            color = sub,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = preset.description,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
