package com.moonforce.ohmyainote.document.store

import com.moonforce.ohmyainote.document.model.NotebookId
import com.moonforce.ohmyainote.document.model.PageId
import com.moonforce.ohmyainote.document.model.PageBackground
import com.moonforce.ohmyainote.document.model.PageModel
import com.moonforce.ohmyainote.document.model.PageSnapshot
import com.moonforce.ohmyainote.document.model.PaperKind
import com.moonforce.ohmyainote.document.model.Sink
import com.moonforce.ohmyainote.document.model.SourceFile
import com.moonforce.ohmyainote.document.model.StockBrush
import com.moonforce.ohmyainote.document.model.StrokePoint
import com.moonforce.ohmyainote.document.model.StrokeRecord
import com.moonforce.ohmyainote.document.model.StrokeTool
import com.moonforce.ohmyainote.document.model.TEXT_MIN_READER_VERSION
import com.moonforce.ohmyainote.document.model.TextRecord
import com.moonforce.ohmyainote.document.format.PageCodec
import com.moonforce.ohmyainote.document.model.READER_CAPABILITY
import com.moonforce.ohmyainote.document.model.NotebookManifest
import com.moonforce.ohmyainote.document.model.NotebookKind
import com.moonforce.ohmyainote.document.model.PageSpec
import com.moonforce.ohmyainote.document.model.validate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class LocalNotebookStoreTest {
    @Test
    fun recoversOldPageWhenCrashHappensAfterBackup() = withTempDirectory { root ->
        val notebook = Files.createDirectories(root.resolve("notebook"))
        val original = PageSnapshot(
            PageModel("0001", 0, 100f, 200f, PageBackground.Template(PaperKind.BLANK)),
            emptyList(),
        )
        PageDirectoryIo(notebook).commit(original)
        val failingIo = PageDirectoryIo(notebook, CrashInjector { point ->
            if (point == CommitPoint.AFTER_BACKUP) error("simulated process death")
        })

        runCatching { failingIo.commit(original.copy(strokes = listOf(sampleStroke()))) }

        val recovered = PageDirectoryIo(notebook).read("0001")
        assertEquals(0, recovered.strokes.size)
    }

    @Test
    fun keepsNewPageWhenCrashHappensAfterPromote() = withTempDirectory { root ->
        val notebook = Files.createDirectories(root.resolve("notebook"))
        val original = PageSnapshot(
            PageModel("0001", 0, 100f, 200f, PageBackground.Template(PaperKind.BLANK)),
            emptyList(),
        )
        PageDirectoryIo(notebook).commit(original)
        val failingIo = PageDirectoryIo(notebook, CrashInjector { point ->
            if (point == CommitPoint.AFTER_PROMOTE) error("simulated process death")
        })

        runCatching { failingIo.commit(original.copy(strokes = listOf(sampleStroke()))) }

        val recovered = PageDirectoryIo(notebook).read("0001")
        assertEquals(listOf("stroke-1"), recovered.strokes.map { it.id })
    }

    @Test
    fun deletingMiddleBlankPageReindexesRemainingPages() = withTempDirectory { root ->
        runBlocking {
            val store = LocalNotebookStore(root)
            val notebook = store.createTemplate("Pad", PaperKind.GRID)
            val session = store.open(notebook)
            session.addTemplatePage()
            session.addTemplatePage()
            session.addTemplatePage()

            session.deleteTemplatePage(PageId("0002"))

            val manifest = session.manifest.value
            assertEquals(listOf("0001", "0003", "0004"), manifest.pageOrder)
            assertEquals(3, manifest.pageCount)
            assertEquals(0, session.page(PageId("0001")).page.index)
            assertEquals(1, session.page(PageId("0003")).page.index)
            assertEquals(2, session.page(PageId("0004")).page.index)
        }
    }

    @Test
    fun deletingFirstBlankTemplatePageSucceeds() = withTempDirectory { root ->
        runBlocking {
            val store = LocalNotebookStore(root)
            val notebook = store.createTemplate("Pad", PaperKind.GRID)
            val session = store.open(notebook)
            session.addTemplatePage()

            session.deleteTemplatePage(PageId("0001"))

            assertEquals(listOf("0002"), session.manifest.value.pageOrder)
            assertEquals(0, session.page(PageId("0002")).page.index)
        }
    }

    @Test
    fun deleteTemplatePageRejectsLastPage() = withTempDirectory { root ->
        runBlocking {
            val store = LocalNotebookStore(root)
            val notebook = store.createTemplate("Pad", PaperKind.GRID)
            val session = store.open(notebook)

            val error = runCatching { session.deleteTemplatePage(PageId("0001")) }.exceptionOrNull()
            assertNotNull(error)
        }
    }

    @Test
    fun deleteTemplatePageRejectsPageWithInk() = withTempDirectory { root ->
        runBlocking {
            val store = LocalNotebookStore(root)
            val notebook = store.createTemplate("Pad", PaperKind.GRID)
            val session = store.open(notebook)
            session.addTemplatePage()
            session.appendStrokes(PageId("0002"), listOf(sampleStroke()))

            val error = runCatching { session.deleteTemplatePage(PageId("0002")) }.exceptionOrNull()
            assertNotNull(error)
            assertEquals(2, session.manifest.value.pageCount)
        }
    }

    @Test
    fun openRepairsStaleIndexAndRemovesOrphanedPageFromCrashedDelete() = withTempDirectory { root ->
        runBlocking {
            val store = LocalNotebookStore(root)
            val notebook = store.createTemplate("Pad", PaperKind.GRID)
            store.open(notebook).apply { addTemplatePage(); addTemplatePage() }
            val dir = root.resolve("notebooks").resolve(notebook.value)
            val io = PageDirectoryIo(dir)
            // Simulate a crash after reindexing 0003 but before the manifest commit.
            val page3 = io.read("0003")
            io.commit(page3.copy(page = page3.page.copy(index = 1)))
            // Simulate the orphaned victim directory of a crashed delete.
            io.commit(
                PageSnapshot(
                    PageModel("0099", 0, 100f, 200f, PageBackground.Template(PaperKind.GRID)),
                    emptyList(),
                ),
            )

            val reopened = store.open(notebook)
            assertEquals(listOf("0001", "0002", "0003"), reopened.manifest.value.pageOrder)
            assertEquals(2, reopened.page(PageId("0003")).page.index)
            assertFalse("0099" in io.listPageIds())
        }
    }

    @Test
    fun replaceStrokesWithTextBumpsMinReaderVersionAndRoundTrips() = withTempDirectory { root ->
        runBlocking {
            val store = LocalNotebookStore(root)
            val notebook = store.createTemplate("Pad", PaperKind.BLANK)
            val session = store.open(notebook)
            val stroke = sampleStroke()
            session.appendStrokes(PageId("0001"), listOf(stroke))
            val text = TextRecord(
                id = "text-1",
                text = "你好",
                x = 1f,
                y = 2f,
                fontSizePt = 12f,
                color = "#FF000000",
                aabb = com.moonforce.ohmyainote.document.model.PageRect(1f, 2f, 25f, 14f),
                createdAt = "2026-01-01T00:00:00Z",
            )

            session.replaceStrokesWithText(PageId("0001"), setOf(stroke.id), text)

            val snapshot = session.page(PageId("0001"))
            assertEquals(emptyList<String>(), snapshot.strokes.map { it.id })
            assertEquals(listOf("你好"), snapshot.page.texts.map { it.text })
            assertEquals(TEXT_MIN_READER_VERSION, session.manifest.value.minReaderVersion)
            val decoded = PageCodec.decode(PageCodec.encode(snapshot.page))
            assertEquals("你好", decoded.texts.single().text)
        }
    }

    @Test
    fun restoreStrokesRemovingTextPutsInkBack() = withTempDirectory { root ->
        runBlocking {
            val store = LocalNotebookStore(root)
            val notebook = store.createTemplate("Pad", PaperKind.BLANK)
            val session = store.open(notebook)
            val stroke = sampleStroke()
            session.appendStrokes(PageId("0001"), listOf(stroke))
            val text = TextRecord(
                id = "text-1",
                text = "你好",
                x = 1f,
                y = 2f,
                fontSizePt = 12f,
                color = "#FF000000",
                aabb = com.moonforce.ohmyainote.document.model.PageRect(1f, 2f, 25f, 14f),
                createdAt = "2026-01-01T00:00:00Z",
            )
            session.replaceStrokesWithText(PageId("0001"), setOf(stroke.id), text)
            session.restoreStrokesRemovingText(PageId("0001"), listOf(stroke), text.id)

            val snapshot = session.page(PageId("0001"))
            assertEquals(listOf("stroke-1"), snapshot.strokes.map { it.id })
            assertEquals(0, snapshot.page.texts.size)
        }
    }

    @Test
    fun readerAcceptsMinReaderVersionTwo() {
        val manifest = NotebookManifest(
            id = "11111111-1111-1111-1111-111111111111",
            title = "Pad",
            kind = NotebookKind.TEMPLATE,
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
            pageCount = 1,
            pageOrder = listOf("0001"),
            defaultPage = PageSpec(),
            template = com.moonforce.ohmyainote.document.model.TemplateSpec(PaperKind.BLANK),
            minReaderVersion = TEXT_MIN_READER_VERSION,
        )
        manifest.validate()
        assertTrue(TEXT_MIN_READER_VERSION <= READER_CAPABILITY)
    }

    @Test
    fun importingSameArchiveAlwaysAllocatesNewNotebookId() = withTempDirectory { root ->
        runBlocking {
            val store = LocalNotebookStore(root)
            val original = store.createTemplate("Class", PaperKind.GRID)
            val archive = root.resolve("class.ainote")
            store.packageToAinote(original, Sink(archive))

            val first = store.importAinote(SourceFile(archive))
            val second = store.importAinote(SourceFile(archive))

            assertNotEquals(original, first)
            assertNotEquals(first, second)
            assertEquals(3, store.list().size)
            assertEquals("Class", store.open(NotebookId(first.value)).manifest.value.title)
        }
    }

    private fun sampleStroke() = StrokeRecord(
        id = "stroke-1",
        tool = StrokeTool.PEN,
        stockBrush = StockBrush.PRESSURE_PEN,
        color = "#FF000000",
        sizePt = 2.5f,
        t0 = "2026-08-18T00:00:00Z",
        points = listOf(StrokePoint(1f, 2f, 0), StrokePoint(3f, 4f, 12, pressure = 0.8f)),
    )

    private fun withTempDirectory(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("ainote-test-")
        try {
            block(root)
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
