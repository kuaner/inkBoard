package ai.openduo.inkboard.ui.controls

import ai.openduo.inkboard.ui.LauncherActions
import ai.openduo.inkboard.ui.components.ControlActionTile
import ai.openduo.inkboard.ui.components.Hairline
import ai.openduo.inkboard.ui.components.PageHeader
import ai.openduo.inkboard.ui.components.PaperFrame
import ai.openduo.inkboard.ui.components.RotationGlyph
import ai.openduo.inkboard.ui.components.VerticalRule
import ai.openduo.inkboard.ui.epd.EpdEntryPoints

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
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
import ai.openduo.inkboard.ui.theme.InkPaper
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
internal fun ControlPage(
    state: LauncherUiState,
    actions: LauncherActions,
    epd: EpdEntryPoints,
    onOpenMotto: () -> Unit,
    onOpenApps: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PaperFrame(modifier) { wide ->
        PageHeader(
            title = "MENU.",
            meta = "INKBOARD",
            actionLabel = null,
            onBack = onBack,
            onAction = null
        )
        Spacer(Modifier.height(if (wide) 24.dp else 16.dp))
        Column(modifier = Modifier.weight(1f)) {
            CompactOrientationRail(
                selected = state.orientation,
                onSelect = actions.onOrientation
            )
            Spacer(Modifier.height(if (wide) 22.dp else 16.dp))
            Hairline(thick = true)
            Spacer(Modifier.height(if (wide) 18.dp else 14.dp))

            val openSystemEpd = epd.openSystem
            val openInkBoardEpd = epd.openInkBoard

            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (openSystemEpd != null && openInkBoardEpd != null) {
                        ControlDomain(
                            kicker = "DISPLAY",
                            title = "显示",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            ControlActionTile(
                                title = "系统默认",
                                detail = "所有未单独设置的应用",
                                onClick = openSystemEpd,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            ControlActionTile(
                                title = "InkBoard",
                                detail = "桌面自己的 DPI · 对比度 · 浅色",
                                onClick = openInkBoardEpd,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                        VerticalRule()
                    }
                    ControlDomain(
                        kicker = "HOME",
                        title = "桌面",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        ControlActionTile(
                            title = "桌面文字",
                            detail = "修改主屏的一句话",
                            onClick = onOpenMotto,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        ControlActionTile(
                            title = "快捷方式",
                            detail = "管理桌面的 8 个位置",
                            onClick = onOpenApps,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                    VerticalRule()
                    ControlDomain(
                        kicker = "SYSTEM",
                        title = "系统",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        ControlActionTile(
                            title = "USB 调试",
                            detail = "ADB",
                            value = if (state.adbEnabled) "开启" else "关闭",
                            emphasized = state.adbEnabled,
                            onClick = actions.onToggleAdb,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        ControlActionTile(
                            title = "系统设置",
                            detail = "Android",
                            onClick = actions.onOpenSystemSettings,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (openSystemEpd != null && openInkBoardEpd != null) {
                        ControlDomain(
                            kicker = "DISPLAY",
                            title = "显示",
                            compact = true,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ControlActionTile(
                                    title = "系统默认",
                                    detail = "未单独设置的应用",
                                    onClick = openSystemEpd,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                                Spacer(Modifier.width(6.dp))
                                ControlActionTile(
                                    title = "InkBoard",
                                    detail = "桌面 EPD",
                                    onClick = openInkBoardEpd,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    ControlDomain(
                        kicker = "HOME",
                        title = "桌面",
                        compact = true,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ControlActionTile(
                                title = "桌面文字",
                                detail = "主屏的一句话",
                                onClick = onOpenMotto,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            Spacer(Modifier.width(6.dp))
                            ControlActionTile(
                                title = "快捷方式",
                                detail = "8 个位置",
                                onClick = onOpenApps,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ControlDomain(
                        kicker = "SYSTEM",
                        title = "系统",
                        compact = true,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ControlActionTile(
                                title = "USB 调试",
                                detail = "ADB",
                                value = if (state.adbEnabled) "开启" else "关闭",
                                emphasized = state.adbEnabled,
                                onClick = actions.onToggleAdb,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            Spacer(Modifier.width(6.dp))
                            ControlActionTile(
                                title = "系统设置",
                                detail = "Android",
                                onClick = actions.onOpenSystemSettings,
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
}

@Composable
private fun CompactOrientationRail(
    selected: OrientationMode,
    onSelect: (OrientationMode) -> Unit
) {
    val choices = listOf(
        OrientationMode.PORTRAIT,
        OrientationMode.LANDSCAPE,
        OrientationMode.PORTRAIT_REVERSE,
        OrientationMode.LANDSCAPE_REVERSE
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ROTATION",
                    color = InkBlack,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.8.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "屏幕旋转",
                    color = InkBlack,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.4).sp
                )
            }
            Text(
                text = "${rotationLabel(selected)} · ${orientationName(selected)}",
                color = InkDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            choices.forEachIndexed { index, mode ->
                if (index > 0) Spacer(Modifier.width(4.dp))
                CompactOrientationChoice(
                    mode = mode,
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CompactOrientationChoice(
    mode: OrientationMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val foreground = if (selected) InkWhite else InkBlack
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(if (selected) InkBlack else InkPaper)
            // Same stroke for selected/unselected so the four cells stay equal size.
            .border(width = 1.5.dp, color = InkBlack)
            .inkClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            RotationGlyph(mode = mode, color = foreground, glyphSize = 25.dp)
            Spacer(Modifier.width(9.dp))
            Text(
                text = rotationLabel(mode),
                color = foreground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun ControlDomain(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
    /** Portrait MENU uses less horizontal inset so paired tiles stay equal width. */
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .padding(horizontal = if (compact) 0.dp else 24.dp)
    ) {
        Text(
            text = kicker,
            color = InkDark,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.8.sp
        )
        Spacer(Modifier.height(if (compact) 5.dp else 7.dp))
        Text(
            text = title,
            color = InkBlack,
            fontSize = if (compact) 22.sp else 26.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
        content()
    }
}

private fun rotationLabel(mode: OrientationMode): String = when (mode) {
    OrientationMode.PORTRAIT -> "0°"
    OrientationMode.LANDSCAPE -> "90°"
    OrientationMode.PORTRAIT_REVERSE -> "180°"
    OrientationMode.LANDSCAPE_REVERSE -> "270°"
}

private fun orientationName(mode: OrientationMode): String = when (mode) {
    OrientationMode.PORTRAIT,
    OrientationMode.PORTRAIT_REVERSE -> "竖屏"
    OrientationMode.LANDSCAPE,
    OrientationMode.LANDSCAPE_REVERSE -> "横屏"
}
