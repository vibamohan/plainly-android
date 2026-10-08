package org.plainly.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import org.plainly.app.analysis.BoundingBox
import org.plainly.app.analysis.TextEvidence
import org.plainly.app.analysis.WordEvidence

enum class OcrLanguage(val label: String) {
    ENGLISH("English"), SPANISH("Spanish"), FRENCH("French"), GERMAN("German"), PORTUGUESE("Portuguese")
}

/** Uses bundled Latin-script OCR; language choices currently share the tested Latin model. */
object OcrEngine {
    suspend fun extract(context: Context, uri: Uri, language: OcrLanguage = OcrLanguage.ENGLISH, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<TextEvidence> {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val lines = mutableListOf<TextEvidence>()
            if (mime == "application/pdf" || uri.toString().endsWith(".pdf", true)) {
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot open PDF")
                descriptor.use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        require(renderer.pageCount <= 20) { "PDF has ${renderer.pageCount} pages. Choose a document of 20 pages or fewer; no pages were analyzed." }
                        // Render one page at a time so memory stays bounded without silently dropping pages.
                        for (pageIndex in 0 until renderer.pageCount) {
                            onProgress(pageIndex + 1, renderer.pageCount)
                            renderer.openPage(pageIndex).use { page ->
                                val scale = minOf(2f, 2200f / maxOf(page.width, page.height))
                                val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                                try {
                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
                                    result.textBlocks.flatMap { it.lines }.forEach { l ->
                                        lines += evidence(l.text, pageIndex + 1, l.boundingBox, l.elements)
                                    }
                                } finally { bitmap.recycle() }
                            }
                        }
                    }
                }
            } else {
                val image = InputImage.fromFilePath(context, uri)
                val result = recognizer.process(image).await()
                result.textBlocks.flatMap { it.lines }.forEach { l ->
                    lines += evidence(l.text, 1, l.boundingBox, l.elements)
                }
            }
            return lines
        } finally { recognizer.close() }
    }
    private fun evidence(text: String, page: Int, box: android.graphics.Rect?, elements: List<com.google.mlkit.vision.text.Text.Element>) = TextEvidence(text, page, box?.height() ?: 0, isCaps(text), box?.let { BoundingBox(it.left, it.top, it.right, it.bottom) }, elements.mapNotNull { e -> e.boundingBox?.let { WordEvidence(e.text, BoundingBox(it.left, it.top, it.right, it.bottom)) } })

    private fun isCaps(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        return letters.length >= 4 && letters == letters.uppercase()
    }
}
