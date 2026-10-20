package org.plainly.app

import java.io.BufferedReader
import java.io.StringReader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainly.app.analysis.LearnedWarningModel

class LearnedWarningModelTest {
    private fun model(): LearnedWarningModel = LearnedWarningModel.fromTsv(
        BufferedReader(StringReader(
            "# positive_docs=2\n" +
                "# negative_docs=2\n" +
                "# positive_features=4\n" +
                "# negative_features=4\n" +
                "warning\t2\t0\n" +
                "normal\t0\t2\n"
        ))
    )

    @Test fun explanationUsesOnlyPositiveFeaturesPresentInText() {
        org.junit.Assert.assertEquals(listOf("warning"), model().topWarningPhrases("normal warning unknown"))
        assertTrue(model().topWarningPhrases("normal").isEmpty())
    }

    @Test fun bundledModelCannotOverrideBenignSchoolRules() {
        val bundled = java.io.File("src/main/assets/models/warning_model.tsv").bufferedReader().use(LearnedWarningModel::fromTsv)
        listOf(
            "Please check the attached file for the updated bell schedule.",
            "Log in to your ParentSquare account to confirm your contact information.",
            "Contact the school office immediately."
        ).forEach { text ->
            val result = org.plainly.app.analysis.DocumentAnalyzer.analyze(listOf(org.plainly.app.analysis.TextEvidence(text, 1)), learnedWarningModel = bundled)
            org.junit.Assert.assertEquals(text, org.plainly.app.analysis.RiskLevel.NO_CLEAR_INDICATORS, result.riskLevel)
        }
    }

    @Test fun experimentalSignalDoesNotChangeRuleScoreOrCopyDocument() {
        val text = "Provide your password warning."
        val lines = listOf(org.plainly.app.analysis.TextEvidence(text, 1))
        val baseline = org.plainly.app.analysis.DocumentAnalyzer.analyze(lines)
        val result = org.plainly.app.analysis.DocumentAnalyzer.analyze(lines, learnedWarningModel = model())
        org.junit.Assert.assertEquals(baseline.score, result.score)
        val finding = result.findings.single { it.label.startsWith("Experimental") }
        assertTrue(finding.evidence.contains("warning"))
        assertFalse(finding.evidence.contains(text))
    }

    @Test fun learnedModelFlagsPositiveText() {
        assertTrue(model().predictsWarning("warning"))
    }

    @Test fun learnedModelLeavesBenignTextUnflagged() {
        assertFalse(model().predictsWarning("normal"))
    }
}
