package com.moonforce.ohmyainote.document.store

import com.moonforce.ohmyainote.document.format.OmaInputsV1
import com.moonforce.ohmyainote.document.format.PageCodec
import com.moonforce.ohmyainote.document.model.PageSnapshot
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID

enum class CommitPoint { AFTER_TEMP_WRITE, AFTER_BACKUP, AFTER_PROMOTE }

fun interface CrashInjector {
    fun hit(point: CommitPoint)

    companion object {
        val NONE = CrashInjector { }
    }
}

internal class PageDirectoryIo(
    private val notebookDir: Path,
    private val crashInjector: CrashInjector = CrashInjector.NONE,
) {
    private val pagesDir = notebookDir.resolve("pages")
    private val tmpDir = notebookDir.resolve("tmp")

    init {
        Files.createDirectories(pagesDir)
        Files.createDirectories(tmpDir)
    }

    fun commit(snapshot: PageSnapshot): PageSnapshot {
        val encoded = OmaInputsV1.encode(snapshot.strokes)
        val persistedPage = snapshot.page.copy(strokes = encoded.records)
        val prepared = PageSnapshot(persistedPage, encoded.records)
        val temp = tmpDir.resolve("page-${snapshot.page.id}-${UUID.randomUUID()}")
        Files.createDirectory(temp)
        writeAndForce(temp.resolve("page.json"), PageCodec.encode(persistedPage).toByteArray(StandardCharsets.UTF_8))
        writeAndForce(temp.resolve("strokes.bin"), encoded.bytes)
        forceDirectory(temp)
        check(isValid(temp)) { "Prepared page directory is invalid" }
        crashInjector.hit(CommitPoint.AFTER_TEMP_WRITE)

        val live = pagesDir.resolve(snapshot.page.id)
        val backup = pagesDir.resolve("${snapshot.page.id}.bak")
        deleteTreeIfPresent(backup)
        if (Files.exists(live)) {
            Files.move(live, backup, ATOMIC_MOVE)
            forceDirectory(pagesDir)
            crashInjector.hit(CommitPoint.AFTER_BACKUP)
        }
        Files.move(temp, live, ATOMIC_MOVE)
        forceDirectory(pagesDir)
        crashInjector.hit(CommitPoint.AFTER_PROMOTE)
        deleteTreeIfPresent(backup)
        forceDirectory(pagesDir)
        return prepared
    }

    fun read(pageId: String): PageSnapshot {
        val live = recover(pageId)
        val page = PageCodec.decode(readUtf8(live.resolve("page.json")))
        val strokes = OmaInputsV1.decode(Files.readAllBytes(live.resolve("strokes.bin")), page.strokes)
        return PageSnapshot(page, strokes)
    }

    fun recoverAll() {
        if (!Files.exists(pagesDir)) return
        Files.list(pagesDir).use { paths ->
            paths.filter { Files.isDirectory(it) }
                .map { it.fileName.toString().removeSuffix(".bak") }
                .distinct()
                .forEach(::recover)
        }
        Files.list(tmpDir).use { paths -> paths.toList().forEach(::deleteTreeIfPresent) }
    }

    private fun recover(pageId: String): Path {
        val live = pagesDir.resolve(pageId)
        val backup = pagesDir.resolve("$pageId.bak")
        val liveValid = isValid(live)
        val backupValid = isValid(backup)
        when {
            liveValid -> deleteTreeIfPresent(backup)
            backupValid -> {
                deleteTreeIfPresent(live)
                Files.move(backup, live, ATOMIC_MOVE)
                forceDirectory(pagesDir)
            }
            Files.exists(live) || Files.exists(backup) -> throw IOException("Page $pageId is corrupt")
            else -> throw IOException("Page $pageId is missing")
        }
        return live
    }

    private fun isValid(directory: Path): Boolean = runCatching {
        if (!Files.isDirectory(directory)) return@runCatching false
        val page = PageCodec.decode(readUtf8(directory.resolve("page.json")))
        OmaInputsV1.decode(Files.readAllBytes(directory.resolve("strokes.bin")), page.strokes)
        true
    }.getOrDefault(false)

    private fun deleteTreeIfPresent(target: Path) {
        if (!Files.exists(target)) return
        val normalizedRoot = notebookDir.toAbsolutePath().normalize()
        val normalizedTarget = target.toAbsolutePath().normalize()
        require(normalizedTarget.startsWith(normalizedRoot) && normalizedTarget != normalizedRoot)
        Files.walk(normalizedTarget).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}

internal class AtomicTextFile(private val root: Path) {
    fun write(target: Path, text: String) {
        require(target.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize()))
        Files.createDirectories(target.parent)
        val temp = target.resolveSibling("${target.fileName}.tmp-${UUID.randomUUID()}")
        val backup = target.resolveSibling("${target.fileName}.bak")
        writeAndForce(temp, text.toByteArray(StandardCharsets.UTF_8))
        Files.deleteIfExists(backup)
        if (Files.exists(target)) Files.move(target, backup, ATOMIC_MOVE)
        Files.move(temp, target, ATOMIC_MOVE)
        forceDirectory(target.parent)
        Files.deleteIfExists(backup)
    }

    fun read(target: Path, validator: (String) -> Unit): String {
        val backup = target.resolveSibling("${target.fileName}.bak")
        fun valid(path: Path): String? = runCatching {
            if (!Files.isRegularFile(path)) return@runCatching null
            readUtf8(path).also(validator)
        }.getOrNull()

        val live = valid(target)
        if (live != null) {
            Files.deleteIfExists(backup)
            return live
        }
        val old = valid(backup) ?: throw IOException("${target.fileName} is missing or corrupt")
        Files.deleteIfExists(target)
        Files.move(backup, target, ATOMIC_MOVE)
        return old
    }
}

private fun readUtf8(path: Path): String = String(Files.readAllBytes(path), StandardCharsets.UTF_8)

private fun writeAndForce(path: Path, bytes: ByteArray) {
    FileChannel.open(path, CREATE, WRITE, TRUNCATE_EXISTING).use { channel ->
        var buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }
}

private fun forceDirectory(directory: Path) {
    try {
        FileChannel.open(directory, READ).use { it.force(true) }
    } catch (_: IOException) {
        // Windows cannot fsync directories; file channels above are still forced.
    } catch (_: UnsupportedOperationException) {
        // Some JVM file-system providers do not expose directory channels.
    }
}
