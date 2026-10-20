package org.plainly.app.analysis

/** Bundled independently of letter content; a match never authenticates a sender. */
data class OfficialContact(
    val id: String, val name: String, val phone: String, val website: String,
    val purpose: String, val sourceUrl: String, val checkedOn: String = "2026-10-19"
)
data class ContactMatch(val contact: OfficialContact, val evidence: String, val page: Int)

object OfficialContacts {
    private data class Entry(val contact: OfficialContact, val pattern: Regex)
    private fun entry(id: String, name: String, phone: String, website: String, purpose: String, source: String, pattern: String) =
        Entry(OfficialContact(id, name, phone, website, purpose, source), Regex(pattern, RegexOption.IGNORE_CASE))

    private val entries = listOf(
        entry("medi-cal", "Medi-Cal", "800-541-5555", "https://www.dhcs.ca.gov/", "California Medi-Cal general information; contact your county for renewal case questions.",
            "https://www.dhcs.ca.gov/individuals/Pages/Contact-Us.aspx", "\\bmedi[- ]cal\\b"),
        entry("calfresh", "CalFresh", "877-847-3663", "https://www.cdss.ca.gov/inforesources/calfresh", "California application information; contact your county for existing case questions.",
            "https://www.cdss.ca.gov/inforesources/calfresh/contacts", "\\bcalfresh\\b"),
        entry("social-security", "Social Security", "800-772-1213", "https://www.ssa.gov/", "National Social Security assistance.",
            "https://www.ssa.gov/agency/contact/phone.html", "\\bsocial security\\b(?!\\s+number)|\\bsupplemental security income\\b"),
        entry("irs", "Internal Revenue Service", "800-829-1040", "https://www.irs.gov/", "Individual federal tax assistance.",
            "https://www.irs.gov/payments/get-help-with-tax-debt", "\\b(?:IRS|internal revenue service)\\b"),
        entry("santa-clara", "Santa Clara County Social Services", "408-758-3800", "https://ssa.santaclaracounty.gov/apply-public-benefits", "Santa Clara County benefits assistance, including Medi-Cal and CalFresh.",
            "https://ssa.santaclaracounty.gov/apply-public-benefits", "\\b(?:santa clara county social services|county of santa clara|santa clara social services)\\b"),
        entry("mvwsd", "Mountain View Whisman School District", "650-526-3552", "https://www.mvwsd.org/", "District contact listed by the California Department of Education.",
            "https://www.cde.ca.gov/sdprofile/details.aspx?cds=43695910000000", "\\b(?:mountain view whisman|MVWSD)\\b"),
        entry("mvla", "Mountain View–Los Altos High School District", "650-940-4650", "https://mvla.net/contact-us", "District main office; confirm the student's district before calling.",
            "https://nces.ed.gov/ccd/districtsearch/district_detail.asp?ID2=0626310&details=5", "\\b(?:mountain view[– -]+los altos|MVLA)\\b")
    )

    fun match(lines: List<TextEvidence>): List<ContactMatch> = entries.mapNotNull { entry ->
        lines.firstOrNull { entry.pattern.containsMatchIn(it.text) }?.let {
            ContactMatch(entry.contact, it.text, it.page)
        }
    }
}
