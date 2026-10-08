package org.plainly.app.ingestion

import android.content.Context
import android.net.Uri
import org.plainly.app.analysis.TextEvidence
import org.plainly.app.ocr.OcrEngine
import org.plainly.app.ocr.OcrLanguage

/** Android ingestion boundary. Implementations must preserve pages and report bounded-processing warnings. */
interface DocumentIngestion {
    suspend fun extract(context: Context, uri: Uri, ocrLanguage: OcrLanguage = OcrLanguage.ENGLISH, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<TextEvidence>
}

object OnDeviceDocumentIngestion : DocumentIngestion {
    override suspend fun extract(context: Context, uri: Uri, ocrLanguage: OcrLanguage, onProgress: (Int, Int) -> Unit): List<TextEvidence> {
        val isPdf = context.contentResolver.getType(uri) == "application/pdf" || uri.toString().endsWith(".pdf", true)
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
        val isPlainText = mime.startsWith("text/") ||
            uri.toString().substringBefore('?').lowercase().let { path ->
                listOf(".txt", ".md", ".csv", ".json", ".xml", ".html", ".htm").any(path::endsWith)
            }
        if (isPdf) {
            runCatching { DigitalPdfTextExtractor.extract(context, uri, onProgress) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        if (isPlainText) return PlainTextExtractor.extract(context, uri)
        return OcrEngine.extract(context, uri, ocrLanguage, onProgress)
    }
}
