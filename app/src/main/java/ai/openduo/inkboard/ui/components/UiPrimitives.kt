package ai.openduo.inkboard.ui.components

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

// Shared layout constants intentionally live here so each feature page uses
// the same page geometry and manual-pagination rhythm.
internal val WidePagePadding = 44.dp
internal val CompactPagePadding = 22.dp
internal val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
internal val DateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy  ·  EEE", Locale.ENGLISH)
internal val DayOfMonthFormatter = DateTimeFormatter.ofPattern("dd")
internal val MonthYearFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
internal val WeekdayFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
internal const val ShortcutColumns = 4
internal const val ShortcutRows = 2
internal const val DrawerPageSize = ShortcutColumns * ShortcutRows
internal const val DrawerGridColumns = 2
internal const val DrawerGridRows = DrawerPageSize / DrawerGridColumns
internal const val IconRows = 4

@Composable
internal fun PaperFrame(
    modifier: Modifier = Modifier,
    bottomPadding: Dp? = null,
    content: @Composable ColumnScope.(wide: Boolean) -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(InkPaper)
    ) {
        val wide = maxWidth >= maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (wide) WidePagePadding else CompactPagePadding,
                    top = if (wide) 32.dp else 22.dp,
                    end = if (wide) WidePagePadding else CompactPagePadding,
                    bottom = bottomPadding ?: (if (wide) 32.dp else 22.dp)
                ),
            content = { content(wide) }
        )
    }
}

@Composable
internal fun EditorialHeader(
    section: String,
    onEdit: () -> Unit,
    onEpd: (() -> Unit)?,
    onSender: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "INKBOARD",
            color = InkBlack,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.2.sp
        )
        Spacer(Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(16.dp)
                .background(InkLine)
        )
        Spacer(Modifier.width(12.dp))
        if (section.isNotBlank()) {
            Text(
                text = section,
                color = InkBlack,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )
        }
        Spacer(Modifier.weight(1f))
        if (onEpd != null) {
            HeaderLink(text = "SYSTEM EPD", onClick = onEpd)
            Spacer(Modifier.width(16.dp))
        }
        HeaderLink(text = "SENDER", onClick = onSender)
        Spacer(Modifier.width(16.dp))
        HeaderLink(text = "APPS", onClick = onEdit)
        Spacer(Modifier.width(16.dp))
        MenuPill(onClick = onMenu)
    }
}

@Composable
internal fun HeaderLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = InkBlack,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier
            .inkClickable(onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp)
    )
}

@Composable
internal fun MenuPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(InkBlack)
            .inkClickable(onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "MENU",
            color = InkWhite,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(InkWhite, RoundedCornerShape(50))
        )
    }
}

@Composable
internal fun PageHeader(
    title: String,
    meta: String,
    actionLabel: String?,
    onBack: () -> Unit,
    onAction: (() -> Unit)?,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .inkClickable(onBack),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "←",
                color = InkBlack,
                fontSize = 31.sp,
                fontWeight = FontWeight.Light
            )
        }
        Text(
            text = title,
            color = InkBlack,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp
        )
        Spacer(Modifier.weight(1f))
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            HeaderLink(text = secondaryActionLabel, onClick = onSecondaryAction)
            Spacer(Modifier.width(14.dp))
        }
        Text(
            text = meta,
            color = InkMid,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(22.dp))
            HeaderLink(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
internal fun ControlActionTile(
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    value: String? = null
) {
    val foreground = if (emphasized) InkWhite else InkBlack
    val secondary = if (emphasized) InkWhite else InkDark
    // fillMaxHeight so side-by-side / stacked weight tiles share one border box
    // even when titles/details wrap differently. Keep stroke width constant so
    // selection/emphasis does not make one tile look larger than its neighbor.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(if (emphasized) InkBlack else InkPaper)
            .border(width = 1.5.dp, color = InkBlack)
            .inkClickable(onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = title,
                color = foreground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.3).sp,
                modifier = Modifier.weight(1f)
            )
            if (value != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = value,
                    color = foreground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.1.sp
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = detail,
            color = secondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}

/**
 * A solid, full-page sheet rather than an Android dialog.  It avoids dimmed
 * overlays, elevation shadows and their residual traces on the e-ink panel.
 */

@Composable
internal fun PageNavigation(
    page: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val canGoPrevious = page > 0
    val canGoNext = page < pageCount - 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(InkPaper)
            .border(1.5.dp, InkBlack)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PagerAction(text = "上一页", enabled = canGoPrevious, onClick = onPrevious)
        Spacer(Modifier.weight(1f))
        Text(
            text = "${page + 1} / $pageCount",
            color = InkBlack,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.weight(1f))
        PagerAction(text = "下一页", enabled = canGoNext, onClick = onNext)
    }
}

