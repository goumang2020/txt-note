package io.github.goumang.txtnote

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.goumang.txtnote.data.NoteRepository
import io.github.goumang.txtnote.data.NotebookStore
import io.github.goumang.txtnote.model.Notebook
import io.github.goumang.txtnote.model.ThemeColor
import io.github.goumang.txtnote.platform.DesktopServices
import io.github.goumang.txtnote.ui.TxtNoteApp
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class EditorUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun chineseTextIsSavedThroughEditorAndFoundByFullTextSearch() {
        var stored: Notebook? = null
        val repository = NoteRepository(object : NotebookStore {
            override fun load() = stored
            override fun save(notebook: Notebook) { stored = notebook }
        }, DesktopServices())
        rule.setContent { Box(Modifier.requiredSize(390.dp, 740.dp)) { TxtNoteApp(repository, DesktopServices()) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("新建笔记").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("新建笔记")[0].performClick()
        rule.onNodeWithTag("note-name").performTextInput("我的跨平台笔记")
        rule.onNodeWithTag("note-content").performTextInput("你好，Kotlin！\n第二行保留换行。")
        rule.onNodeWithText("保存").performClick()
        rule.waitUntil(10_000) { repository.state.value.notes.size == 1 }
        assertEquals("我的跨平台笔记.txt", repository.state.value.notes.single().name)
        assertEquals("你好，Kotlin！\n第二行保留换行。", repository.state.value.notes.single().content)
        // The test viewport uses the phone layout, including returning to the list.
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("返回") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithTag("search").performTextInput("第二行")
        rule.onNodeWithText("我的跨平台笔记").assertExists()
    }

    @Test fun navigatingAwayFromChangedNoteRequiresSaveOrDiscard() {
        val repository = NoteRepository(object : NotebookStore {
            override fun load(): Notebook? = null
            override fun save(notebook: Notebook) {}
        }, DesktopServices())
        rule.setContent { Box(Modifier.requiredSize(390.dp, 740.dp)) { TxtNoteApp(repository, DesktopServices()) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("新建笔记").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("新建笔记")[0].performClick()
        rule.onNodeWithTag("note-name").performTextInput("尚未保存")
        rule.onNodeWithTag("note-content").performTextInput("保留修改")
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithText("保存这次修改？").assertExists()
        assertEquals(0, repository.state.value.notes.size)
        rule.onNodeWithText("放弃修改").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("search").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, repository.state.value.notes.size)
    }
    @Test fun androidBackAutosavesUnnamedNoteAndEditingKeepsOneNote() {
        var stored: Notebook? = null
        var systemBack: (() -> Unit)? = null
        val repository = NoteRepository(object : NotebookStore {
            override fun load() = stored
            override fun save(notebook: Notebook) { stored = notebook }
        }, DesktopServices())
        rule.setContent { Box(Modifier.requiredSize(390.dp, 740.dp)) {
            TxtNoteApp(repository, DesktopServices(), autoSave = true, onBackHandler = { systemBack = it })
        } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("新建笔记").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("新建笔记")[0].performClick()
        rule.onNodeWithTag("note-content").performTextInput("正文第一句。后面还有内容。")
        rule.runOnIdle { systemBack!!() }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("search").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("保存这次修改？").assertDoesNotExist()
        assertEquals("正文第一句.txt", stored!!.notes.single().name)
        val id = stored!!.notes.single().id
        rule.onNodeWithText("正文第一句").performClick()
        rule.onNodeWithTag("note-content").performTextReplacement("正文第一句。后面还有内容。追加内容")
        rule.onNodeWithContentDescription("返回").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("search").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(id, stored!!.notes.single().id)
        assertEquals("正文第一句。后面还有内容。追加内容", stored!!.notes.single().content)
    }

    @Test fun androidAutosavesWhileEditingAndFlushesOnBackground() {
        var saveInBackground: (() -> Unit)? = null
        val repository = NoteRepository(object : NotebookStore {
            override fun load(): Notebook? = null
            override fun save(notebook: Notebook) {}
        }, DesktopServices())
        rule.setContent { Box(Modifier.requiredSize(390.dp, 740.dp)) {
            TxtNoteApp(repository, DesktopServices(), autoSave = true, onSaveHandler = { saveInBackground = it })
        } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("新建笔记").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("新建笔记")[0].performClick()
        rule.onNodeWithTag("note-content").performTextInput("自动保存正文。")
        rule.waitUntil(10_000) { repository.state.value.notes.singleOrNull()?.content == "自动保存正文。" }
        rule.onNodeWithTag("note-content").assertExists()
        rule.onNodeWithTag("note-content").performTextInput("后台前的修改")
        rule.runOnIdle { saveInBackground!!() }
        rule.waitUntil(10_000) { repository.state.value.notes.singleOrNull()?.content == "自动保存正文。后台前的修改" }
    }

    @Test fun androidEmptyNoteReturnsWithoutCreatingFile() {
        val repository = NoteRepository(object : NotebookStore {
            override fun load(): Notebook? = null
            override fun save(notebook: Notebook) {}
        }, DesktopServices())
        rule.setContent { Box(Modifier.requiredSize(390.dp, 740.dp)) { TxtNoteApp(repository, DesktopServices(), autoSave = true) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("新建笔记").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("新建笔记")[0].performClick()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithTag("search").assertExists()
        rule.onNodeWithText("保存这次修改？").assertDoesNotExist()
        assertEquals(0, repository.state.value.notes.size)
    }

    @Test fun androidFailedAutosaveKeepsVisibleTextAndDoesNotNavigate() {
        var fail = false
        var stored: Notebook? = null
        val repository = NoteRepository(object : NotebookStore {
            override fun load() = stored
            override fun save(notebook: Notebook) { if (fail) error("Disk full"); stored = notebook }
        }, DesktopServices())
        rule.setContent { Box(Modifier.requiredSize(390.dp, 740.dp)) { TxtNoteApp(repository, DesktopServices(), autoSave = true) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("新建笔记").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("新建笔记")[0].performClick()
        rule.onNodeWithTag("note-content").performTextInput("已保存正文。")
        rule.waitUntil(10_000) { repository.state.value.notes.size == 1 }
        rule.runOnIdle { fail = true }
        rule.onNodeWithTag("note-content").performTextReplacement("需要保留的修改。")
        rule.onNodeWithContentDescription("返回").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Disk full").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("note-content").assertTextContains("需要保留的修改。")
        rule.onNodeWithTag("search").assertDoesNotExist()
        rule.onNodeWithText("保存这次修改？").assertDoesNotExist()
        assertEquals("已保存正文。", stored!!.notes.single().content)
    }

    @Test fun androidInvalidExplicitNameRetainsRecoverableDraft() {
        var stored: Notebook? = null
        val repository = NoteRepository(object : NotebookStore {
            override fun load() = stored
            override fun save(notebook: Notebook) { stored = notebook }
        }, DesktopServices())
        rule.setContent { Box(Modifier.requiredSize(390.dp, 740.dp)) { TxtNoteApp(repository, DesktopServices(), autoSave = true) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("新建笔记").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("新建笔记")[0].performClick()
        rule.onNodeWithTag("note-name").performTextInput("../invalid")
        rule.onNodeWithTag("note-content").performTextInput("名称无效也应保留正文。")
        rule.onNodeWithContentDescription("返回").performClick()
        rule.waitUntil(10_000) { stored?.draft?.content == "名称无效也应保留正文。" }
        rule.onNodeWithTag("note-content").assertTextContains("名称无效也应保留正文。")
        assertEquals(0, stored!!.notes.size)
    }

    @Test fun androidBackDuringInFlightAutosaveKeepsLatestTextWithoutDuplicates() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var systemBack: (() -> Unit)? = null
        val repository = NoteRepository(object : NotebookStore {
            override fun load(): Notebook? = null
            override fun save(notebook: Notebook) {
                if (notebook.notes.isNotEmpty() && entered.count > 0) {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                }
            }
        }, DesktopServices())
        rule.setContent { Box(Modifier.requiredSize(390.dp, 740.dp)) {
            TxtNoteApp(repository, DesktopServices(), autoSave = true, onBackHandler = { systemBack = it })
        } }
        try {
            rule.waitUntil(10_000) { rule.onAllNodesWithText("新建笔记").fetchSemanticsNodes().isNotEmpty() }
            rule.onAllNodesWithText("新建笔记")[0].performClick()
            rule.onNodeWithTag("note-content").performTextInput("第一版正文。")
            rule.waitUntil(10_000) { entered.count == 0L }
            rule.onNodeWithTag("note-content").performTextReplacement("返回时的最新正文。")
            rule.runOnIdle { systemBack!!(); release.countDown() }
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("search").fetchSemanticsNodes().isNotEmpty() }
            assertEquals("返回时的最新正文。", repository.state.value.notes.single().content)
            rule.onNodeWithText("保存这次修改？").assertDoesNotExist()
        } finally { release.countDown() }
    }

    @Test fun settingsApplyPresetAndCustomColorsImmediately() {
        val repository = NoteRepository(object : NotebookStore {
            override fun load(): Notebook? = null
            override fun save(notebook: Notebook) {}
        }, DesktopServices())
        rule.setContent { Box(Modifier.requiredSize(390.dp, 740.dp)) { TxtNoteApp(repository, DesktopServices()) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("新建笔记").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("设置").performClick()
        rule.onNodeWithTag("theme-color-BLUE").performScrollTo().performClick()
        rule.waitUntil(10_000) { repository.state.value.preferences.themeColor == ThemeColor.BLUE }
        rule.onNodeWithTag("theme-color-BLUE").assertIsSelected()
        rule.onNodeWithTag("custom-theme-color").performScrollTo().performTextInput("#FF8800")
        rule.onNodeWithText("应用").performClick()
        rule.waitUntil(10_000) { repository.state.value.preferences.customThemeColor == "#FF8800" }
        rule.onNodeWithText("完成").performClick()
        rule.onNodeWithTag("search").assertExists()
    }
}
