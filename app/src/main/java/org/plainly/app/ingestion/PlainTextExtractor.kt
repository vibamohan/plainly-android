package org.plainly.app.ingestion

import android.content.Context
import android.net.Uri
import org.plainly.app.analysis.TextEvidence

/** Reads user-selected plain text locally; it does not copy or upload the source file. */
object PlainTextExtractor {
    fun extract(context: Context, uri: Uri): List<TextEvidence> =
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            toEvidence(reader.readText())
        } ?: error("Could not open text document")

    internal fun toEvidence(text: String): List<TextEvidence> = text
        .lineSequence()
        .mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() } }
        .map { TextEvidence(it, page = 1, height = 0) }
        .toList()
}
