package com.moonforce.ohmyainote.ui.editor

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.moonforce.ohmyainote.ai.PageRegionDrawer
import com.moonforce.ohmyainote.ai.RasterizedRegion
import com.moonforce.ohmyainote.ai.RegionRasterizer
import com.moonforce.ohmyainote.document.model.NotebookManifest
import com.moonforce.ohmyainote.document.model.PageBackground
import com.moonforce.ohmyainote.document.model.PageRect
import com.moonforce.ohmyainote.document.model.PageSnapshot
import com.moonforce.ohmyainote.document.model.PaperKind
import com.moonforce.ohmyainote.ink.StrokeBridge
import com.moonforce.ohmyainote.pdf.PageBitmapRequest
import com.moonforce.ohmyainote.pdf.PdfPageRenderer
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PageRasterComposer(private val rasterizer: RegionRasterizer) {
    suspend fun compose(
        manifest: NotebookManifest,
        snapshot: PageSnapshot,
        notebookDir: Path,
        selection: PageRect,
    ): RasterizedRegion = withContext(Dispatchers.Default) {
        val (targetWidth, targetHeight) = rasterizer.targetDimensions(selection)
        val pageToBitmap = selectionMatrix(selection, targetWidth, targetHeight)
        val pdfBackground = (snapshot.page.background as? PageBackground.PdfPage)?.let { background ->
            PdfPageRenderer(notebookDir.resolve(background.sourcePath).toFile()).use { renderer ->
                renderer.render(
                    PageBitmapRequest(
                        pageIndex = background.pdfPageIndex,
                        widthPx = targetWidth,
                        heightPx = targetHeight,
                        pageToBitmap = pageToBitmap,
                    )
                )
            }
        }
        try {
            rasterizer.rasterize(selection, PageRegionDrawer { canvas, matrix ->
                if (pdfBackground != null) {
                    canvas.drawBitmap(
                        pdfBackground,
                        null,
                        Rect(0, 0, canvas.width, canvas.height),
                        Paint(Paint.FILTER_BITMAP_FLAG),
                    )
                } else {
                    drawBackground(canvas, matrix, manifest, snapshot, notebookDir)
                }
                val renderer = CanvasStrokeRenderer.create()
                snapshot.strokes.forEach { record -> renderer.draw(canvas, StrokeBridge.toInk(record), matrix) }
                drawTexts(canvas, matrix, snapshot)
                drawCards(canvas, matrix, snapshot, notebookDir)
            })
        } finally {
            pdfBackground?.recycle()
        }
    }

    private fun drawBackground(
        canvas: Canvas,
        pageToBitmap: Matrix,
        manifest: NotebookManifest,
        snapshot: PageSnapshot,
        notebookDir: Path,
    ) {
        canvas.save()
        canvas.concat(pageToBitmap)
        when (val background = snapshot.page.background) {
            is PageBackground.Template -> {
                val template = requireNotNull(manifest.template)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(template.backgroundColor) }
                canvas.drawRect(0f, 0f, snapshot.page.widthPt, snapshot.page.heightPt, paint)
                if (template.paper != PaperKind.BLANK) {
                    paint.color = Color.parseColor(template.ruleColor)
                    paint.strokeWidth = 0.5f
                    var offset = template.lineSpacingPt
                    while (offset < snapshot.page.heightPt) {
                        canvas.drawLine(0f, offset, snapshot.page.widthPt, offset, paint)
                        offset += template.lineSpacingPt
                    }
                    if (template.paper == PaperKind.GRID) {
                        offset = template.lineSpacingPt
                        while (offset < snapshot.page.widthPt) {
                            canvas.drawLine(offset, 0f, offset, snapshot.page.heightPt, paint)
                            offset += template.lineSpacingPt
                        }
                    }
                }
            }
            is PageBackground.Image -> {
                val bitmap = BitmapFactory.decodeFile(notebookDir.resolve(background.sourcePath).toString())
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, null, RectF(0f, 0f, snapshot.page.widthPt, snapshot.page.heightPt), null)
                    bitmap.recycle()
                }
            }
            is PageBackground.PdfPage -> Unit
        }
        canvas.restore()
    }

    private fun drawTexts(canvas: Canvas, pageToBitmap: Matrix, snapshot: PageSnapshot) {
        if (snapshot.page.texts.isEmpty()) return
        canvas.save()
        canvas.concat(pageToBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true }
        snapshot.page.texts.forEach { record ->
            paint.color = Color.parseColor(record.color)
            paint.textSize = record.fontSizePt
            canvas.drawText(record.text, record.x, record.y + record.fontSizePt * 0.8f, paint)
        }
        canvas.restore()
    }

    private fun drawCards(canvas: Canvas, pageToBitmap: Matrix, snapshot: PageSnapshot, notebookDir: Path) {
        canvas.save()
        canvas.concat(pageToBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        snapshot.page.cards.forEach { card ->
            val rect = RectF(card.anchor.x, card.anchor.y, card.anchor.x + card.anchor.w, card.anchor.y + card.anchor.h)
            paint.color = Color.WHITE
            canvas.drawRoundRect(rect, 8f, 8f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = Color.LTGRAY
            canvas.drawRoundRect(rect, 8f, 8f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.DKGRAY
            paint.textSize = 9f
            canvas.drawText(card.question.take(28), rect.left + 8f, rect.top + 18f, paint)
            canvas.drawText(card.answer.take(36), rect.left + 8f, rect.top + 36f, paint)
        }
        canvas.restore()
    }

    private fun selectionMatrix(selection: PageRect, width: Int, height: Int) = Matrix().apply {
        val sx = width / selection.width
        val sy = height / selection.height
        setValues(
            floatArrayOf(
                sx, 0f, -selection.l * sx,
                0f, sy, -selection.t * sy,
                0f, 0f, 1f,
            )
        )
    }
}
