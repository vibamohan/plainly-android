package org.plainly.app

import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainly.app.analysis.DocumentAnalyzer
import org.plainly.app.analysis.TextEvidence

/** Small synthetic/redacted evaluation set for the explainable warning baseline. */
class DocumentAnalyzerEvaluationTest {
    private data class Example(val text: String, val warningExpected: Boolean)

    private val examples = listOf(
        Example("Pay with gift cards today or face arrest.", true),
        Example("Send bitcoin immediately to avoid deportation.", true),
        Example("Provide your password within 24 hours.", true),
        Example("Buy an iTunes card and send the code.", true),
        Example("Failure may result in imprisonment.", true),
        Example("Act today or your account will be closed.", true),
        Example("We need your verification code immediately.", true),
        Example("Pay cryptocurrency to release the package.", true),
        Example("Send gift cards or contact law enforcement will arrest you.", true),
        Example("Respond immediately or face deportation.", true),
        Example("We do not request gift cards.", false),
        Example("This letter explains how to pay a normal invoice.", false),
        Example("Your appointment is scheduled for October 20.", false),
        Example("Never share your password with anyone.", false),
        Example("The agency does not accept cryptocurrency payments.", false),
        Example("Please contact our office with general questions.", false),
        Example("We do not threaten arrest for late paperwork.", false),
        Example("A verification code is not requested by this service.", false),
        Example("The document describes a standard renewal process.", false),
        Example("No payment is required to submit this form.", false)
    )

    @Test fun warningBaselineMeetsSyntheticEvaluationThreshold() {
        val predictions = examples.map { example ->
            example to (DocumentAnalyzer.analyze(listOf(TextEvidence(example.text, 1))).score > 0)
        }
        val truePositives = predictions.count { (example, predicted) -> example.warningExpected && predicted }
        val falsePositives = predictions.count { (example, predicted) -> !example.warningExpected && predicted }
        val falseNegatives = predictions.count { (example, predicted) -> example.warningExpected && !predicted }
        val precision = truePositives.toDouble() / (truePositives + falsePositives).coerceAtLeast(1)
        val recall = truePositives.toDouble() / (truePositives + falseNegatives).coerceAtLeast(1)
        assertTrue("precision=$precision", precision >= 0.8)
        assertTrue("recall=$recall", recall >= 0.8)
    }
}
