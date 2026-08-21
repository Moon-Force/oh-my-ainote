package com.moonforce.ohmyainote.export

import com.moonforce.ohmyainote.document.model.NotebookKind
import com.moonforce.ohmyainote.document.model.NotebookManifest
import com.moonforce.ohmyainote.document.model.PageBackground
import com.moonforce.ohmyainote.document.model.PageModel
import com.moonforce.ohmyainote.document.model.PageRect
import com.moonforce.ohmyainote.document.model.PageSnapshot
import com.moonforce.ohmyainote.document.model.PageSpec
import com.moonforce.ohmyainote.document.model.PaperKind
import com.moonforce.ohmyainote.document.model.StrokePoint
import com.moonforce.ohmyainote.document.model.StrokeRecord
import com.moonforce.ohmyainote.document.model.StrokeTool
import com.moonforce.ohmyainote.document.model.StockBrush
import com.moonforce.ohmyainote.document.model.TemplateSpec
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FlattenedPdfExporterTest {

    private lateinit var dir: Path

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("ainote-export-test")
    }

    @After
    fun tearDown() {
        Files.walk(dir).use { stream ->
            stream.sorted(java.util.Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun exportsTemplatePagesWithStrokesAndPressureVarying() {
        val manifest = NotebookManifest(
            id = "nb-x",
            title = "X",
            kind = NotebookKind.TEMPLATE,
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
            pageCount = 2,
            pageOrder = listOf("0001", "0002"),
            defaultPage = PageSpec(),
            template = TemplateSpec(PaperKind.GRID),
        )
        val stroke = StrokeRecord(
            id = "s1",
            tool = StrokeTool.PEN,
            stockBrush = StockBrush.OMA_PRESSURE_INK_V1,
            color = "#FF336699",
            sizePt = 3f,
            t0 = "2026-01-01T00:00:00Z",
            pointCount = 2,
            aabb = PageRect(10f, 10f, 50f, 50f),
            points = listOf(
                StrokePoint(10f, 10f, 0, pressure = 0.2f),
                StrokePoint(50f, 50f, 10, pressure = 0.9f),
            ),
        )
        val highlighter = StrokeRecord(
            id = "s2",
            tool = StrokeTool.HIGHLIGHTER,
            stockBrush = StockBrush.HIGHLIGHTER,
            color = "#FFFFEE00",
            sizePt = 8f,
            t0 = "2026-01-01T00:00:00Z",
            pointCount = 2,
            aabb = PageRect(20f, 20f, 60f, 30f),
            points = listOf(StrokePoint(20f, 20f, 0), StrokePoint(60f, 30f, 10)),
        )
        val page1 = PageSnapshot(
            PageModel("0001", 0, 100f, 200f, PageBackground.Template(PaperKind.GRID), strokes = listOf(stroke)),
            listOf(stroke),
        )
        val page2 = PageSnapshot(
            PageModel("0002", 1, 100f, 200f, PageBackground.Template(PaperKind.GRID), strokes = listOf(highlighter)),
            listOf(highlighter),
        )
        val snapshots = mapOf("0001" to page1, "0002" to page2)

        val progress = mutableListOf<Pair<Int, Int>>()
        val output = dir.resolve("out.pdf")
        runBlocking {
            FlattenedPdfExporter(PureJvmArgbColor).export(
                manifest = manifest,
                notebookDir = dir,
                output = output,
                pageProvider = { snapshots.getValue(it) },
                options = ExportOptions(pressureVarying = true, producer = "test-producer"),
                progress = ExportProgress { c, t -> progress += c to t },
            )
        }

        assertTrue(Files.exists(output))
        assertEquals(listOf(1 to 2, 2 to 2), progress)
        PDDocument.load(output.toFile()).use { doc ->
            assertEquals(2, doc.numberOfPages)
            assertEquals(100f, doc.getPage(0).mediaBox.width, 0.01f)
            assertEquals(200f, doc.getPage(0).mediaBox.height, 0.01f)
            assertEquals("test-producer", doc.documentInformation.producer)
        }
    }

    @Test
    fun exportWritesAtomicallyAndLeavesNoTempFiles() {
        val manifest = NotebookManifest(
            id = "nb-y",
            title = "Y",
            kind = NotebookKind.TEMPLATE,
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
            pageCount = 1,
            pageOrder = listOf("0001"),
            defaultPage = PageSpec(),
            template = TemplateSpec(PaperKind.BLANK),
        )
        val snapshot = PageSnapshot(
            PageModel("0001", 0, 100f, 200f, PageBackground.Template(PaperKind.BLANK)),
            emptyList(),
        )
        val output = dir.resolve("sub/out.pdf")
        runBlocking {
            FlattenedPdfExporter(PureJvmArgbColor).export(
                manifest = manifest,
                notebookDir = dir,
                output = output,
                pageProvider = { snapshot },
            )
        }
        assertTrue(Files.exists(output))
        var leftovers = 0L
        Files.list(dir).use { stream ->
            stream.forEach { if (it.fileName.toString().startsWith(".")) leftovers++ }
        }
        assertEquals(0L, leftovers)
    }
}
