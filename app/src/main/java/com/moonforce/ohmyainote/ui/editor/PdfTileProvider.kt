package com.moonforce.ohmyainote.ui.editor

import android.graphics.Bitmap
import android.graphics.Matrix
import com.moonforce.ohmyainote.pdf.PageBitmapRequest
import com.moonforce.ohmyainote.pdf.PdfPageInfo
import com.moonforce.ohmyainote.pdf.PdfPageRenderer
import com.moonforce.ohmyainote.pdf.TileCache
import com.moonforce.ohmyainote.pdf.TileKey
import java.io.File

const val PDF_TILE_PX = 512

/**
 * Shared PDF tile renderer for the editor. Keeps one [PdfPageRenderer] open per source
 * file and a process-wide [TileCache] (96 MB LRU). Tiles are rendered at a scale bucket
 * resolution and drawn at the live viewport scale, so high zoom stays sharp without a
 * single 4096-capped whole-page bitmap.
 */
class PdfTileProvider {
    val cache = TileCache()
    private val renderers = mutableMapOf<String, PdfPageRenderer>()
    private val pageInfos = mutableMapOf<Int, PdfPageInfo>()

    val tileCount: Int get() = cache.tileCount
    val cacheBytes: Long get() = cache.sizeBytes.toLong()

    @Synchronized
    private fun rendererFor(file: File): PdfPageRenderer =
        renderers.getOrPut(file.absolutePath) { PdfPageRenderer(file) }

    private suspend fun pageInfoFor(renderer: PdfPageRenderer, pageIndex: Int): PdfPageInfo {
        pageInfos[pageIndex]?.let { return it }
        val info = renderer.pageInfo(pageIndex)
        synchronized(pageInfos) { pageInfos[pageIndex] = info }
        return info
    }

    suspend fun tile(
        key: TileKey,
        file: File,
        pageWidthPt: Float,
        pageHeightPt: Float,
        tileLeftPt: Float,
        tileTopPt: Float,
        tilePt: Float,
        tilePx: Int,
    ): Bitmap {
        cache.get(key)?.let { return it }
        val renderer = rendererFor(file)
        val info = pageInfoFor(renderer, key.pageIndex)
        // Map the tile's display-space rect into the renderer's page space using the same
        // per-axis factors the whole-page background render uses, so tiles align with it.
        val rx = info.widthPt.toFloat().coerceAtLeast(1f) / pageWidthPt
        val ry = info.heightPt.toFloat().coerceAtLeast(1f) / pageHeightPt
        val rLeft = tileLeftPt * rx
        val rTop = tileTopPt * ry
        val rWidth = tilePt * rx
        val rHeight = tilePt * ry
        val matrix = Matrix().apply {
            setTranslate(-rLeft, -rTop)
            postScale(tilePx / rWidth, tilePx / rHeight)
        }
        val bitmap = renderer.render(PageBitmapRequest(key.pageIndex, tilePx, tilePx, pageToBitmap = matrix))
        cache.put(key, bitmap)
        return bitmap
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    @Synchronized
    fun close() {
        renderers.values.forEach { it.close() }
        renderers.clear()
        pageInfos.clear()
        cache.clear()
    }
}
