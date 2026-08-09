package ai.openduo.inkboard.ui.controls

import ai.openduo.inkboard.ui.LauncherActions
import ai.openduo.inkboard.ui.components.ControlActionTile
import ai.openduo.inkboard.ui.components.Hairline
import ai.openduo.inkboard.ui.components.PageHeader
import ai.openduo.inkboard.ui.components.PaperFrame
import ai.openduo.inkboard.ui.components.RotationGlyph
import ai.openduo.inkboard.ui.components.inkClickable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openduo.inkboard.LauncherUiState
import ai.openduo.inkboard.data.PreferencesRepository
import ai.openduo.inkboard.ui.theme.InkBlack
import ai.openduo.inkboard.ui.theme.InkDark
import ai.openduo.inkboard.ui.theme.InkPaper
import ai.openduo.inkboard.ui.theme.InkWhite
import ai.openduo.inkboard.util.OrientationMode

@Composable
internal fun ControlPage(
    state: LauncherUiState,
    actions: LauncherActions,
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

            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    ControlDomain(
                        kicker = "HOME",
                        title = "桌面",
                        modifier = Modifier
                            .weight(1.35f)
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
                            detail = "编辑桌面 ${state.homeSlotCount} 个钉位",
                            onClick = onOpenApps,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        HomeDensityChooser(
                            selected = state.homeSlotCount,
                            onSelect = actions.onSetHomeSlotCount,
                            modifier = Modifier
                                .weight(1.15f)
                                .fillMaxWidth()
                        )
                    }
                    VerticalRuleLocal()
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
                    ControlDomain(
                        kicker = "HOME",
                        title = "桌面",
                        compact = true,
                        modifier = Modifier.weight(1.35f)
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
                                detail = "${state.homeSlotCount} 个位置",
                                onClick = onOpenApps,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        HomeDensityChooser(
                            selected = state.homeSlotCount,
                            onSelect = actions.onSetHomeSlotCount,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.9f)
                        )
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
private fun HomeDensityChooser(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Title/detail on top; options sit on the bottom edge of the tile
    // (matches MENU magazine layout — never vertically centered in the box).
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .border(1.5.dp, InkBlack)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = "桌面密度",
            color = InkBlack,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "快捷方式数量 · 英雄区会随密度自动缩放",
            color = InkDark,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PreferencesRepository.SLOT_COUNT_OPTIONS.forEach { count ->
                val active = count == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (active) InkBlack else InkPaper)
                        .border(1.5.dp, InkBlack)
                        .inkClickable(onClick = { onSelect(count) }),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = count.toString(),
                        color = if (active) InkWhite else InkBlack,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
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
                    letterSpacing = (-0.5).sp
                )
            }
            Text(
                text = orientationName(selected),
                color = InkDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            choices.forEach { mode ->
                val active = mode == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (active) InkBlack else InkPaper)
                        .border(1.5.dp, InkBlack)
                        .inkClickable(onClick = { onSelect(mode) }),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        RotationGlyph(
                            mode = mode,
                            color = if (active) InkWhite else InkBlack,
                            glyphSize = 25.dp
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = rotationLabel(mode),
                            color = if (active) InkWhite else InkBlack,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
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

@Composable
private fun VerticalRuleLocal() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(InkBlack)
    )
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
