package io.github.goumang.txtnote

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.goumang.txtnote.data.FileNotebookStore
import io.github.goumang.txtnote.data.NoteRepository
import io.github.goumang.txtnote.platform.DesktopServices
import io.github.goumang.txtnote.ui.TxtNoteApp

fun main() = application {
    val platform = remember { DesktopServices() }
    val repository = remember { NoteRepository(FileNotebookStore(platform.dataDirectory), platform) }
    var closeRequested by remember { mutableStateOf(false) }
    Window(onCloseRequest = { closeRequested = true }, title = "txtNote", state = rememberWindowState(width = 1200.dp, height = 820.dp)) {
        TxtNoteApp(repository, platform, closeRequested, ::exitApplication, { closeRequested = false })
    }
}
