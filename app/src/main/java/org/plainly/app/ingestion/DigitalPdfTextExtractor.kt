package org.plainly.app.ingestion

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import org.plainly.app.analysis.BoundingBox
import org.plainly.app.analysis.TextEvidence

/** Extracts selectable PDF text. Empty output means callers should use scanned-page OCR. */
object DigitalPdfTextExtractor {
    suspend fun extract(context: Context, uri: Uri): List<TextEvidence> {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open PDF")
        input.use { stream ->
            PDDocument.load(stream).use { document ->
                if (document.numberOfPages == 0) return emptyList()
                return (1..document.numberOfPages).flatMap { page ->
                    val stripper = PositionedTextStripper().apply {
                        startPage = page
                        endPage = page
                    }
                    stripper.getText(document)
                    if (stripper.lines.isEmpty()) normalizePageText(stripper.rawText, page)
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
        var rawText: String = ""
            private set

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            rawText += text + "\n"
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
