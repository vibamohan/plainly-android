package org.plainly.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainly.app.ingestion.PlainTextExtractor

class PlainTextExtractorTest {
    @Test
    fun `blank lines are omitted and source page is preserved`() {
        val evidence = PlainTextExtractor.toEvidence("Notice\n\nRespond by April 30, 2026\n")

        assertEquals(listOf("Notice", "Respond by April 30, 2026"), evidence.map { it.text })
        assertTrue(evidence.all { it.page == 1 })
    }

    @Test fun preservesTextLikeContentWithoutRequiringPdfOrOcr() {
        val evidence = PlainTextExtractor.toEvidence("{\"notice\":\"Respond by October 20\"}")
        assertEquals("{\"notice\":\"Respond by October 20\"}", evidence.single().text)
        assertEquals(1, evidence.single().page)
    }
}
