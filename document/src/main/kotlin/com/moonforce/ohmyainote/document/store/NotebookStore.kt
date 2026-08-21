package com.moonforce.ohmyainote.document.store

import com.moonforce.ohmyainote.document.model.AiCardRecord
import com.moonforce.ohmyainote.document.model.Folder
import com.moonforce.ohmyainote.document.model.FolderId
import com.moonforce.ohmyainote.document.model.NotebookId
import com.moonforce.ohmyainote.document.model.NotebookManifest
import com.moonforce.ohmyainote.document.model.NotebookSummary
import com.moonforce.ohmyainote.document.model.PageId
import com.moonforce.ohmyainote.document.model.PageSnapshot
import com.moonforce.ohmyainote.document.model.PageSpec
import com.moonforce.ohmyainote.document.model.PaperKind
import com.moonforce.ohmyainote.document.model.Sink
import com.moonforce.ohmyainote.document.model.SourceFile
import com.moonforce.ohmyainote.document.model.StrokeRecord
import com.moonforce.ohmyainote.document.model.TextRecord
import kotlinx.coroutines.flow.StateFlow

interface NotebookStore {
    suspend fun list(): List<NotebookSummary>
    suspend fun createTemplate(title: String, paper: PaperKind, pageSpec: PageSpec = PageSpec()): NotebookId
    suspend fun importPdf(title: String, pdf: SourceFile): NotebookId
    suspend fun importImages(title: String, images: List<SourceFile>): NotebookId
    suspend fun open(id: NotebookId): NotebookSession
    suspend fun delete(id: NotebookId)
    suspend fun packageToAinote(id: NotebookId, dest: Sink)
    suspend fun importAinote(src: SourceFile): NotebookId
    suspend fun listFolders(): List<Folder>
    suspend fun createFolder(name: String): FolderId
    suspend fun renameFolder(id: FolderId, name: String)
    suspend fun deleteFolder(id: FolderId)
    suspend fun moveNotebook(id: NotebookId, folderId: FolderId?)
}

interface NotebookSession {
    val manifest: StateFlow<NotebookManifest>
    suspend fun page(id: PageId): PageSnapshot
    suspend fun appendStrokes(pageId: PageId, strokes: List<StrokeRecord>)
    suspend fun removeStrokes(pageId: PageId, ids: Set<String>)
    suspend fun insertCard(pageId: PageId, card: AiCardRecord)
    suspend fun deleteCard(pageId: PageId, cardId: String)
    suspend fun replaceStrokesWithText(pageId: PageId, strokeIds: Set<String>, text: TextRecord)
    suspend fun restoreStrokesRemovingText(pageId: PageId, strokes: List<StrokeRecord>, textId: String)
    suspend fun removeStrokesAndTexts(pageId: PageId, strokeIds: Set<String>, textIds: Set<String>)
    suspend fun insertStrokesAndTexts(pageId: PageId, strokes: List<StrokeRecord>, texts: List<TextRecord>)
    suspend fun addTemplatePage()
    suspend fun deleteTemplatePage(pageId: PageId)
    suspend fun addImagePage(image: SourceFile)
    suspend fun rename(title: String)
}
