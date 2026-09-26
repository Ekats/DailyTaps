package io.github.ekats.dailytaps.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import io.github.ekats.dailytaps.data.Fill
import io.github.ekats.dailytaps.data.GradientDirection
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Glance has no gradient or (below Android 12) rounded-corner backgrounds, so those fills are
 * drawn into small bitmaps that the widget stretches over the cell. Bitmaps keep the cell's aspect
 * ratio so rounded corners stay round when scaled.
 */
object FillBitmaps {
    private const val MAX_SIDE_PX = 160

    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun render(fill: Fill, widthPx: Int, heightPx: Int, radiusPx: Float): Bitmap {
        val scale = minOf(1f, MAX_SIDE_PX.toFloat() / max(widthPx, heightPx).coerceAtLeast(1))
        val w = (widthPx * scale).roundToInt().coerceAtLeast(1)
        val h = (heightPx * scale).roundToInt().coerceAtLeast(1)
        val r = radiusPx * scale
        val key = "$fill|$w|$h|${r.roundToInt()}"
        cache.get(key)?.let { return it }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (fill) {
            is Fill.Solid -> paint.color = fill.color
            is Fill.Gradient -> paint.shader = shader(fill, w.toFloat(), h.toFloat())
        }
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        Canvas(bitmap).drawRoundRect(rect, r, r, paint)
        cache.put(key, bitmap)
        return bitmap
    }

    private fun shader(fill: Fill.Gradient, w: Float, h: Float): Shader = when (fill.direction) {
        GradientDirection.TOP_BOTTOM -> LinearGradient(0f, 0f, 0f, h, fill.start, fill.end, Shader.TileMode.CLAMP)
        GradientDirection.LEFT_RIGHT -> LinearGradient(0f, 0f, w, 0f, fill.start, fill.end, Shader.TileMode.CLAMP)
        GradientDirection.TOP_LEFT_BOTTOM_RIGHT -> LinearGradient(0f, 0f, w, h, fill.start, fill.end, Shader.TileMode.CLAMP)
        GradientDirection.BOTTOM_LEFT_TOP_RIGHT -> LinearGradient(0f, h, w, 0f, fill.start, fill.end, Shader.TileMode.CLAMP)
        GradientDirection.RADIAL -> RadialGradient(
            w / 2f, h / 2f, (hypot(w, h) / 2f).coerceAtLeast(1f),
            fill.start, fill.end, Shader.TileMode.CLAMP,
        )
    }
}
