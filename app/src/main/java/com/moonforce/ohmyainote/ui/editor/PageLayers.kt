package com.moonforce.ohmyainote.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntSize
import com.moonforce.ohmyainote.document.model.NotebookManifest
import com.moonforce.ohmyainote.document.model.PageBackground
import com.moonforce.ohmyainote.document.model.PagePoint
import com.moonforce.ohmyainote.document.model.PageSnapshot
import com.moonforce.ohmyainote.document.model.PaperKind
import com.moonforce.ohmyainote.ink.ViewportState
import com.moonforce.ohmyainote.pdf.TileKey
import java.nio.file.Path
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class TilePlan(
    val pageIndex: Int,
    val tilePt: Float,
    val x0: Int,
    val y0: Int,
    val x1: Int,
    val y1: Int,
)

private fun visibleTilePlan(
    viewport: ViewportState,
    viewSize: IntSize,
    pageWidthPt: Float,
    pageHeightPt: Float,
    scaleBucket: Float,
    pageIndex: Int,
): TilePlan {
    val inverse = viewport.viewToPageCompose()
    val a = inverse.map(Offset(0f, 0f))
    val b = inverse.map(Offset(viewSize.width.toFloat(), viewSize.height.toFloat()))
    val left = minOf(a.x, b.x).coerceIn(0f, pageWidthPt)
    val top = minOf(a.y, b.y).coerceIn(0f, pageHeightPt)
    val right = maxOf(a.x, b.x).coerceIn(0f, pageWidthPt)
    val bottom = maxOf(a.y, b.y).coerceIn(0f, pageHeightPt)
    val pixelsPerPoint = viewport.densityDpi / 72f * scaleBucket
    val tilePt = PDF_TILE_PX / pixelsPerPoint
    val l = (left - tilePt).coerceAtLeast(0f)
    val t = (top - tilePt).coerceAtLeast(0f)
    val r = (right + tilePt).coerceAtMost(pageWidthPt)
    val bo = (bottom + tilePt).coerceAtMost(pageHeightPt)
    return TilePlan(
        pageIndex = pageIndex,
        tilePt = tilePt,
        x0 = floor(l / tilePt).toInt(),
        y0 = floor(t / tilePt).toInt(),
        x1 = floor(r / tilePt).toInt(),
        y1 = floor(bo / tilePt).toInt(),
    )
}

