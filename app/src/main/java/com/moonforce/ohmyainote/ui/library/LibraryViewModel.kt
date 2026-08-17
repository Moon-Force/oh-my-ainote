package com.moonforce.ohmyainote.ui.library

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moonforce.ohmyainote.di.AppContainer
import com.moonforce.ohmyainote.document.model.Folder
import com.moonforce.ohmyainote.document.model.FolderId
import com.moonforce.ohmyainote.document.model.NotebookId
import com.moonforce.ohmyainote.document.model.NotebookSummary
import com.moonforce.ohmyainote.document.model.PaperKind
import com.moonforce.ohmyainote.document.model.SourceFile
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val notebooks: List<NotebookSummary> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val selectedFolderId: String? = ALL,
    val loading: Boolean = true,
    val error: String? = null,
) {
    val visibleNotebooks: List<NotebookSummary>
        get() = when (selectedFolderId) {
            ALL -> notebooks
            UNFILED -> notebooks.filter { it.folderId == null }
            else -> notebooks.filter { it.folderId == selectedFolderId }
        }

    companion object {
        const val ALL = "__all__"
        const val UNFILED = "__unfiled__"
    }
}

class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() = launchTask {
        val notebooks = container.notebookStore.list()
        val folders = container.notebookStore.listFolders()
        mutableState.update { it.copy(notebooks = notebooks, folders = folders) }
    }

    fun selectFolder(id: String?) {
        mutableState.update { it.copy(selectedFolderId = id) }
    }

    fun createTemplate(title: String, paper: PaperKind, onCreated: (NotebookId) -> Unit = {}) = launchTask {
        val id = container.notebookStore.createTemplate(title, paper)
        refreshInline()
        onCreated(id)
    }

    fun createFolder(name: String) = launchTask {
        container.notebookStore.createFolder(name)
        refreshInline()
    }

    fun renameFolder(id: String, name: String) = launchTask {
        container.notebookStore.renameFolder(FolderId(id), name)
        refreshInline()
    }

    fun deleteFolder(id: String) = launchTask {
        container.notebookStore.deleteFolder(FolderId(id))
        mutableState.update { current ->
            current.copy(selectedFolderId = if (current.selectedFolderId == id) LibraryUiState.ALL else current.selectedFolderId)
        }
        refreshInline()
    }

    fun moveNotebook(notebookId: String, folderId: String?) = launchTask {
        container.notebookStore.moveNotebook(NotebookId(notebookId), folderId?.let(::FolderId))
        refreshInline()
    }

    fun renameNotebook(notebookId: String, title: String) = launchTask {
        container.notebookStore.open(NotebookId(notebookId)).rename(title)
        refreshInline()
    }

    fun deleteNotebook(notebookId: String) = launchTask {
        container.notebookStore.delete(NotebookId(notebookId))
        refreshInline()
    }

    fun importPdf(uri: Uri) = launchTask {
        val temporary = container.importProcessors.copyUri(uri, ".pdf")
        try {
            val inspected = container.pdfImportInspector.inspect(temporary)
            container.notebookStore.importPdf(displayName(uri, "Imported PDF"), inspected)
        } finally {
            Files.deleteIfExists(temporary)
        }
        refreshInline()
    }

    fun importImages(uris: List<Uri>) = launchTask {
        require(uris.isNotEmpty())
        val sources = withContext(Dispatchers.IO) {
            uris.map { uri -> async { container.importProcessors.bakeImage(uri) } }.awaitAll()
        }
        try {
            container.notebookStore.importImages(displayName(uris.first(), "Images"), sources)
        } finally {
            sources.forEach { Files.deleteIfExists(it.path) }
        }
        refreshInline()
    }

    fun importAinote(uri: Uri) = launchTask {
        val temporary = container.importProcessors.copyUri(uri, ".ainote")
        try {
            container.notebookStore.importAinote(SourceFile(temporary))
        } finally {
            Files.deleteIfExists(temporary)
        }
        refreshInline()
    }

    fun clearError() = mutableState.update { it.copy(error = null) }

    fun coverPath(notebookId: String) =
        container.context.filesDir.resolve("notebooks/$notebookId/media/cover.jpg").absolutePath

    private fun launchTask(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            runCatching { block() }
                .onFailure { failure -> mutableState.update { it.copy(error = failure.message ?: "操作失败") } }
            mutableState.update { it.copy(loading = false) }
        }
    }

    private suspend fun refreshInline() {
        mutableState.update {
            it.copy(
                notebooks = container.notebookStore.list(),
                folders = container.notebookStore.listFolders(),
            )
        }
    }

    private fun displayName(uri: Uri, fallback: String): String {
        val name = container.context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: fallback
        return name.substringBeforeLast('.').ifBlank { fallback }
    }
}
