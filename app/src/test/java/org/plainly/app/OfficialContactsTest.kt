package org.plainly.app

import org.junit.Assert.*
import org.junit.Test
import org.plainly.app.analysis.*
import java.time.LocalDate
import java.io.File

class OfficialContactsTest {
    private fun match(text: String, id: String, phone: String) {
        val result = DocumentAnalyzer.analyze(listOf(TextEvidence(text, 2))).officialContacts.single()
        assertEquals(id, result.contact.id)
        assertEquals(phone, result.contact.phone)
        assertEquals(text, result.evidence)
        assertEquals(2, result.page)
        assertTrue(java.net.URI(result.contact.sourceUrl).host.endsWith(".gov"))
    }
    @Test fun mediCal() = match("Medi-Cal renewal", "medi-cal", "800-541-5555")
    @Test fun calFresh() = match("CalFresh report", "calfresh", "877-847-3663")
    @Test fun socialSecurity() = match("Social Security benefits", "social-security", "800-772-1213")
    @Test fun irs() = match("IRS notice", "irs", "800-829-1040")
    @Test fun county() = match("Santa Clara County Social Services", "santa-clara", "408-758-3800")
    @Test fun elementaryDistrict() = match("MVWSD meeting", "mvwsd", "650-526-3552")
    @Test fun highSchoolDistrict() = match("Mountain View Los Altos district", "mvla", "650-940-4650")
    @Test fun doesNotGuessDistrictOrMatchSubstrings() {
        assertTrue(OfficialContacts.match(listOf(TextEvidence("School district chairs; medical notice; social security number", 1))).isEmpty())
    }
    @Test fun letterCannotOverrideBundledDestinationOrDuplicateContact() {
        val matches = OfficialContacts.match(listOf(TextEvidence("IRS call 555-1234 or visit https://example.com", 1), TextEvidence("IRS", 2)))
        assertEquals(1, matches.size)
        assertEquals("https://www.irs.gov/", matches.single().contact.website)
        assertEquals("800-829-1040", matches.single().contact.phone)
    }
    @Test fun fictionalBenefitsSamplesHaveExpectedDatesAndContacts() {
        for ((name, id, date) in listOf(
            Triple("medi-cal-renewal.txt", "medi-cal", LocalDate.of(2026, 11, 5)),
            Triple("calfresh-report.txt", "calfresh", LocalDate.of(2026, 11, 10))
        )) {
            val text = File("../samples/$name").readText()
            val result = DocumentAnalyzer.analyze(listOf(TextEvidence(text, 1)))
            assertEquals(date, result.deadlines.single().date)
            assertEquals(0, result.score)
            assertTrue(result.officialContacts.any { it.contact.id == id })
        }
        val scam = DocumentAnalyzer.analyze(listOf(TextEvidence(File("../samples/irs-gift-card-scam.txt").readText(), 1)))
        assertEquals(RiskLevel.HIGH, scam.riskLevel)
        assertEquals("irs", scam.officialContacts.single().contact.id)
    }
}
