package com.moonforce.ohmyainote.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moonforce.ohmyainote.OhMyAinoteApp
import com.moonforce.ohmyainote.document.model.NotebookId
import com.moonforce.ohmyainote.ui.editor.EditorScreen
import com.moonforce.ohmyainote.ui.editor.EditorViewModel
import com.moonforce.ohmyainote.ui.library.LibraryScreen
import com.moonforce.ohmyainote.ui.library.LibraryViewModel
import com.moonforce.ohmyainote.ui.settings.SettingsScreen
import com.moonforce.ohmyainote.ui.settings.SettingsViewModel

private const val LIBRARY = "library"
private const val SETTINGS = "settings"
private const val EDITOR_PREFIX = "editor:"

@Composable
fun OhMyAinoteRoot() {
    val container = (LocalContext.current.applicationContext as OhMyAinoteApp).container
    var destination by rememberSaveable { mutableStateOf(LIBRARY) }
    var settingsReturn by rememberSaveable { mutableStateOf(LIBRARY) }

    fun openSettings() {
        settingsReturn = destination
        destination = SETTINGS
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when {
                destination == LIBRARY -> {
                    val library: LibraryViewModel = viewModel(factory = factory { LibraryViewModel(container) })
                    LibraryScreen(
                        viewModel = library,
                        onOpenNotebook = { destination = EDITOR_PREFIX + it },
                        onOpenSettings = ::openSettings,
                    )
                }
                destination == SETTINGS -> {
                    val settings: SettingsViewModel = viewModel(factory = factory { SettingsViewModel(container) })
                    SettingsScreen(settings) { destination = settingsReturn }
                }
                destination.startsWith(EDITOR_PREFIX) -> {
                    val notebookId = destination.removePrefix(EDITOR_PREFIX)
                    val editor: EditorViewModel = viewModel(
                        key = destination,
                        factory = factory { EditorViewModel(container, NotebookId(notebookId)) },
                    )
                    EditorScreen(
                        viewModel = editor,
                        onBack = { destination = LIBRARY },
                        onOpenSettings = ::openSettings,
                    )
                }
            }
        }
    }
}

private fun <T : ViewModel> factory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
