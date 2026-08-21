package com.moonforce.ohmyainote.ui.editor

import android.content.Intent
import androidx.annotation.VisibleForTesting
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Choreographer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.ink.strokes.Stroke
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moonforce.ohmyainote.ai.OverlaySession
import com.moonforce.ohmyainote.di.AppContainer
import com.moonforce.ohmyainote.document.model.AiCardRecord
import com.moonforce.ohmyainote.document.model.NotebookId
import com.moonforce.ohmyainote.document.model.NotebookManifest
import com.moonforce.ohmyainote.document.model.PageId
import com.moonforce.ohmyainote.document.model.PageRect
import com.moonforce.ohmyainote.document.model.PageSnapshot
import com.moonforce.ohmyainote.document.model.Sink
import com.moonforce.ohmyainote.document.format.DigitalInkGeometry
import com.moonforce.ohmyainote.document.model.StrokeRecord
import com.moonforce.ohmyainote.document.model.TextRecord
import com.moonforce.ohmyainote.document.store.NotebookSession
import com.moonforce.ohmyainote.export.ExportOptions
import com.moonforce.ohmyainote.ink.BrushCatalog
import com.moonforce.ohmyainote.ink.FinishedStroke
import com.moonforce.ohmyainote.ink.StrokeBridge
import com.moonforce.ohmyainote.ink.Tool
import com.moonforce.ohmyainote.ink.eraseIntersectingStrokes
import com.moonforce.ohmyainote.ink.eraseIntersectingTexts
import java.time.Instant
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EditorUiState(
    val manifest: NotebookManifest? = null,
    val pageIndex: Int = 0,
    val snapshot: PageSnapshot? = null,
    val tool: Tool = Tool.PEN,
    val colorArgb: Int = BrushCatalog.defaultColor(Tool.PEN),
    val brushSizePt: Float = BrushCatalog.defaultSize(Tool.PEN),
    val overlay: OverlaySession? = null,
    val aiLoading: Boolean = false,
    val confirmationUrl: String? = null,
    val settingsRequired: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val busyMessage: String? = null,
    val error: String? = null,
    val lastDryHandoffMs: Double? = null,
    val hwrEnabled: Boolean = false,
    val hwrDownloading: Boolean = false,
)

