package io.github.goumang.txtnote.model

import kotlinx.serialization.Serializable

@Serializable enum class Category { DAILY, WORK, HOBBIES }
@Serializable enum class SortOrder { UPDATED, CREATED, NAME }
@Serializable enum class ThemeMode { SYSTEM, LIGHT, DARK }
@Serializable enum class ThemeColor { GREEN, BLUE, PURPLE, ORANGE, ROSE, GRAPHITE }
@Serializable enum class Language { ZH, EN, JA }

@Serializable data class Attachment(val name: String, val base64: String)
@Serializable data class Note(
    val id: String,
    val name: String,
    val content: String = "",
    val category: Category = Category.DAILY,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val attachment: Attachment? = null,
)
@Serializable data class Preferences(
    val language: Language = Language.ZH,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val themeColor: ThemeColor = ThemeColor.GREEN,
    val customThemeColor: String? = null,
    val sort: SortOrder = SortOrder.UPDATED,
    val searchContent: Boolean = true,
    val fontSize: Int = 17,
    val background: Attachment? = null,
)

fun isValidThemeHex(value: String): Boolean = Regex("^#[0-9a-fA-F]{6}$").matches(value)
@Serializable data class Draft(
    val noteId: String? = null,
    val name: String = "",
    val content: String = "",
    val category: Category = Category.DAILY,
    val attachment: Attachment? = null,
)
@Serializable data class Notebook(
    val schemaVersion: Int = 2,
    val notes: List<Note> = emptyList(),
    val preferences: Preferences = Preferences(),
    val draft: Draft? = null,
)

fun List<Note>.query(text: String, category: Category?, fullText: Boolean, sort: SortOrder): List<Note> {
    val matching = filter {
        (category == null || it.category == category) &&
            (it.name.contains(text, ignoreCase = true) || (fullText && it.content.contains(text, ignoreCase = true)))
    }
    val comparator = when (sort) {
        SortOrder.UPDATED -> compareByDescending<Note> { it.updatedAt }.thenBy { it.name.lowercase() }
        SortOrder.CREATED -> compareByDescending<Note> { it.createdAt }.thenBy { it.name.lowercase() }
        SortOrder.NAME -> compareBy<Note> { it.name.lowercase() }
    }
    return matching.sortedWith(compareByDescending<Note> { it.pinned }.then(comparator))
}

fun normalizeName(input: String): String {
    val trimmed = input.trim()
    val name = (if (trimmed.endsWith(".txt", true)) trimmed.dropLast(4) else trimmed).trim()
    require(name.isNotEmpty()) { "Please enter a file name / 请输入文件名" }
    require(name.length <= 160 && name.none { it in "/\\:*?\"<>|" || it.code < 32 }) {
        "Invalid file name / 文件名含有无效字符或过长"
    }
    return "$name.txt"
}

fun uniqueName(requested: String, existing: Set<String>): String {
    val name = normalizeName(requested)
    val occupied = existing.map { it.lowercase() }.toSet()
    if (name.lowercase() !in occupied) return name
    val stem = name.dropLast(4).take(150)
    var suffix = 2
    while ("${stem}_$suffix.txt".lowercase() in occupied) suffix++
    return "${stem}_$suffix.txt"
}

/** Local title strategy; replace this implementation when a summarization service is available. */
fun suggestedNoteName(content: String): String {
    val text = content.trimStart()
    val end = text.indexOfFirst { it in "。！？.!?\n\r" }
    val sentence = (if (end >= 0) text.take(end) else text).trim()
    val stem = sentence.map { if (it in "/\\:*?\"<>|" || it.code < 32) ' ' else it }
        .joinToString("").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(60).trim()
    return normalizeName(stem.ifBlank { "未命名笔记" })
}

fun Draft.fileName(): String = if (name.isBlank()) suggestedNoteName(content) else normalizeName(name)
