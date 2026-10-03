@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, kotlin.io.encoding.ExperimentalEncodingApi::class)
package io.github.goumang.txtnote.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.goumang.txtnote.data.*
import io.github.goumang.txtnote.model.*
import io.github.goumang.txtnote.platform.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.encoding.Base64

private val Ink: Color @Composable get() = LocalThemePalette.current.ink
private val Mint: Color @Composable get() = LocalThemePalette.current.accent

private fun Note.toDraft() = Draft(id, name, content, category, attachment)

@Composable
fun TxtNoteApp(repository: NoteRepository, platform: PlatformServices,
               closeRequested: Boolean = false, onCloseReady: () -> Unit = {},
               onCloseCancelled: () -> Unit = {}, onBackHandler: ((() -> Unit)?) -> Unit = {},
               autoSave: Boolean = false, onSaveHandler: ((() -> Unit)?) -> Unit = {}) {
    val book by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val editorWrites = remember { Mutex() }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<Draft?>(null) }
    var category by remember { mutableStateOf<Category?>(null) }
    var query by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf(emptySet<String>()) }
    var settingsOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var deleteIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    val s = remember(book.preferences.language) { Strings(book.preferences.language) }
    var savedEditor by remember { mutableStateOf<Draft?>(null) }
    val original = book.notes.find { it.id == draft?.noteId }
    val dirty = draft != null && ((if (autoSave) savedEditor?.takeIf { it.noteId == draft?.noteId } ?: original?.toDraft() else original?.toDraft()) != draft) &&
        (original != null || draft!!.name.isNotBlank() || draft!!.content.isNotBlank() || draft!!.attachment != null)
    var persistedDraft by remember { mutableStateOf<Draft?>(null) }

    fun message(text: String) { scope.launch { snack.showSnackbar(text) } }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message(e.message ?: s.text("操作失败，请重试", "Operation failed; please retry", "操作に失敗しました")) }
            finally { busy = false }
        }
    }
    suspend fun saveAutomatically(snapshot: Draft) = editorWrites.withLock {
        if (draft != snapshot) return@withLock
        // Keep recoverable text even if an explicitly entered file name cannot be saved.
        repository.saveDraft(snapshot)
        repository.save(snapshot)
        if (draft == snapshot) savedEditor = snapshot
    }
    fun navigate(block: () -> Unit) {
        if (dirty && autoSave) action {
            draft?.let { saveAutomatically(it) }
            block()
        } else if (dirty) pendingNavigation = block else block()
    }
    fun edit(note: Note) = navigate { draft = note.toDraft() }
    fun create() = navigate { draft = Draft(noteId = if (autoSave) platform.newId() else null, category = category ?: Category.DAILY) }

    fun recoverDraft(recovered: Draft?): Draft? = recovered?.let {
        if (autoSave && it.noteId == null) it.copy(noteId = platform.newId()) else it
    }

    LaunchedEffect(repository) {
        try {
            repository.load()
            draft = recoverDraft(repository.state.value.draft)
            persistedDraft = draft
            loaded = true
        } catch (e: Exception) { loadError = e.message ?: "Cannot open notebook" }
    }
    // Writes are serialized in the repository; failures retain the visible unsaved draft.
    LaunchedEffect(draft, loaded, dirty) {
        if (loaded) {
            val snapshot = if (dirty) draft else null
            try {
                delay(250)
                if (autoSave) {
                    if (snapshot != null) saveAutomatically(snapshot)
                } else {
                    repository.saveDraft(snapshot)
                    persistedDraft = snapshot
                }
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message(e.message ?: "Cannot retain draft") }
        }
    }
    LaunchedEffect(closeRequested) {
        if (closeRequested && loaded) {
            navigate(onCloseReady)
        } else if (closeRequested && loadError != null) onCloseReady()
    }
    val currentBack by rememberUpdatedState<() -> Unit> { navigate { draft = null } }
    val stableBack = remember { { currentBack() } }
    val backAction: (() -> Unit)? = if (draft != null) stableBack else null
    val currentSave by rememberUpdatedState<() -> Unit> {
        if (autoSave && dirty) action { draft?.let { saveAutomatically(it) } }
    }
    val stableSave = remember { { currentSave() } }
    SideEffect { onBackHandler(backAction); onSaveHandler(if (autoSave) stableSave else null) }

    val dark = when (book.preferences.theme) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    val palette = remember(book.preferences.themeColor, book.preferences.customThemeColor) { book.preferences.palette() }
    val colors = remember(palette, dark) { palette.colorScheme(dark) }

    CompositionLocalProvider(LocalThemePalette provides palette) {
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize()) {
            if (!loaded) {
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("txtNote", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(20.dp))
                    if (loadError == null) CircularProgressIndicator() else {
                        Text(loadError!!)
                        TextButton(onClick = { action { repository.load(); draft = recoverDraft(repository.state.value.draft); loaded = true; loadError = null } }) { Text(s.text("重新打开", "Retry", "再試行")) }
                    }
                }
                return@Surface
            }
            val visible = book.notes.query(query, category, book.preferences.searchContent, book.preferences.sort)
            Scaffold(snackbarHost = { SnackbarHost(snack) }, containerColor = colors.background) { padding ->
                BoxWithConstraints(Modifier.fillMaxSize().padding(padding).safeDrawingPadding().imePadding()) {
                    val wide = maxWidth >= 900.dp
                    Row(Modifier.fillMaxSize()) {
                        if (wide) Sidebar(s, category, book.notes, platform.platformName, busy,
                            onCategory = { category = it; selection = emptySet() }, onNew = ::create,
                            onSettings = { settingsOpen = true }, onHelp = { helpOpen = true })
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            AppHeader(s, busy, onImport = { action {
                                val files = platform.importFiles(FileKind.TEXT)
                                if (files.isNotEmpty()) {
                                    val count = repository.importNotes(files.flatMap(TextExchange::parse))
                                    message(s.text("已导入 $count 篇笔记", "Imported $count notes", "$count 件読み込みました"))
                                }
                            } }, onBackup = { action {
                                if (platform.exportFile("txtNote-backup.json", repository.backup(if (dirty) draft else null), "application/json")) message(s.saved)
                            } }, onSettings = { settingsOpen = true }, showSettings = !wide)
                            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            Row(Modifier.weight(1f)) {
                                if (wide || draft == null) NoteList(s, visible, book.notes.size, category, query,
                                    book.preferences, selection, draft?.noteId, busy,
                                    modifier = if (wide) Modifier.width(320.dp).fillMaxHeight() else Modifier.fillMaxSize(),
                                    onSearch = { query = it }, onCategory = { category = it; selection = emptySet() },
                                    onPreferences = { action { repository.preferences(it) } },
                                    onSelect = { id -> selection = if (id in selection) selection - id else selection + id },
                                    onSelectAll = { selection = if (visible.all { it.id in selection }) selection - visible.map { it.id }.toSet() else selection + visible.map { it.id } },
                                    onOpen = ::edit, onNew = ::create, onDelete = { deleteIds = selection },
                                    onMerge = { action {
                                        val notes = book.notes.filter { it.id in selection }
                                        if (platform.exportFile("txtNote_Merge.txt", TextExchange.merge(notes).encodeToByteArray(), "text/plain")) message(s.saved)
                                    } }, compact = !wide)
                                if (wide) VerticalDivider()
                                if (wide || draft != null) Box(Modifier.weight(1f).fillMaxHeight()) {
                                    val current = draft
                                    if (current == null) EmptyEditor(s, ::create) else Editor(s, current, original, book.preferences, platform, busy, dirty,
                                        retained = persistedDraft == draft, compact = !wide, autoSave = autoSave,
                                        onChange = { draft = it }, onSave = { action {
                                            val id = repository.save(current)
                                            draft = repository.state.value.notes.first { it.id == id }.toDraft()
                                            message(s.saved)
                                        } }, onBack = { navigate { draft = null } },
                                        onDelete = { original?.let { deleteIds = setOf(it.id) } },
                                        onPin = { original?.let { note -> action { repository.pin(note.id) } } },
                                        onCopy = { action { platform.copyText(current.content); message(s.text("已复制到剪贴板", "Copied to clipboard", "コピーしました")) } },
                                        onPaste = { action { draft = current.copy(content = current.content + platform.readClipboard()) } },
                                        onAttach = { action {
                                            platform.importFiles(FileKind.IMAGE).firstOrNull()?.let {
                                                decodeImage(it.bytes) // Validate before saving.
                                                draft = current.copy(attachment = Attachment(it.name, Base64.encode(it.bytes)))
                                            }
                                        } }, onExport = { action {
                                            if (platform.exportFile(current.fileName(), current.content.encodeToByteArray(), "text/plain")) message(s.saved)
                                        } }, onShare = { action { platform.shareText(current.fileName(), current.content) } })
                                }
                            }
                        }
                    }
                }
            }
            if (settingsOpen) SettingsDialog(s, book.preferences, platform, busy,
                onDismiss = { settingsOpen = false }, onChange = { action { repository.preferences(it) } },
                onRestore = { action {
                    val files = platform.importFiles(FileKind.BACKUP)
                    var count = 0
                    for (file in files) count += repository.restore(file.bytes)
                    if (draft == null) draft = recoverDraft(repository.state.value.draft)
                    if (files.isNotEmpty()) { settingsOpen = false; message(s.text("已恢复 $count 篇笔记", "Restored $count notes", "$count 件復元しました")) }
                } }, onBackground = { action {
                    platform.importFiles(FileKind.IMAGE).firstOrNull()?.let {
                        decodeImage(it.bytes)
                        repository.preferences(book.preferences.copy(background = Attachment(it.name, Base64.encode(it.bytes))))
                    }
                } })
            if (helpOpen) AlertDialog(onDismissRequest = { helpOpen = false }, title = { Text(s.text("关于 txtNote", "About txtNote", "txtNote について")) }, text = {
                Text(s.text("纯文本，随身带走。\n\n笔记和草稿保存在本机。TXT 导出保留正文；合并导出可再次导入；JSON 备份包含分类、时间、图片和设置。\n\n从 Windows Phone 迁移时，可导入旧版导出的 TXT、txtNote_Merge.txt 或 Excel TSV。云盘传输请使用系统文件选择器。",
                    "Plain text. Always yours.\n\nNotes and drafts are stored locally. TXT exports contain the text; merged exports can be imported again. JSON backups include categories, dates, images, and settings.\n\nImport TXT, txtNote_Merge.txt, or Excel TSV exports from the Windows Phone version. Use your system file picker for cloud drives.",
                    "テキストはいつでも持ち出せます。\n\nノートと下書きは端末に保存されます。TXT は本文、JSON バックアップは分類・日時・画像・設定を含みます。旧 Windows Phone の TXT、txtNote_Merge.txt、TSV を読み込めます。"))
            }, confirmButton = { TextButton(onClick = { helpOpen = false }) { Text(s.text("知道了", "Got it", "OK")) } })
            if (deleteIds.isNotEmpty()) AlertDialog(onDismissRequest = { if (!busy) deleteIds = emptySet() }, title = { Text("${s.delete} ${deleteIds.size}") }, text = { Text(s.deleteBody) },
                confirmButton = { TextButton(enabled = !busy, onClick = { action {
                    val ids = deleteIds
                    editorWrites.withLock {
                        repository.delete(ids)
                        if (draft?.noteId in ids) draft = null
                    }
                    selection = selection - ids
                    deleteIds = emptySet()
                } }) { Text(s.delete, color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(enabled = !busy, onClick = { deleteIds = emptySet() }) { Text(s.cancel) } })
            if (pendingNavigation != null) AlertDialog(onDismissRequest = { if (!busy) { pendingNavigation = null; onCloseCancelled() } },
                title = { Text(s.discardTitle) }, text = { Text(s.discardBody) },
                confirmButton = { TextButton(enabled = !busy, onClick = { action {
                    val current = draft ?: return@action
                    val id = repository.save(current)
                    val next = pendingNavigation
                    pendingNavigation = null
                    draft = repository.state.value.notes.first { it.id == id }.toDraft()
                    next?.invoke()
                } }) { Text(s.save) } }, dismissButton = { Row {
                    TextButton(enabled = !busy, onClick = { pendingNavigation = null; onCloseCancelled() }) { Text(s.cancel) }
                    TextButton(enabled = !busy, onClick = { action {
                        repository.saveDraft(null)
                        val next = pendingNavigation
                        pendingNavigation = null
                        draft = null
                        next?.invoke()
                    } }) { Text(s.discard) }
                } })
        }
    }
}
}

