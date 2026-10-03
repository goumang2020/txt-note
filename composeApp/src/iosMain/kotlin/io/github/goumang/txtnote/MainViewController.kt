package io.github.goumang.txtnote

import androidx.compose.ui.window.ComposeUIViewController
import io.github.goumang.txtnote.data.FileNotebookStore
import io.github.goumang.txtnote.data.NoteRepository
import io.github.goumang.txtnote.platform.IosServices
import io.github.goumang.txtnote.ui.TxtNoteApp
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    lateinit var controller: UIViewController
    val platform = IosServices { controller }
    val repository = NoteRepository(FileNotebookStore(platform.dataDirectory), platform)
    controller = ComposeUIViewController { TxtNoteApp(repository, platform) }
    return controller
}
