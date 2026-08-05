package ai.openduo.inkboard.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.caverock.androidsvg.SVG
import java.io.File

/**
 * Process-wide monochrome icon cache.
 *
 * Home used to re-decode package drawables on every composition / app-list
 * refresh, which made icons appear a beat after the empty grid on this tablet.
 */
object MonoIconCache {

    private val cache = object : LruCache<String, Bitmap>(48) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    fun get(
        cacheKey: String,
        sizePx: Int,
        darkBackground: Boolean,
        drawable: Drawable?,
        svgRelativePath: String?,
        context: Context
    ): Bitmap? {
        val key = "$cacheKey|$sizePx|${if (darkBackground) 1 else 0}|${svgRelativePath.orEmpty()}"
        cache.get(key)?.let { return it }

        val created = when {
            !svgRelativePath.isNullOrBlank() ->
                svgFileToMonoBitmap(context, svgRelativePath, sizePx, darkBackground)
                    ?: drawable?.let { drawableToMonoBitmap(it, sizePx, darkBackground) }
            drawable != null -> drawableToMonoBitmap(drawable, sizePx, darkBackground)
            else -> null
        } ?: return null

        cache.put(key, created)
        return created
    }

    fun prewarm(
        app: AppInfo,
        sizePx: Int,
        context: Context,
        darkBackground: Boolean = false
    ) {
        get(
            cacheKey = app.key,
            sizePx = sizePx,
            darkBackground = darkBackground,
            drawable = app.icon,
            svgRelativePath = app.customIconPath,
            context = context
        )
    }

    fun invalidate(appKey: String) {
        val snapshot = cache.snapshot().keys.toList()
        snapshot.forEach { key ->
            if (key.startsWith("$appKey|")) cache.remove(key)
        }
    }

    private fun drawableToMonoBitmap(drawable: Drawable, sizePx: Int, invert: Boolean): Bitmap {
        val src = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(src)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        val output = applyMonoFilter(src, invert)
        src.recycle()
        return output
    }

    private fun svgFileToMonoBitmap(
        context: Context,
        relativePath: String,
        sizePx: Int,
        invert: Boolean
    ): Bitmap? {
        val svgText = readSvgText(context, relativePath) ?: return null
        return runCatching {
            val normalized = svgText.replace("currentColor", "#000000")
            val picture = SVG.getFromString(normalized).renderToPicture(sizePx, sizePx)
            val source = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            Canvas(source).drawPicture(picture)
            val output = applyMonoFilter(source, invert)
            source.recycle()
            output
        }.getOrNull()
    }

    /**
     * Bundled assets (`koboyo/svg/…`) first, then legacy filesDir paths
     * (`icons/…`) from older online-cache installs.
     */
    private fun readSvgText(context: Context, relativePath: String): String? {
        val asset = runCatching {
            context.assets.open(relativePath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
        if (!asset.isNullOrBlank()) return asset

        val file = File(context.filesDir, relativePath)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    private fun applyMonoFilter(source: Bitmap, invert: Boolean): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.45f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        if (invert) {
            matrix.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }
}