@Composable private fun Sidebar(s: Strings, selected: Category?, notes: List<Note>, platform: String, busy: Boolean,
    onCategory: (Category?) -> Unit, onNew: () -> Unit, onSettings: () -> Unit, onHelp: () -> Unit) {
    Column(Modifier.width(220.dp).fillMaxHeight().background(Ink).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, null, tint = Mint, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp)); Text("txtNote", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp)); Text(s.text("让想法留下来。", "Make room for ideas.", "思いつきを、残そう。"), color = Mint.copy(alpha = .75f), fontSize = 12.sp)
        Spacer(Modifier.height(32.dp))
        Button(enabled = !busy, onClick = onNew, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(s.newNote)
        }
        Spacer(Modifier.height(30.dp)); Text(s.text("笔记本", "NOTEBOOK", "ノートブック"), color = Mint.copy(alpha = .65f), fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        val categories = listOf<Category?>(null) + Category.entries
        categories.forEach { category ->
            val chosen = selected == category
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (chosen) Color.White.copy(alpha = .12f) else Color.Transparent)
                .clickable { onCategory(category) }.padding(horizontal = 10.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (category == null) Icons.Default.FolderOpen else Icons.Default.Folder, null, Modifier.size(18.dp), tint = if (chosen) Mint else Color.White.copy(alpha = .6f))
                Spacer(Modifier.width(12.dp)); Text(category?.let(s::category) ?: s.all, Modifier.weight(1f), color = Color.White, fontSize = 14.sp)
                Text(notes.count { category == null || it.category == category }.toString(), color = Mint.copy(alpha = .75f), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(s.text("本机存储 · 离线可用", "LOCAL · OFFLINE READY", "端末に保存 · オフライン対応"), color = Mint.copy(alpha = .7f), fontSize = 10.sp)
        Text(platform, color = Color.White.copy(alpha = .45f), fontSize = 11.sp)
        Spacer(Modifier.height(14.dp))
        Row {
            TextButton(onClick = onSettings) { Text(s.settings, color = Mint) }
            TextButton(onClick = onHelp) { Text(s.text("帮助", "Help", "ヘルプ"), color = Mint) }
        }
    }
}

