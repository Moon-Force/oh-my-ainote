package com.moonforce.ohmyainote.document.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.nio.file.Path

const val FORMAT_VERSION = 1
const val MIN_READER_VERSION = 1
/** This app can open notebooks whose [NotebookManifest.minReaderVersion] is at most this. */
const val READER_CAPABILITY = 2
/** Notebooks that persist typeset [TextRecord]s require a reader that understands them. */
const val TEXT_MIN_READER_VERSION = 2
const val A4_WIDTH_PT = 595.27563f
const val A4_HEIGHT_PT = 841.88976f

@JvmInline
@Serializable
value class NotebookId(val value: String)

@JvmInline
@Serializable
value class FolderId(val value: String)

@JvmInline
@Serializable
value class PageId(val value: String)

@Serializable
enum class NotebookKind {
    @SerialName("template") TEMPLATE,
    @SerialName("pdf") PDF,
    @SerialName("image") IMAGE,
}

@Serializable
enum class PaperKind {
    @SerialName("blank") BLANK,
    @SerialName("lined") LINED,
    @SerialName("grid") GRID,
}

@Serializable
enum class StrokeTool {
    @SerialName("pen") PEN,
    @SerialName("highlighter") HIGHLIGHTER,
}

@Serializable
enum class StockBrush { PRESSURE_PEN, OMA_PRESSURE_INK_V1, HIGHLIGHTER }

/** Persisted pressure/opacity curve for [StockBrush.OMA_PRESSURE_INK_V1]. */
object OmaPressureInkV1 {
    const val LOW_PRESSURE_END = 0.8f
    const val MIN_WIDTH_MULTIPLIER = 0.55f
    const val MAX_WIDTH_MULTIPLIER = 1.5f
    const val MIN_OPACITY_MULTIPLIER = 0.35f
    const val DAMPING_GAP_SECONDS = 0.03f

    fun widthMultiplier(pressure: Float): Float {
        val normalized = pressure.coerceIn(0f, 1f)
        return if (normalized <= LOW_PRESSURE_END) {
            MIN_WIDTH_MULTIPLIER +
                (1f - MIN_WIDTH_MULTIPLIER) * normalized / LOW_PRESSURE_END
        } else {
            1f +
                (MAX_WIDTH_MULTIPLIER - 1f) *
                (normalized - LOW_PRESSURE_END) / (1f - LOW_PRESSURE_END)
        }
    }

    fun opacityMultiplier(pressure: Float): Float =
        MIN_OPACITY_MULTIPLIER +
            (1f - MIN_OPACITY_MULTIPLIER) * pressure.coerceIn(0f, 1f)
}

@Serializable
data class PageSpec(
    val widthPt: Float = A4_WIDTH_PT,
    val heightPt: Float = A4_HEIGHT_PT,
)

@Serializable
data class TemplateSpec(
    val paper: PaperKind,
    val widthPt: Float = A4_WIDTH_PT,
    val heightPt: Float = A4_HEIGHT_PT,
    val lineSpacingPt: Float = if (paper == PaperKind.GRID) 20f else 28f,
    val marginLeftPt: Float = 56f,
    val backgroundColor: String = "#FFF7F4EC",
    val ruleColor: String = "#FFD0D5DD",
)

@Serializable
sealed interface NotebookSource {
    @Serializable
    @SerialName("pdf")
    data class Pdf(
        val relativePath: String,
        val sha256: String,
    ) : NotebookSource

    @Serializable
    @SerialName("image")
    data class Images(val relativePaths: List<String>) : NotebookSource
}

@Serializable
data class NotebookManifest(
    val formatVersion: Int = FORMAT_VERSION,
    val minReaderVersion: Int = MIN_READER_VERSION,
    val id: String,
    val title: String,
    val kind: NotebookKind,
    val createdAt: String,
    val updatedAt: String,
    val pageCount: Int,
    val pageOrder: List<String>,
    val defaultPage: PageSpec,
    val template: TemplateSpec? = null,
    val source: NotebookSource? = null,
    val importedFromId: String? = null,
)

@Serializable
data class PdfRect(
    val l: Float,
    val b: Float,
    val r: Float,
    val t: Float,
) {
    val width: Float get() = r - l
    val height: Float get() = t - b
}

@Serializable
data class PageRect(
    val l: Float,
    val t: Float,
    val r: Float,
    val b: Float,
) {
    val width: Float get() = r - l
    val height: Float get() = b - t

    fun overlaps(other: PageRect): Boolean =
        l <= other.r && r >= other.l && t <= other.b && b >= other.t

    companion object {
        fun union(rects: Iterable<PageRect>): PageRect {
            val list = rects.toList()
            require(list.isNotEmpty()) { "Cannot union empty rects" }
            return PageRect(
                l = list.minOf { it.l },
                t = list.minOf { it.t },
                r = list.maxOf { it.r },
                b = list.maxOf { it.b },
            )
        }
    }
}

