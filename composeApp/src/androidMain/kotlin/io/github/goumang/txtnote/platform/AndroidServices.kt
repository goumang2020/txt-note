package io.github.goumang.txtnote.platform

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

actual fun decodeImage(bytes: ByteArray): ImageBitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Invalid image" }.asImageBitmap()

class AndroidServices(private val activity: ComponentActivity) : PlatformServices {
    private val mutex = Mutex()
    private var importResult: CompletableDeferred<List<Uri>>? = null
    private var exportResult: CompletableDeferred<Uri?>? = null
    private val open = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val intent = result.data
        val uris = if (result.resultCode != Activity.RESULT_OK || intent == null) emptyList() else {
            intent.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } } ?: listOfNotNull(intent.data)
        }
        importResult?.complete(uris)
    }
    private val save = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        exportResult?.complete(if (result.resultCode == Activity.RESULT_OK) result.data?.data else null)
    }
    override val dataDirectory: String = activity.filesDir.resolve("notebook").absolutePath
    override val platformName = "Android"
    override fun now(): Long = System.currentTimeMillis()
    override fun newId(): String = UUID.randomUUID().toString()
    override fun formatTime(timestamp: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

    override suspend fun importFiles(kind: FileKind): List<ImportedFile> = mutex.withLock {
        val result = CompletableDeferred<List<Uri>>()
        importResult = result
        try {
            withContext(Dispatchers.Main) {
                open.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = if (kind == FileKind.IMAGE) "image/*" else "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, kind != FileKind.IMAGE)
                })
            }
            val uris = result.await()
            withContext(Dispatchers.IO) { uris.map { uri ->
                val name = activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "import.txt"
                val limit = when (kind) { FileKind.BACKUP -> 100; FileKind.IMAGE -> 10; FileKind.TEXT -> 20 } * 1024 * 1024
                val bytes = activity.contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        require(output.size() + size <= limit) { "File too large / 文件过大" }
                        output.write(buffer, 0, size)
                    }
                    output.toByteArray()
                } ?: error("Cannot open file / 无法打开文件")
                ImportedFile(name, bytes)
            } }
        } finally { importResult = null }
    }
    override suspend fun exportFile(name: String, bytes: ByteArray, mimeType: String): Boolean = mutex.withLock {
        val result = CompletableDeferred<Uri?>()
        exportResult = result
        try {
            withContext(Dispatchers.Main) { save.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = mimeType; putExtra(Intent.EXTRA_TITLE, name)
            }) }
            val uri = result.await() ?: return@withLock false
            withContext(Dispatchers.IO) { activity.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Cannot write file / 无法写入文件") }
            true
        } finally { exportResult = null }
    }
    override fun copyText(text: String) = (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("txtNote", text))
    override fun readClipboard(): String = (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(activity)?.toString().orEmpty()
    override suspend fun shareText(title: String, text: String) = withContext(Dispatchers.Main) {
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, title); putExtra(Intent.EXTRA_TEXT, text) }, title))
    }
}
