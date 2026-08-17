package com.moonforce.ohmyainote.document.store

import com.moonforce.ohmyainote.document.model.NotebookId
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
