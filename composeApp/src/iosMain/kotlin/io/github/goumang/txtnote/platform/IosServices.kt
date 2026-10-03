@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package io.github.goumang.txtnote.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.Foundation.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.*
import platform.darwin.NSObject
import platform.posix.memcpy

actual fun decodeImage(bytes: ByteArray): ImageBitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()

class IosServices(private val presenter: () -> UIViewController) : PlatformServices {
    private val mutex = Mutex()
    private var activeDelegate: PickerDelegate? = null
    override val platformName = "iOS"
    override val dataDirectory: String = (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String) + "/txtNote"
    override fun now(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
    override fun newId(): String = NSUUID().UUIDString
    override fun formatTime(timestamp: Long): String = NSDateFormatter().apply { dateFormat = "yyyy-MM-dd HH:mm" }.stringFromDate(NSDate(timeIntervalSinceReferenceDate = timestamp / 1000.0 - 978307200.0))

    private suspend fun pick(picker: UIDocumentPickerViewController): List<NSURL> {
        val result = CompletableDeferred<List<NSURL>>()
        val delegate = PickerDelegate(result)
        activeDelegate = delegate // UIKit's delegate property is weak.
        try {
            withContext(Dispatchers.Main) {
                picker.delegate = delegate
                presenter().presentViewController(picker, animated = true, completion = null)
            }
            return result.await()
        } finally { activeDelegate = null }
    }

    override suspend fun importFiles(kind: FileKind): List<ImportedFile> = mutex.withLock {
        val types = when (kind) {
            FileKind.IMAGE -> listOf(UTTypeImage)
            FileKind.BACKUP -> listOf(UTTypeJSON)
            FileKind.TEXT -> listOf(UTTypePlainText, UTTypeTabSeparatedText, UTTypeData)
        }
        val urls = withContext(Dispatchers.Main) {
            pick(UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true).apply { allowsMultipleSelection = kind != FileKind.IMAGE })
        }
        withContext(Dispatchers.Default) { urls.map { url ->
            val access = url.startAccessingSecurityScopedResource()
            try {
                val data = NSData.dataWithContentsOfURL(url) ?: error("Cannot read file / 无法读取文件")
                val limit = when (kind) { FileKind.BACKUP -> 100; FileKind.IMAGE -> 10; FileKind.TEXT -> 20 } * 1024 * 1024
                require(data.length <= limit.toULong()) { "File too large / 文件过大" }
                ImportedFile(url.lastPathComponent ?: "import.txt", data.toBytes())
            } finally { if (access) url.stopAccessingSecurityScopedResource() }
        } }
    }

    override suspend fun exportFile(name: String, bytes: ByteArray, mimeType: String): Boolean = mutex.withLock {
        val directory = NSTemporaryDirectory() + "txtnote-${newId()}/"
        NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
        val path = directory + name
        try {
            require(bytes.toData().writeToFile(path, atomically = true)) { "Cannot write export file" }
            withContext(Dispatchers.Main) { pick(UIDocumentPickerViewController(forExportingURLs = listOf(NSURL.fileURLWithPath(path)), asCopy = true)).isNotEmpty() }
        } finally { NSFileManager.defaultManager.removeItemAtPath(directory, error = null) }
    }
    override fun copyText(text: String) { UIPasteboard.generalPasteboard.string = text }
    override fun readClipboard(): String = UIPasteboard.generalPasteboard.string.orEmpty()
    override suspend fun shareText(title: String, text: String) = withContext(Dispatchers.Main) {
        val controller = presenter()
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        sheet.popoverPresentationController?.sourceView = controller.view
        sheet.popoverPresentationController?.sourceRect = controller.view.bounds
        controller.presentViewController(sheet, animated = true, completion = null)
    }
}

private class PickerDelegate(private val result: CompletableDeferred<List<NSURL>>) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) { result.complete(didPickDocumentsAtURLs.filterIsInstance<NSURL>()) }
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { result.complete(emptyList()) }
}
private fun NSData.toBytes(): ByteArray = ByteArray(length.toInt()).also { array ->
    if (array.isNotEmpty()) array.usePinned { memcpy(it.addressOf(0), bytes, length) }
}
private fun ByteArray.toData(): NSData = if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
