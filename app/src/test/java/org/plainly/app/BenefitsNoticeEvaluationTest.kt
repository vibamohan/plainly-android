package org.plainly.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainly.app.analysis.DocumentAnalyzer
import org.plainly.app.analysis.TextEvidence

/** Fixed, fictional behavior check. This is not a representative accuracy estimate. */
class BenefitsNoticeEvaluationTest {
    private data class Example(val id: String, val expectsReviewSignal: Boolean, val text: String)

    private val examples = listOf(
        Example("routine_medical_renewal", false, "Your Medi-Cal renewal packet is due November 18. Return the signed form to the county office."),
        Example("routine_food_report", false, "Your CalFresh periodic report is due December 3. Submit your household update through the county portal."),
        Example("routine_school_attachment", false, "Please open the attached file for the school meal-program schedule."),
        Example("routine_parent_portal", false, "Log in to your ParentSquare account to confirm your contact information."),
        Example("routine_utility_bill", false, "Your PG&E bill is due November 12. Pay using the payment methods listed on your regular statement."),
        Example("routine_appointment", false, "Attend your county benefits appointment on November 9 at 10:30 a.m. Bring proof of residency."),
        Example("routine_ssa_notice", false, "Social Security sent a benefits notice. Call the number printed on your official statement with questions."),
        Example("routine_irs_attachment", false, "Please check the attached file for the updated tax form and mailing instructions."),
        Example("routine_renewal_contact", false, "Your benefits renewal is due November 5, 2026. Contact the county office if you have questions."),
        Example("routine_document_request", false, "Return the requested proof of income by November 21 using the enclosed return envelope."),
        Example("routine_school_iep", false, "Your IEP meeting is scheduled for November 14. Reply to the school office to confirm attendance."),
        Example("routine_case_update", false, "We received your application. Check your case status through the county website when convenient."),
        Example("routine_tax_balance", false, "Your tax account statement lists a balance due by December 15. Review payment options on IRS.gov."),
        Example("routine_address_change", false, "Please update your mailing address with the benefits office so future notices reach you."),
        Example("routine_paperwork_deadline", false, "No later than November 30, send the completed renewal form to the address shown on page two."),

        Example("scam_gift_cards_arrest", true, "FINAL NOTICE: Pay your overdue benefits fee with gift cards today or face arrest."),
        Example("scam_password_deadline", true, "Respond no later than Friday with your password and verification code to keep your case open."),
        Example("scam_account_link", true, "Your CalFresh account is suspended. Click here within 24 hours and enter your password to restore benefits."),
        Example("scam_crypto", true, "Send bitcoin to the listed wallet today to release your delayed tax refund."),
        Example("scam_wire_transfer", true, "Wire transfer the processing fee immediately or your Medi-Cal coverage will be terminated."),
        Example("scam_ssn_request", true, "Reply with your Social Security number and date of birth to avoid losing your benefits."),
        Example("scam_fake_irs", true, "The IRS will arrest you unless you pay this final tax penalty with iTunes cards."),
        Example("scam_remote_access", true, "Install this software and share your screen now so our benefits agent can verify your bank account."),
        Example("scam_urgent_link", true, "Your coverage ends today. Visit this link and enter your verification code to prevent termination."),
        Example("scam_text_credentials", true, "Text your one-time code and password to this number within 24 hours or your claim will be closed."),
        Example("scam_bank_details", true, "Send your bank account number and routing number to receive the promised emergency grant."),
        Example("scam_fake_refund", true, "Pay a $75 wire transfer fee immediately to unlock your Social Security refund."),
        Example("scam_threat", true, "Final notice: pay with gift cards now or you will be deported."),
        Example("scam_portal_credential", true, "Your benefits application will be canceled today. Open this website and provide your password."),
        Example("scam_crypto_threat", true, "Transfer cryptocurrency within 24 hours or law enforcement will arrest you."),
    )

    @Test fun fixedSyntheticBenefitsSetReportsExactConfusionMatrix() {
        var tp = 0
        var fp = 0
        var fn = 0
        var tn = 0
        examples.forEach { example ->
            val result = DocumentAnalyzer.analyze(listOf(TextEvidence(example.text, 1)))
            // Treat any nonzero deterministic score as a review signal, not a fraud verdict.
            val predictedReviewSignal = result.score > 0
            when {
                example.expectsReviewSignal && predictedReviewSignal -> tp++
                !example.expectsReviewSignal && predictedReviewSignal -> fp++
                example.expectsReviewSignal && !predictedReviewSignal -> fn++
                else -> tn++
            }
        }
        assertEquals(15, examples.count { !it.expectsReviewSignal })
        assertEquals(15, examples.count { it.expectsReviewSignal })
        assertEquals(examples.size, tp + fp + fn + tn)
        assertEquals(15, tp)
        assertEquals(0, fp)
        assertEquals(0, fn)
        assertEquals(15, tn)
        assertTrue("Accuracy", (tp + tn).toDouble() / examples.size >= 0.90)
    }
}
