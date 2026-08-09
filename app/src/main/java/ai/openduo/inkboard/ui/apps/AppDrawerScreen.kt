package ai.openduo.inkboard.ui.apps

import ai.openduo.inkboard.ui.components.AppGlyph
import ai.openduo.inkboard.ui.components.DrawerGridColumns
import ai.openduo.inkboard.ui.components.DrawerGridRows
import ai.openduo.inkboard.ui.components.DrawerPageSize
import ai.openduo.inkboard.ui.components.DrawerSlotPageSize
import ai.openduo.inkboard.ui.components.Hairline
import ai.openduo.inkboard.ui.components.PageHeader
import ai.openduo.inkboard.ui.components.PageNavigation
import ai.openduo.inkboard.ui.components.PaperFrame
import ai.openduo.inkboard.ui.components.VerticalRule
import ai.openduo.inkboard.ui.components.inkClickable
import ai.openduo.inkboard.ui.theme.InkBlack
import ai.openduo.inkboard.ui.theme.InkDark
import ai.openduo.inkboard.ui.theme.InkPaper
import ai.openduo.inkboard.data.AppInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AppDrawer(
    slots: List<AppInfo?>,
    apps: List<AppInfo>,
    selectedSlot: Int,
    onSelectSlot: (Int) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onAddShortcut: (AppInfo) -> Unit,
    onClearSlot: () -> Unit,
    onOpenIconPicker: () -> Unit,
    page: Int,
    onPageChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PaperFrame(modifier, bottomPadding = 16.dp) { wide ->
        val availableApps = apps.filter { app ->
            slots.none { slot -> slot?.key == app.key }
        }
        val pageCount = maxOf(1, (availableApps.size + DrawerPageSize - 1) / DrawerPageSize)
        val activePage = page.coerceIn(0, pageCount - 1)
        val pageApps = availableApps
            .drop(activePage * DrawerPageSize)
            .take(DrawerPageSize)

        PageHeader(
            title = "APPS.",
            meta = "SHORTCUTS",
            actionLabel = null,
            onBack = onBack,
            onAction = null
        )
        Spacer(Modifier.height(if (wide) 24.dp else 16.dp))

        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                ShortcutPanel(
                    slots = slots,
                    selectedSlot = selectedSlot,
                    onSelectSlot = onSelectSlot,
                    onOpenIconPicker = onOpenIconPicker,
                    onClearSlot = onClearSlot,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(26.dp))
                VerticalRule()
                Spacer(Modifier.width(26.dp))
                AppCatalogPanel(
                    apps = pageApps,
                    availableCount = availableApps.size,
                    page = activePage,
                    pageCount = pageCount,
                    onLaunch = onLaunch,
                    onAddShortcut = onAddShortcut,
                    wide = wide,
                    onPrevious = { onPageChange((activePage - 1).coerceAtLeast(0)) },
                    onNext = { onPageChange((activePage + 1).coerceAtMost(pageCount - 1)) },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                ShortcutPanel(
                    slots = slots,
                    selectedSlot = selectedSlot,
                    onSelectSlot = onSelectSlot,
                    onOpenIconPicker = onOpenIconPicker,
                    onClearSlot = onClearSlot,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.height(18.dp))
                Hairline(thick = true)
                Spacer(Modifier.height(18.dp))
                AppCatalogPanel(
                    apps = pageApps,
                    availableCount = availableApps.size,
                    page = activePage,
                    pageCount = pageCount,
                    onLaunch = onLaunch,
                    onAddShortcut = onAddShortcut,
                    wide = wide,
                    onPrevious = { onPageChange((activePage - 1).coerceAtLeast(0)) },
                    onNext = { onPageChange((activePage + 1).coerceAtMost(pageCount - 1)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ShortcutPanel(
    slots: List<AppInfo?>,
    selectedSlot: Int,
    onSelectSlot: (Int) -> Unit,
    onOpenIconPicker: () -> Unit,
    onClearSlot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedApp = slots.getOrNull(selectedSlot)
    val slotPageCount = maxOf(1, (slots.size + DrawerSlotPageSize - 1) / DrawerSlotPageSize)
    var slotPage by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(selectedSlot, slots.size) {
        if (slots.isEmpty()) {
            slotPage = 0
            return@LaunchedEffect
        }
        val pageForSelection = (selectedSlot / DrawerSlotPageSize).coerceIn(0, slotPageCount - 1)
        slotPage = pageForSelection
    }
    val activeSlotPage = slotPage.coerceIn(0, slotPageCount - 1)
    val pageSlots = (0 until DrawerSlotPageSize).map { offset ->
        val index = activeSlotPage * DrawerSlotPageSize + offset
        index to slots.getOrNull(index)
    }

    Column(modifier = modifier.fillMaxHeight()) {
        DrawerPanelHeading(
            kicker = "SHORTCUTS",
            title = "快捷方式 · ${slots.size}"
        )
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            for (row in 0 until DrawerGridRows) {
                if (row > 0) Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.weight(1f)) {
                    for (column in 0 until DrawerGridColumns) {
                        if (column > 0) Spacer(Modifier.width(6.dp))
                        val (index, app) = pageSlots[row * DrawerGridColumns + column]
                        if (index >= slots.size) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            ShortcutSlotTile(
                                index = index,
                                app = app,
                                selected = index == selectedSlot,
                                onClick = { onSelectSlot(index) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
        if (slotPageCount > 1) {
            Spacer(Modifier.height(8.dp))
            PageNavigation(
                page = activeSlotPage,
                pageCount = slotPageCount,
                onPrevious = { slotPage = (activeSlotPage - 1).coerceAtLeast(0) },
                onNext = { slotPage = (activeSlotPage + 1).coerceAtMost(slotPageCount - 1) }
            )
        }
        Spacer(Modifier.height(12.dp))
        ShortcutActionBar(
            app = selectedApp,
            onOpenIconPicker = onOpenIconPicker,
            onClearSlot = onClearSlot
        )
    }
}

@Composable
private fun AppCatalogPanel(
    apps: List<AppInfo>,
    availableCount: Int,
    page: Int,
    pageCount: Int,
    onLaunch: (AppInfo) -> Unit,
    onAddShortcut: (AppInfo) -> Unit,
    wide: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxHeight()) {
        DrawerPanelHeading(kicker = "APPLICATIONS", title = "应用")
        Spacer(Modifier.height(16.dp))
        if (apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (availableCount == 0) "没有可添加的应用" else "此页没有应用",
                    color = InkDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                for (row in 0 until DrawerGridRows) {
                    if (row > 0) Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.weight(1f)) {
                        for (column in 0 until DrawerGridColumns) {
                            if (column > 0) Spacer(Modifier.width(6.dp))
                            val app = apps.getOrNull(row * DrawerGridColumns + column)
                            if (app == null) {
                                Spacer(Modifier.weight(1f))
                            } else {
                                DrawerAppTile(
                                    app = app,
                                    onLaunch = { onLaunch(app) },
                                    onAddShortcut = { onAddShortcut(app) },
                                    wide = wide,
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
        Spacer(Modifier.height(12.dp))
        PageNavigation(
            page = page,
            pageCount = pageCount,
            onPrevious = onPrevious,
            onNext = onNext
        )
    }
}

@Composable
private fun DrawerPanelHeading(kicker: String, title: String) {
    Column {
        Text(
            text = kicker,
            color = InkDark,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.8.sp
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = title,
            color = InkBlack,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp
        )
    }
}

@Composable
private fun ShortcutSlotTile(
    index: Int,
    app: AppInfo?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val foreground = InkBlack
    Row(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(InkPaper)
            // Fixed outer stroke; selection is an inset ring so neighbors stay equal.
            .border(1.5.dp, InkBlack)
            .drawBehind {
                if (selected) {
                    val inset = 3.dp.toPx()
                    val stroke = 1.5.dp.toPx()
                    drawRect(
                        color = InkBlack,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(width = stroke)
                    )
                }
            }
            .inkClickable(onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            color = InkDark,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.0.sp
        )
        Spacer(Modifier.width(10.dp))
        if (app == null) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = foreground,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light
                )
            }
        } else {
            AppGlyph(app = app, size = 48.dp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = app?.label ?: "添加",
            color = foreground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ShortcutActionBar(
    app: AppInfo?,
    onOpenIconPicker: () -> Unit,
    onClearSlot: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(InkPaper)
            .border(1.5.dp, InkBlack)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app == null) {
            Text(
                text = "从右侧应用点 + 加入此位置",
                color = InkDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                text = app.label,
                color = InkBlack,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            ShortcutActionButton(text = "图标", onClick = onOpenIconPicker)
            Spacer(Modifier.width(6.dp))
            ShortcutActionButton(text = "移除", onClick = onClearSlot)
        }
    }
}

@Composable
private fun ShortcutActionButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(76.dp)
            .height(48.dp)
            .background(InkPaper)
            .border(2.dp, InkBlack)
            .inkClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = InkBlack,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.1.sp
        )
    }
}

@Composable
private fun DrawerAppTile(
    app: AppInfo,
    onLaunch: () -> Unit,
    onAddShortcut: () -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(InkPaper)
            .border(1.5.dp, InkBlack),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .inkClickable(onLaunch)
                .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppGlyph(app = app, size = 58.dp)
            Spacer(Modifier.width(16.dp))
            Text(
                text = app.label,
                color = InkBlack,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
        }
        Box(
            modifier = Modifier
                .width(58.dp)
                .fillMaxHeight()
                .padding(end = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            DrawerTileAction(
                text = "+",
                onClick = onAddShortcut,
                isAddAction = true,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun DrawerTileAction(
    text: String,
    onClick: () -> Unit,
    isAddAction: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.inkClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = InkBlack,
            fontSize = if (isAddAction) 27.sp else 9.sp,
            lineHeight = if (isAddAction) 26.sp else 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = if (isAddAction) 0.sp else 1.1.sp
        )
    }
}
