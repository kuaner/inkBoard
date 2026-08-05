package ai.openduo.inkboard.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.openduo.inkboard.data.MonoIconCache
import ai.openduo.inkboard.ui.theme.InkBlack
import ai.openduo.inkboard.ui.theme.InkDark

val InkStroke = 1.5.dp
val InkStrokeThick = 2.5.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.inkClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return if (onLongClick != null) {
        this.then(
            combinedClickable(
                indication = null,
                interactionSource = interaction,
                onClick = onClick,
                onLongClick = onLongClick
            )
        )
    } else {
        this.then(
            clickable(
                indication = null,
                interactionSource = interaction,
                onClick = onClick
            )
        )
    }
}

@Composable
fun MonochromeIcon(
    drawable: Drawable?,
    size: Dp,
    darkBackground: Boolean = false,
    svgPath: String? = null,
    cacheKey: String? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx().coerceAtLeast(1) }
    val key = cacheKey ?: svgPath ?: drawable?.constantState?.hashCode()?.toString() ?: "none"

    // Keyed by stable app identity, not Drawable instance identity. Full-catalog
    // refreshes create new Drawable objects; the monochrome bitmap can stay.
    val bitmap = remember(key, px, darkBackground, svgPath) {
        MonoIconCache.get(
            cacheKey = key,
            sizePx = px,
            darkBackground = darkBackground,
            drawable = drawable,
            svgRelativePath = svgPath,
            context = context
        )
    }

    if (bitmap != null) {
        Image(
            painter = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = null,
            modifier = Modifier.size(size)
        )
        return
    }

    Box(
        modifier = Modifier
            .size(size)
            .background(if (darkBackground) InkBlack.copy(alpha = 0.15f) else InkDark)
    )
}
