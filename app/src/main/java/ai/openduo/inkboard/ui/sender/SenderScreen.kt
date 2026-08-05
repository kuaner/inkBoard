package ai.openduo.inkboard.ui.sender

import ai.openduo.inkboard.ui.components.ControlActionTile
import ai.openduo.inkboard.ui.components.Hairline
import ai.openduo.inkboard.ui.components.PageHeader
import ai.openduo.inkboard.ui.components.PaperFrame

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
internal fun SenderPage(
    snapshot: SenderSnapshot,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    DisposableEffect(Unit) {
        onDispose { onStop() }
    }

    PaperFrame(modifier) { wide ->
        PageHeader(
            title = "SENDER.",
            meta = "TEMPORARY",
            actionLabel = "CLOSE",
            onBack = onBack,
            onAction = onBack
        )
        Spacer(Modifier.height(if (wide) 30.dp else 20.dp))

        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.Top
            ) {
                SenderInstructions(modifier = Modifier.weight(1.05f))
                Spacer(Modifier.width(42.dp))
                SenderAddressPanel(
                    snapshot = snapshot,
                    onRetry = onRetry,
                    wide = true,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SenderInstructions()
                Spacer(Modifier.height(26.dp))
                SenderAddressPanel(
                    snapshot = snapshot,
                    onRetry = onRetry,
                    wide = false
                )
            }
        }
    }
}

@Composable
private fun SenderInstructions(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxHeight()) {
        Text(
            text = "LOCAL FILE DROP",
            color = InkBlack,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "把文件送到\n平板。",
            color = InkBlack,
            fontSize = 44.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1.2).sp
        )
        Spacer(Modifier.height(22.dp))
        Hairline(thick = true)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "电脑与平板连接同一网络，\n在电脑浏览器打开右侧地址。",
            color = InkBlack,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "网页可以选择文件夹，目录结构会保留。\n文件保存到 Download / InkBoard；返回后服务关闭。",
            color = InkDark,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SenderAddressPanel(
    snapshot: SenderSnapshot,
    onRetry: () -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "CONNECT TO THIS DEVICE",
            color = InkBlack,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.8.sp
        )
        Spacer(Modifier.height(12.dp))
        SenderConnectionCard(
            snapshot = snapshot,
            qrSize = if (wide) 176.dp else 154.dp
        )
        if (!snapshot.running && !snapshot.loading) {
            Spacer(Modifier.height(16.dp))
            ControlActionTile(
                title = "RETRY",
                detail = "重新启动临时服务",
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
            )
        }
    }
}

@Composable
private fun SenderConnectionCard(
    snapshot: SenderSnapshot,
    qrSize: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(InkBlack)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LOCAL DROP",
                    color = InkWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.8.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (snapshot.url != null) "扫码或打开地址" else "正在准备连接",
                    color = InkWhite,
                    fontSize = 21.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = when {
                        snapshot.url != null -> snapshot.url
                        snapshot.loading -> "正在启动…"
                        else -> "局域网地址未发现"
                    },
                    color = InkWhite,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (snapshot.port != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "PORT ${snapshot.port}",
                        color = InkWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = when {
                        snapshot.loading -> "正在打开临时服务…"
                        snapshot.running && snapshot.url != null -> "服务已开启 · 等待上传"
                        snapshot.running -> "服务已开启，但未找到局域网地址"
                        snapshot.error != null -> snapshot.error
                        else -> "服务已关闭"
                    },
                    color = InkWhite,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (snapshot.url != null) {
                Spacer(Modifier.width(18.dp))
                SenderQrMark(
                    url = snapshot.url,
                    size = qrSize
                )
            }
        }
    }
}

@Composable
private fun SenderQrMark(
    url: String,
    size: Dp
) {
    val bitmap = remember(url) { createSenderQrBitmap(url) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(size)
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "扫码打开文件上传页面",
            modifier = Modifier.size(size),
            filterQuality = FilterQuality.None
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "PHONE / SCAN",
            color = InkWhite,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.1.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun createSenderQrBitmap(content: String, size: Int = 512): Bitmap {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 4)
    )
    val pixels = IntArray(size * size) { android.graphics.Color.WHITE }
    for (y in 0 until size) {
        for (x in 0 until size) {
            if (matrix.get(x, y)) {
                pixels[y * size + x] = android.graphics.Color.BLACK
            }
        }
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
        it.setPixels(pixels, 0, size, 0, 0, size, size)
    }
}
