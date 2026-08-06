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
internal fun SystemEpdPage(
    profile: EpdProfile?,
    loading: Boolean,
    error: String?,
    onProfileChange: (EpdProfile) -> Unit,
    onRefreshScreen: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PaperFrame(modifier) { wide ->
        PageHeader(
            title = "SYSTEM EPD.",
            meta = "DEFAULT",
            actionLabel = "FULL",
            onBack = onBack,
            onAction = onRefreshScreen
        )
        Spacer(Modifier.height(if (wide) 20.dp else 14.dp))
        EpdSystemNotice("这里设置全局缺省策略；应用单独策略请从 APPS 中该应用右侧的 EPD 进入。")
        Spacer(Modifier.height(18.dp))

        when {
            loading && profile == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "读取系统 EPD 参数",
                    color = InkBlack,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            else -> {
                val active = profile ?: EpdProfile(
                    packageName = "ai.openduo.inkboard.system-default",
                    refreshEnabled = true
                )
                val selected = EpdRefreshPreset.fromProfile(active)
                EpdSectionTitle(
                    title = "全局缺省",
                    value = selected?.title ?: active.refreshMode.label
                )
                Spacer(Modifier.height(12.dp))
                Hairline(thick = true)
                Spacer(Modifier.height(18.dp))
                EpdRefreshPresetGrid(
                    selected = selected,
                    columns = if (wide) 3 else 2,
                    onSelect = { preset -> onProfileChange(preset.applyTo(active)) }
                )
                Spacer(Modifier.height(18.dp))
                EpdSystemNotice(
                    "${active.refreshMode.label} · 每完成 ${active.refreshFrequency} 次局部刷新后，自动执行 1 次全屏刷新。"
                )
                Spacer(Modifier.height(10.dp))
                EpdSystemNotice("没有单独策略的应用会跟随此处的全局缺省；选择 APP 后独立保存。")
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
internal fun EpdRefreshPresetGrid(
    selected: EpdRefreshPreset?,
    columns: Int = 2,
    onSelect: (EpdRefreshPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        EpdRefreshPreset.entries.chunked(columns.coerceIn(1, EpdRefreshPreset.entries.size)).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(94.dp)
            ) {
                row.forEachIndexed { index, preset ->
                    if (index > 0) Spacer(Modifier.width(6.dp))
                    EpdRefreshPresetOption(
                        preset = preset,
                        selected = preset == selected,
                        onClick = { onSelect(preset) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun EpdRefreshPresetOption(
    preset: EpdRefreshPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val foreground = if (selected) InkWhite else InkBlack
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(if (selected) InkBlack else InkPaper)
            // Fixed stroke; selected state is the fill, not a fatter border.
            .border(width = 1.5.dp, color = InkBlack)
            .inkClickable(onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = preset.title,
                color = foreground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.weight(1f))
            if (selected) {
                Text(
                    text = "当前",
                    color = foreground,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Text(
            text = "${preset.modeLabel} · 每 ${preset.frequency} 次局部刷新后全屏 1 次",
            color = foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = preset.description,
            color = foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