@Composable
internal fun PagerAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (enabled) InkBlack else InkDark,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.1.sp,
        modifier = Modifier
            .then(if (enabled) Modifier.inkClickable(onClick) else Modifier)
            .padding(vertical = 12.dp)
    )
}

@Composable
internal fun AppGlyph(
    app: AppInfo,
    size: Dp,
    darkBackground: Boolean = false
) {
    if (app.customIconPath != null || app.builtInShortcut == null) {
        MonochromeIcon(
            drawable = app.icon,
            size = size,
            darkBackground = darkBackground,
            svgPath = app.customIconPath,
            cacheKey = app.key
        )
        return
    }

    val svgPath = when (app.builtInShortcut ?: return) {
        BuiltInShortcut.CLEAR_BACKGROUND -> "koboyo/svg/trash.svg"
        BuiltInShortcut.FULL_REFRESH -> "koboyo/svg/refresh-circular-arrow.svg"
        BuiltInShortcut.LOCK_SCREEN -> "koboyo/svg/lock-closed.svg"
    }
    MonochromeIcon(
        drawable = null,
        size = size,
        darkBackground = darkBackground,
        svgPath = svgPath,
        cacheKey = app.key
    )
}

@Composable
internal fun RotationGlyph(mode: OrientationMode, color: Color, glyphSize: Dp = 42.dp) {
    Canvas(Modifier.size(glyphSize)) {
        val vertical = mode == OrientationMode.PORTRAIT || mode == OrientationMode.PORTRAIT_REVERSE
        val width = if (vertical) size.width * 0.45f else size.width * 0.78f
        val height = if (vertical) size.height * 0.78f else size.height * 0.45f
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = CornerRadius(3f, 3f),
            style = Stroke(width = 2.2f)
        )
        val reverse = mode == OrientationMode.PORTRAIT_REVERSE || mode == OrientationMode.LANDSCAPE_REVERSE
        val markerY = if (reverse) top + 4f else top + height - 4f
        drawCircle(
            color = color,
            radius = 1.8f,
            center = Offset(size.width / 2f, markerY)
        )
    }
}

internal fun formatCurrentTime(): String = LocalTime.now().format(TimeFormatter)

internal fun formatCurrentDate(): String = LocalDate.now().format(DateFormatter).uppercase(Locale.ENGLISH)

/** Structured pieces for the home clock panel (magazine layout). */
internal data class HomeDateParts(
    val dayOfMonth: String,
    val monthYear: String,
    val weekday: String
)

internal fun formatHomeDateParts(date: LocalDate = LocalDate.now()): HomeDateParts = HomeDateParts(
    dayOfMonth = date.format(DayOfMonthFormatter),
    monthYear = date.format(MonthYearFormatter).uppercase(Locale.ENGLISH),
    weekday = date.format(WeekdayFormatter).uppercase(Locale.ENGLISH)
)

@Composable
internal fun VerticalRule() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(InkLine)
    )
}

@Composable
internal fun Hairline(thick: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (thick) 2.dp else 1.dp)
            .background(if (thick) InkBlack else InkLine)
    )
}
