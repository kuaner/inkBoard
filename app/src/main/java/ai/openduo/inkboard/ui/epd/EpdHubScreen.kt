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
 * EPD single page:
 * - Target grid 4×3 (全局 / InkBoard / apps) + pager under it
 * - Mode cards below, compact 3-line, fill remaining width
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
        PageHeader(
            title = "EPD.",
            meta = selected?.label ?: "REFRESH",
            actionLabel = "FULL",
            onBack = onBack,
            onAction = onRefreshScreen
        )
        Spacer(Modifier.height(if (wide) 12.dp else 8.dp))

        // —— Target grid (fills upper band) + pager glued under it ——
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (wide) 0.48f else 0.46f)
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

        // —— Mode cards fill remaining space ——
        EpdPresetCardGrid(
            selected = modeChoice.preset,
            showFollow = showFollow,
            followSelected = modeChoice.followGlobal,
            columns = if (wide) 3 else 2,
            onSelectPreset = { onModeChoice(EpdModeChoice.of(it)) },
            onSelectFollow = { onModeChoice(EpdModeChoice.follow()) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (wide) 0.52f else 0.54f)
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(EpdGridRows) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val cellMin = min(constraints.maxWidth, constraints.maxHeight).toFloat()
        val iconDp = with(density) {
            (cellMin * 0.42f).toDp().coerceIn(22.dp, 48.dp)
        }
        val labelSp = with(density) {
            (cellMin * 0.12f).toSp().value.roundToInt().coerceIn(10, 13)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(Modifier.weight(1f))
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
            Spacer(Modifier.weight(1f))
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
 * Mode grid fills remaining height: rows share space equally,
 * cards stay 3-line content without fake empty spacers inside.
 */
@Composable
internal fun EpdPresetCardGrid(
    selected: EpdRefreshPreset?,
    showFollow: Boolean,
    followSelected: Boolean,
    columns: Int,
    onSelectPreset: (EpdRefreshPreset) -> Unit,
    onSelectFollow: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val cols = columns.coerceIn(1, 3)
    val items: List<Any?> = buildList {
        if (showFollow) add(null)
        addAll(EpdRefreshPreset.entries)
    }
    val rows = items.chunked(cols)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                        EpdPresetCard(
                            preset = preset,
                            selected = !followSelected && preset == selected,
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "跟随全局",
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = preset.title,
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            text = "${preset.modeLabel} · 每${preset.frequency}次→全刷",
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
