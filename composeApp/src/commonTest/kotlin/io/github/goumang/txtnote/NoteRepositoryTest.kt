package io.github.goumang.txtnote

import io.github.goumang.txtnote.data.*
import io.github.goumang.txtnote.model.*
import io.github.goumang.txtnote.platform.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

private class MemoryStore : NotebookStore {
    var book: Notebook? = null
    var fail = false
    override fun load() = book
    override fun save(notebook: Notebook) { if (fail) error("Disk full"); book = notebook }
}
private class TestPlatform : PlatformServices {
    private var counter = 0
    var time = 1000L
    override val dataDirectory = "/unused"
    override val platformName = "test"
    override fun newId() = "id-${++counter}"
    override fun now() = time++
    override fun formatTime(timestamp: Long) = timestamp.toString()
    override suspend fun importFiles(kind: FileKind) = emptyList<ImportedFile>()
    override suspend fun exportFile(name: String, bytes: ByteArray, mimeType: String) = true
    override fun copyText(text: String) {}
    override fun readClipboard() = ""
    override suspend fun shareText(title: String, text: String) {}
}

class NoteRepositoryTest {
    @Test fun blankNamesUseFirstSentenceAndResolveCollisions() = runTest {
        val repository = NoteRepository(MemoryStore(), TestPlatform()); repository.load()
        repository.save(Draft(content = "  今天去公园散步。第二句不作为标题。"))
        repository.save(Draft(content = "今天去公园散步！另一个笔记"))
        repository.save(Draft(content = "Hello world. Another sentence."))
        assertEquals(listOf("今天去公园散步.txt", "今天去公园散步_2.txt", "Hello world.txt"), repository.state.value.notes.map { it.name })
    }
    @Test fun autosaveReusesReservedIdentityAcrossRepeatedWrites() = runTest {
        val store = MemoryStore(); val repository = NoteRepository(store, TestPlatform()); repository.load()
        val draft = Draft(noteId = "editor-id", content = "第一句。")
        repository.save(draft)
        val before = repository.state.value.notes.single()
        repository.save(draft.copy(content = "新标题。更新正文"))
        val after = repository.state.value.notes.single()
        assertEquals(before.id, after.id); assertEquals(before.createdAt, after.createdAt)
        assertEquals("新标题.txt", after.name)
        assertEquals(1, repository.state.value.notes.size)
        assertEquals(after, NoteRepository(store, TestPlatform()).also { it.load() }.state.value.notes.single())
    }
    @Test fun automaticTitlesAreSafeBoundedAndHaveEmptyFallback() {
        assertEquals("a b c d.txt", suggestedNoteName("a/b:c|d。内容"))
        assertEquals("第一行.txt", suggestedNoteName("\n第一行\n第二行"))
        assertEquals("未命名笔记.txt", suggestedNoteName(" 。"))
        assertEquals(64, suggestedNoteName("字".repeat(100)).length)
        assertEquals("手动标题.txt", Draft(name = "手动标题", content = "其他正文。").fileName())
    }

