package org.plainly.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainly.app.analysis.DocumentAnalyzer
import org.plainly.app.analysis.TextEvidence

/** One-feature-at-a-time robustness checks; these do not establish real-world causation. */
class DocumentAnalyzerCounterfactualTest {
    @Test fun unrelatedBenignWordDoesNotChangeWarningScore() {
        val original = DocumentAnalyzer.analyze(listOf(TextEvidence("Pay with gift cards immediately.", 1)))
        val changed = DocumentAnalyzer.analyze(listOf(TextEvidence("Pay with gift cards immediately at school.", 1)))
        assertEquals(original.score, changed.score)
    }

    @Test fun replacingCredentialRequestRemovesCredentialEvidence() {
        val credential = DocumentAnalyzer.analyze(listOf(TextEvidence("Provide your password to continue.", 1)))
        val ordinary = DocumentAnalyzer.analyze(listOf(TextEvidence("Provide the ordinary form to continue.", 1)))
        assertTrue(credential.findings.any { it.label == "Credential request" })
        assertFalse(ordinary.findings.any { it.label == "Credential request" })
    }
}