@Composable
fun BackgroundLayer(
    manifest: NotebookManifest,
    snapshot: PageSnapshot,
    notebookDir: Path,
    viewport: ViewportState,
    viewSize: IntSize,
    tileProvider: PdfTileProvider,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    val background = snapshot.page.background
    val page = snapshot.page
    val pdf = background as? PageBackground.PdfPage
    val scaleBucket = when {
        viewport.scale <= 1.25f -> 1.25f
        viewport.scale <= 2f -> 2f
        viewport.scale <= 4f -> 4f
        else -> 8f
    }

    val tilePlan = remember(background, scaleBucket, viewport, viewSize, page.widthPt, page.heightPt) {
        if (pdf == null || viewSize.width <= 0 || viewSize.height <= 0) null
        else visibleTilePlan(viewport, viewSize, page.widthPt, page.heightPt, scaleBucket, pdf.pdfPageIndex)
    }
    val tileVersion = remember { mutableIntStateOf(0) }

    LaunchedEffect(tilePlan) {
        val plan = tilePlan ?: return@LaunchedEffect
        val source = pdf ?: return@LaunchedEffect
        val file = notebookDir.resolve(source.sourcePath).toFile()
        val renderTile: suspend (tx: Int, ty: Int, pageIndex: Int) -> Unit = { tx, ty, pageIndex ->
            val key = TileKey(manifest.id, pageIndex, scaleBucket, tx, ty)
            if (tileProvider.cache.get(key) == null) {
                tileProvider.tile(key, file, page.widthPt, page.heightPt, tx * plan.tilePt, ty * plan.tilePt, plan.tilePt, PDF_TILE_PX)
                tileVersion.value++
            }
        }
        for (ty in plan.y0..plan.y1) for (tx in plan.x0..plan.x1) renderTile(tx, ty, plan.pageIndex)
        // Best-effort prefetch of the adjacent pages at the same camera (paging keeps the viewport).
        delay(400)
        for (adjacent in listOf(plan.pageIndex - 1, plan.pageIndex + 1).filter { it in 0 until pageCount }) {
            for (ty in plan.y0..plan.y1) for (tx in plan.x0..plan.x1) renderTile(tx, ty, adjacent)
        }
    }

    val imageBitmap by produceState<Bitmap?>(initialValue = null, key1 = background) {
        value = if (background is PageBackground.Image) {
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(notebookDir.resolve(background.sourcePath).toString()) }
        } else {
            null
        }
    }

    Canvas(modifier.fillMaxSize()) {
        val native = drawContext.canvas.nativeCanvas
        val pageToView = viewport.pageToView
        val topLeft = pageToView.transform(PagePoint(0f, 0f))
        val bottomRight = pageToView.transform(PagePoint(page.widthPt, page.heightPt))
        val destination = RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
        when (background) {
            is PageBackground.Template -> {
                val template = requireNotNull(manifest.template)
                native.save()
                native.concat(viewport.pageToViewAndroid())
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor(template.backgroundColor) }
                native.drawRect(0f, 0f, page.widthPt, page.heightPt, paint)
                if (background.paper != PaperKind.BLANK) {
                    paint.color = AndroidColor.parseColor(template.ruleColor)
                    paint.strokeWidth = 0.5f
                    var offset = template.lineSpacingPt
                    while (offset < page.heightPt) {
                        native.drawLine(0f, offset, page.widthPt, offset, paint)
                        offset += template.lineSpacingPt
                    }
                    if (background.paper == PaperKind.GRID) {
                        offset = template.lineSpacingPt
                        while (offset < page.widthPt) {
                            native.drawLine(offset, 0f, offset, page.heightPt, paint)
                            offset += template.lineSpacingPt
                        }
                    } else {
                        native.drawLine(template.marginLeftPt, 0f, template.marginLeftPt, page.heightPt, paint)
                    }
                }
                native.restore()
            }
            is PageBackground.Image -> imageBitmap?.let { native.drawBitmap(it, null, destination, Paint(Paint.FILTER_BITMAP_FLAG)) }
            is PageBackground.PdfPage -> {
                val plan = tilePlan
                if (plan != null) {
                    tileVersion.value.let { }
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                    for (ty in plan.y0..plan.y1) {
                        for (tx in plan.x0..plan.x1) {
                            val key = TileKey(manifest.id, plan.pageIndex, scaleBucket, tx, ty)
                            val bitmap = tileProvider.cache.get(key) ?: continue
                            val l = pageToView.transform(PagePoint(tx * plan.tilePt, ty * plan.tilePt))
                            val r = pageToView.transform(PagePoint((tx + 1) * plan.tilePt, (ty + 1) * plan.tilePt))
                            native.drawBitmap(bitmap, null, RectF(l.x, l.y, r.x, r.y), paint)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiCardLayer(snapshot: PageSnapshot, notebookDir: Path, viewport: ViewportState, modifier: Modifier = Modifier) {
    val thumbnails by produceState<Map<String, Bitmap>>(
        initialValue = emptyMap(),
        key1 = snapshot.page.cards,
        key2 = notebookDir,
    ) {
        value = withContext(Dispatchers.IO) {
            snapshot.page.cards.mapNotNull { card ->
                BitmapFactory.decodeFile(notebookDir.resolve(card.thumbPath).toString())?.let { card.id to it }
            }.toMap()
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.concat(viewport.pageToViewAndroid())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        snapshot.page.cards.forEach { card ->
            val rect = RectF(card.anchor.x, card.anchor.y, card.anchor.x + card.anchor.w, card.anchor.y + card.anchor.h)
            paint.color = AndroidColor.WHITE
            canvas.drawRoundRect(rect, 8f, 8f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = AndroidColor.LTGRAY
            canvas.drawRoundRect(rect, 8f, 8f, paint)
            paint.style = Paint.Style.FILL
            thumbnails[card.id]?.let { thumbnail ->
                canvas.drawBitmap(
                    thumbnail,
                    null,
                    RectF(rect.left + 8f, rect.top + 8f, rect.left + 56f, rect.top + 56f),
                    paint,
                )
            }
            paint.color = AndroidColor.DKGRAY
            paint.textSize = 9f
            canvas.drawText(card.question.take(24), rect.left + 64f, rect.top + 19f, paint)
            canvas.drawText(card.answer.take(30), rect.left + 64f, rect.top + 38f, paint)
        }
        canvas.restore()
    }
}
