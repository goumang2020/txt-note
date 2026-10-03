package io.github.goumang.txtnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
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
            BackHandler(enabled = back != null) { back?.invoke() }
            TxtNoteApp(repository, platform, onBackHandler = { back = it })
        }
    }
}
