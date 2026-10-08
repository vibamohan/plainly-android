package org.plainly.app.export

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

/** Writes only to the destination explicitly selected by the user. No retained cache copy. */
object AnalysisPdfWriter {
    fun write(text: String, output: OutputStream) {
        val paint = TextPaint().apply { color = Color.BLACK; textSize = 12f; isAntiAlias = true }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 523)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(3f, 1f).build()
        val pdf = PdfDocument()
        try {
            var firstLine = 0
            var pageNumber = 1
            while (firstLine < layout.lineCount) {
                val top = layout.getLineTop(firstLine)
                var lastLine = firstLine
                while (lastLine + 1 < layout.lineCount && layout.getLineBottom(lastLine + 1) - top <= 750) lastLine++
                val height = layout.getLineBottom(lastLine) - top
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                page.canvas.save()
                page.canvas.translate(36f, 36f)
                page.canvas.clipRect(0, 0, 523, height)
                page.canvas.translate(0f, -top.toFloat())
                layout.draw(page.canvas)
                page.canvas.restore()
                page.canvas.drawText("Plainly · $pageNumber", 36f, 818f, paint)
                pdf.finishPage(page)
                firstLine = lastLine + 1
                pageNumber++
            }
            pdf.writeTo(output)
        } finally { pdf.close() }
    }
}
