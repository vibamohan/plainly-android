package org.plainly.app

import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.plainly.app.export.AnalysisPdfWriter
import org.plainly.app.ingestion.DigitalPdfTextExtractor
import org.plainly.app.ocr.OcrEngine
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfSafetyTest {
    @Test fun rejectsOversizedPdfInsteadOfSilentlyDroppingPages() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File.createTempFile("oversized", ".pdf", context.cacheDir)
        try {
            val pdf = PdfDocument()
            try {
                repeat(21) { index -> pdf.finishPage(pdf.startPage(PdfDocument.PageInfo.Builder(100, 100, index + 1).create())) }
                file.outputStream().use(pdf::writeTo)
            } finally { pdf.close() }
            val digital = runCatching { DigitalPdfTextExtractor.extract(context, Uri.fromFile(file)) }.exceptionOrNull()
            val scanned = runCatching { OcrEngine.extract(context, Uri.fromFile(file)) }.exceptionOrNull()
            assertTrue(digital?.message.orEmpty().contains("20 pages or fewer"))
            assertTrue(scanned?.message.orEmpty().contains("20 pages or fewer"))
        } finally { file.delete() }
    }

    @Test fun exportedPdfPaginatesAndPreservesEndOfReport() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File.createTempFile("export", ".pdf", context.cacheDir)
        try {
            val text = (1..200).joinToString("\n") { "Evidence line $it: confirm against the original document." } + "\nEND OF REPORT"
            file.outputStream().use { AnalysisPdfWriter.write(text, it) }
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { assertTrue(it.pageCount > 1) }
            }
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { pdf ->
                val extracted = com.tom_roush.pdfbox.text.PDFTextStripper().getText(pdf)
                assertTrue(extracted.contains("Evidence line 1:"))
                assertTrue(extracted.contains("END OF REPORT"))
            }
        } finally { file.delete() }
    }
}
