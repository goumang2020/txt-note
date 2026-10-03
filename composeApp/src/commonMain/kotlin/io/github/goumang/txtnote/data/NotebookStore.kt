package io.github.goumang.txtnote.data

import io.github.goumang.txtnote.model.Notebook
import io.github.goumang.txtnote.model.isValidThemeHex
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

val notebookJson = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

interface NotebookStore {
    fun load(): Notebook?
    fun save(notebook: Notebook)
}

/** Commit via rename; keep the last valid generation for recovery. Never overwrite a corrupt store. */
class FileNotebookStore(directory: String, private val fs: FileSystem = FileSystem.SYSTEM) : NotebookStore {
    private val dir = directory.toPath()
    private val primary = dir / "notebook.json"
    private val backup = dir / "notebook.previous.json"
    private fun read(path: Path): Notebook = validateNotebook(notebookJson.decodeFromString(fs.read(path) { readUtf8() }))

    override fun load(): Notebook? {
        if (!fs.exists(primary) && !fs.exists(backup)) return null
        val original = runCatching { read(primary) }
        if (original.isSuccess) return original.getOrThrow()
        val recovered = runCatching { read(backup) }
        if (recovered.isFailure) throw IllegalStateException(
            "Cannot read notebook. Original files preserved at $dir / 笔记文件损坏，原文件已保留", original.exceptionOrNull()
        )
        if (fs.exists(primary)) {
            var index = 1
            while (fs.exists(dir / "notebook.corrupt.$index.json")) index++
            fs.atomicMove(primary, dir / "notebook.corrupt.$index.json")
        }
        fs.copy(backup, primary)
        return recovered.getOrThrow()
    }

    override fun save(notebook: Notebook) {
        validateNotebook(notebook)
        fs.createDirectories(dir)
        val pending = dir / "notebook.pending.json"
        fs.write(pending) { writeUtf8(notebookJson.encodeToString(notebook)) }
        // Read before rotating so an invalid primary can never replace the recovery file.
        if (fs.exists(primary)) {
            read(primary)
            val pendingBackup = dir / "notebook.previous.pending.json"
            fs.copy(primary, pendingBackup)
            fs.atomicMove(pendingBackup, backup)
        }
        fs.atomicMove(pending, primary)
    }
}

fun validateNotebook(notebook: Notebook): Notebook {
    require(notebook.schemaVersion in 1..2) { "Unsupported backup version / 不支持的备份版本" }
    require(notebook.notes.map { it.id }.toSet().size == notebook.notes.size) { "Duplicate note IDs" }
    require(notebook.notes.all { it.id.isNotBlank() && it.name.isNotBlank() && it.createdAt >= 0 && it.updatedAt >= 0 }) { "Invalid notes" }
    require(notebook.preferences.fontSize in 12..28) { "Invalid font size" }
    require(notebook.preferences.customThemeColor?.let(::isValidThemeHex) != false) { "Invalid theme color" }
    return notebook
}