class EditorViewModel(
    private val container: AppContainer,
    private val notebookId: NotebookId,
) : ViewModel() {
    private lateinit var session: NotebookSession
    private val mutableState = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = mutableState.asStateFlow()

    /**
     * Dry ink presented by [com.moonforce.ohmyainote.ink.FinishedStrokesView].
     * The editor must [android.view.View.invalidate] that view in the same UI run loop as
     * [onStrokesFinished]; Compose `StateFlow` collection is one Looper message too late.
     */
    var finishedStrokes by mutableStateOf(emptyList<FinishedStroke>(), referentialEqualityPolicy())
        private set
    private val undo = ArrayDeque<EditorAction>()
    private val redo = ArrayDeque<EditorAction>()
    private val persistenceJobs = mutableSetOf<Job>()
    private val persistenceMutex = Mutex()
    private var coverRefreshJob: Job? = null
    private var pendingQuestion: String? = null
    private var loadGeneration = 0
    private var penColorArgb = BrushCatalog.defaultColor(Tool.PEN)
    private var highlighterColorArgb = BrushCatalog.defaultColor(Tool.HIGHLIGHTER)
    private var penSizePt = BrushCatalog.defaultSize(Tool.PEN)
    private var highlighterSizePt = BrushCatalog.defaultSize(Tool.HIGHLIGHTER)
    private val pendingConvertIds = linkedSetOf<String>()
    private var convertJob: Job? = null
    private var hwrDownloadGeneration = 0

    val notebookDirectory get() = container.context.filesDir.toPath().resolve("notebooks/${notebookId.value}")
    private val notebookDir get() = notebookDirectory

    init {
        viewModelScope.launch {
            runCatching {
                session = container.notebookStore.open(notebookId)
                mutableState.update { it.copy(manifest = session.manifest.value) }
                loadPage(0)
                restoreHwrToggle()
            }.onFailure(::report)
        }
    }

    fun loadPage(index: Int) {
        val manifest = mutableState.value.manifest ?: return
        if (index !in manifest.pageOrder.indices) return
        cancelPendingConvert()
        val generation = ++loadGeneration
        viewModelScope.launch {
            runCatching {
                val snapshot = session.page(PageId(manifest.pageOrder[index]))
                val finished = StrokeBridge.load(snapshot.strokes)
                if (generation == loadGeneration) {
                    undo.clear()
                    redo.clear()
                    finishedStrokes = finished
                    mutableState.update {
                        it.copy(
                            pageIndex = index,
                            snapshot = snapshot,
                            overlay = null,
                            canUndo = false,
                            canRedo = false,
                        )
                    }
                }
            }.onFailure(::report)
        }
    }

    fun setTool(tool: Tool) {
        mutableState.update {
            it.copy(
                tool = tool,
                colorArgb = when (tool) {
                    Tool.PEN -> penColorArgb
                    Tool.HIGHLIGHTER -> highlighterColorArgb
                    else -> BrushCatalog.defaultColor(tool)
                },
                brushSizePt = when (tool) {
                    Tool.PEN -> penSizePt
                    Tool.HIGHLIGHTER -> highlighterSizePt
                    else -> BrushCatalog.defaultSize(tool)
                },
                overlay = if (tool != Tool.BOX_ASK) null else it.overlay,
            )
        }
    }

    fun setBrushColor(colorArgb: Int) {
        when (mutableState.value.tool) {
            Tool.PEN -> penColorArgb = colorArgb
            Tool.HIGHLIGHTER -> highlighterColorArgb = colorArgb
            else -> return
        }
        mutableState.update { it.copy(colorArgb = colorArgb) }
    }

    fun setBrushSize(sizePt: Float) {
        when (mutableState.value.tool) {
            Tool.PEN -> penSizePt = sizePt
            Tool.HIGHLIGHTER -> highlighterSizePt = sizePt
            else -> return
        }
        mutableState.update { it.copy(brushSizePt = sizePt) }
    }

    /** Called on the UI run loop; dry list is updated so the editor can invalidate the dry View. */
    fun onStrokesFinished(strokes: List<Stroke>) {
        val started = System.nanoTime()
        val current = mutableState.value
        val snapshot = current.snapshot ?: return
        if (!current.tool.isWritingTool()) return
        val records = strokes.map { StrokeBridge.fromInk(it, current.tool, current.colorArgb) }
        val finished = records.zip(strokes).map { (record, ink) -> FinishedStroke(record, ink) }
        val nextRecords = snapshot.strokes + records
        finishedStrokes = finishedStrokes + finished
        undo.addLast(EditorAction.Add(snapshot.page.id, records))
        while (undo.size > 80) undo.removeFirst()
        redo.clear()
        persist { session.appendStrokes(PageId(snapshot.page.id), records) }
        maybeScheduleCover(snapshot.page.id)
        if (current.hwrEnabled && current.tool == Tool.PEN) {
            scheduleConvert(records.map { it.id })
        }
        // Keep pager / toolbar StateFlow off this HWUI frame so dry View.invalidate() wins the handoff.
        Choreographer.getInstance().postFrameCallback {
            applySnapshotStrokes(nextRecords)
            mutableState.update {
                it.copy(
                    canUndo = undo.isNotEmpty(),
                    canRedo = redo.isNotEmpty(),
                    lastDryHandoffMs = (System.nanoTime() - started) / 1_000_000.0,
                )
            }
        }
    }

    fun eraseSegment(previousX: Float, previousY: Float, currentX: Float, currentY: Float) {
        val current = mutableState.value
        val snapshot = current.snapshot ?: return
        val strokeIds = eraseIntersectingStrokes(previousX, previousY, currentX, currentY, finishedStrokes)
        val textIds = eraseIntersectingTexts(previousX, previousY, currentX, currentY, snapshot.page.texts)
        if (strokeIds.isEmpty() && textIds.isEmpty()) return
        pendingConvertIds.removeAll(strokeIds)
        val removedStrokes = snapshot.strokes.filter { it.id in strokeIds }
        val removedTexts = snapshot.page.texts.filter { it.id in textIds }
        val records = snapshot.strokes.filterNot { it.id in strokeIds }
        val finished = finishedStrokes.filterNot { it.record.id in strokeIds }
        applyLocalStrokes(records, finished)
        mutableState.update { state ->
            val page = state.snapshot?.page ?: return@update state
            state.copy(snapshot = state.snapshot.copy(page = page.copy(texts = page.texts.filterNot { it.id in textIds })))
        }
        push(EditorAction.Erase(snapshot.page.id, removedStrokes, removedTexts))
        persist { session.removeStrokesAndTexts(PageId(snapshot.page.id), strokeIds, textIds) }
        maybeScheduleCover(snapshot.page.id)
    }

    fun toggleHandwritingRecognition() {
        val current = mutableState.value
        if (current.hwrDownloading) return
        if (current.hwrEnabled) {
            cancelPendingConvert()
            mutableState.update { it.copy(hwrEnabled = false) }
            viewModelScope.launch { container.hwrSettingsStore.setEnabled(false) }
            return
        }
        val generation = ++hwrDownloadGeneration
        viewModelScope.launch {
            mutableState.update { it.copy(hwrDownloading = true, error = null) }
            runCatching {
                if (!container.digitalInkModelStore.isDownloaded()) {
                    container.digitalInkModelStore.download()
                }
                container.hwrSettingsStore.setEnabled(true)
            }.onSuccess {
                if (generation != hwrDownloadGeneration) return@onSuccess
                mutableState.update { it.copy(hwrEnabled = true, hwrDownloading = false) }
            }.onFailure { error ->
                if (generation != hwrDownloadGeneration) return@onFailure
                mutableState.update { it.copy(hwrEnabled = false, hwrDownloading = false) }
                report(error)
            }
        }
    }

    fun selectForAsk(selection: PageRect) {
        val current = mutableState.value
        val manifest = current.manifest ?: return
        val snapshot = current.snapshot ?: return
        viewModelScope.launch {
            val settings = container.aiSettingsStore.resolvedOrNull()
            if (settings == null) {
                mutableState.update { it.copy(settingsRequired = true) }
                return@launch
            }
            mutableState.update { it.copy(busyMessage = "正在生成选区图片…", error = null) }
            runCatching {
                awaitPersistence()
                container.pageRasterComposer.compose(manifest, snapshot, notebookDir, selection)
            }.onSuccess { region ->
                mutableState.update {
                    it.copy(overlay = OverlaySession(snapshot.page.id, selection, region), busyMessage = null, tool = Tool.BOX_ASK)
                }
            }.onFailure(::report)
        }
    }

    fun ask(question: String) {
        if (question.isBlank() || mutableState.value.overlay == null) return
        viewModelScope.launch {
            val settings = container.aiSettingsStore.resolvedOrNull()
            if (settings == null) {
                mutableState.update { it.copy(settingsRequired = true) }
                return@launch
            }
            if (!container.aiSettingsStore.isConfirmedFor(settings.baseUrl)) {
                pendingQuestion = question.trim()
                mutableState.update { it.copy(confirmationUrl = settings.baseUrl) }
                return@launch
            }
            performAsk(question.trim())
        }
    }

    fun confirmAndAsk() {
        val url = mutableState.value.confirmationUrl ?: return
        val question = pendingQuestion ?: return
        viewModelScope.launch {
            container.aiSettingsStore.confirm(url)
            mutableState.update { it.copy(confirmationUrl = null) }
            pendingQuestion = null
            performAsk(question)
        }
    }

    fun cancelConfirmation() {
        pendingQuestion = null
        mutableState.update { it.copy(confirmationUrl = null) }
    }

    private suspend fun performAsk(question: String) {
        val overlay = mutableState.value.overlay ?: return
        val settings = container.aiSettingsStore.resolvedOrNull() ?: return
        mutableState.update { it.copy(aiLoading = true, error = null) }
        runCatching {
            container.aiClient.askAboutImage(overlay.region.jpeg, question, settings)
        }.onSuccess { answer ->
            overlay.add(question, answer)
            mutableState.update { it.copy(overlay = overlay, aiLoading = false) }
        }.onFailure(::report)
    }

    fun insertCurrentCard() {
        val current = mutableState.value
        val overlay = current.overlay ?: return
        val snapshot = current.snapshot ?: return
        if (overlay.turns.isEmpty()) return
        val cardId = UUID.randomUUID().toString()
        val relative = "media/cards/$cardId.jpg"
        val card = overlay.currentCard(snapshot.page.widthPt, snapshot.page.heightPt, relative, cardId)
        val target = notebookDir.resolve(relative)
        runCatching {
            Files.createDirectories(target.parent)
            Files.write(target, overlay.region.jpeg)
        }.onFailure { report(it); return }
        val nextPage = snapshot.page.copy(cards = snapshot.page.cards + card)
        mutableState.update { it.copy(snapshot = snapshot.copy(page = nextPage), overlay = null) }
        push(EditorAction.InsertCard(snapshot.page.id, card))
        persist { session.insertCard(PageId(snapshot.page.id), card) }
        maybeScheduleCover(snapshot.page.id)
    }

    fun closeOverlay() = mutableState.update { it.copy(overlay = null, tool = Tool.PEN) }

    fun undo() {
        val action = undo.removeLastOrNull() ?: return
        redo.addLast(action)
        applyAction(action, reverse = true)
        updateHistoryFlags()
    }

    fun redo() {
        val action = redo.removeLastOrNull() ?: return
        undo.addLast(action)
        applyAction(action, reverse = false)
        updateHistoryFlags()
    }

    private fun applyAction(action: EditorAction, reverse: Boolean) {
        val current = mutableState.value
        val snapshot = current.snapshot ?: return
        if (snapshot.page.id != action.pageId) return
        when (action) {
            is EditorAction.Add -> if (reverse) removeRecords(snapshot, action.records) else addRecords(snapshot, action.records)
            is EditorAction.Remove -> if (reverse) addRecords(snapshot, action.records) else removeRecords(snapshot, action.records)
            is EditorAction.InsertCard -> {
                val cards = if (reverse) snapshot.page.cards.filterNot { it.id == action.card.id } else snapshot.page.cards + action.card
                mutableState.update { it.copy(snapshot = snapshot.copy(page = snapshot.page.copy(cards = cards))) }
                persist {
                    if (reverse) session.deleteCard(PageId(action.pageId), action.card.id)
                    else session.insertCard(PageId(action.pageId), action.card)
                }
                maybeScheduleCover(action.pageId)
            }
            is EditorAction.Convert -> if (reverse) revertConvert(snapshot, action) else applyConvert(snapshot, action)
            is EditorAction.Erase -> if (reverse) restoreErased(snapshot, action) else applyErased(snapshot, action)
        }
    }

    private fun addRecords(snapshot: PageSnapshot, records: List<StrokeRecord>) {
        val next = snapshot.strokes + records
        applyLocalStrokes(next, StrokeBridge.load(next))
        persist { session.appendStrokes(PageId(snapshot.page.id), records) }
    }

    private fun removeRecords(snapshot: PageSnapshot, records: List<StrokeRecord>) {
        val ids = records.map { it.id }.toSet()
        pendingConvertIds.removeAll(ids)
        val next = snapshot.strokes.filterNot { it.id in ids }
        applyLocalStrokes(next, finishedStrokes.filterNot { it.record.id in ids })
        persist { session.removeStrokes(PageId(snapshot.page.id), ids) }
    }

    private fun applyConvert(snapshot: PageSnapshot, action: EditorAction.Convert) {
        val ids = action.strokes.map { it.id }.toSet()
        pendingConvertIds.removeAll(ids)
        val next = snapshot.strokes.filterNot { it.id in ids }
        applyLocalStrokes(next, finishedStrokes.filterNot { it.record.id in ids })
        insertTexts(listOf(action.text))
        persist { session.replaceStrokesWithText(PageId(action.pageId), ids, action.text) }
        maybeScheduleCover(action.pageId)
    }

    private fun revertConvert(snapshot: PageSnapshot, action: EditorAction.Convert) {
        pendingConvertIds.removeAll(action.strokes.map { it.id })
        val next = snapshot.strokes + action.strokes
        applyLocalStrokes(next, StrokeBridge.load(next))
        removeTexts(setOf(action.text.id))
        persist { session.restoreStrokesRemovingText(PageId(action.pageId), action.strokes, action.text.id) }
        maybeScheduleCover(action.pageId)
    }

    private fun applyErased(snapshot: PageSnapshot, action: EditorAction.Erase) {
        val strokeIds = action.strokes.map { it.id }.toSet()
        val textIds = action.texts.map { it.id }.toSet()
        pendingConvertIds.removeAll(strokeIds)
        val next = snapshot.strokes.filterNot { it.id in strokeIds }
        applyLocalStrokes(next, finishedStrokes.filterNot { it.record.id in strokeIds })
        removeTexts(textIds)
        persist { session.removeStrokesAndTexts(PageId(action.pageId), strokeIds, textIds) }
        maybeScheduleCover(action.pageId)
    }

    private fun restoreErased(snapshot: PageSnapshot, action: EditorAction.Erase) {
        val next = snapshot.strokes + action.strokes
        applyLocalStrokes(next, StrokeBridge.load(next))
        insertTexts(action.texts)
        persist { session.insertStrokesAndTexts(PageId(action.pageId), action.strokes, action.texts) }
        maybeScheduleCover(action.pageId)
    }

    private fun insertTexts(texts: List<TextRecord>) {
        if (texts.isEmpty()) return
        mutableState.update { current ->
            val snapshot = current.snapshot ?: return@update current
            current.copy(snapshot = snapshot.copy(page = snapshot.page.copy(texts = snapshot.page.texts + texts)))
        }
    }

    private fun removeTexts(ids: Set<String>) {
        if (ids.isEmpty()) return
        mutableState.update { current ->
            val snapshot = current.snapshot ?: return@update current
            current.copy(snapshot = snapshot.copy(page = snapshot.page.copy(texts = snapshot.page.texts.filterNot { it.id in ids })))
        }
    }

    private fun scheduleConvert(ids: List<String>) {
        if (ids.isEmpty()) return
        pendingConvertIds += ids
        convertJob?.cancel()
        convertJob = viewModelScope.launch {
            delay(convertDelayMs)
            commitPendingConvert()
        }
    }

    @VisibleForTesting
    internal var convertDelayMs: Long = CONVERT_DELAY_MS
        set(value) {
            field = value
        }

    @VisibleForTesting
    internal suspend fun commitPendingConvertForTest() = commitPendingConvert()

    private fun cancelPendingConvert() {
        convertJob?.cancel()
        convertJob = null
        pendingConvertIds.clear()
    }

    private suspend fun commitPendingConvert() {
        val current = mutableState.value
        val snapshot = current.snapshot ?: return
        if (!current.hwrEnabled) return
        val ids = pendingConvertIds.toList()
        pendingConvertIds.removeAll(ids)
        val pens = DigitalInkGeometry.penStrokes(snapshot.strokes.filter { it.id in ids })
        if (pens.isEmpty()) return
        awaitPersistence()
        val live = mutableState.value.snapshot ?: return
        val stillPresent = DigitalInkGeometry.penStrokes(live.strokes.filter { stroke -> pens.any { it.id == stroke.id } })
        if (stillPresent.isEmpty()) return
        val recognized = runCatching { container.handwritingRecognizer.recognize(stillPresent) }
            .onFailure(::report)
            .getOrNull()
        if (recognized.isNullOrBlank()) {
            if (recognized != null) {
                mutableState.update { it.copy(error = "无法识别这串笔迹") }
            }
            return
        }
        val latest = mutableState.value.snapshot ?: return
        if (stillPresent.any { stroke -> latest.strokes.none { it.id == stroke.id } }) return
        val text = DigitalInkGeometry.placement(
            stillPresent,
            recognized,
            UUID.randomUUID().toString(),
            Instant.now().toString(),
        )
        applyConvert(latest, EditorAction.Convert(latest.page.id, stillPresent, text))
        push(EditorAction.Convert(latest.page.id, stillPresent, text))
    }

    private suspend fun restoreHwrToggle() {
        if (!container.hwrSettingsStore.isEnabled()) return
        val generation = ++hwrDownloadGeneration
        mutableState.update { it.copy(hwrDownloading = true) }
        runCatching {
            if (!container.digitalInkModelStore.isDownloaded()) {
                container.digitalInkModelStore.download()
            }
        }.onSuccess {
            if (generation != hwrDownloadGeneration) return
            mutableState.update { it.copy(hwrEnabled = true, hwrDownloading = false) }
        }.onFailure { error ->
            if (generation != hwrDownloadGeneration) return
            mutableState.update { it.copy(hwrEnabled = false, hwrDownloading = false) }
            report(error)
        }
    }

    fun addTemplatePage() {
        viewModelScope.launch {
            runCatching {
                session.addTemplatePage()
                mutableState.update { it.copy(manifest = session.manifest.value) }
                loadPage(session.manifest.value.pageCount - 1)
            }.onFailure(::report)
        }
    }

    fun appendImagePage(uri: Uri) {
        viewModelScope.launch {
            var source: com.moonforce.ohmyainote.document.model.SourceFile? = null
            runCatching {
                source = container.importProcessors.bakeImage(uri)
                session.addImagePage(requireNotNull(source))
                mutableState.update { it.copy(manifest = session.manifest.value) }
                loadPage(session.manifest.value.pageCount - 1)
            }.onFailure(::report)
            source?.path?.let { Files.deleteIfExists(it) }
        }
    }

    fun close(onClosed: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                convertJob?.cancel()
                awaitPersistence()
                coverRefreshJob?.cancel()
                updateCover()
            }
            onClosed()
        }
    }

    fun deleteTemplatePage() {
        val current = mutableState.value
        val manifest = current.manifest ?: return
        val pageIndex = current.pageIndex
        viewModelScope.launch {
            runCatching {
                awaitPersistence()
                val pageId = PageId(manifest.pageOrder[pageIndex])
                session.deleteTemplatePage(pageId)
                val next = session.manifest.value
                mutableState.update { it.copy(manifest = next) }
                maybeScheduleCover(next.pageOrder.first())
                loadPage(pageIndex.coerceIn(0, next.pageCount - 1))
            }.onFailure(::report)
        }
    }

    fun sharePdf() = viewModelScope.launch {
        val manifest = mutableState.value.manifest ?: return@launch
        mutableState.update { it.copy(busyMessage = "正在准备分享 PDF…", error = null) }
        val target = shareStagingDir().resolve("share-${UUID.randomUUID()}.pdf")
        runCatching {
            awaitPersistence()
            val pressure = container.aiSettingsStore.exportPressureVarying.first()
            container.exporter.export(
                manifest,
                notebookDir,
                target,
                pageProvider = { session.page(PageId(it)) },
                options = ExportOptions(pressureVarying = pressure, producer = "oh-my-ainote ${com.moonforce.ohmyainote.BuildConfig.VERSION_NAME}"),
            )
            sendShareIntent(target, "application/pdf")
        }.onFailure(::report)
        mutableState.update { it.copy(busyMessage = null) }
    }

    fun sharePackage() = viewModelScope.launch {
        mutableState.update { it.copy(busyMessage = "正在准备分享 .ainote…", error = null) }
        val target = shareStagingDir().resolve("share-${UUID.randomUUID()}.ainote")
        runCatching {
            awaitPersistence()
            container.notebookStore.packageToAinote(notebookId, Sink(target))
            sendShareIntent(target, "application/zip")
        }.onFailure(::report)
        mutableState.update { it.copy(busyMessage = null) }
    }

    private fun shareStagingDir(): Path {
        val dir = container.context.cacheDir.toPath().resolve("exports")
        Files.createDirectories(dir)
        return dir
    }

    private fun sendShareIntent(file: Path, mimeType: String) {
        val context = container.context
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file.toFile())
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "分享")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun exportPdf(uri: Uri) = viewModelScope.launch {
        val manifest = mutableState.value.manifest ?: return@launch
        mutableState.update { it.copy(busyMessage = "正在导出 PDF…", error = null) }
        val temporary = container.context.cacheDir.toPath().resolve("export-${UUID.randomUUID()}.pdf")
        runCatching {
            awaitPersistence()
            val pressure = container.aiSettingsStore.exportPressureVarying.first()
            container.exporter.export(
                manifest,
                notebookDir,
                temporary,
                pageProvider = { session.page(PageId(it)) },
                options = ExportOptions(pressureVarying = pressure, producer = "oh-my-ainote ${com.moonforce.ohmyainote.BuildConfig.VERSION_NAME}"),
            )
            container.context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output)
                Files.copy(temporary, output)
            }
        }.onFailure(::report)
        Files.deleteIfExists(temporary)
        mutableState.update { it.copy(busyMessage = null) }
    }

    fun exportPackage(uri: Uri) = viewModelScope.launch {
        mutableState.update { it.copy(busyMessage = "正在打包 .ainote…", error = null) }
        val temporary = container.context.cacheDir.toPath().resolve("export-${UUID.randomUUID()}.ainote")
        runCatching {
            awaitPersistence()
            container.notebookStore.packageToAinote(notebookId, Sink(temporary))
            container.context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output)
                Files.copy(temporary, output)
            }
        }.onFailure(::report)
        Files.deleteIfExists(temporary)
        mutableState.update { it.copy(busyMessage = null) }
    }

    fun consumeSettingsRequest() = mutableState.update { it.copy(settingsRequired = false) }
    fun clearError() = mutableState.update { it.copy(error = null, aiLoading = false, busyMessage = null) }

    private fun applyLocalStrokes(records: List<StrokeRecord>, finished: List<FinishedStroke>) {
        finishedStrokes = finished
        applySnapshotStrokes(records)
    }

    private fun applySnapshotStrokes(records: List<StrokeRecord>) {
        mutableState.update { current ->
            val snapshot = current.snapshot ?: return@update current
            current.copy(
                snapshot = snapshot.copy(page = snapshot.page.copy(strokes = records), strokes = records),
            )
        }
    }

    private fun push(action: EditorAction) {
        undo.addLast(action)
        while (undo.size > 80) undo.removeFirst()
        redo.clear()
        updateHistoryFlags()
    }

    private fun updateHistoryFlags() = mutableState.update { it.copy(canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty()) }

    private fun persist(block: suspend () -> Unit) {
        val job = viewModelScope.launch {
            runCatching { persistenceMutex.withLock { block() } }.onFailure(::report)
        }
        persistenceJobs += job
        job.invokeOnCompletion { persistenceJobs -= job }
    }

    private suspend fun awaitPersistence() = persistenceJobs.toList().joinAll()

    /** Debounce a cover refresh for 5 s after the first page changes; close() flushes immediately. */
    private fun maybeScheduleCover(pageId: String) {
        val manifest = mutableState.value.manifest ?: return
        if (pageId != manifest.pageOrder.first()) return
        coverRefreshJob?.cancel()
        coverRefreshJob = viewModelScope.launch {
            delay(5_000)
            runCatching { updateCover() }
        }
    }

    private suspend fun updateCover() {
        val manifest = mutableState.value.manifest ?: return
        val first = session.page(PageId(manifest.pageOrder.first()))
        val region = container.pageRasterComposer.compose(
            manifest,
            first,
            notebookDir,
            PageRect(0f, 0f, first.page.widthPt, first.page.heightPt),
        )
        withContext(Dispatchers.IO) {
            val source = requireNotNull(BitmapFactory.decodeByteArray(region.jpeg, 0, region.jpeg.size))
            val scale = minOf(1f, 512f / maxOf(source.width, source.height))
            val width = maxOf(1, (source.width * scale).toInt())
            val height = maxOf(1, (source.height * scale).toInt())
            val cover = if (width == source.width && height == source.height) source
            else Bitmap.createScaledBitmap(source, width, height, true)
            val target = notebookDir.resolve("media/cover.jpg")
            val temporary = notebookDir.resolve("tmp/cover.jpg.tmp")
            try {
                Files.newOutputStream(temporary).use { output -> check(cover.compress(Bitmap.CompressFormat.JPEG, 85, output)) }
                Files.move(temporary, target, REPLACE_EXISTING, ATOMIC_MOVE)
            } finally {
                if (cover !== source) cover.recycle()
                source.recycle()
                Files.deleteIfExists(temporary)
            }
        }
    }

    private fun report(failure: Throwable) {
        mutableState.update {
            it.copy(error = failure.message ?: failure.javaClass.simpleName, aiLoading = false, busyMessage = null)
        }
    }

    private fun Tool.isWritingTool() = this == Tool.PEN || this == Tool.HIGHLIGHTER

    override fun onCleared() {
        convertJob?.cancel()
        super.onCleared()
    }

    private sealed interface EditorAction {
        val pageId: String
        data class Add(override val pageId: String, val records: List<StrokeRecord>) : EditorAction
        data class Remove(override val pageId: String, val records: List<StrokeRecord>) : EditorAction
        data class InsertCard(override val pageId: String, val card: AiCardRecord) : EditorAction
        data class Convert(override val pageId: String, val strokes: List<StrokeRecord>, val text: TextRecord) : EditorAction
        data class Erase(override val pageId: String, val strokes: List<StrokeRecord>, val texts: List<TextRecord>) : EditorAction
    }

    private companion object {
        const val CONVERT_DELAY_MS = 2_000L
    }
}
