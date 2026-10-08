package org.plainly.app

import org.junit.Assert.*
import org.junit.Test
import org.plainly.app.analysis.DocumentAnalyzer
import org.plainly.app.analysis.TextEvidence
import java.time.LocalDate

class DocumentAnalyzerTest {
    @Test fun routineSchoolDirectionsDoNotRaiseWarnings() {
        listOf(
            "Please check the attached file for the updated bell schedule.",
            "Log in to your ParentSquare account to confirm your contact information.",
            "Contact the school office immediately.",
            "Open the attachment from this message.",
            "Social Security sent a benefits notice."
        ).forEach { text -> assertEquals(text, 0, DocumentAnalyzer.analyze(listOf(TextEvidence(text, 1))).score) }
    }
    @Test fun urgencyIdiomsDoNotHideCredentialOrPaymentRequest() {
        listOf(
            "Respond no later than Friday with your password and verification code.",
            "Do not delay, send gift cards.",
            "Do not ignore this request for your password.",
            "Do not miss the deadline to send gift cards."
        ).forEach { text -> assertTrue(text, DocumentAnalyzer.analyze(listOf(TextEvidence(text, 1))).score > 0) }
    }
    @Test fun negationDoesNotCrossSentenceOrContrastBoundaries() {
        listOf("Do not share your password. Send your password to us.", "Never send gift cards, but send bitcoin.")
            .forEach { assertTrue(DocumentAnalyzer.analyze(listOf(TextEvidence(it, 1))).score > 0) }
    }
    @Test fun linksNeedStrongerContext() {
        assertEquals(0, DocumentAnalyzer.analyze(listOf(TextEvidence("Click here to view the school newsletter.", 1))).score)
        assertTrue(DocumentAnalyzer.analyze(listOf(TextEvidence("Your account is suspended; click here to confirm payment.", 1)))
            .findings.any { it.label == "Suspicious link or attachment direction" })
    }
    @Test fun adjacentUrgentBenefitsInterruptionAndContactDirectionRaisesReviewSignal() {
        val result = DocumentAnalyzer.analyze(listOf(
            TextEvidence("Your CalFresh benefits will be suspended today.", 1),
            TextEvidence("Click here to keep your account active.", 1)
        ))
        val finding = result.findings.single { it.label == "Urgent account or benefits interruption with contact direction" }
        assertEquals(25, finding.weight)
        assertEquals(2, finding.sourceSpanIds.size)
        assertTrue(finding.evidence.contains("CalFresh"))
    }
    @Test fun adjacentUrgentCoverageInterruptionAndCredentialRequestRaisesReviewSignal() {
        val result = DocumentAnalyzer.analyze(listOf(
            TextEvidence("Your coverage may be terminated within 24 hours.", 1),
            TextEvidence("Reply with your verification code to keep the case open.", 1)
        ))
        assertTrue(result.findings.any { it.label == "Urgent account or benefits interruption with sensitive request" })
    }
    @Test fun benefitsDatesAndRoutineAgencyContactDoNotTriggerRelationalWarning() {
        val routine = DocumentAnalyzer.analyze(listOf(
            TextEvidence("Your benefits renewal is due November 5, 2026.", 1),
            TextEvidence("Contact the county office if you have questions.", 1)
        ))
        assertEquals(0, routine.score)
    }
    @Test fun missingYearRollsForwardOnlyForDeadlineAndExplainsAssumption() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Issued January 2. Return the form by January 9.", 1)), LocalDate.of(2026, 10, 19))
        assertEquals(LocalDate.of(2027, 1, 9), result.deadlines.single().date)
        assertTrue(result.deadlines.single().needsReview)
        assertTrue(result.deadlines.single().reviewReason!!.contains("assumption"))
        assertEquals(LocalDate.of(2026, 1, 2), result.issueDates.single().date)
    }
    @Test fun explicitPastYearIsNeverChanged() {
        val deadline = DocumentAnalyzer.analyze(listOf(TextEvidence("Return by January 9, 2025.", 1)), LocalDate.of(2026, 10, 19)).deadlines.single()
        assertEquals(LocalDate.of(2025, 1, 9), deadline.date)
        assertFalse(deadline.needsReview)
    }
    @Test fun abbreviatedMonthAndTwoDigitYearAreSupported() {
        val date = LocalDate.of(2026, 11, 5)
        assertEquals(date, DocumentAnalyzer.analyze(listOf(TextEvidence("Respond by Nov. 5, 2026.", 1))).deadlines.single().date)
        val numeric = DocumentAnalyzer.analyze(listOf(TextEvidence("Respond by 11/05/26.", 1))).deadlines.single()
        assertEquals(date, numeric.date)
        assertTrue(numeric.needsReview)
        assertTrue(numeric.reviewReason!!.contains("2000"))
    }
    @Test fun invalidDatesAreNotInvented() {
        assertTrue(DocumentAnalyzer.analyze(listOf(TextEvidence("Return by Feb. 30, 2026 or 13/05/26.", 1))).deadlines.isEmpty())
    }
    @Test fun appointmentTimeSupportsDottedMeridiem() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Please attend the meeting on Nov. 5, 2026 at 3:30 p.m.", 1)))
        assertTrue(result.decisionSummary!!.whatItIs.contains("3:30 p.m."))
    }
    @Test fun smallFeeDoesNotOverrideAppointmentOrPermissionIntent() {
        val appointment = DocumentAnalyzer.analyze(listOf(TextEvidence("Please attend the meeting. A fee of $5 applies.", 1)))
        assertTrue(appointment.decisionSummary!!.whatItIs.contains("Meeting"))
        val permission = DocumentAnalyzer.analyze(listOf(TextEvidence("Sign the permission slip for the field trip and return the $5 fee.", 1)))
        assertTrue(permission.decisionSummary!!.whatItIs.contains("Permission"))
        assertTrue(permission.decisionSummary!!.nextSteps.single().contains("$5"))
    }
    @Test fun extractsDeadlineAndAction() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Please submit proof of residency by October 20, 2026.", 1)), LocalDate.of(2026, 10, 11))
        assertEquals(LocalDate.of(2026, 10, 20), result.deadlines.single().date)
        assertTrue(result.actions.isNotEmpty())
        assertTrue(result.actions.single().sourceSpanIds.isNotEmpty())
        assertTrue(result.deadlines.single().sourceSpanIds.isNotEmpty())
    }
    @Test fun urgentNoticeIsNotAutomaticallyFraud() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Submit your form immediately.", 1)))
        assertTrue(result.score < 25)
    }
    @Test fun detectsGiftCardDemand() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Pay with gift cards immediately or face arrest.", 1)))
        assertTrue(result.score >= 60)
    }
    @Test fun allCapsRaisesImportanceNotRisk() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("SUBMIT DOCUMENTS", 1, 30, true)))
        assertTrue(result.actions.single().importance > 1.0)
        assertEquals(0, result.score)
    }
    @Test fun ordersColumnsAndReconstructsSentence() {
        val right = TextEvidence("Right", 1, box = org.plainly.app.analysis.BoundingBox(600, 10, 700, 30))
        val left = TextEvidence("Please submit", 1, box = org.plainly.app.analysis.BoundingBox(20, 100, 120, 120))
        assertEquals(listOf("Please submit", "Right"), DocumentAnalyzer.readingOrder(listOf(right, left)).map { it.text })
        val second = TextEvidence("the form.", 1)
        val sentence = DocumentAnalyzer.reconstructSentences(listOf(second, left)).single()
        assertEquals("Please submit the form.", DocumentAnalyzer.sentenceText(sentence))
    }
    @Test fun extractsRelativeDeadlineAndRequiresReview() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Respond within 30 days of this notice.", 1)), LocalDate.of(2026, 10, 11))
        assertEquals(LocalDate.of(2026, 11, 10), result.deadlines.single().date)
        assertTrue(result.deadlines.single().needsReview)
        assertTrue(result.deadlines.single().isCalculatedFromReferenceDate)
    }
    @Test fun ordinaryDateWithoutDeadlineLanguageIsNotDeadline() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("This notice was issued October 20, 2026.", 1)), LocalDate.of(2026, 10, 11))
        assertTrue(result.deadlines.isEmpty())
    }
    @Test fun ordinaryDateIsReportedAsIssueDate() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("This notice was issued October 20, 2026.", 1)), LocalDate.of(2026, 10, 11))
        assertEquals(LocalDate.of(2026, 10, 20), result.issueDates.single().date)
        assertTrue(!result.issueDates.single().needsReview)
        assertTrue(result.issueDates.single().sourceSpanIds.isNotEmpty())
    }
    @Test fun mixedIssueAndDeadlineDatesKeepTheirRoles() {
        val result = DocumentAnalyzer.analyze(
            listOf(TextEvidence("Issued October 20, 2026. Respond by November 1, 2026.", 1)),
            LocalDate.of(2026, 10, 11)
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 1)), result.deadlines.map { it.date })
        assertEquals(listOf(LocalDate.of(2026, 10, 20)), result.issueDates.map { it.date })
    }
    @Test fun colonFormDeadlineKeepsDeadlineRole() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Deadline: October 20, 2026", 1)))
        assertEquals(LocalDate.of(2026, 10, 20), result.deadlines.single().date)
        assertTrue(result.issueDates.isEmpty())
    }
    @Test fun negatedPaymentRequestIsNotFlagged() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("We do not request gift cards.", 1)))
        assertEquals(0, result.score)
    }
    @Test fun paymentAndThreatCombinationAddsFinding() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Pay with gift cards or face arrest.", 1)))
        assertTrue(result.findings.any { it.label == "Payment request combined with threat" })
        assertTrue(result.findings.first().sourceSpanIds.isNotEmpty())
    }
    @Test fun adjacentPaymentAndThreatSentencesAddCombinedFinding() {
        val result = DocumentAnalyzer.analyze(
            listOf(
                TextEvidence("Send payment with gift cards.", 1),
                TextEvidence("Failure to comply may result in arrest.", 1)
            )
        )
        assertTrue(result.findings.any { it.label == "Payment request near threat" })
        assertTrue(result.findings.first { it.label == "Payment request near threat" }.sourceSpanIds.size == 2)
    }
    @Test fun negatedPaymentDoesNotCombineWithThreat() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("We do not request gift cards or threaten arrest.", 1)))
        assertTrue(result.findings.none { it.label.contains("Payment request") })
    }
    @Test fun negatedPaymentSentenceDoesNotCombineWithAdjacentThreat() {
        val result = DocumentAnalyzer.analyze(
            listOf(TextEvidence("We do not request gift cards.", 1), TextEvidence("This notice does not threaten arrest.", 1))
        )
        assertTrue(result.findings.none { it.label.contains("Payment request") })
    }
    @Test fun detectsCryptocurrencySignal() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Send bitcoin to continue.", 1)))
        assertTrue(result.findings.any { it.label == "Cryptocurrency payment" })
    }
    @Test fun detectsThreatSignal() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Failure may result in deportation.", 1)))
        assertTrue(result.findings.any { it.label == "Threatening language" })
    }
    @Test fun detectsCredentialRequestSignal() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Provide your verification code.", 1)))
        assertTrue(result.findings.any { it.label == "Credential request" })
    }
    @Test fun detectsAccountVerificationRedirection() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Your account is suspended. Click here to verify it.", 1)))
        assertTrue(result.findings.any { it.label == "Account verification or redirection" })
    }
    @Test fun detectsSuspiciousAttachmentDirection() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Open the attachment to send your password.", 1)))
        assertTrue(result.findings.any { it.label == "Suspicious link or attachment direction" })
    }
    @Test fun detectsMalwareClaim() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Your computer is infected with malware.", 1)))
        assertTrue(result.findings.any { it.label == "Malware or infection claim" })
    }
    @Test fun negatedMalwareClaimIsNotFlagged() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("This tool does not claim your computer is infected.", 1)))
        assertTrue(result.findings.none { it.label == "Malware or infection claim" })
    }
    @Test fun numericDateIsMarkedAmbiguous() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Respond by 10/20/2026.", 1)))
        assertTrue(result.deadlines.single().needsReview)
    }
    @Test fun monthDateWithoutYearRequiresReview() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Payment is due by October 20.", 1)), LocalDate.of(2026, 10, 11))
        assertEquals(LocalDate.of(2026, 10, 20), result.deadlines.single().date)
        assertTrue(result.deadlines.single().needsReview)
    }
    @Test fun isoDeadlineIsExtractedWithoutAmbiguity() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Respond by 2026-10-20.", 1)))
        assertEquals(LocalDate.of(2026, 10, 20), result.deadlines.single().date)
        assertTrue(!result.deadlines.single().needsReview)
    }
    @Test fun isoIssueDateKeepsIssueDateRole() {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence("Issued on 2026-10-20.", 1)))
        assertTrue(result.deadlines.isEmpty())
        assertEquals(LocalDate.of(2026, 10, 20), result.issueDates.single().date)
    }
    @Test fun evidenceGraphLinksRequestToDeadlineAndThreat() {
        val analysis = DocumentAnalyzer.analyze(listOf(TextEvidence("Pay by October 20, 2026 or face arrest.", 1)), LocalDate.of(2026, 1, 1))
        assertTrue(analysis.intents.contains(org.plainly.app.analysis.IntentKind.PAYMENT_REQUEST))
        assertTrue(analysis.evidence.any { it.kind == org.plainly.app.analysis.EvidenceKind.DATE })
        assertTrue(analysis.evidence.any { it.kind == org.plainly.app.analysis.EvidenceKind.CONSEQUENCE })
        assertTrue(analysis.relations.any { it.relation == org.plainly.app.analysis.RelationKind.DUE_BY })
        assertTrue(analysis.relations.any { it.relation == org.plainly.app.analysis.RelationKind.THREATENS })
        assertTrue(analysis.evidence.all { it.sourceSpanIds.isNotEmpty() })
    }
    @Test fun cleanDocumentHasExplicitUncertainLanguage() {
        val analysis = DocumentAnalyzer.analyze(listOf(TextEvidence("This is a routine school announcement.", 1)))
        assertEquals(org.plainly.app.analysis.RiskLevel.NO_CLEAR_INDICATORS, analysis.riskLevel)
        assertTrue(analysis.warnings.any { it.contains("does not verify") })
    }
    @Test fun evidenceGraphExtractsAmountAndDestination() {
        val analysis = DocumentAnalyzer.analyze(listOf(TextEvidence("Pay \\$25 at https://example.com/pay.", 1)))
        assertTrue(analysis.evidence.any { it.kind == org.plainly.app.analysis.EvidenceKind.AMOUNT && it.value == "\$25" })
        assertTrue(analysis.evidence.any { it.kind == org.plainly.app.analysis.EvidenceKind.DESTINATION && it.value == "https://example.com/pay" })
        assertTrue(analysis.relations.any { it.relation == org.plainly.app.analysis.RelationKind.DIRECTS_TO })
    }
    @Test fun intentGraphTagsAppointmentAndInformationalDocuments() {
        val appointment = DocumentAnalyzer.analyze(listOf(TextEvidence("Please schedule an appointment for the hearing.", 1)))
        val informational = DocumentAnalyzer.analyze(listOf(TextEvidence("This notice explains the process.", 1)))
        assertTrue(appointment.intents.contains(org.plainly.app.analysis.IntentKind.APPOINTMENT_OR_ATTENDANCE))
        assertTrue(informational.intents.contains(org.plainly.app.analysis.IntentKind.INFORMATIONAL))
    }
    @Test fun decisionSummaryPrioritizesPaymentVerification() {
        val analysis = DocumentAnalyzer.analyze(listOf(TextEvidence("Pay with gift cards by October 20, 2026 or face arrest.", 1)))
        val summary = analysis.decisionSummary!!
        assertTrue(summary.whatItIs.contains("payment", true))
        assertTrue(summary.verifyBeforeActing.any { it.contains("trusted channel") })
        assertTrue(summary.whyItMatters.isNotEmpty())
        assertTrue(summary.sourceSpanIds.isNotEmpty())
    }
    @Test fun decisionSummaryDoesNotCallCleanDocumentSafe() {
        val summary = DocumentAnalyzer.analyze(listOf(TextEvidence("This is a routine school announcement.", 1))).decisionSummary!!
        assertTrue(summary.whyItMatters.any { it.contains("does not verify") })
    }
    @Test fun credentialSummaryWarnsAgainstSharingCodes() {
        val summary = DocumentAnalyzer.analyze(listOf(TextEvidence("Provide your verification code immediately.", 1))).decisionSummary!!
        assertTrue(summary.whatItIs.contains("sensitive"))
        assertTrue(summary.verifyBeforeActing.any { it.contains("passwords", true) || it.contains("codes", true) })
    }
    @Test fun bankTransferAndRemoteAccessSignalsAreExplainable() {
        val analysis = DocumentAnalyzer.analyze(listOf(TextEvidence("Wire transfer the fee and install this software for remote access.", 1)))
        assertTrue(analysis.findings.any { it.label == "Bank or wire payment request" })
        assertTrue(analysis.findings.any { it.label == "Remote access or software installation request" })
        assertTrue(analysis.score > 0)
    }
    @Test fun negatedSensitiveRequestIsNotFlagged() {
        val analysis = DocumentAnalyzer.analyze(listOf(TextEvidence("We do not request your social security number or bank account number.", 1)))
        assertTrue(analysis.findings.none { it.label == "Personal information request" || it.label == "Bank or wire payment request" })
    }
    @Test fun summaryUsesActionGuidanceInsteadOfOnlyRepeatingCredentialText() {
        val summary = DocumentAnalyzer.analyze(listOf(TextEvidence("Provide your password immediately.", 1))).decisionSummary!!
        assertTrue(summary.nextSteps.first().startsWith("Verify"))
    }
    @Test fun decisionSummaryIsConciseAndDoesNotCopyActionSentence() {
        val summary = DocumentAnalyzer.analyze(listOf(TextEvidence("The school meeting will be held on October 20, 2026. Please bring the completed permission form and attend the meeting.", 1))).decisionSummary!!
        assertTrue(summary.whatItIs.contains("School meeting"))
        assertTrue(summary.whatItIs.contains("October 20, 2026"))
        assertEquals("October 20, 2026 — mentioned date; confirm against the original", summary.importantDates.single())
        assertTrue(summary.nextSteps.single().contains("completed permission form"))
        assertTrue(summary.nextSteps.single().contains("location was not stated"))
        assertTrue(summary.nextSteps.single().contains("October 20, 2026"))
        assertTrue(summary.nextSteps.single().length < 220)
        assertFalse(summary.nextSteps.any { it.contains("The school meeting will be held", true) })
    }
    @Test fun actionWithoutDeadlineExplicitlyAvoidsInventingOne() {
        val summary = DocumentAnalyzer.analyze(listOf(TextEvidence("Please sign and return the permission form.", 1))).decisionSummary!!
        assertTrue(summary.uncertainty.any { it.contains("No response deadline was identified") })
        assertFalse(summary.uncertainty.any { it.contains("deadline is") && it.contains("today") })
    }
    @Test fun compactHistorySummaryContainsSynthesisNotSourceSentence() {
        val source = "The school meeting will be held on October 20, 2026. Please bring the completed permission form and attend the meeting."
        val analysis = DocumentAnalyzer.analyze(listOf(TextEvidence(source, 1)))
        assertFalse(analysis.summary.contains("The school meeting will be held", true))
        assertTrue(analysis.summary.contains("School meeting", true))
        assertTrue(analysis.summary.contains("October 20, 2026", true))
        assertTrue(analysis.summary.length < source.length * 2.0)
    }
}
