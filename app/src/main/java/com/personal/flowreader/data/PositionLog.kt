package com.personal.flowreader.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Append-only log of every position write and of Queue ingest events, rotated to `.1` past
 * [maxBytes]. Lines are written on one background thread.
 */
class PositionLog(private val file: File, private val maxBytes: Long = 512L * 1024L) {
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "position-log").apply { isDaemon = true } }

    val files: List<File> get() = listOf(rotated(), file).filter { it.exists() }

    fun position(update: ProgressUpdate, written: Boolean) =
        append(PositionLogFormat.position(update, written, System.currentTimeMillis()))

    fun event(kind: String, vararg fields: Pair<String, Any?>) =
        append(PositionLogFormat.event(kind, fields.toList(), System.currentTimeMillis()))

    private fun append(line: String) {
        io.execute {
            runCatching {
                file.parentFile?.mkdirs()
                if (file.length() > maxBytes) {
                    rotated().delete()
                    file.renameTo(rotated())
                }
                file.appendText(line + "\n")
            }
        }
    }

    private fun rotated() = File(file.parentFile, file.name + ".1")

    companion object {
        /** App frames of the current call stack (outside the catalog and log), innermost first. */
        fun callers(limit: Int = 4): String =
            Thread.currentThread().stackTrace
                .filter { it.className.startsWith("com.personal.flowreader") }
                .filterNot { it.className.startsWith(PositionLog::class.java.name) || it.className.contains("BookCatalog") }
                .take(limit)
                .joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
    }
}

/** Line format of [PositionLog] (tab-separated). */
object PositionLogFormat {
    private fun time(at: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(at))

    fun position(update: ProgressUpdate, written: Boolean, now: Long): String = listOf(
        time(now),
        "POS",
        update.source.name,
        update.session.toString(),
        "${update.domain.name.lowercase()}:${update.rowKey}",
        "c=${update.chapterIndex} b=${update.blockIndex} o=${update.charOffset}",
        "f=" + String.format(Locale.US, "%.4f", update.fraction),
        "href=${update.chapterHref}",
        if (written) "written" else "noRow",
    ).joinToString("\t")

    fun event(kind: String, fields: List<Pair<String, Any?>>, now: Long): String =
        (listOf(time(now), kind) + fields.map { (k, v) -> "$k=${v.toString().replace('\t', ' ').replace('\n', ' ')}" })
            .joinToString("\t")
}
