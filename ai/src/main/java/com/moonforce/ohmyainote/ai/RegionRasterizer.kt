package com.moonforce.ohmyainote.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import com.moonforce.ohmyainote.document.model.PageRect
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun interface PageRegionDrawer {
    fun draw(canvas: Canvas, pageToBitmap: Matrix)
}

data class RasterizedRegion(
    val jpeg: ByteArray,
    val widthPx: Int,
    val heightPx: Int,
)

class RegionRasterizer {
    fun targetDimensions(selection: PageRect): Pair<Int, Int> = dimensions(selection, TARGET_DPI, MAX_EDGE)

    fun rasterize(selection: PageRect, drawer: PageRegionDrawer): RasterizedRegion {
        require(selection.width >= 24f && selection.height >= 24f) { "Selection must be at least 24 pt" }
        var dimensions = dimensions(selection, TARGET_DPI, MAX_EDGE)
        var result = render(selection, dimensions.first, dimensions.second, 88, drawer)
        if (result.jpeg.size > MAX_BYTES) result = render(selection, dimensions.first, dimensions.second, 80, drawer)
        if (result.jpeg.size > MAX_BYTES && max(dimensions.first, dimensions.second) > RETRY_MAX_EDGE) {
            dimensions = dimensions(selection, TARGET_DPI, RETRY_MAX_EDGE)
            result = render(selection, dimensions.first, dimensions.second, 80, drawer)
        }
        return result
    }

    private fun render(
        selection: PageRect,
        width: Int,
        height: Int,
        quality: Int,
        drawer: PageRegionDrawer,
    ): RasterizedRegion {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val sx = width / selection.width
            val sy = height / selection.height
            val matrix = Matrix().apply {
                setValues(
                    floatArrayOf(
                        sx, 0f, -selection.l * sx,
                        0f, sy, -selection.t * sy,
                        0f, 0f, 1f,
                    )
                )
            }
            drawer.draw(canvas, matrix)
            val output = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
            return RasterizedRegion(output.toByteArray(), width, height)
        } finally {
            bitmap.recycle()
        }
    }

    private fun dimensions(rect: PageRect, dpi: Int, maxEdge: Int): Pair<Int, Int> {
        var width = max(1, (rect.width * dpi / 72f).roundToInt())
        var height = max(1, (rect.height * dpi / 72f).roundToInt())
        val longest = max(width, height)
        if (longest > maxEdge) {
            val factor = maxEdge.toFloat() / longest
            width = max(1, (width * factor).roundToInt())
            height = max(1, (height * factor).roundToInt())
        }
        val shortest = min(width, height)
        if (shortest < MIN_EDGE) {
            val factor = MIN_EDGE.toFloat() / shortest
            width = (width * factor).roundToInt()
            height = (height * factor).roundToInt()
            val secondLongest = max(width, height)
            if (secondLongest > maxEdge) {
                val shrink = maxEdge.toFloat() / secondLongest
                width = max(1, (width * shrink).roundToInt())
                height = max(1, (height * shrink).roundToInt())
            }
        }
        return width to height
    }

    private companion object {
        const val TARGET_DPI = 180
        const val MAX_EDGE = 1600
        const val RETRY_MAX_EDGE = 1280
        const val MIN_EDGE = 256
        const val MAX_BYTES = 1_500_000
    }
}