    @Test fun editingKeepsIdentityCreationTimeAndAttachment() = runTest {
        val store = MemoryStore(); val repository = NoteRepository(store, TestPlatform()); repository.load()
        val id = repository.save(Draft(name = "hello", content = "你好", attachment = Attachment("image.png", "YWJj")))
        val before = repository.state.value.notes.single()
        repository.pin(id)
        repository.save(Draft(id, "renamed", "new", Category.WORK, before.attachment))
        val after = repository.state.value.notes.single()
        assertEquals(id, after.id); assertEquals(before.createdAt, after.createdAt)
        assertTrue(after.updatedAt > before.updatedAt); assertTrue(after.pinned)
        assertEquals(before.attachment, after.attachment); assertEquals("renamed.txt", after.name)
    }
    @Test fun failedWriteNeverPublishesUncommittedState() = runTest {
        val store = MemoryStore(); val repository = NoteRepository(store, TestPlatform()); repository.load()
        repository.save(Draft(name = "safe", content = "saved"))
        val before = repository.state.value
        store.fail = true
        assertFailsWith<IllegalStateException> { repository.save(Draft(name = "lost")) }
        assertEquals(before, repository.state.value); assertEquals(before, store.book)
    }
    @Test fun duplicateSaveIsRejectedButImportRenames() = runTest {
        val repository = NoteRepository(MemoryStore(), TestPlatform()); repository.load()
        repository.save(Draft(name = "HELLO"))
        assertFailsWith<IllegalArgumentException> { repository.save(Draft(name = "hello.txt")) }
        repository.importNotes(listOf(ImportedNote("hello.txt", "a"), ImportedNote("hello.txt", "b")))
        assertEquals(listOf("HELLO.txt", "hello_2.txt", "hello_3.txt"), repository.state.value.notes.map { it.name })
    }
    @Test fun rejectedImportIsAtomic() = runTest {
        val repository = NoteRepository(MemoryStore(), TestPlatform()); repository.load()
        assertFailsWith<IllegalArgumentException> { repository.importNotes(listOf(ImportedNote("good", "a"), ImportedNote("../bad", "b"))) }
        assertTrue(repository.state.value.notes.isEmpty())
    }
    @Test fun draftSurvivesRestartAndIsClearedWhenSaved() = runTest {
        val store = MemoryStore(); val platform = TestPlatform(); val repository = NoteRepository(store, platform); repository.load()
        val draft = Draft(name = "unfinished", content = "never lose this")
        repository.saveDraft(draft)
        val restarted = NoteRepository(store, platform); restarted.load()
        assertEquals(draft, restarted.state.value.draft)
        restarted.save(draft); assertNull(restarted.state.value.draft)
    }
    @Test fun backupRestorePreservesMetadataAndExistingNotes() = runTest {
        val platform = TestPlatform()
        val source = NoteRepository(MemoryStore(), platform); source.load()
        val id = source.save(Draft(name = "note", content = "多行\n文本", category = Category.HOBBIES, attachment = Attachment("photo", "eHl6")))
        source.pin(id); source.preferences(Preferences(language = Language.JA, theme = ThemeMode.DARK))
        val target = NoteRepository(MemoryStore(), platform); target.load()
        target.save(Draft(name = "note", content = "existing"))
        assertEquals(1, target.restore(source.backup()))
        val notes = target.state.value.notes
        assertEquals("existing", notes.first().content)
        assertEquals("note_2.txt", notes.last().name)
        assertEquals(source.state.value.notes.single().attachment, notes.last().attachment)
        assertTrue(notes.last().pinned); assertNotEquals(id, notes.last().id)
        assertEquals(Language.ZH, target.state.value.preferences.language)
    }
    @Test fun emptyRestoreAdoptsSettingsAndFutureSchemaIsRejected() = runTest {
        val source = NoteRepository(MemoryStore(), TestPlatform()); source.load()
        source.preferences(Preferences(language = Language.EN))
        val target = NoteRepository(MemoryStore(), TestPlatform()); target.load()
        target.restore(source.backup()); assertEquals(Language.EN, target.state.value.preferences.language)
        assertFailsWith<IllegalArgumentException> { target.restore("{\"schemaVersion\":99}".encodeToByteArray()) }
    }
    @Test fun deletingSelectedNotesLeavesOthersAndClearsRelatedDraft() = runTest {
        val repository = NoteRepository(MemoryStore(), TestPlatform()); repository.load()
        val id = repository.save(Draft(name = "one")); repository.save(Draft(name = "two"))
        repository.saveDraft(Draft(noteId = id, name = "one", content = "draft"))
        repository.delete(setOf(id)); assertEquals("two.txt", repository.state.value.notes.single().name); assertNull(repository.state.value.draft)
    }
    @Test fun backupDraftPointsToRestoredIdentityAndCanUpdateIt() = runTest {
        val platform = TestPlatform()
        val source = NoteRepository(MemoryStore(), platform); source.load()
        val id = source.save(Draft(name = "note", content = "saved"))
        source.saveDraft(Draft(noteId = id, name = "note.txt", content = "unfinished"))
        val target = NoteRepository(MemoryStore(), platform); target.load()
        target.save(Draft(name = "note"))
        target.restore(source.backup())
        val draft = target.state.value.draft!!
        assertEquals("note_2.txt", draft.name)
        target.save(draft)
        assertEquals(2, target.state.value.notes.size)
        assertEquals("unfinished", target.state.value.notes.last().content)
    }
    @Test fun queriesRespectScopeCaseCategorySortAndPin() {
        val notes = listOf(Note("a", "Zulu.txt", "unique word", Category.WORK, 1, 3), Note("b", "Alpha.txt", "", Category.DAILY, 3, 1), Note("c", "Beta.txt", "", Category.WORK, 2, 2, pinned = true))
        assertEquals(listOf("c", "a", "b"), notes.query("", null, true, SortOrder.UPDATED).map { it.id })
        assertEquals(listOf("c", "b", "a"), notes.query("", null, true, SortOrder.NAME).map { it.id })
        assertEquals("a", notes.query("WORD", Category.WORK, true, SortOrder.NAME).single().id)
        assertTrue(notes.query("word", null, false, SortOrder.NAME).isEmpty())
        assertTrue(notes.query("word", Category.DAILY, true, SortOrder.NAME).isEmpty())
    }
    @Test fun themeColorPersistsThroughRestartAndBackupRestore() = runTest {
        val store = MemoryStore(); val platform = TestPlatform()
        val source = NoteRepository(store, platform); source.load()
        source.preferences(Preferences(themeColor = ThemeColor.PURPLE, customThemeColor = "#FF8800"))
        val restarted = NoteRepository(store, platform); restarted.load()
        assertEquals(ThemeColor.PURPLE, restarted.state.value.preferences.themeColor)
        assertEquals("#FF8800", restarted.state.value.preferences.customThemeColor)
        val target = NoteRepository(MemoryStore(), platform); target.load(); target.restore(restarted.backup())
        assertEquals(restarted.state.value.preferences, target.state.value.preferences)
    }
    @Test fun oldBackupsDefaultToGreenAndInvalidCustomColorsAreRejected() {
        val old = validateNotebook(notebookJson.decodeFromString<Notebook>("{\"schemaVersion\":2,\"preferences\":{\"language\":\"EN\"}}"))
        assertEquals(ThemeColor.GREEN, old.preferences.themeColor)
        assertNull(old.preferences.customThemeColor)
        assertFailsWith<IllegalArgumentException> { validateNotebook(old.copy(preferences = old.preferences.copy(customThemeColor = "#oops"))) }
    }
}

