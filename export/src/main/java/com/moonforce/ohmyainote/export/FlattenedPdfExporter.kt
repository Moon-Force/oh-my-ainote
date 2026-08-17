package com.moonforce.ohmyainote.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.moonforce.ohmyainote.document.format.pageToPdfUserSpace
import com.moonforce.ohmyainote.document.model.AiCardRecord
import com.moonforce.ohmyainote.document.model.NotebookKind
import com.moonforce.ohmyainote.document.model.NotebookManifest
import com.moonforce.ohmyainote.document.model.PageBackground
import com.moonforce.ohmyainote.document.model.PagePoint
import com.moonforce.ohmyainote.document.model.PageSnapshot
import com.moonforce.ohmyainote.document.model.PaperKind
import com.moonforce.ohmyainote.document.model.PdfRect
import com.moonforce.ohmyainote.document.model.StockBrush
import com.moonforce.ohmyainote.document.model.StrokeRecord
import com.moonforce.ohmyainote.document.model.TemplateSpec
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FlattenedPdfExporter {
    suspend fun export(
        manifest: NotebookManifest,
        notebookDir: Path,
        output: Path,
        pageProvider: suspend (String) -> PageSnapshot,
        options: ExportOptions = ExportOptions(),
        progress: ExportProgress = ExportProgress { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        val parent = output.toAbsolutePath().parent
        Files.createDirectories(parent)
        val temporary = parent.resolve(".${output.fileName}.${UUID.randomUUID()}.tmp")
        val document = if (manifest.kind == NotebookKind.PDF) {
            PDDocument.load(notebookDir.resolve("media/source.pdf").toFile())
        } else {
            PDDocument()
        }
        try {
            document.documentInformation.producer = options.producer
            manifest.pageOrder.forEachIndexed { index, pageId ->
                val snapshot = pageProvider(pageId)
                val pdfPage = when (manifest.kind) {
                    NotebookKind.PDF -> document.getPage(index)
                    NotebookKind.TEMPLATE, NotebookKind.IMAGE -> PDPage(
                        PDRectangle(snapshot.page.widthPt, snapshot.page.heightPt)
                    ).also(document::addPage)
                }
                val append = manifest.kind == NotebookKind.PDF
                PDPageContentStream(
                    document,
                    pdfPage,
                    if (append) PDPageContentStream.AppendMode.APPEND else PDPageContentStream.AppendMode.OVERWRITE,
                    true,
                    append,
                ).use { stream ->
                    if (!append) drawNewPageBackground(document, stream, manifest, snapshot, notebookDir)
                    drawStrokes(stream, snapshot, options.pressureVarying)
                    snapshot.page.cards.forEach { drawCard(document, stream, snapshot, notebookDir, it) }
                }
                progress.onPage(index + 1, manifest.pageCount)
            }
            document.save(temporary.toFile())
            Files.move(temporary, output, REPLACE_EXISTING, ATOMIC_MOVE)
        } finally {
            document.close()
            Files.deleteIfExists(temporary)
        }
    }

    private fun drawNewPageBackground(
        document: PDDocument,
        stream: PDPageContentStream,
        manifest: NotebookManifest,
        snapshot: PageSnapshot,
        notebookDir: Path,
    ) {
        when (val background = snapshot.page.background) {
            is PageBackground.Template -> drawTemplate(stream, requireNotNull(manifest.template))
            is PageBackground.Image -> {
                val path = notebookDir.resolve(background.sourcePath).normalize()
                require(path.startsWith(notebookDir.normalize()))
                val bitmap = BitmapFactory.decodeFile(path.toString()) ?: error("Unable to decode image page")
                try {
                    val image = LosslessFactory.createFromImage(document, bitmap)
                    stream.drawImage(image, 0f, 0f, snapshot.page.widthPt, snapshot.page.heightPt)
                } finally {
                    bitmap.recycle()
                }
            }
            is PageBackground.PdfPage -> error("PDF background must append to the source document")
        }
    }

    private fun drawTemplate(stream: PDPageContentStream, template: TemplateSpec) {
        val background = Color.parseColor(template.backgroundColor)
        stream.setNonStrokingColor(Color.red(background), Color.green(background), Color.blue(background))
        stream.addRect(0f, 0f, template.widthPt, template.heightPt)
        stream.fill()
        if (template.paper == PaperKind.BLANK) return
        val rule = Color.parseColor(template.ruleColor)
        stream.setStrokingColor(Color.red(rule), Color.green(rule), Color.blue(rule))
        stream.setLineWidth(0.5f)
        var offset = template.lineSpacingPt
        while (offset < template.heightPt) {
            stream.moveTo(0f, template.heightPt - offset)
            stream.lineTo(template.widthPt, template.heightPt - offset)
            offset += template.lineSpacingPt
        }
        if (template.paper == PaperKind.GRID) {
            offset = template.lineSpacingPt
            while (offset < template.widthPt) {
                stream.moveTo(offset, 0f)
                stream.lineTo(offset, template.heightPt)
                offset += template.lineSpacingPt
            }
        } else {
            stream.moveTo(template.marginLeftPt, 0f)
            stream.lineTo(template.marginLeftPt, template.heightPt)
        }
        stream.stroke()
    }

    private fun drawStrokes(stream: PDPageContentStream, snapshot: PageSnapshot, pressureVarying: Boolean) {
        val transform: (PagePoint) -> PagePoint = when (val background = snapshot.page.background) {
            is PageBackground.PdfPage -> { point -> pageToPdfUserSpace(point, background.cropBox, background.rotate) }
            else -> { point -> pageToPdfUserSpace(point, PdfRect(0f, 0f, snapshot.page.widthPt, snapshot.page.heightPt), 0) }
        }
        snapshot.strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            val color = Color.parseColor(stroke.color)
            val alpha = if (stroke.stockBrush == StockBrush.HIGHLIGHTER) 0.35f else Color.alpha(color) / 255f
            stream.saveGraphicsState()
            stream.setGraphicsStateParameters(PDExtendedGraphicsState().apply { strokingAlphaConstant = alpha })
            stream.setStrokingColor(Color.red(color), Color.green(color), Color.blue(color))
            stream.setLineCapStyle(1)
            stream.setLineJoinStyle(1)
            if (stroke.points.size == 1) {
                val point = transform(PagePoint(stroke.points[0].x, stroke.points[0].y))
                stream.setLineWidth(stroke.sizePt)
                stream.moveTo(point.x, point.y)
                stream.lineTo(point.x + 0.01f, point.y + 0.01f)
                stream.stroke()
            } else if (pressureVarying && stroke.stockBrush == StockBrush.PRESSURE_PEN) {
                stroke.points.zipWithNext().forEach { (from, to) ->
                    val a = transform(PagePoint(from.x, from.y))
                    val b = transform(PagePoint(to.x, to.y))
                    val pressure = listOfNotNull(from.pressure, to.pressure).averageOrNull() ?: 1f
                    stream.setLineWidth(max(0.1f, stroke.sizePt * pressure))
                    stream.moveTo(a.x, a.y)
                    stream.lineTo(b.x, b.y)
                    stream.stroke()
                }
            } else {
                stream.setLineWidth(stroke.sizePt)
                val first = transform(PagePoint(stroke.points.first().x, stroke.points.first().y))
                stream.moveTo(first.x, first.y)
                stroke.points.drop(1).forEach { point ->
                    val mapped = transform(PagePoint(point.x, point.y))
                    stream.lineTo(mapped.x, mapped.y)
                }
                stream.stroke()
            }
            stream.restoreGraphicsState()
        }
    }

    private fun drawCard(
        document: PDDocument,
        stream: PDPageContentStream,
        snapshot: PageSnapshot,
        notebookDir: Path,
        card: AiCardRecord,
    ) {
        val scale = 2f
        val bitmap = Bitmap.createBitmap(
            max(1, (card.anchor.w * scale).toInt()),
            max(1, (card.anchor.h * scale).toInt()),
            Bitmap.Config.ARGB_8888,
        )
        try {
            paintCard(Canvas(bitmap), bitmap.width, bitmap.height, notebookDir, card)
            val image = LosslessFactory.createFromImage(document, bitmap)
            val transform: (PagePoint) -> PagePoint = when (val background = snapshot.page.background) {
                is PageBackground.PdfPage -> { point -> pageToPdfUserSpace(point, background.cropBox, background.rotate) }
                else -> { point -> pageToPdfUserSpace(point, PdfRect(0f, 0f, snapshot.page.widthPt, snapshot.page.heightPt), 0) }
            }
            val left = card.anchor.x
            val top = card.anchor.y
            val right = left + card.anchor.w
            val bottom = top + card.anchor.h
            val bottomLeft = transform(PagePoint(left, bottom))
            val bottomRight = transform(PagePoint(right, bottom))
            val topLeft = transform(PagePoint(left, top))
            val matrix = Matrix(
                bottomRight.x - bottomLeft.x,
                bottomRight.y - bottomLeft.y,
                topLeft.x - bottomLeft.x,
                topLeft.y - bottomLeft.y,
                bottomLeft.x,
                bottomLeft.y,
            )
            stream.drawImage(image, matrix)
        } finally {
            bitmap.recycle()
        }
    }

    private fun paintCard(canvas: Canvas, width: Int, height: Int, notebookDir: Path, card: AiCardRecord) {
        val density = width / card.anchor.w
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.TRANSPARENT)
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 10f * density, 10f * density, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = Color.rgb(210, 214, 220)
        canvas.drawRoundRect(RectF(0.5f, 0.5f, width - 0.5f, height - 0.5f), 10f * density, 10f * density, paint)
        paint.style = Paint.Style.FILL

        val thumbnailSize = 48f * density
        val thumbnailPath = notebookDir.resolve(card.thumbPath).normalize()
        if (thumbnailPath.startsWith(notebookDir.normalize())) {
            BitmapFactory.decodeFile(thumbnailPath.toString())?.let { thumbnail ->
                try {
                    canvas.drawBitmap(thumbnail, null, RectF(8f * density, 8f * density, 8f * density + thumbnailSize, 8f * density + thumbnailSize), paint)
                } finally {
                    thumbnail.recycle()
                }
            }
        }
        val textX = 64f * density
        val textWidth = max(1, (width - textX - 8f * density).toInt())
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 35, 42)
            textSize = 9f * density
        }
        canvas.save()
        canvas.translate(textX, 8f * density)
        StaticLayout.Builder.obtain("${card.question}\n${card.answer}", 0, card.question.length + card.answer.length + 1, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(6)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
            .draw(canvas)
        canvas.restore()
    }

    private fun List<Float>.averageOrNull(): Float? = if (isEmpty()) null else sum() / size
}
