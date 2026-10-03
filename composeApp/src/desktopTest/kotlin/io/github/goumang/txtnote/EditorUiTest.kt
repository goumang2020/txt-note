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
