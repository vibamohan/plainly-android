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

    @Test fun learnedModelFlagsPositiveText() {
        assertTrue(model().predictsWarning("warning"))
    }

    @Test fun learnedModelLeavesBenignTextUnflagged() {
        assertFalse(model().predictsWarning("normal"))
    }
}
