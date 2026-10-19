package org.plainly.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainly.app.ingestion.DigitalPdfTextExtractor

class DigitalPdfTextExtractorTest {
    @Test fun normalizesSelectableTextIntoNonBlankPageLines() {
        val lines = DigitalPdfTextExtractor.normalizePageText("  Notice title  \n\nRespond by October 20.  \n", 3)
        assertEquals(listOf("Notice title", "Respond by October 20."), lines.map { it.text })
        assertTrue(lines.all { it.page == 3 })
    }
}
