package io.github.goumang.txtnote.data

import io.github.goumang.txtnote.model.Note
import io.github.goumang.txtnote.platform.ImportedFile

data class ImportedNote(val name: String, val content: String)

object TextExchange {
    private const val HEADER = "# txtNote TSV v2"
    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n")
    private fun unescape(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            if (value[i] == '\\' && i + 1 < value.length) {
                when (val next = value[++i]) {
                    't' -> append('\t'); 'n' -> append('\n'); 'r' -> append('\r'); '\\' -> append('\\')
                    else -> { append('\\'); append(next) }
                }
            } else append(value[i])
            i++
        }
    }
    fun merge(notes: List<Note>): String = HEADER + "\n" + notes.joinToString("\n") { "${escape(it.name)}\t${escape(it.content)}" }

    fun parse(file: ImportedFile): List<ImportedNote> {
        require(file.bytes.size <= 20 * 1024 * 1024) { "Text file too large (20 MB maximum)" }
        val bytes = file.bytes
        val text = when {
            bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> decodeUtf16(bytes, true)
            bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> decodeUtf16(bytes, false)
            else -> bytes.decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF")
        }
        val lines = text.replace("\r\n", "\n").split('\n')
        if (lines.firstOrNull() == HEADER) {
            return lines.drop(1).filter { it.isNotBlank() }.map { line ->
                require('\t' in line) { "Invalid txtNote merge file" }
                ImportedNote(unescape(line.substringBefore('\t')), unescape(line.substringAfter('\t')))
            }
        }
        // Legacy WP Merge.txt and Excel TSV: the first tab separates title and body.
        if (file.name.endsWith(".tsv", true) || file.name.equals("txtNote_Merge.txt", true)) {
            val result = mutableListOf<ImportedNote>()
            for (line in lines) {
                if ('\t' in line) result += ImportedNote(line.substringBefore('\t'), line.substringAfter('\t'))
                else if (result.isNotEmpty()) result[result.lastIndex] = result.last().copy(content = result.last().content + "\n" + line)
                else require(line.isBlank()) { "Invalid legacy TSV" }
            }
            return result
        }
        return listOf(ImportedNote(file.name, text))
    }

    private fun decodeUtf16(bytes: ByteArray, littleEndian: Boolean): String {
        require(bytes.size % 2 == 0) { "Invalid UTF-16 text" }
        return buildString {
            for (i in 2 until bytes.size step 2) {
                val a = bytes[i].toInt() and 255
                val b = bytes[i + 1].toInt() and 255
                append((if (littleEndian) a or (b shl 8) else (a shl 8) or b).toChar())
            }
        }
    }
}
