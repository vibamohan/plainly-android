package org.plainly.app.ingestion

import android.content.Context
import kotlinx.coroutines.ensureActive
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import org.plainly.app.analysis.BoundingBox
import org.plainly.app.analysis.TextEvidence

/** Extracts selectable PDF text. Empty output means callers should use scanned-page OCR. */
object DigitalPdfTextExtractor {
    suspend fun extract(context: Context, uri: Uri, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<TextEvidence> {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open PDF")
        input.use { stream ->
            PDDocument.load(stream).use { document ->
                require(document.numberOfPages <= 20) { "PDF has ${document.numberOfPages} pages. Choose a document of 20 pages or fewer; no pages were analyzed." }
                if (document.numberOfPages == 0) return emptyList()
                return (1..document.numberOfPages).flatMap { page ->
                    onProgress(page, document.numberOfPages)
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val stripper = PositionedTextStripper().apply {
                        startPage = page
                        endPage = page
                    }
                    stripper.getText(document)
                    if (stripper.lines.isEmpty()) normalizePageText(stripper.rawText.toString(), page)
                    else stripper.lines.map { (text, box) -> TextEvidence(text, page, box.height, box = box) }
                }
            }
        }
    }

    fun normalizePageText(text: String, page: Int): List<TextEvidence> = text
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { TextEvidence(it, page) }
        .toList()

    private class PositionedTextStripper : PDFTextStripper() {
        val lines = mutableListOf<Pair<String, BoundingBox>>()
        val rawText = StringBuilder()

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            rawText.append(text).append('\n')
            val positions = textPositions.filter { it.unicode.isNotEmpty() }
            if (positions.isEmpty()) return
            val left = positions.minOf { it.xDirAdj }.toInt()
            val top = positions.minOf { it.yDirAdj - it.heightDir }.toInt()
            val right = positions.maxOf { it.xDirAdj + it.widthDirAdj }.toInt()
            val bottom = positions.maxOf { it.yDirAdj }.toInt()
            lines += text.trim() to BoundingBox(left, top, right, bottom)
        }
    }
}
