package io.github.goumang.txtnote.platform

import androidx.compose.ui.graphics.ImageBitmap

enum class FileKind { TEXT, BACKUP, IMAGE }
data class ImportedFile(val name: String, val bytes: ByteArray)

interface PlatformServices {
    val dataDirectory: String
    val platformName: String
    fun now(): Long
    fun newId(): String
    fun formatTime(timestamp: Long): String
    suspend fun importFiles(kind: FileKind): List<ImportedFile>
    suspend fun exportFile(name: String, bytes: ByteArray, mimeType: String): Boolean
    fun copyText(text: String)
    fun readClipboard(): String
    suspend fun shareText(title: String, text: String)
}

expect fun decodeImage(bytes: ByteArray): ImageBitmap
