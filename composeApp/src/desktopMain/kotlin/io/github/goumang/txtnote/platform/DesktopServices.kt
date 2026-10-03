package io.github.goumang.txtnote.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

actual fun decodeImage(bytes: ByteArray): ImageBitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()

class DesktopServices : PlatformServices {
    override val platformName: String = System.getProperty("os.name")
    override val dataDirectory: String = System.getProperty("txtnote.dataDir") ?: run {
        val home = System.getProperty("user.home")
        when {
            platformName.contains("Mac") -> "$home/Library/Application Support/txtNote"
            platformName.contains("Windows") -> "${System.getenv("APPDATA") ?: home}/txtNote"
            else -> "${System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"}/txtNote"
        }
    }
    override fun now(): Long = System.currentTimeMillis()
    override fun newId(): String = UUID.randomUUID().toString()
    override fun formatTime(timestamp: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))

    override suspend fun importFiles(kind: FileKind): List<ImportedFile> {
        val files = withContext(Dispatchers.Swing) {
            val chooser = JFileChooser().apply {
                isMultiSelectionEnabled = kind != FileKind.IMAGE
                fileFilter = when (kind) {
                    FileKind.TEXT -> FileNameExtensionFilter("Text / 文本 (*.txt, *.tsv)", "txt", "tsv")
                    FileKind.BACKUP -> FileNameExtensionFilter("txtNote backup (*.json)", "json")
                    FileKind.IMAGE -> FileNameExtensionFilter("Image / 图片", "png", "jpg", "jpeg", "webp")
                }
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                if (kind == FileKind.IMAGE) listOf(chooser.selectedFile) else chooser.selectedFiles.toList()
            } else emptyList()
        }
        return withContext(Dispatchers.IO) {
            files.map {
                val limit = when (kind) { FileKind.BACKUP -> 100L; FileKind.IMAGE -> 10L; FileKind.TEXT -> 20L } * 1024 * 1024
                require(it.length() <= limit) { "File too large / 文件过大" }
                ImportedFile(it.name, it.readBytes())
            }
        }
    }

    override suspend fun exportFile(name: String, bytes: ByteArray, mimeType: String): Boolean {
        val target = withContext(Dispatchers.Swing) {
            val chooser = JFileChooser().apply { selectedFile = java.io.File(name) }
            if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) null
            else chooser.selectedFile.takeIf { !it.exists() || JOptionPane.showConfirmDialog(null,
                "Replace ${it.name}? / 覆盖此文件？", "txtNote", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION }
        } ?: return false
        withContext(Dispatchers.IO) {
            val path = target.toPath().toAbsolutePath()
            val temporary = Files.createTempFile(path.parent, ".txtnote-", ".tmp")
            try {
                Files.write(temporary, bytes)
                Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temporary) }
        }
        return true
    }

    override fun copyText(text: String) = Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    override fun readClipboard(): String = Toolkit.getDefaultToolkit().systemClipboard.let { clipboard ->
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) clipboard.getData(DataFlavor.stringFlavor) as String else ""
    }
    override suspend fun shareText(title: String, text: String) {
        exportFile(if (title.endsWith(".txt")) title else "$title.txt", text.encodeToByteArray(), "text/plain")
    }
}
