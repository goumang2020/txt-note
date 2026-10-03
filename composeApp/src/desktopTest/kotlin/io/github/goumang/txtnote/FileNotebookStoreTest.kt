package io.github.goumang.txtnote

import io.github.goumang.txtnote.data.*
import io.github.goumang.txtnote.model.*
import java.nio.file.Files
import kotlin.test.*

class FileNotebookStoreTest {
    @Test fun atomicStoreRecoversPreviousGenerationWithoutDestroyingCorruptFile() {
        val dir = Files.createTempDirectory("txtnote-test-").toFile()
        try {
            val store = FileNotebookStore(dir.absolutePath)
            val first = Notebook(notes = listOf(Note("1", "first.txt", "safe", createdAt = 1, updatedAt = 1)))
            store.save(first)
            store.save(first.copy(notes = first.notes.map { it.copy(content = "latest") }))
            dir.resolve("notebook.json").writeText("broken")
            assertEquals(first, store.load())
            assertEquals("broken", dir.resolve("notebook.corrupt.1.json").readText())
            assertEquals(first, store.load())
        } finally { dir.deleteRecursively() }
    }
    @Test fun corruptStoreWithoutRecoveryFailsAndPreservesOriginal() {
        val dir = Files.createTempDirectory("txtnote-test-").toFile()
        try {
            dir.resolve("notebook.json").writeText("broken")
            val store = FileNotebookStore(dir.absolutePath)
            assertFailsWith<IllegalStateException> { store.load() }
            assertEquals("broken", dir.resolve("notebook.json").readText())
        } finally { dir.deleteRecursively() }
    }
}