@Composable private fun AppHeader(s: Strings, busy: Boolean, onImport: () -> Unit, onBackup: () -> Unit, onSettings: () -> Unit, showSettings: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (showSettings) "txtNote" else s.text("你的文字，你的空间", "Your words, your space", "あなたの言葉、あなたの場所"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (!showSettings) Text("PLAIN TEXT. CLEAR MIND.", fontSize = 10.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(enabled = !busy, onClick = onImport) { Icon(Icons.Default.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(s.import) }
        TextButton(enabled = !busy, onClick = onBackup) { Icon(Icons.Default.CloudDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text(s.backup) }
        if (showSettings) IconButton(onClick = onSettings, enabled = !busy) { Icon(Icons.Default.Settings, s.settings) }
    }
    HorizontalDivider()
}

@Composable private fun NoteList(s: Strings, notes: List<Note>, total: Int, category: Category?, query: String,
    prefs: Preferences, selection: Set<String>, activeId: String?, busy: Boolean, modifier: Modifier,
    onSearch: (String) -> Unit, onCategory: (Category?) -> Unit, onPreferences: (Preferences) -> Unit,
    onSelect: (String) -> Unit, onSelectAll: () -> Unit, onOpen: (Note) -> Unit, onNew: () -> Unit,
    onDelete: () -> Unit, onMerge: () -> Unit, compact: Boolean) {
    var sortMenu by remember { mutableStateOf(false) }
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category?.let(s::category) ?: s.all, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onNew, enabled = !busy) { Icon(Icons.Default.Add, s.newNote) }
            }
            Text(s.text("${notes.size} 篇笔记", "${notes.size} notes", "${notes.size} 件のノート"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(query, onSearch, modifier = Modifier.fillMaxWidth().testTag("search"), singleLine = true, placeholder = { Text(s.search, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) }, trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Close, s.text("清空搜索", "Clear search", "検索をクリア")) } }, shape = RoundedCornerShape(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(prefs.searchContent, { onPreferences(prefs.copy(searchContent = it)) }, enabled = !busy, modifier = Modifier.size(30.dp))
                Text(s.fullText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, s.text("排序", "Sort", "並び替え"), Modifier.size(20.dp)) }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        SortOrder.entries.forEach { sort -> DropdownMenuItem(text = { Text(when (sort) {
                            SortOrder.UPDATED -> s.text("修改时间", "Last edited", "更新日時")
                            SortOrder.CREATED -> s.text("创建时间", "Date created", "作成日時")
                            SortOrder.NAME -> s.text("名称", "Name", "名前")
                        }) }, onClick = { sortMenu = false; onPreferences(prefs.copy(sort = sort)) }) }
                    }
                }
            }
            if (compact) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(category == null, { onCategory(null) }, label = { Text(s.text("全部", "All", "すべて"), fontSize = 12.sp) })
                Category.entries.forEach { FilterChip(category == it, { onCategory(it) }, label = { Text(s.category(it), fontSize = 12.sp) }) }
            }
        }
        HorizontalDivider()
        if (selection.isNotEmpty()) Column(Modifier.padding(horizontal = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(notes.isNotEmpty() && notes.all { it.id in selection }, { onSelectAll() }, enabled = !busy)
                Text(s.text("已选 ${selection.size} 篇", "${selection.size} selected", "${selection.size} 件選択"), fontSize = 12.sp)
                Spacer(Modifier.weight(1f)); IconButton(enabled = !busy, onClick = onDelete) { Icon(Icons.Default.DeleteOutline, s.delete) }
            }
            TextButton(enabled = !busy, onClick = onMerge) { Icon(Icons.AutoMirrored.Filled.MergeType, null, Modifier.size(18.dp)); Text(s.merge) }
            HorizontalDivider()
        }
        if (notes.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.Article, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp)); Text(s.text(if (total == 0) "还没有笔记" else "没有找到笔记", if (total == 0) "No notes yet" else "No matching notes", "ノートがありません"))
            Spacer(Modifier.height(10.dp)); TextButton(enabled = !busy, onClick = onNew) { Text(s.newNote) }
        } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(notes, key = { it.id }) { note ->
                val selected = note.id == activeId
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f) else Color.Transparent)
                    .clickable(enabled = !busy) { onOpen(note) }.padding(end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
                    Checkbox(note.id in selection, { onSelect(note.id) }, enabled = !busy, modifier = Modifier.size(36.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(note.name.removeSuffix(".txt"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (note.pinned) Icon(Icons.Default.PushPin, s.pin, Modifier.size(13.dp))
                            if (note.attachment != null) Icon(Icons.Default.Image, s.attach, Modifier.size(13.dp))
                        }
                        Spacer(Modifier.height(6.dp)); Text(note.content.ifBlank { s.text("空白笔记", "Empty note", "空のノート") }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp)); Text(s.category(note.category), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable private fun EmptyEditor(s: Strings, onNew: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(24.dp)).background(Mint.copy(alpha = .3f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.EditNote, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(26.dp)); Text(s.emptyTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp)); Text(s.emptyBody, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 24.sp)
        Spacer(Modifier.height(24.dp)); Button(onClick = onNew) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(s.newNote) }
    }
}

@Composable private fun Editor(s: Strings, draft: Draft, original: Note?, prefs: Preferences, platform: PlatformServices,
    busy: Boolean, dirty: Boolean, retained: Boolean, compact: Boolean, autoSave: Boolean, onChange: (Draft) -> Unit, onSave: () -> Unit,
    onBack: () -> Unit, onDelete: () -> Unit, onPin: () -> Unit, onCopy: () -> Unit, onPaste: () -> Unit,
    onAttach: () -> Unit, onExport: () -> Unit, onShare: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val background = remember(prefs.background) { prefs.background?.let { runCatching { decodeImage(Base64.decode(it.base64)) }.getOrNull() } }
    Box(Modifier.fillMaxSize()) {
        if (background != null) Image(background, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .08f)
        Column(Modifier.fillMaxSize().padding(if (compact) 16.dp else 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (compact) IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, s.text("返回", "Back", "戻る")) }
                Text(if (original == null) s.newNote else s.text("编辑笔记", "Edit note", "ノート編集"), fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Box {
                    IconButton(enabled = !busy, onClick = { menuOpen = true }) { Icon(Icons.Default.MoreHoriz, s.text("更多操作", "More actions", "その他")) }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        fun item(label: String, icon: ImageVector, action: () -> Unit): @Composable () -> Unit = {
                            DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = { menuOpen = false; action() })
                        }
                        item(s.export, Icons.Default.FileDownload, onExport)()
                        item(s.share, Icons.Default.Share, onShare)()
                        item(s.attach, Icons.Default.Image, onAttach)()
                        if (original != null) {
                            item(if (original.pinned) s.unpin else s.pin, Icons.Default.PushPin, onPin)()
                            item(s.delete, Icons.Default.DeleteOutline, onDelete)()
                        }
                    }
                }
                if (autoSave) Text(s.text("自动保存", "Auto-save", "自動保存"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Button(onClick = onSave, enabled = !busy && (dirty || original == null), shape = RoundedCornerShape(10.dp)) { Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s.save) }
            }
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(draft.name, { onChange(draft.copy(name = it)) }, modifier = Modifier.fillMaxWidth().testTag("note-name"), label = { Text(s.text("文件名（可选）", "File name (optional)", "ファイル名（任意）")) }, placeholder = { Text(s.text("留空时使用正文第一句", "First sentence when blank", "空欄の場合は本文の最初の文"), fontSize = 12.sp) }, suffix = { if (!draft.name.endsWith(".txt")) Text(".txt", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                enabled = !busy, singleLine = true, textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), shape = RoundedCornerShape(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Category.entries.forEach { category ->
                FilterChip(draft.category == category, { onChange(draft.copy(category = category)) }, enabled = !busy, label = { Text(s.category(category)) })
            } }
            if (original != null && !compact) Text(s.text("创建", "Created", "作成") + " ${platform.formatTime(original.createdAt)}  ·  " + s.text("修改", "Edited", "更新") + " ${platform.formatTime(original.updatedAt)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            OutlinedTextField(draft.content, { onChange(draft.copy(content = it)) }, enabled = !busy, modifier = Modifier.weight(1f).fillMaxWidth().testTag("note-content"), placeholder = { Text(s.text("从这里，开始写下你的想法…", "Start with a thought…", "思いつきを書いてみましょう…")) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = prefs.fontSize.sp, lineHeight = (prefs.fontSize * 1.65f).sp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, disabledBorderColor = Color.Transparent))
            draft.attachment?.let { attachment ->
                val bitmap = remember(attachment) { runCatching { decodeImage(Base64.decode(attachment.base64)) }.getOrNull() }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (bitmap != null) Image(bitmap, attachment.name, Modifier.size(80.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Fit)
                    Text(attachment.name, Modifier.weight(1f).padding(8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    IconButton(enabled = !busy, onClick = { onChange(draft.copy(attachment = null)) }) { Icon(Icons.Default.Close, s.text("移除图片", "Remove image", "画像を削除")) }
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${draft.content.length} " + s.text("字", "chars", "文字"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(14.dp))
                Text(if (!dirty) s.saved else if (autoSave) s.text("正在自动保存…", "Saving…", "保存中…") else if (retained) s.draftSaved else s.text("正在保留草稿…", "Retaining draft…", "下書きを保存中…"), fontSize = 11.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(enabled = !busy, onClick = onCopy) { Icon(Icons.Default.ContentCopy, s.copy, Modifier.size(18.dp)) }
                IconButton(enabled = !busy, onClick = onPaste) { Icon(Icons.Default.ContentPaste, s.paste, Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable private fun SettingsDialog(s: Strings, prefs: Preferences, platform: PlatformServices, busy: Boolean, onDismiss: () -> Unit,
    onChange: (Preferences) -> Unit, onRestore: () -> Unit, onBackground: () -> Unit) {
    var customHex by remember(prefs.customThemeColor) { mutableStateOf(prefs.customThemeColor.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(s.settings) }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(s.text("语言", "Language", "言語"), fontWeight = FontWeight.SemiBold) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Language.entries.forEach { language ->
                FilterChip(prefs.language == language, { onChange(prefs.copy(language = language)) }, enabled = !busy, label = { Text(when (language) { Language.ZH -> "中文"; Language.EN -> "English"; Language.JA -> "日本語" }) })
            } } }
            item { Text(s.text("外观", "Appearance", "外観"), fontWeight = FontWeight.SemiBold) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ThemeMode.entries.forEach { theme ->
                FilterChip(prefs.theme == theme, { onChange(prefs.copy(theme = theme)) }, enabled = !busy, label = { Text(when (theme) {
                    ThemeMode.SYSTEM -> s.text("跟随系统", "System", "システム"); ThemeMode.LIGHT -> s.text("浅色", "Light", "ライト"); ThemeMode.DARK -> s.text("深色", "Dark", "ダーク")
                }) })
            } } }
            item { Text(s.text("主题色", "Theme color", "テーマカラー"), fontWeight = FontWeight.SemiBold) }
            items(ThemeColor.entries.chunked(3)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { color ->
                        FilterChip(selected = prefs.customThemeColor == null && prefs.themeColor == color,
                            onClick = { onChange(prefs.copy(themeColor = color, customThemeColor = null)) },
                            enabled = !busy, modifier = Modifier.weight(1f).testTag("theme-color-${color.name}"),
                            label = { Text(s.themeColor(color), fontSize = 12.sp) },
                            leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(color.palette().ink)) })
                    }
                }
            }
            item {
                val valid = isValidThemeHex(customHex)
                OutlinedTextField(customHex, { customHex = it.take(7) }, modifier = Modifier.fillMaxWidth().testTag("custom-theme-color"),
                    singleLine = true, enabled = !busy, label = { Text(s.text("自定义颜色", "Custom color", "カスタムカラー")) },
                    placeholder = { Text("#3B82F6") }, isError = customHex.isNotEmpty() && !valid,
                    supportingText = { Text(s.text("输入 # 加六位十六进制颜色", "Enter # followed by six hex digits", "# に続けて6桁の色を入力"), fontSize = 11.sp) },
                    trailingIcon = { TextButton(enabled = !busy && valid && customHex != prefs.customThemeColor,
                        onClick = { onChange(prefs.copy(customThemeColor = customHex.uppercase())) }) { Text(s.text("应用", "Apply", "適用")) } })
            }
            item { Text(s.text("正文字号", "Editor font size", "本文の文字サイズ") + " · ${prefs.fontSize}"); Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = !busy && prefs.fontSize > 12, onClick = { onChange(prefs.copy(fontSize = prefs.fontSize - 1)) }) { Text("A−") }
                Text("${prefs.fontSize} sp", Modifier.weight(1f))
                TextButton(enabled = !busy && prefs.fontSize < 28, onClick = { onChange(prefs.copy(fontSize = prefs.fontSize + 1)) }) { Text("A+") }
            } }
            item { Row {
                TextButton(enabled = !busy, onClick = onBackground) { Text(s.text("更换背景图片", "Set background", "背景画像を変更")) }
                if (prefs.background != null) TextButton(enabled = !busy, onClick = { onChange(prefs.copy(background = null)) }) { Text(s.text("移除", "Remove", "削除")) }
            } }
            item { HorizontalDivider() }
            item { OutlinedButton(enabled = !busy, onClick = onRestore) { Icon(Icons.Default.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(s.restore) } }
            item { Text(s.text("恢复会追加笔记，同名文件会自动重命名。原有笔记会保留。", "Restored notes are added. Duplicate names are renamed automatically. Existing notes are preserved.", "復元ノートは追加されます。同名のノートは自動で名前を変更します。"), fontSize = 12.sp) }
            item { Text(s.text("存储位置", "Storage location", "保存場所"), fontWeight = FontWeight.SemiBold); Text(platform.dataDirectory, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
            item { Text("txtNote 2.0 · Kotlin Multiplatform", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(s.text("完成", "Done", "完了")) } })
}
