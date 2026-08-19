package com.moonforce.ohmyainote.document.store

import com.moonforce.ohmyainote.document.format.LibraryCodec
import com.moonforce.ohmyainote.document.format.ManifestCodec
import com.moonforce.ohmyainote.document.model.AiCardRecord
import com.moonforce.ohmyainote.document.model.Folder
import com.moonforce.ohmyainote.document.model.FolderId
import com.moonforce.ohmyainote.document.model.FolderMembership
import com.moonforce.ohmyainote.document.model.LibraryIndex
import com.moonforce.ohmyainote.document.model.NotebookId
import com.moonforce.ohmyainote.document.model.NotebookKind
import com.moonforce.ohmyainote.document.model.NotebookManifest
import com.moonforce.ohmyainote.document.model.NotebookSource
import com.moonforce.ohmyainote.document.model.NotebookSummary
import com.moonforce.ohmyainote.document.model.PageBackground
import com.moonforce.ohmyainote.document.model.PageId
import com.moonforce.ohmyainote.document.model.PageModel
import com.moonforce.ohmyainote.document.model.PageSnapshot
import com.moonforce.ohmyainote.document.model.PageSpec
import com.moonforce.ohmyainote.document.model.PaperKind
import com.moonforce.ohmyainote.document.model.Sink
import com.moonforce.ohmyainote.document.model.SourceFile
import com.moonforce.ohmyainote.document.model.StrokeRecord
import com.moonforce.ohmyainote.document.model.TemplateSpec
import com.moonforce.ohmyainote.document.model.validate
import com.moonforce.ohmyainote.document.model.validateFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class LocalNotebookStore(
    private val filesDir: Path,
    private val crashInjector: CrashInjector = CrashInjector.NONE,
) : NotebookStore {
    private val notebooksDir = filesDir.resolve("notebooks")
    private val libraryFile = filesDir.resolve("library.json")
    private val atomicFiles = AtomicTextFile(filesDir)
    private val libraryMutex = Mutex()

    init {
        Files.createDirectories(notebooksDir)
        if (!Files.exists(libraryFile)) atomicFiles.write(libraryFile, LibraryCodec.encode(LibraryIndex()))
    }

    override suspend fun list(): List<NotebookSummary> = io {
        val membership = libraryMutex.withLock {
            readLibrary().membership.associate { it.notebookId to it.folderId }
        }
        Files.list(notebooksDir).use { paths ->
            paths.filter { Files.isDirectory(it) && !it.fileName.toString().startsWith(".import-") }
                .map { runCatching { readManifest(it) }.getOrNull() }
                .filter { it != null }
                .map { manifest ->
                    manifest!!
                    NotebookSummary(
                        id = NotebookId(manifest.id),
                        title = manifest.title,
                        kind = manifest.kind,
                        pageCount = manifest.pageCount,
                        updatedAt = manifest.updatedAt,
                        folderId = membership[manifest.id],
                    )
                }
                .sorted(compareByDescending<NotebookSummary> { it.updatedAt })
                .toList()
        }
    }

    override suspend fun createTemplate(title: String, paper: PaperKind, pageSpec: PageSpec): NotebookId = io {
        val cleanTitle = requireTitle(title)
        require(pageSpec.widthPt > 0f && pageSpec.heightPt > 0f)
        val id = NotebookId(UUID.randomUUID().toString())
        val dir = createNotebookDirectory(id)
        try {
            val now = Instant.now().toString()
            val template = TemplateSpec(paper, pageSpec.widthPt, pageSpec.heightPt)
            val manifest = NotebookManifest(
                id = id.value,
                title = cleanTitle,
                kind = NotebookKind.TEMPLATE,
                createdAt = now,
                updatedAt = now,
                pageCount = 1,
                pageOrder = listOf("0001"),
                defaultPage = pageSpec,
                template = template,
            )
            val page = PageModel(
                id = "0001",
                index = 0,
                widthPt = pageSpec.widthPt,
                heightPt = pageSpec.heightPt,
                background = PageBackground.Template(paper),
            )
            PageDirectoryIo(dir, crashInjector).commit(PageSnapshot(page, emptyList()))
            writeManifest(dir, manifest)
            id
        } catch (failure: Throwable) {
            deleteTree(dir)
            throw failure
        }
    }

    override suspend fun importPdf(title: String, pdf: SourceFile): NotebookId = io {
        val cleanTitle = requireTitle(title)
        require(Files.isRegularFile(pdf.path)) { "PDF source is missing" }
        require(pdf.pdfPages.isNotEmpty()) { "PDF page descriptors are required" }
        val id = NotebookId(UUID.randomUUID().toString())
        val dir = createNotebookDirectory(id)
        try {
            val media = Files.createDirectories(dir.resolve("media"))
            val targetPdf = media.resolve("source.pdf")
            Files.copy(pdf.path, targetPdf, REPLACE_EXISTING)
            val order = pdf.pdfPages.indices.map { pageName(it) }
            val now = Instant.now().toString()
            val manifest = NotebookManifest(
                id = id.value,
                title = cleanTitle,
                kind = NotebookKind.PDF,
                createdAt = now,
                updatedAt = now,
                pageCount = order.size,
                pageOrder = order,
                defaultPage = PageSpec(pdf.pdfPages.first().widthPt, pdf.pdfPages.first().heightPt),
                source = NotebookSource.Pdf("media/source.pdf", sha256(targetPdf)),
            )
            val pageIo = PageDirectoryIo(dir, crashInjector)
            pdf.pdfPages.forEachIndexed { index, descriptor ->
                require(descriptor.rotate in setOf(0, 90, 180, 270))
                val page = PageModel(
                    id = order[index],
                    index = index,
                    widthPt = descriptor.widthPt,
                    heightPt = descriptor.heightPt,
                    background = PageBackground.PdfPage(
                        sourcePath = "media/source.pdf",
                        pdfPageIndex = index,
                        rotate = descriptor.rotate,
                        mediaBox = descriptor.mediaBox,
                        cropBox = descriptor.cropBox,
                    ),
                )
                pageIo.commit(PageSnapshot(page, emptyList()))
            }
            writeManifest(dir, manifest)
            id
        } catch (failure: Throwable) {
            deleteTree(dir)
            throw failure
        }
    }

    override suspend fun importImages(title: String, images: List<SourceFile>): NotebookId = io {
        val cleanTitle = requireTitle(title)
        require(images.isNotEmpty())
        val id = NotebookId(UUID.randomUUID().toString())
        val dir = createNotebookDirectory(id)
        try {
            val imageDir = Files.createDirectories(dir.resolve("media/images"))
            val pageIo = PageDirectoryIo(dir, crashInjector)
            val relativePaths = images.mapIndexed { index, image ->
                val width = requireNotNull(image.widthPt) { "Baked image width is required" }
                val height = requireNotNull(image.heightPt) { "Baked image height is required" }
                require(width > 0 && height > 0 && Files.isRegularFile(image.path))
                val pageId = pageName(index)
                val extension = supportedImageExtension(image.path)
                val relative = "media/images/$pageId.$extension"
                Files.copy(image.path, imageDir.resolve("$pageId.$extension"), REPLACE_EXISTING)
                val page = PageModel(
                    id = pageId,
                    index = index,
                    widthPt = width,
                    heightPt = height,
                    background = PageBackground.Image(relative),
                )
                pageIo.commit(PageSnapshot(page, emptyList()))
                relative
            }
            val first = images.first()
            val now = Instant.now().toString()
            val order = images.indices.map(::pageName)
            val manifest = NotebookManifest(
                id = id.value,
                title = cleanTitle,
                kind = NotebookKind.IMAGE,
                createdAt = now,
                updatedAt = now,
                pageCount = order.size,
                pageOrder = order,
                defaultPage = PageSpec(requireNotNull(first.widthPt), requireNotNull(first.heightPt)),
                source = NotebookSource.Images(relativePaths),
            )
            writeManifest(dir, manifest)
            id
        } catch (failure: Throwable) {
            deleteTree(dir)
            throw failure
        }
    }

    override suspend fun open(id: NotebookId): NotebookSession = io {
        val dir = notebookDirectory(id)
        val manifest = readManifest(dir)
        val pageIo = PageDirectoryIo(dir, crashInjector)
        pageIo.recoverAll()
        // A deleteTemplatePage crash after the manifest commit can leave the victim directory orphaned.
        val validIds = manifest.pageOrder.toSet()
        pageIo.listPageIds().filterNot { it in validIds }.forEach(pageIo::delete)
        // A deleteTemplatePage crash before the manifest commit can leave stale indexes; manifest order is authoritative.
        manifest.pageOrder.forEachIndexed { index, pageId ->
            var snapshot = pageIo.read(pageId)
            if (snapshot.page.index != index) {
                snapshot = pageIo.commit(snapshot.copy(page = snapshot.page.copy(index = index)))
            }
            snapshot.page.validateFor(manifest)
        }
        LocalNotebookSession(dir, manifest, crashInjector, ::writeManifest)
    }

    override suspend fun delete(id: NotebookId) = io<Unit> {
        val target = notebookDirectory(id)
        if (Files.exists(target)) deleteTree(target)
        libraryMutex.withLock {
            val index = readLibrary()
            writeLibrary(index.copy(membership = index.membership.filterNot { it.notebookId == id.value }))
        }
    }

    override suspend fun packageToAinote(id: NotebookId, dest: Sink) = io<Unit> {
        val source = notebookDirectory(id)
        readManifest(source)
        Files.createDirectories(dest.path.toAbsolutePath().parent)
        ZipOutputStream(BufferedOutputStream(Files.newOutputStream(dest.path))).use { zip ->
            Files.walk(source).use { paths ->
                paths.filter { Files.isRegularFile(it) }
                    .filter { path ->
                        val relative = source.relativize(path).toString().replace('\\', '/')
                        !(relative.startsWith("tmp/") || relative.endsWith(".bak") || relative.contains(".bak/"))
                    }
                    .forEach { path ->
                        val name = source.relativize(path).toString().replace('\\', '/')
                        zip.putNextEntry(ZipEntry(name))
                        Files.copy(path, zip)
                        zip.closeEntry()
                    }
            }
        }
    }

    override suspend fun importAinote(src: SourceFile): NotebookId = io {
        require(Files.isRegularFile(src.path))
        val importDir = notebooksDir.resolve(".import-${UUID.randomUUID()}")
        Files.createDirectory(importDir)
        try {
            unzipSafely(src.path, importDir)
            val original = readManifest(importDir)
            val pageIo = PageDirectoryIo(importDir)
            pageIo.recoverAll()
            original.pageOrder.forEach { pageId -> pageIo.read(pageId).page.validateFor(original) }
            val newId = NotebookId(UUID.randomUUID().toString())
            val rewritten = original.copy(
                id = newId.value,
                importedFromId = original.id,
                updatedAt = Instant.now().toString(),
            )
            writeManifest(importDir, rewritten)
            val destination = notebookDirectory(newId)
            Files.move(importDir, destination, ATOMIC_MOVE)
            newId
        } catch (failure: Throwable) {
            deleteTree(importDir)
            throw failure
        }
    }

    override suspend fun listFolders(): List<Folder> = io {
        libraryMutex.withLock { readLibrary().folders.sortedBy { it.order } }
    }

    override suspend fun createFolder(name: String): FolderId = io {
        libraryMutex.withLock {
            val cleanName = requireTitle(name)
            val index = readLibrary()
            val id = FolderId(UUID.randomUUID().toString())
            val folder = Folder(id.value, cleanName, (index.folders.maxOfOrNull { it.order } ?: -1) + 1)
            writeLibrary(index.copy(folders = index.folders + folder))
            id
        }
    }

    override suspend fun renameFolder(id: FolderId, name: String) = io<Unit> {
        libraryMutex.withLock {
            val cleanName = requireTitle(name)
            val index = readLibrary()
            require(index.folders.any { it.id == id.value }) { "Folder not found" }
            writeLibrary(index.copy(folders = index.folders.map { if (it.id == id.value) it.copy(name = cleanName) else it }))
        }
    }

    override suspend fun deleteFolder(id: FolderId) = io<Unit> {
        libraryMutex.withLock {
            val index = readLibrary()
            writeLibrary(
                index.copy(
                    folders = index.folders.filterNot { it.id == id.value },
                    membership = index.membership.filterNot { it.folderId == id.value },
                )
            )
        }
    }

    override suspend fun moveNotebook(id: NotebookId, folderId: FolderId?) = io<Unit> {
        readManifest(notebookDirectory(id))
        libraryMutex.withLock {
            val index = readLibrary()
            if (folderId != null) require(index.folders.any { it.id == folderId.value }) { "Folder not found" }
            val remaining = index.membership.filterNot { it.notebookId == id.value }
            val updated = if (folderId == null) remaining else remaining + FolderMembership(id.value, folderId.value)
            writeLibrary(index.copy(membership = updated))
        }
    }

    private fun createNotebookDirectory(id: NotebookId): Path {
        val dir = notebookDirectory(id)
        Files.createDirectory(dir)
        Files.createDirectories(dir.resolve("media"))
        Files.createDirectories(dir.resolve("pages"))
        Files.createDirectories(dir.resolve("tmp"))
        return dir
    }

    private fun notebookDirectory(id: NotebookId): Path {
        require(id.value.matches(Regex("[0-9a-fA-F-]{36}"))) { "Invalid notebook id" }
        return notebooksDir.resolve(id.value)
    }

    private fun readManifest(dir: Path): NotebookManifest {
        val path = dir.resolve("manifest.json")
        return ManifestCodec.decode(atomicFiles.read(path) { ManifestCodec.decode(it).validate() }).also { it.validate() }
    }

    private fun writeManifest(dir: Path, manifest: NotebookManifest) {
        manifest.validate()
        atomicFiles.write(dir.resolve("manifest.json"), ManifestCodec.encode(manifest))
    }

    private fun readLibrary(): LibraryIndex {
        val raw = atomicFiles.read(libraryFile) { require(LibraryCodec.decode(it).formatVersion == 1) }
        val index = LibraryCodec.decode(raw)
        val folderIds = index.folders.map { it.id }.toSet()
        val cleanedMembership = index.membership
            .filter { it.folderId in folderIds }
            .distinctBy { it.notebookId }
        return if (cleanedMembership == index.membership) index else index.copy(membership = cleanedMembership).also(::writeLibrary)
    }

    private fun writeLibrary(index: LibraryIndex) {
        require(index.formatVersion == 1)
        require(index.folders.all { it.name.isNotBlank() })
        require(index.folders.map { it.id }.distinct().size == index.folders.size)
        require(index.membership.map { it.notebookId }.distinct().size == index.membership.size)
        atomicFiles.write(libraryFile, LibraryCodec.encode(index))
    }

    private fun unzipSafely(zipPath: Path, destination: Path) {
        var count = 0
        var total = 0L
        ZipInputStream(BufferedInputStream(Files.newInputStream(zipPath))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                count += 1
                require(count <= 10_000) { "Archive has too many entries" }
                val target = destination.resolve(entry.name).normalize()
                require(target.startsWith(destination) && target != destination) { "Unsafe archive path" }
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    BufferedOutputStream(Files.newOutputStream(target)).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= 2L * 1024 * 1024 * 1024) { "Archive is too large" }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun deleteTree(target: Path) {
        if (!Files.exists(target)) return
        val root = notebooksDir.toAbsolutePath().normalize()
        val normalized = target.toAbsolutePath().normalize()
        require(normalized.startsWith(root) && normalized != root)
        Files.walk(normalized).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        private fun pageName(index: Int) = (index + 1).toString().padStart(4, '0')
        private fun requireTitle(value: String) = value.trim().also { require(it.isNotBlank()) { "Name cannot be blank" } }

        private fun supportedImageExtension(path: Path): String {
            val extension = path.fileName.toString().substringAfterLast('.', "").lowercase()
            require(extension in setOf("jpg", "jpeg", "png")) { "Only baked JPEG/PNG images are supported" }
            return if (extension == "jpeg") "jpg" else extension
        }

        private fun sha256(path: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

private class LocalNotebookSession(
    private val notebookDir: Path,
    initialManifest: NotebookManifest,
    crashInjector: CrashInjector,
    private val manifestWriter: (Path, NotebookManifest) -> Unit,
) : NotebookSession {
    private val mutex = Mutex()
    private val pageIo = PageDirectoryIo(notebookDir, crashInjector)
    private val mutableManifest = MutableStateFlow(initialManifest)
    override val manifest: StateFlow<NotebookManifest> = mutableManifest.asStateFlow()

    override suspend fun page(id: PageId): PageSnapshot = withContext(Dispatchers.IO) {
        pageIo.read(id.value).also { it.page.validateFor(mutableManifest.value) }
    }

    override suspend fun appendStrokes(pageId: PageId, strokes: List<StrokeRecord>) = mutatePage(pageId) { snapshot ->
        require(strokes.all { it.points.isNotEmpty() })
        val duplicate = (snapshot.strokes.map { it.id } + strokes.map { it.id }).groupingBy { it }.eachCount().any { it.value > 1 }
        require(!duplicate) { "Duplicate stroke id" }
        snapshot.copy(page = snapshot.page.copy(strokes = snapshot.page.strokes + strokes), strokes = snapshot.strokes + strokes)
    }

    override suspend fun removeStrokes(pageId: PageId, ids: Set<String>) = mutatePage(pageId) { snapshot ->
        val remaining = snapshot.strokes.filterNot { it.id in ids }
        snapshot.copy(page = snapshot.page.copy(strokes = remaining), strokes = remaining)
    }

    override suspend fun insertCard(pageId: PageId, card: AiCardRecord) = mutatePage(pageId) { snapshot ->
        require(card.pageId == pageId.value && snapshot.page.cards.none { it.id == card.id })
        snapshot.copy(page = snapshot.page.copy(cards = snapshot.page.cards + card))
    }

    override suspend fun deleteCard(pageId: PageId, cardId: String) = mutatePage(pageId) { snapshot ->
        snapshot.copy(page = snapshot.page.copy(cards = snapshot.page.cards.filterNot { it.id == cardId }))
    }

    override suspend fun addTemplatePage() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = mutableManifest.value
            require(current.kind == NotebookKind.TEMPLATE)
            val id = (current.pageCount + 1).toString().padStart(4, '0')
            val template = requireNotNull(current.template)
            val page = PageModel(
                id = id,
                index = current.pageCount,
                widthPt = template.widthPt,
                heightPt = template.heightPt,
                background = PageBackground.Template(template.paper),
            )
            pageIo.commit(PageSnapshot(page, emptyList()))
            updateManifest(current.copy(pageCount = current.pageCount + 1, pageOrder = current.pageOrder + id))
        }
    }

    override suspend fun deleteTemplatePage(pageId: PageId) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = mutableManifest.value
            require(current.kind == NotebookKind.TEMPLATE) { "Only template notebooks support page deletion" }
            require(current.pageCount > 1) { "Cannot delete the last page" }
            require(pageId.value in current.pageOrder) { "Page ${pageId.value} is absent" }
            val victim = pageIo.read(pageId.value)
            require(victim.strokes.isEmpty() && victim.page.cards.isEmpty()) { "Only blank pages can be deleted" }
            val nextOrder = current.pageOrder.filterNot { it == pageId.value }
            // Reindex first: a crash here leaves stale indexes under the old manifest, which open() repairs.
            nextOrder.forEachIndexed { newIndex, id ->
                val oldIndex = current.pageOrder.indexOf(id)
                if (newIndex != oldIndex) {
                    val snapshot = pageIo.read(id)
                    pageIo.commit(snapshot.copy(page = snapshot.page.copy(index = newIndex)))
                }
            }
            // Manifest commit is the atomic boundary: both sides are independently consistent.
            updateManifest(current.copy(pageCount = nextOrder.size, pageOrder = nextOrder))
            victim.page.cards.forEach { card -> Files.deleteIfExists(notebookDir.resolve(card.thumbPath)) }
            pageIo.delete(pageId.value)
        }
    }

    override suspend fun addImagePage(image: SourceFile) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = mutableManifest.value
            require(current.kind == NotebookKind.IMAGE)
            val width = requireNotNull(image.widthPt)
            val height = requireNotNull(image.heightPt)
            require(Files.isRegularFile(image.path) && width > 0 && height > 0)
            val id = (current.pageCount + 1).toString().padStart(4, '0')
            val extension = image.path.fileName.toString().substringAfterLast('.').lowercase().let { if (it == "jpeg") "jpg" else it }
            require(extension in setOf("jpg", "png"))
            val relative = "media/images/$id.$extension"
            Files.copy(image.path, notebookDir.resolve(relative), REPLACE_EXISTING)
            val page = PageModel(id, current.pageCount, width, height, PageBackground.Image(relative))
            pageIo.commit(PageSnapshot(page, emptyList()))
            val source = current.source as NotebookSource.Images
            updateManifest(
                current.copy(
                    pageCount = current.pageCount + 1,
                    pageOrder = current.pageOrder + id,
                    source = source.copy(relativePaths = source.relativePaths + relative),
                )
            )
        }
    }

    override suspend fun rename(title: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val clean = title.trim()
            require(clean.isNotBlank())
            updateManifest(mutableManifest.value.copy(title = clean))
        }
    }

    private suspend fun mutatePage(pageId: PageId, transform: (PageSnapshot) -> PageSnapshot) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(pageId.value in mutableManifest.value.pageOrder)
            val current = pageIo.read(pageId.value)
            val next = transform(current)
            next.page.validateFor(mutableManifest.value)
            pageIo.commit(next)
            updateManifest(mutableManifest.value)
        }
    }

    private fun updateManifest(value: NotebookManifest) {
        val updated = value.copy(updatedAt = Instant.now().toString())
        manifestWriter(notebookDir, updated)
        mutableManifest.value = updated
    }
}