class TextExchangeTest {
    @Test fun mergedTextRoundTripsMultilineUnicodeTabsAndLiteralEscapes() {
        val notes = listOf(Note("a", "中文.txt", "first\nsecond\tthird\r\n\\n\\t", createdAt = 1, updatedAt = 1), Note("b", "empty.txt", "", createdAt = 1, updatedAt = 1))
        assertEquals(notes.map { ImportedNote(it.name, it.content) }, TextExchange.parse(ImportedFile("merged.txt", TextExchange.merge(notes).encodeToByteArray())))
    }
    @Test fun legacyMergeAndExcelTsvSupportMultilineContinuation() {
        val text = "one.txt\tfirst\ncontinued\ntwo.txt\tsecond"
        val expected = listOf(ImportedNote("one.txt", "first\ncontinued"), ImportedNote("two.txt", "second"))
        assertEquals(expected, TextExchange.parse(ImportedFile("txtNote_Merge.txt", text.encodeToByteArray())))
        assertEquals(expected, TextExchange.parse(ImportedFile("excel.tsv", text.encodeToByteArray())))
    }
    @Test fun ordinaryTextWithTabsRemainsOneNote() {
        val text = "name\tvalue\nbody"
        assertEquals(listOf(ImportedNote("regular.txt", text)), TextExchange.parse(ImportedFile("regular.txt", text.encodeToByteArray())))
    }
    @Test fun utf8BomAndUtf16AreSupportedAndInvalidUtf8Rejected() {
        assertEquals("你好", TextExchange.parse(ImportedFile("a.txt", "\uFEFF你好".encodeToByteArray())).single().content)
        assertEquals("你好", TextExchange.parse(ImportedFile("a.txt", byteArrayOf(-1, -2, 0x60, 0x4F, 0x7D, 0x59))).single().content)
        assertEquals("你好", TextExchange.parse(ImportedFile("a.txt", byteArrayOf(-2, -1, 0x4F, 0x60, 0x59, 0x7D))).single().content)
        assertFails { TextExchange.parse(ImportedFile("invalid.txt", byteArrayOf(-1))) }
    }
    @Test fun namesCannotEscapeStorageAndCollisionsAreCaseInsensitive() {
        assertEquals("hello.txt", normalizeName(" hello.txt "))
        assertFailsWith<IllegalArgumentException> { normalizeName("../bad") }
        assertFailsWith<IllegalArgumentException> { normalizeName("  ") }
        assertEquals("hello_3.txt", uniqueName("hello", setOf("HELLO.txt", "hello_2.txt")))
    }
}
