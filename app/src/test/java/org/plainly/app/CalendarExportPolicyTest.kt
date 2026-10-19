package org.plainly.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainly.app.analysis.CalendarExportPolicy
import org.plainly.app.analysis.Deadline
import java.time.LocalDate

class CalendarExportPolicyTest {
    private val deadline = Deadline(LocalDate.of(2026, 10, 20), "Page 1: Respond by October 20", needsReview = true)

    @Test fun ambiguousDateCannotBeExportedBeforeExplicitConfirmation() {
        assertFalse(CalendarExportPolicy.canExport(deadline, userConfirmedDate = false))
    }

    @Test fun confirmedDateMayBeHandedToCalendarEditor() {
        assertTrue(CalendarExportPolicy.canExport(deadline, userConfirmedDate = true))
    }
}
