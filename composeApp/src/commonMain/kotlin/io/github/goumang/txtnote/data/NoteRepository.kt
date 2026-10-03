package io.github.goumang.txtnote.data

import io.github.goumang.txtnote.model.*
import io.github.goumang.txtnote.platform.PlatformServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class NoteRepository(private val store: NotebookStore, private val platform: PlatformServices) {
    private val mutable = MutableStateFlow(Notebook())
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private var loaded = false

    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        val notebook = withContext(Dispatchers.Default) { store.load() } ?: Notebook()
        mutable.value = notebook
        loaded = true
    }

    private suspend fun commit(transform: (Notebook) -> Notebook) = mutex.withLock {
        check(loaded) { "Notebook is not loaded" }
        val next = transform(mutable.value)
        // A cancelled UI effect must not leave disk and observable state at different generations.
        withContext(NonCancellable + Dispatchers.Default) {
            store.save(next)
            mutable.value = next
        }
    }

    suspend fun save(draft: Draft): String {
        var savedId = ""
        commit { book ->
            val old = draft.noteId?.let { id -> book.notes.find { it.id == id } }
            val name = normalizeName(draft.name)
            require(book.notes.none { it.id != old?.id && it.name.equals(name, true) }) {
                "A note with this name already exists / 同名笔记已存在"
            }
            val now = platform.now()
            savedId = old?.id ?: platform.newId()
            val note = Note(savedId, name, draft.content, draft.category, old?.createdAt ?: now, now, old?.pinned ?: false, draft.attachment)
            book.copy(notes = if (old == null) book.notes + note else book.notes.map { if (it.id == old.id) note else it }, draft = null)
        }
        return savedId
    }

    suspend fun saveDraft(draft: Draft?) = commit { it.copy(draft = draft) }
    suspend fun preferences(preferences: Preferences) = commit { it.copy(preferences = preferences) }
    suspend fun delete(ids: Set<String>) = commit { book -> book.copy(notes = book.notes.filterNot { it.id in ids }, draft = book.draft?.takeUnless { it.noteId in ids }) }
    suspend fun pin(id: String) = commit { book -> book.copy(notes = book.notes.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }) }

    suspend fun importNotes(imported: List<ImportedNote>): Int {
        commit { book ->
            val names = book.notes.map { it.name }.toMutableSet()
            val notes = imported.map {
                val name = uniqueName(it.name, names)
                names += name
                val now = platform.now()
                Note(platform.newId(), name, it.content, createdAt = now, updatedAt = now)
            }
            book.copy(notes = book.notes + notes)
        }
        return imported.size
    }

    fun backup(draft: Draft? = mutable.value.draft): ByteArray = notebookJson.encodeToString(mutable.value.copy(draft = draft)).encodeToByteArray()

    /** Restore by addition. Existing notes and their identifiers are never overwritten. */
    suspend fun restore(bytes: ByteArray): Int {
        require(bytes.size <= 100 * 1024 * 1024) { "Backup too large (100 MB maximum)" }
        val imported = validateNotebook(notebookJson.decodeFromString<Notebook>(bytes.decodeToString(throwOnInvalidSequence = true)))
        commit { book ->
            val names = book.notes.map { it.name }.toMutableSet()
            val notes = imported.notes.map {
                val name = uniqueName(it.name, names)
                names += name
                it.copy(id = platform.newId(), name = name)
            }
            val recoveredDraft = imported.draft?.let { draft ->
                val index = imported.notes.indexOfFirst { it.id == draft.noteId }
                val recoveredNote = notes.getOrNull(index)
                draft.copy(noteId = recoveredNote?.id,
                    name = if (index >= 0 && draft.name == imported.notes[index].name) recoveredNote!!.name else draft.name)
            }
            // An empty installation can adopt settings and recover an unfinished draft.
            book.copy(notes = book.notes + notes,
                preferences = if (book.notes.isEmpty()) imported.preferences else book.preferences,
                draft = book.draft ?: recoveredDraft)
        }
        return imported.notes.size
    }
}
