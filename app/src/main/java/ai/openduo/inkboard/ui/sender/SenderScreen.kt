package ai.openduo.inkboard.ui.sender

import ai.openduo.inkboard.ui.components.ControlActionTile
import ai.openduo.inkboard.ui.components.Hairline
import ai.openduo.inkboard.ui.components.PageHeader
import ai.openduo.inkboard.ui.components.PaperFrame
import ai.openduo.inkboard.ui.components.VerticalRule

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openduo.inkboard.SenderSnapshot
import ai.openduo.inkboard.ui.theme.InkBlack
import ai.openduo.inkboard.ui.theme.InkDark
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.min

/**
 * Temporary LAN file drop UI.
 *
 * Content band under the header is always split 50/50:
 * landscape left/right, portrait top/bottom. Paper-only (no solid black
 * panels). Portrait scales type and QR from the half-panel size so the
 * short axis does not look sparse.
 */
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
        Spacer(Modifier.height(if (wide) 18.dp else 10.dp))

        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.Top
            ) {
                SenderInstructionsLandscape(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                Spacer(Modifier.width(20.dp))
                VerticalRule()
                Spacer(Modifier.width(20.dp))
                SenderConnectLandscape(
                    snapshot = snapshot,
                    onRetry = onRetry,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SenderInstructionsPortrait(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Hairline(thick = true)
                Spacer(Modifier.height(8.dp))
                SenderConnectPortrait(
                    snapshot = snapshot,
                    onRetry = onRetry,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }
}

// ── Landscape (already fine; keep bold magazine scale) ──────────────────────

@Composable
private fun SenderInstructionsLandscape(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "LOCAL FILE DROP",
                color = InkBlack,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "把文件送到\n平板。",
                color = InkBlack,
                fontSize = 42.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.2).sp
            )
            Spacer(Modifier.height(18.dp))
            Hairline(thick = true)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "电脑与平板连接同一网络，\n在电脑浏览器打开右侧地址。",
                color = InkBlack,
                fontSize = 17.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "网页可以选择文件夹，目录结构会保留。\n文件保存到 Download / InkBoard 等目录；返回后服务关闭。",
            color = InkDark,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SenderConnectLandscape(
    snapshot: SenderSnapshot,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val urlText = connectionUrlText(snapshot)
    val statusText = connectionStatusText(snapshot)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "CONNECT TO THIS DEVICE",
                color = InkBlack,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.8.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (snapshot.url != null) "扫码或打开地址" else "正在准备连接",
                color = InkBlack,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = urlText,
                color = InkBlack,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Black,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = statusText,
                color = InkDark,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (snapshot.url != null) {
                SenderQrMark(
                    url = snapshot.url,
                    modifier = Modifier.fillMaxSize(0.95f)
                )
            } else {
                Text(
                    text = if (snapshot.loading) "准备二维码…" else "暂无地址",
                    color = InkDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (!snapshot.running && !snapshot.loading) {
            ControlActionTile(
                title = "RETRY",
                detail = "重新启动临时服务",
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            )
        }
    }
}

// ── Portrait: scale everything from the half-panel bounds ───────────────────

@Composable
private fun SenderInstructionsPortrait(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val h = maxHeight.value
        val w = maxWidth.value
        // Use the shorter half-panel axis as the scale driver.
        val scale = min(h / 420f, w / 360f).coerceIn(0.85f, 1.55f)

        fun s(base: Float): TextUnit = (base * scale).sp
        fun d(base: Float): Dp = (base * scale).dp

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "LOCAL FILE DROP",
                    color = InkBlack,
                    fontSize = s(13f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(d(10f)))
                Text(
                    text = "把文件送到平板。",
                    color = InkBlack,
                    fontSize = s(34f),
                    lineHeight = s(38f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(d(12f)))
                Hairline(thick = true)
                Spacer(Modifier.height(d(12f)))
                Text(
                    text = "电脑与平板同一网络。\n浏览器打开下方地址，或扫码。",
                    color = InkBlack,
                    fontSize = s(18f),
                    lineHeight = s(26f),
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "支持选文件夹 · 默认 Download / InkBoard\n返回本页后服务关闭",
                color = InkDark,
                fontSize = s(15f),
                lineHeight = s(22f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SenderConnectPortrait(
    snapshot: SenderSnapshot,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val urlText = connectionUrlText(snapshot)
    val statusText = connectionStatusText(snapshot)
    val showRetry = !snapshot.running && !snapshot.loading

    BoxWithConstraints(modifier = modifier) {
        val h = maxHeight.value
        val w = maxWidth.value
        val scale = min(h / 420f, w / 360f).coerceIn(0.85f, 1.55f)

        fun s(base: Float): TextUnit = (base * scale).sp
        fun d(base: Float): Dp = (base * scale).dp

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "CONNECT TO THIS DEVICE",
                    color = InkBlack,
                    fontSize = s(13f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.6.sp
                )
                Spacer(Modifier.height(d(8f)))
                Text(
                    text = if (snapshot.url != null) "扫码或打开地址" else "正在准备连接",
                    color = InkBlack,
                    fontSize = s(28f),
                    lineHeight = s(32f),
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(d(10f)))
                Text(
                    text = urlText,
                    color = InkBlack,
                    fontSize = s(20f),
                    lineHeight = s(26f),
                    fontWeight = FontWeight.Black,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(d(10f)))
                Text(
                    text = statusText,
                    color = InkDark,
                    fontSize = s(16f),
                    lineHeight = s(22f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // QR takes the leftover band — size from actual remaining height.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .padding(vertical = d(6f)),
                contentAlignment = Alignment.Center
            ) {
                if (snapshot.url != null) {
                    SenderQrMark(
                        url = snapshot.url,
                        modifier = Modifier.fillMaxSize(),
                        captionSize = s(11f)
                    )
                } else {
                    Text(
                        text = if (snapshot.loading) "准备二维码…" else "暂无地址",
                        color = InkDark,
                        fontSize = s(16f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (showRetry) {
                ControlActionTile(
                    title = "RETRY",
                    detail = "重新启动临时服务",
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(d(68f).coerceIn(56.dp, 80.dp))
                )
            }
        }
    }
}

@Composable
private fun SenderQrMark(
    url: String,
    modifier: Modifier = Modifier,
    captionSize: TextUnit = 10.sp
) {
    val bitmap = remember(url) { createSenderQrBitmap(url) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Leave a thin strip for the caption under the code.
        val captionReserve = with(density) { (captionSize.value * 1.6f).sp.toDp() + 8.dp }
        val side = minOf(maxWidth, (maxHeight - captionReserve).coerceAtLeast(80.dp))
            .coerceAtLeast(120.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "扫码打开文件上传页面",
                modifier = Modifier.size(side),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "PHONE / SCAN",
                color = InkBlack,
                fontSize = captionSize,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

private fun connectionUrlText(snapshot: SenderSnapshot): String = when {
    snapshot.url != null -> snapshot.url
    snapshot.loading -> "正在启动…"
    else -> "局域网地址未发现"
}

private fun connectionStatusText(snapshot: SenderSnapshot): String = when {
    snapshot.loading -> "正在启动临时服务…"
    snapshot.running && snapshot.url != null ->
        "服务已开启，等待上传。\n返回本页即关闭服务。"
    snapshot.running ->
        "服务已开启，但未找到局域网地址。\n返回本页即关闭服务。"
    snapshot.error != null -> snapshot.error.orEmpty()
    else -> "服务已关闭。重新进入 SENDER 可再开启。"
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