@Serializable
data class CardAnchor(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

@Serializable
data class PagePoint(val x: Float, val y: Float)

@Serializable
sealed interface PageBackground {
    @Serializable
    @SerialName("template")
    data class Template(val paper: PaperKind) : PageBackground

    @Serializable
    @SerialName("pdfPage")
    data class PdfPage(
        val sourcePath: String,
        val pdfPageIndex: Int,
        val rotate: Int,
        val mediaBox: PdfRect,
        val cropBox: PdfRect,
    ) : PageBackground

    @Serializable
    @SerialName("image")
    data class Image(val sourcePath: String) : PageBackground
}

@Serializable
data class StrokePoint(
    val x: Float,
    val y: Float,
    val tMs: Long,
    val pressure: Float? = null,
    val tiltRadians: Float? = null,
    val orientationRadians: Float? = null,
)

@Serializable
data class StrokeRecord(
    val id: String,
    val tool: StrokeTool,
    val stockBrush: StockBrush,
    val color: String,
    val sizePt: Float,
    val epsilon: Float = 0.01f,
    val t0: String,
    val byteOffset: Long = 0,
    val byteLength: Int = 0,
    val pointCount: Int = 0,
    val aabb: PageRect = PageRect(0f, 0f, 0f, 0f),
    @Transient val points: List<StrokePoint> = emptyList(),
)

@Serializable
data class TextRecord(
    val id: String,
    val text: String,
    val x: Float,
    val y: Float,
    val fontSizePt: Float,
    val color: String,
    val aabb: PageRect,
    val createdAt: String,
)

@Serializable
data class AiCardRecord(
    val id: String,
    val pageId: String,
    val selection: PageRect,
    val anchor: CardAnchor,
    val question: String,
    val answer: String,
    val thumbPath: String,
    val model: String,
    val createdAt: String,
)

@Serializable
data class PageModel(
    val id: String,
    val index: Int,
    val widthPt: Float,
    val heightPt: Float,
    val background: PageBackground,
    val strokes: List<StrokeRecord> = emptyList(),
    val cards: List<AiCardRecord> = emptyList(),
    val texts: List<TextRecord> = emptyList(),
)

data class PageSnapshot(
    val page: PageModel,
    val strokes: List<StrokeRecord>,
)

data class NotebookSummary(
    val id: NotebookId,
    val title: String,
    val kind: NotebookKind,
    val pageCount: Int,
    val updatedAt: String,
    val folderId: String? = null,
)

@Serializable
data class Folder(
    val id: String,
    val name: String,
    val order: Int,
)

@Serializable
data class FolderMembership(
    val notebookId: String,
    val folderId: String,
)

@Serializable
data class LibraryIndex(
    val formatVersion: Int = FORMAT_VERSION,
    val folders: List<Folder> = emptyList(),
    val membership: List<FolderMembership> = emptyList(),
)

data class PdfPageDescriptor(
    val widthPt: Float,
    val heightPt: Float,
    val rotate: Int,
    val mediaBox: PdfRect,
    val cropBox: PdfRect,
)

/** Android import layers materialize metadata/EXIF before handing files to :document. */
data class SourceFile(
    val path: Path,
    val widthPt: Float? = null,
    val heightPt: Float? = null,
    val pdfPages: List<PdfPageDescriptor> = emptyList(),
)

data class Sink(val path: Path)

fun NotebookManifest.validate() {
    require(formatVersion == FORMAT_VERSION) { "Unsupported formatVersion=$formatVersion" }
    require(minReaderVersion <= READER_CAPABILITY) { "Reader upgrade required" }
    require(id.isNotBlank() && title.isNotBlank()) { "Notebook id and title are required" }
    require(pageCount > 0 && pageCount == pageOrder.size) { "pageCount/pageOrder mismatch" }
    require(pageOrder.distinct().size == pageOrder.size) { "Duplicate page id" }
    require(defaultPage.widthPt > 0f && defaultPage.heightPt > 0f)
    when (kind) {
        NotebookKind.TEMPLATE -> {
            require(template != null && source == null) { "Template notebook has invalid source" }
        }
        NotebookKind.PDF -> {
            require(template == null && source is NotebookSource.Pdf) { "PDF notebook has invalid source" }
        }
        NotebookKind.IMAGE -> {
            require(template == null && source is NotebookSource.Images) { "Image notebook has invalid source" }
        }
    }
}

fun PageModel.validateFor(manifest: NotebookManifest) {
    require(id in manifest.pageOrder) { "Page $id is absent from manifest" }
    require(index == manifest.pageOrder.indexOf(id)) { "Page index mismatch" }
    require(widthPt > 0f && heightPt > 0f)
    val validKind = when (manifest.kind) {
        NotebookKind.TEMPLATE -> background is PageBackground.Template
        NotebookKind.PDF -> background is PageBackground.PdfPage
        NotebookKind.IMAGE -> background is PageBackground.Image
    }
    require(validKind) { "Page background does not match notebook kind" }
    if (background is PageBackground.PdfPage) {
        require(background.rotate in setOf(0, 90, 180, 270)) { "Unsupported PDF rotation" }
        require(background.mediaBox.width > 0 && background.mediaBox.height > 0)
        require(background.cropBox.width > 0 && background.cropBox.height > 0)
    }
    require(strokes.map { it.id }.distinct().size == strokes.size) { "Duplicate stroke id" }
    require(cards.map { it.id }.distinct().size == cards.size) { "Duplicate card id" }
    require(texts.map { it.id }.distinct().size == texts.size) { "Duplicate text id" }
    require(texts.all { it.text.isNotBlank() && it.fontSizePt > 0f }) { "Invalid text object" }
}
