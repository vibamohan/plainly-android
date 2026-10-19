package org.plainly.app.analysis

/** Calendar handoff is allowed only after the user explicitly confirms the detected date. */
object CalendarExportPolicy {
    fun canExport(deadline: Deadline, userConfirmedDate: Boolean): Boolean =
        userConfirmedDate
}
