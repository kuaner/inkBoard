package ai.openduo.inkboard.ui.epd

import ai.openduo.inkboard.ui.components.AppGlyph
import ai.openduo.inkboard.ui.components.Hairline
import ai.openduo.inkboard.ui.components.PageHeader
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
internal fun EpdProfilePage(
    target: EpdTarget,
    profile: EpdProfile?,
    loading: Boolean,
    error: String?,
    section: EpdSection,
    onSectionChange: (EpdSection) -> Unit,
    onProfileChange: (EpdProfile) -> Unit,
    onRefreshScreen: () -> Unit,
    applyOnExit: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PaperFrame(modifier) { wide ->
        PageHeader(
            title = "EPD.",
            meta = if (target.app == null) "INKBOARD" else "APP",
            actionLabel = "FULL",
            onBack = onBack,
            onAction = onRefreshScreen
        )
        Spacer(Modifier.height(if (wide) 20.dp else 14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            target.app?.let { app ->
                AppGlyph(app = app, size = 34.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = target.label,
                color = InkBlack,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(16.dp))
        if (applyOnExit) {
            EpdSystemNotice("InkBoard 的修改会在离开此页后一次性应用，调参时不会反复全刷。")
            Spacer(Modifier.height(16.dp))
        } else {
            EpdSystemNotice("这是此应用的单独策略；切回 DEFAULT 后会跟随全局缺省策略。")
            Spacer(Modifier.height(16.dp))
        }
        EpdTabs(section = section, onChange = onSectionChange)
        Spacer(Modifier.height(if (wide) 22.dp else 16.dp))

        when {
            loading && profile == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "读取系统 EPD 档案",
                    color = InkBlack,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            else -> {
                val activeProfile = profile ?: EpdProfile(packageName = target.packageName)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (section) {
                        EpdSection.REFRESH -> EpdRefreshPanel(
                            profile = activeProfile,
                            wide = wide,
                            onProfileChange = onProfileChange,
                            modifier = Modifier.fillMaxSize()
                        )

                        EpdSection.DISPLAY -> EpdDisplayPanel(
                            profile = activeProfile,
                            // This tablet reports a portrait logical display even
                            // while it is used landscape. The controls still have
                            // desktop-class width, so keep these paired panels
                            // horizontal instead of wasting half the page vertically.
                            wide = true,
                            onProfileChange = onProfileChange,
                            modifier = Modifier.fillMaxSize()
                        )

                        EpdSection.FILTER -> EpdFilterPanel(
                            profile = activeProfile,
                            wide = true,
                            onProfileChange = onProfileChange,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = error,
                color = InkBlack,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EpdTabs(section: EpdSection, onChange: (EpdSection) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        EpdTab(
            text = "刷新",
            selected = section == EpdSection.REFRESH,
            onClick = { onChange(EpdSection.REFRESH) },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(2.dp))
        EpdTab(
            text = "显示",
            selected = section == EpdSection.DISPLAY,
            onClick = { onChange(EpdSection.DISPLAY) },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(2.dp))
        EpdTab(
            text = "浅色",
            selected = section == EpdSection.FILTER,
            onClick = { onChange(EpdSection.FILTER) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EpdTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(if (selected) InkBlack else InkPaper)
            .border(width = if (selected) 2.dp else 1.5.dp, color = InkBlack)
            .inkClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) InkWhite else InkBlack,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun EpdRefreshPanel(
    profile: EpdProfile,
    wide: Boolean,
    onProfileChange: (EpdProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        val selectedPreset = EpdRefreshPreset.fromProfile(profile)
        EpdSectionTitle(
            title = "刷新策略",
            value = if (profile.refreshEnabled) selectedPreset?.title ?: "自定义" else "系统默认"
        )
        Spacer(Modifier.height(12.dp))
        Hairline(thick = true)
        Spacer(Modifier.height(18.dp))
        EpdProfileToggle(
            title = "策略归属",
            enabled = profile.refreshEnabled,
            onChange = { enabled -> onProfileChange(profile.copy(refreshEnabled = enabled)) }
        )
        Spacer(Modifier.height(24.dp))

        if (!profile.refreshEnabled) {
            EpdSystemNotice("跟随 SYSTEM EPD 中的全局缺省策略；需要单独调校时切换为 APP。")
            return@Column
        }

        Text(
            text = "预置策略",
            color = InkBlack,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.8.sp
        )
        Spacer(Modifier.height(12.dp))
        EpdRefreshPresetGrid(
            selected = selectedPreset,
            columns = if (wide) 3 else 2,
            onSelect = { preset -> onProfileChange(preset.applyTo(profile)) }
        )
        Spacer(Modifier.height(14.dp))
        EpdSystemNotice(
            "每完成 ${profile.refreshFrequency} 次局部刷新，系统自动执行 1 次全屏刷新；次数越小，残影越少，但全屏刷新越频繁。"
        )
    }
}

@Composable
private fun EpdDisplayPanel(
    profile: EpdProfile,
    wide: Boolean,
    onProfileChange: (EpdProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    if (wide) {
        Row(modifier = modifier) {
            EpdDpiBlock(
                profile = profile,
                onProfileChange = onProfileChange,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(28.dp))
            VerticalRule()
            Spacer(Modifier.width(28.dp))
            EpdContrastBlock(
                profile = profile,
                onProfileChange = onProfileChange,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        Column(modifier = modifier) {
            EpdDpiBlock(profile = profile, onProfileChange = onProfileChange)
            Spacer(Modifier.height(28.dp))
            Hairline(thick = true)
            Spacer(Modifier.height(28.dp))
            EpdContrastBlock(profile = profile, onProfileChange = onProfileChange)
        }
    }
}

@Composable
private fun EpdDpiBlock(
    profile: EpdProfile,
    onProfileChange: (EpdProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedPreset = EpdDpiPreset.fromValue(profile.dpi)
    val currentValue = when {
        !profile.dpiEnabled -> "系统默认"
        selectedPreset != null -> "${selectedPreset.title} · ${selectedPreset.value}"
        else -> "自定义 ${profile.dpi}"
    }
    Column(modifier = modifier) {
        EpdSectionTitle(title = "DPI", value = currentValue)
        Spacer(Modifier.height(12.dp))
        Hairline(thick = true)
        Spacer(Modifier.height(18.dp))
        EpdProfileToggle(
            title = "应用 DPI",
            enabled = profile.dpiEnabled,
            onChange = { enabled -> onProfileChange(profile.copy(dpiEnabled = enabled)) }
        )
        Spacer(Modifier.height(28.dp))
        if (profile.dpiEnabled) {
            Text(
                text = "显示大小",
                color = InkBlack,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.3.sp
            )
            Spacer(Modifier.height(10.dp))
            EpdDpiPresetGrid(
                selected = selectedPreset,
                onSelect = { preset ->
                    onProfileChange(profile.copy(dpi = preset.value))
                }
            )
            Spacer(Modifier.height(12.dp))
            EpdSystemNotice(
                if (selectedPreset == null) {
                    "当前保存值为 ${profile.dpi}；选择一个系统档位即可切换为固定 DPI。"
                } else {
                    "${selectedPreset.description} · ${selectedPreset.value} dpi"
                }
            )
        } else {
            EpdSystemNotice("跟随系统默认显示大小（原生 260 dpi）")
        }
    }
}

@Composable
private fun EpdDpiPresetGrid(
    selected: EpdDpiPreset?,
    onSelect: (EpdDpiPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp)
    ) {
        EpdDpiPreset.entries.forEachIndexed { index, preset ->
            if (index > 0) Spacer(Modifier.width(5.dp))
            EpdDpiPresetOption(
                preset = preset,
                selected = preset == selected,
                onClick = { onSelect(preset) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EpdDpiPresetOption(
    preset: EpdDpiPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val foreground = if (selected) InkWhite else InkBlack
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(if (selected) InkBlack else InkPaper)
            .border(width = if (selected) 2.dp else 1.5.dp, color = InkBlack)
            .inkClickable(onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = preset.title,
                color = foreground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.weight(1f))
            if (selected) {
                Text(
                    text = "当前",
                    color = foreground,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Text(
            text = "${preset.value} dpi",
            color = foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun EpdContrastBlock(
    profile: EpdProfile,
    onProfileChange: (EpdProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        EpdSectionTitle(title = "对比度", value = if (profile.contrastEnabled) "${profile.contrast}" else "系统默认")
        Spacer(Modifier.height(12.dp))
        Hairline(thick = true)
        Spacer(Modifier.height(18.dp))
        EpdProfileToggle(
            title = "应用对比度",
            enabled = profile.contrastEnabled,
            onChange = { enabled -> onProfileChange(profile.copy(contrastEnabled = enabled)) }
        )
        Spacer(Modifier.height(28.dp))
        if (profile.contrastEnabled) {
            EpdValueStepper(
                title = "增强等级",
                valueText = profile.contrast.toString(),
                canDecrease = profile.contrast > 0,
                canIncrease = profile.contrast < 80,
                onDecrease = { onProfileChange(profile.copy(contrast = (profile.contrast - 10).coerceAtLeast(0))) },
                onIncrease = { onProfileChange(profile.copy(contrast = (profile.contrast + 10).coerceAtMost(80))) }
            )
        } else {
            EpdSystemNotice("0–80，数值越高，黑白越分明")
        }
    }
}

@Composable
private fun EpdFilterPanel(
    profile: EpdProfile,
    wide: Boolean,
    onProfileChange: (EpdProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        EpdSectionTitle(title = "浅色处理", value = if (profile.bleachEnabled) "已开启" else "关闭")
        Spacer(Modifier.height(12.dp))
        Hairline(thick = true)
        Spacer(Modifier.height(18.dp))
        EpdSystemNotice("只处理该应用中接近白色的图标、封面与背景，让浅色内容在墨水屏上仍能看见。")
        Spacer(Modifier.height(18.dp))
        EpdBooleanToggle(
            title = "浅色处理",
            enabled = profile.bleachEnabled,
            onChange = { enabled -> onProfileChange(profile.copy(bleachEnabled = enabled)) }
        )
        Spacer(Modifier.height(20.dp))

        if (!profile.bleachEnabled) {
            EpdSystemNotice("开启后可分别调整图标、封面、背景，并启用文字增强。")
            return@Column
        }

        EpdBooleanToggle(
            title = "文字增强",
            enabled = profile.bleachTextPlus,
            onChange = { enabled -> onProfileChange(profile.copy(bleachTextPlus = enabled)) }
        )
        Spacer(Modifier.height(28.dp))
        if (wide) {
            Row(modifier = Modifier.fillMaxWidth()) {
                EpdFilterStepper(
                    title = "图标",
                    value = profile.bleachIconColor,
                    max = 255,
                    onChange = { value -> onProfileChange(profile.copy(bleachIconColor = value)) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(18.dp))
                EpdFilterStepper(
                    title = "封面",
                    value = profile.bleachCoverColor,
                    max = 150,
                    onChange = { value -> onProfileChange(profile.copy(bleachCoverColor = value)) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(18.dp))
                EpdFilterStepper(
                    title = "背景",
                    value = profile.bleachBackgroundColor,
                    max = 255,
                    onChange = { value -> onProfileChange(profile.copy(bleachBackgroundColor = value)) },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                EpdFilterStepper(
                    title = "图标",
                    value = profile.bleachIconColor,
                    max = 255,
                    onChange = { value -> onProfileChange(profile.copy(bleachIconColor = value)) }
                )
                Spacer(Modifier.height(16.dp))
                EpdFilterStepper(
                    title = "封面",
                    value = profile.bleachCoverColor,
                    max = 150,
                    onChange = { value -> onProfileChange(profile.copy(bleachCoverColor = value)) }
                )
                Spacer(Modifier.height(16.dp))
                EpdFilterStepper(
                    title = "背景",
                    value = profile.bleachBackgroundColor,
                    max = 255,
                    onChange = { value -> onProfileChange(profile.copy(bleachBackgroundColor = value)) }
                )
            }
        }
    }
}

@Composable
private fun EpdFilterStepper(
    title: String,
    value: Int,
    max: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val percent = ((value.toFloat() / max) * 100).roundToInt().coerceIn(0, 100)
    EpdValueStepper(
        title = title,
        valueText = "$percent%",
        canDecrease = percent > 0,
        canIncrease = percent < 100,
        onDecrease = {
            val next = (percent - 10).coerceAtLeast(0)
            onChange(((next / 100f) * max).roundToInt())
        },
        onIncrease = {
            val next = (percent + 10).coerceAtMost(100)
            onChange(((next / 100f) * max).roundToInt())
        },
        modifier = modifier
    )
}

@Composable
private fun EpdProfileToggle(
    title: String,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = InkBlack,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f)
        )
        EpdBinaryChoice(
            text = "DEFAULT",
            selected = !enabled,
            onClick = { onChange(false) }
        )
        Spacer(Modifier.width(2.dp))
        EpdBinaryChoice(
            text = "APP",
            selected = enabled,
            onClick = { onChange(true) }
        )
    }
}

/** Boolean vendor flags: unlike DPI/refresh/contrast, they have no system/app override mode. */
@Composable
private fun EpdBooleanToggle(
    title: String,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = InkBlack,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f)
        )
        EpdBinaryChoice(
            text = "关闭",
            selected = !enabled,
            onClick = { onChange(false) }
        )
        Spacer(Modifier.width(2.dp))
        EpdBinaryChoice(
            text = "开启",
            selected = enabled,
            onClick = { onChange(true) }
        )
    }
}

@Composable
private fun EpdBinaryChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .width(86.dp)
            .background(if (selected) InkBlack else InkPaper)
            .border(width = if (selected) 2.dp else 1.5.dp, color = InkBlack)
            .inkClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) InkWhite else InkBlack,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun EpdValueStepper(
    title: String,
    valueText: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = InkBlack,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.3.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EpdStepButton(symbol = "−", enabled = canDecrease, onClick = onDecrease)
            Text(
                text = valueText,
                color = InkBlack,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            EpdStepButton(symbol = "+", enabled = canIncrease, onClick = onIncrease)
        }
    }
}

@Composable
private fun EpdStepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .background(if (enabled) InkBlack else InkPaper)
            .border(width = if (enabled) 2.dp else 1.5.dp, color = InkBlack)
            .then(if (enabled) Modifier.inkClickable(onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            color = if (enabled) InkWhite else InkBlack,
            fontSize = 30.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Black
        )
    }
}
