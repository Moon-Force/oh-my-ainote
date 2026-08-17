package com.moonforce.ohmyainote.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import com.moonforce.ohmyainote.document.model.NotebookManifest
import com.moonforce.ohmyainote.document.model.PageBackground
import com.moonforce.ohmyainote.document.model.PagePoint
import com.moonforce.ohmyainote.document.model.PageSnapshot
import com.moonforce.ohmyainote.document.model.PaperKind
import com.moonforce.ohmyainote.ink.ViewportState
import com.moonforce.ohmyainote.pdf.PageBitmapRequest
import com.moonforce.ohmyainote.pdf.PdfPageRenderer
import java.nio.file.Path
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BackgroundLayer(
    manifest: NotebookManifest,
    snapshot: PageSnapshot,
    notebookDir: Path,
    viewport: ViewportState,
    modifier: Modifier = Modifier,
) {
    val background = snapshot.page.background
    val scaleBucket = when {
        viewport.scale <= 1.25f -> 1.25f
        viewport.scale <= 2f -> 2f
        viewport.scale <= 4f -> 4f
        else -> 8f
    }
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = background,
        key2 = scaleBucket,
    ) {
        value = withContext(Dispatchers.Default) {
            when (background) {
                is PageBackground.PdfPage -> {
                    val pixelsPerPoint = viewport.densityDpi / 72f * scaleBucket
                    val width = ceil(snapshot.page.widthPt * pixelsPerPoint).toInt().coerceIn(1, 4096)
                    val height = ceil(snapshot.page.heightPt * pixelsPerPoint).toInt().coerceIn(1, 4096)
                    PdfPageRenderer(notebookDir.resolve(background.sourcePath).toFile()).use { renderer ->
                        renderer.render(PageBitmapRequest(background.pdfPageIndex, width, height))
                    }
                }
                is PageBackground.Image -> BitmapFactory.decodeFile(notebookDir.resolve(background.sourcePath).toString())
                is PageBackground.Template -> null
            }
        }
    }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }

    Canvas(modifier.fillMaxSize()) {
        val native = drawContext.canvas.nativeCanvas
        val pageToView = viewport.pageToView
        val topLeft = pageToView.transform(PagePoint(0f, 0f))
        val bottomRight = pageToView.transform(PagePoint(snapshot.page.widthPt, snapshot.page.heightPt))
        val destination = RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
        when (background) {
            is PageBackground.Template -> {
                val template = requireNotNull(manifest.template)
                native.save()
                native.concat(viewport.pageToViewAndroid())
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor(template.backgroundColor) }
                native.drawRect(0f, 0f, snapshot.page.widthPt, snapshot.page.heightPt, paint)
                if (background.paper != PaperKind.BLANK) {
                    paint.color = AndroidColor.parseColor(template.ruleColor)
                    paint.strokeWidth = 0.5f
                    var offset = template.lineSpacingPt
                    while (offset < snapshot.page.heightPt) {
                        native.drawLine(0f, offset, snapshot.page.widthPt, offset, paint)
                        offset += template.lineSpacingPt
                    }
                    if (background.paper == PaperKind.GRID) {
                        offset = template.lineSpacingPt
                        while (offset < snapshot.page.widthPt) {
                            native.drawLine(offset, 0f, offset, snapshot.page.heightPt, paint)
                            offset += template.lineSpacingPt
                        }
                    } else {
                        native.drawLine(template.marginLeftPt, 0f, template.marginLeftPt, snapshot.page.heightPt, paint)
                    }
                }
                native.restore()
            }
            else -> bitmap?.let { native.drawBitmap(it, null, destination, Paint(Paint.FILTER_BITMAP_FLAG)) }
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
    DisposableEffect(thumbnails) { onDispose { thumbnails.values.forEach(Bitmap::recycle) } }

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
