package io.github.goumang.txtnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.goumang.txtnote.data.FileNotebookStore
import io.github.goumang.txtnote.data.NoteRepository
import io.github.goumang.txtnote.platform.AndroidServices
import io.github.goumang.txtnote.ui.TxtNoteApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val platform = AndroidServices(this)
        val repository = NoteRepository(FileNotebookStore(platform.dataDirectory), platform)
        setContent {
            var back by remember { mutableStateOf<(() -> Unit)?>(null) }
            var save by remember { mutableStateOf<(() -> Unit)?>(null) }
            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) save?.invoke()
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            BackHandler(enabled = back != null) { back?.invoke() }
            TxtNoteApp(repository, platform, autoSave = true, onBackHandler = { back = it }, onSaveHandler = { save = it })
        }
    }
}
