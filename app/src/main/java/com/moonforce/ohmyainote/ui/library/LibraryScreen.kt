package com.moonforce.ohmyainote.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moonforce.ohmyainote.document.model.NotebookSummary
import com.moonforce.ohmyainote.document.model.PaperKind
import com.moonforce.ohmyainote.document.model.Folder
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenNotebook: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    var createNotebook by remember { mutableStateOf(false) }
    var createFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<NotebookSummary?>(null) }
    var renameFolderTarget by remember { mutableStateOf<Folder?>(null) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::importPdf)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importImages(uris)
    }
    val packagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importAinote)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("oh-my-ainote") },
                actions = {
                    TextButton(onClick = { createNotebook = true }) { Text("新建") }
                    TextButton(onClick = { pdfPicker.launch("application/pdf") }) { Text("导入 PDF") }
                    TextButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) { Text("导入图片") }
                    TextButton(onClick = { packagePicker.launch(arrayOf("*/*")) }) { Text("导入 .ainote") }
                    TextButton(onClick = onOpenSettings) { Text("设置") }
                },
            )
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.width(190.dp).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FolderButton("全部", state.selectedFolderId == LibraryUiState.ALL) { viewModel.selectFolder(LibraryUiState.ALL) }
                FolderButton("未归档", state.selectedFolderId == LibraryUiState.UNFILED) { viewModel.selectFolder(LibraryUiState.UNFILED) }
                state.folders.forEach { folder ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            FolderButton(folder.name, state.selectedFolderId == folder.id) { viewModel.selectFolder(folder.id) }
                        }
                        TextButton(onClick = { viewModel.deleteFolder(folder.id) }) { Text("×") }
                        TextButton(onClick = { renameFolderTarget = folder }) { Text("改") }
                    }
                }
                OutlinedButton(onClick = { createFolder = true }, modifier = Modifier.fillMaxWidth()) { Text("新建文件夹") }
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                if (state.visibleNotebooks.isEmpty() && !state.loading) {
                    Text("还没有笔记本", modifier = Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleLarge)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(220.dp),
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.visibleNotebooks, key = { it.id.value }) { notebook ->
                            NotebookCard(
                                notebook = notebook,
                                coverPath = viewModel.coverPath(notebook.id.value),
                                folders = state.folders.map { it.id to it.name },
                                onOpen = { onOpenNotebook(notebook.id.value) },
                                onMove = { viewModel.moveNotebook(notebook.id.value, it) },
                                onRename = { renameTarget = notebook },
                                onDelete = { viewModel.deleteNotebook(notebook.id.value) },
                            )
                        }
                    }
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    if (createNotebook) CreateNotebookDialog(
        onDismiss = { createNotebook = false },
        onCreate = { title, paper ->
            createNotebook = false
            viewModel.createTemplate(title, paper) { onOpenNotebook(it.value) }
        },
    )
    if (createFolder) NameDialog("新建文件夹", "名称", onDismiss = { createFolder = false }) { name ->
        createFolder = false
        viewModel.createFolder(name)
    }
    renameTarget?.let { notebook ->
        NameDialog("重命名笔记本", notebook.title, onDismiss = { renameTarget = null }) { name ->
            renameTarget = null
            viewModel.renameNotebook(notebook.id.value, name)
        }
    }
    renameFolderTarget?.let { folder ->
        NameDialog("重命名文件夹", folder.name, onDismiss = { renameFolderTarget = null }) { name ->
            renameFolderTarget = null
            viewModel.renameFolder(folder.id, name)
        }
    }
    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } },
            title = { Text("操作失败") },
            text = { Text(message) },
        )
    }
}

@Composable
private fun FolderButton(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun NotebookCard(
    notebook: NotebookSummary,
    coverPath: String,
    folders: List<Pair<String, String>>,
    onOpen: () -> Unit,
    onMove: (String?) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val cover by produceState<Bitmap?>(null, coverPath, notebook.updatedAt) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(coverPath) }
    }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cover?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "${notebook.title} 封面",
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Text(notebook.title, style = MaterialTheme.typography.titleMedium)
            Text("${notebook.kind.name.lowercase()} · ${notebook.pageCount} 页", style = MaterialTheme.typography.bodySmall)
            Row {
                Button(onClick = onOpen) { Text("打开") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onRename) { Text("改名") }
                Box {
                    TextButton(onClick = { menu = true }) { Text("归档") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("未归档") }, onClick = { menu = false; onMove(null) })
                        folders.forEach { (id, name) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { menu = false; onMove(id) })
                        }
                    }
                }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

@Composable
private fun CreateNotebookDialog(onDismiss: () -> Unit, onCreate: (String, PaperKind) -> Unit) {
    var title by remember { mutableStateOf("") }
    var paper by remember { mutableStateOf(PaperKind.GRID) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建模板笔记本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaperKind.entries.forEach { kind ->
                        FilterChip(selected = paper == kind, onClick = { paper = kind }, label = { Text(kind.name.lowercase()) })
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onCreate(title, paper) }) { Text("创建") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }) },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
