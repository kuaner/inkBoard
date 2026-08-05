package ai.openduo.inkboard.ui.apps

import ai.openduo.inkboard.ui.components.AppGlyph
import ai.openduo.inkboard.ui.components.DrawerGridColumns
import ai.openduo.inkboard.ui.components.DrawerGridRows
import ai.openduo.inkboard.ui.components.DrawerPageSize
import ai.openduo.inkboard.ui.components.Hairline
import ai.openduo.inkboard.ui.components.PageHeader
import ai.openduo.inkboard.ui.components.PageNavigation
import ai.openduo.inkboard.ui.components.PaperFrame
import ai.openduo.inkboard.ui.components.VerticalRule

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openduo.inkboard.LauncherUiState
import ai.openduo.inkboard.SenderSnapshot
import ai.openduo.inkboard.data.AppInfo
import ai.openduo.inkboard.data.BuiltInShortcut
import ai.openduo.inkboard.data.EpdDpiPreset
import ai.openduo.inkboard.data.EpdProfile
import ai.openduo.inkboard.data.EpdRefreshPreset
import ai.openduo.inkboard.data.KoboyoIcon
import ai.openduo.inkboard.data.KoboyoIconCategory
import ai.openduo.inkboard.data.KoboyoIconGroup
import ai.openduo.inkboard.ui.components.MonochromeIcon
import ai.openduo.inkboard.ui.components.inkClickable
import ai.openduo.inkboard.ui.theme.InkBlack
import ai.openduo.inkboard.ui.theme.InkDark
import ai.openduo.inkboard.ui.theme.InkLine
import ai.openduo.inkboard.ui.theme.InkMid
import ai.openduo.inkboard.ui.theme.InkPaper
import ai.openduo.inkboard.ui.theme.InkSoft
import ai.openduo.inkboard.ui.theme.InkWhite
import ai.openduo.inkboard.util.OrientationMode
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    onOpenEpd: ((AppInfo) -> Unit)?,
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
                    onOpenEpd = onOpenEpd,
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
                    onOpenEpd = onOpenEpd,
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
                    onOpenEpd = onOpenEpd,
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
                    onOpenEpd = onOpenEpd,
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
    onOpenEpd: ((AppInfo) -> Unit)?,
    onClearSlot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedApp = slots.getOrNull(selectedSlot)
    Column(modifier = modifier.fillMaxHeight()) {
        DrawerPanelHeading(kicker = "SHORTCUTS", title = "快捷方式")
        Spacer(Modifier.height(16.dp))
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
                        val index = row * DrawerGridColumns + column
                        ShortcutSlotTile(
                            app = slots.getOrNull(index),
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
        Spacer(Modifier.height(12.dp))
        ShortcutActionBar(
            app = selectedApp,
            onOpenIconPicker = onOpenIconPicker,
            onOpenEpd = onOpenEpd?.let { open -> { selectedApp?.let(open) } },
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
    onOpenEpd: ((AppInfo) -> Unit)?,
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
                                    onOpenEpd = onOpenEpd?.let { open -> { open(app) } },
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
    app: AppInfo?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val foreground = InkBlack
    Row(
        modifier = modifier
            .background(InkPaper)
            .border(if (selected) 2.dp else 1.5.dp, InkBlack)
            .inkClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app == null) {
            Box(
                modifier = Modifier.size(58.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = foreground,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Light
                )
            }
        } else {
            AppGlyph(app = app, size = 58.dp)
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = app?.label ?: "添加",
            color = foreground,
            fontSize = 17.sp,
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
    onOpenEpd: (() -> Unit)?,
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
            if (onOpenEpd != null && app.builtInShortcut == null) {
                Spacer(Modifier.width(6.dp))
                ShortcutActionButton(text = "EPD", onClick = onOpenEpd)
            }
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
    onOpenEpd: (() -> Unit)?,
    wide: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
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
        if (wide) {
            Column(
                modifier = Modifier
                    .width(58.dp)
                    .fillMaxHeight()
                    .padding(end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                DrawerTileAction(
                    text = "+",
                    onClick = onAddShortcut,
                    isAddAction = true,
                    modifier = Modifier.size(48.dp)
                )
                if (onOpenEpd != null && app.builtInShortcut == null) {
                    Spacer(Modifier.height(4.dp))
                    DrawerTileAction(
                        text = "EPD",
                        onClick = onOpenEpd,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DrawerTileAction(
                    text = "+",
                    onClick = onAddShortcut,
                    isAddAction = true,
                    modifier = Modifier.size(48.dp)
                )
                if (onOpenEpd != null && app.builtInShortcut == null) {
                    DrawerTileAction(
                        text = "EPD",
                        onClick = onOpenEpd,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
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
