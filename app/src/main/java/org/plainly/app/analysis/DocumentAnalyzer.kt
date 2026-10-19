package org.plainly.app.analysis

import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.plainly.app.reasoning.EvidenceGraphBuilder

data class BoundingBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
}
data class WordEvidence(val text: String, val box: BoundingBox)
data class TextEvidence(val text: String, val page: Int, val height: Int = 0, val allCaps: Boolean = false, val box: BoundingBox? = null, val words: List<WordEvidence> = emptyList())
data class Finding(val label: String, val evidence: String, val weight: Int, val sourceSpanIds: List<String> = emptyList())
data class Deadline(
    val date: LocalDate,
    val evidence: String,
    val needsReview: Boolean,
    val sourceSpanIds: List<String> = emptyList(),
    val isCalculatedFromReferenceDate: Boolean = false
)
data class IssueDate(val date: LocalDate, val evidence: String, val needsReview: Boolean, val sourceSpanIds: List<String> = emptyList())
data class ActionItem(val instruction: String, val evidence: String, val importance: Double, val sourceSpanIds: List<String> = emptyList())
enum class IntentKind { ACTION_REQUIRED, PAYMENT_REQUEST, CREDENTIAL_REQUEST, APPOINTMENT_OR_ATTENDANCE, INFORMATIONAL }
enum class RiskLevel { HIGH, UNCERTAIN, NO_CLEAR_INDICATORS }
enum class EvidenceKind { AUTHORITY_CLAIM, REQUEST, CONDITION, CONSEQUENCE, DESTINATION, AMOUNT, DATE }
enum class Provenance { EXTRACTED, RULE_INFERRED, MODEL_INFERRED }
enum class RelationKind { CLAIMS_AUTHORITY, REQUESTS, CONDITIONAL_ON, THREATENS, DIRECTS_TO, DUE_BY }
data class GraphEvidence(val id: String, val kind: EvidenceKind, val sourceSpanIds: List<String>, val value: String, val confidence: Float? = null, val provenance: Provenance = Provenance.RULE_INFERRED)
data class EvidenceRelation(val fromEvidenceId: String, val toEvidenceId: String, val relation: RelationKind, val sourceSpanIds: List<String>, val provenance: Provenance = Provenance.RULE_INFERRED)
data class SummaryItem(val text: String, val sourceSpanIds: List<String>)
data class DecisionSummary(
    val whatItIs: String,
    val nextSteps: List<String>,
    val importantDates: List<String>,
    val whyItMatters: List<String>,
    val verifyBeforeActing: List<String>,
    val uncertainty: List<String>,
    val sourceSpanIds: List<String>,
    val qualityNotes: List<String> = emptyList()
)
data class Analysis(
    val actions: List<ActionItem>, val deadlines: List<Deadline>, val findings: List<Finding>, val score: Int, val summary: String,
    val intents: Set<IntentKind> = emptySet(), val evidence: List<GraphEvidence> = emptyList(), val relations: List<EvidenceRelation> = emptyList(),
    val riskLevel: RiskLevel = RiskLevel.NO_CLEAR_INDICATORS, val warnings: List<String> = emptyList(), val summaryItems: List<SummaryItem> = emptyList(),
    val issueDates: List<IssueDate> = emptyList(),
    val decisionSummary: DecisionSummary? = null
)

/** Conservative, deterministic analysis. Scores are heuristics, NOT fraud probabilities. */
object DocumentAnalyzer {
    private val actionWords = Regex("\\b(submit|return|complete|send|provide|sign|pay|contact|respond|schedule|bring|upload)\\b", RegexOption.IGNORE_CASE)
    private val deadlineWords = Regex("\\b(by|before|due|deadline|no later than|on or before)\\b", RegexOption.IGNORE_CASE)
    private val monthDate = Regex("\\b(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{1,2})(?:,?\\s+(\\d{4}))?\\b", RegexOption.IGNORE_CASE)
    private val numericDate = Regex("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b")
    private val isoDate = Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b")
    private val relativeDays = Regex("\\b(?:within|in|no later than)\\s+(\\d{1,3})\\s+days?\\b", RegexOption.IGNORE_CASE)
    private val rules = listOf(
        Triple("Gift-card payment", Regex("gift\\s*cards?|itunes\\s*cards?", RegexOption.IGNORE_CASE), 35),
        Triple("Cryptocurrency payment", Regex("\\b(bitcoin|crypto(?:currency)?)\\b", RegexOption.IGNORE_CASE), 30),
        Triple("Threatening language", Regex("\\b(arrest|deportation|imprisonment)\\b", RegexOption.IGNORE_CASE), 25),
        Triple("Credential request", Regex("\\b(password|one.time code|verification code)\\b", RegexOption.IGNORE_CASE), 25),
        Triple("Personal information request", Regex("\\b(social security|tax id|date of birth|bank account number|routing number)\\b", RegexOption.IGNORE_CASE), 25),
        Triple("Bank or wire payment request", Regex("\\b(wire transfer|bank transfer|routing number|wire funds|send a payment)\\b", RegexOption.IGNORE_CASE), 20),
        Triple("Remote access or software installation request", Regex("\\b(remote access|screen share|install (?:this|the) software|download (?:this|the) app)\\b", RegexOption.IGNORE_CASE), 20),
        Triple("Urgent pressure", Regex("\\b(act today|immediately|within 24 hours|last chance|final notice)\\b", RegexOption.IGNORE_CASE), 10)
    )

    fun analyze(
        lines: List<TextEvidence>,
        referenceDate: LocalDate = LocalDate.now(),
        learnedWarningModel: LearnedWarningModel? = null
    ): Analysis {
        val actions = mutableListOf<ActionItem>()
        val deadlines = mutableListOf<Deadline>()
        val issueDates = mutableListOf<IssueDate>()
        val findings = mutableListOf<Finding>()
        val paymentPattern = Regex("\\b(gift\\s*cards?|itunes\\s*cards?|bitcoin|crypto(?:currency)?)\\b", RegexOption.IGNORE_CASE)
        val threatPattern = Regex("\\b(arrest|deportation|imprisonment)\\b", RegexOption.IGNORE_CASE)
        fun containsNonNegated(text: String, pattern: Regex): Boolean =
            pattern.findAll(text).any { !isNegated(text, it.range.first) }
        val medianHeight = lines.map { it.height }.filter { it > 0 }.sorted().let { if (it.isEmpty()) 0 else it[it.size / 2] }
        val sentences = reconstructSentences(lines)
        sentences.forEachIndexed { sentenceIndex, sentence ->
            val line = sentence.first()
            val text = sentenceText(sentence)
            val sourceSpanIds = sentence.mapIndexed { index, item -> "span-${item.page}-$sentenceIndex-$index" }
            if (text.isBlank()) return@forEachIndexed
            if (actionWords.containsMatchIn(text)) {
                // Layout affects ranking only; never whether a fact or risk signal is true.
                val emphasis = (if (line.allCaps) 0.15 else 0.0) + (if (medianHeight > 0 && line.height > medianHeight * 1.3) 0.25 else 0.0)
                actions += ActionItem(text, evidence(sentence), 1.0 + emphasis, sourceSpanIds)
            }
            monthDate.findAll(text).forEach { match ->
                    val month = Month.valueOf(match.groupValues[1].uppercase(Locale.US))
                    val day = match.groupValues[2].toInt()
                    val hasYear = match.groupValues[3].isNotBlank()
                    val year = match.groupValues[3].toIntOrNull() ?: referenceDate.year
                    runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let {
                        if (isDeadlineContext(text, match.range)) deadlines += Deadline(it, evidence(sentence), !hasYear, sourceSpanIds)
                        else issueDates += IssueDate(it, evidence(sentence), !hasYear, sourceSpanIds)
                    }
            }
            numericDate.findAll(text).forEach { match ->
                    runCatching { LocalDate.of(match.groupValues[3].toInt(), match.groupValues[1].toInt(), match.groupValues[2].toInt()) }.getOrNull()?.let {
                        if (isDeadlineContext(text, match.range)) deadlines += Deadline(it, evidence(sentence), true, sourceSpanIds)
                        else issueDates += IssueDate(it, evidence(sentence), true, sourceSpanIds)
                    }
            }
            isoDate.findAll(text).forEach { match ->
                runCatching {
                    LocalDate.of(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
                }.getOrNull()?.let {
                    if (isDeadlineContext(text, match.range)) deadlines += Deadline(it, evidence(sentence), false, sourceSpanIds)
                    else issueDates += IssueDate(it, evidence(sentence), false, sourceSpanIds)
                }
            }
            relativeDays.findAll(text).forEach { match ->
                val days = match.groupValues[1].toLongOrNull()
                if (days != null && days in 0..3650) deadlines += Deadline(
                    referenceDate.plusDays(days), evidence(sentence), true, sourceSpanIds,
                    isCalculatedFromReferenceDate = true
                )
            }
            rules.forEach { (label, pattern, weight) ->
                val match = pattern.find(text)
                if (match != null && !isNegated(text, match.range.first)) findings += Finding(label, evidence(sentence), weight, sourceSpanIds)
            }
            if (containsNonNegated(text, Regex("\\b(account|subscription|profile)\\b", RegexOption.IGNORE_CASE)) &&
                containsNonNegated(text, Regex("\\b(verify|reset|confirm|suspended|compromised|hacked|unauthorized)\\b", RegexOption.IGNORE_CASE))) {
                findings += Finding("Account verification or redirection", evidence(sentence), 15, sourceSpanIds)
            }
            if (containsNonNegated(text, Regex("\\b(click|open|check|follow)\\b", RegexOption.IGNORE_CASE)) &&
                containsNonNegated(text, Regex("\\b(here|link|attachment|file)\\b", RegexOption.IGNORE_CASE))) {
                findings += Finding("Suspicious link or attachment direction", evidence(sentence), 15, sourceSpanIds)
            }
            if (containsNonNegated(text, Regex("\\b(infected|malware|ransom|botnet|malicious software)\\b", RegexOption.IGNORE_CASE))) {
                findings += Finding("Malware or infection claim", evidence(sentence), 20, sourceSpanIds)
            }
            val payment = containsNonNegated(text, paymentPattern)
            val threat = containsNonNegated(text, threatPattern)
            if (payment && threat) findings += Finding("Payment request combined with threat", evidence(sentence), 20, sourceSpanIds)
        }
        sentences.zipWithNext().forEachIndexed { index, pair ->
            val firstText = sentenceText(pair.first)
            val secondText = sentenceText(pair.second)
            val paymentAndThreat = (containsNonNegated(firstText, paymentPattern) && containsNonNegated(secondText, threatPattern)) ||
                (containsNonNegated(secondText, paymentPattern) && containsNonNegated(firstText, threatPattern))
            if (paymentAndThreat) {
                val sourceSpanIds = pair.first.mapIndexed { lineOffset, item ->
                    "span-${item.page}-$index-$lineOffset"
                } + pair.second.mapIndexed { lineOffset, item ->
                    "span-${item.page}-${index + 1}-$lineOffset"
                }
                findings += Finding(
                    "Payment request near threat",
                    listOf(pair.first, pair.second).joinToString(" ") { evidence(it) },
                    20,
                    sourceSpanIds
                )
            }
        }
        if (learnedWarningModel != null) {
            val fullText = lines.joinToString(" ") { it.text }
            if (learnedWarningModel.predictsWarning(fullText)) {
                findings += Finding(
                    "Learned text signal; review the source",
                    lines.joinToString(" ") { "Page ${it.page}: ${it.text.trim()}" },
                    15,
                    lines.mapIndexed { index, item -> "span-${item.page}-model-$index" }
                )
            }
        }
        val unique = findings.distinctBy { it.label }
        val score = unique.sumOf { it.weight }.coerceAtMost(100)
        val sorted = actions.sortedByDescending { it.importance }.take(8)
        val fallbackSummary = when {
            sorted.isNotEmpty() -> "This document appears to request action. Review the extracted instructions and original text."
            deadlines.isNotEmpty() -> "This document mentions a possible deadline. Confirm the date against the original."
            else -> "No clear action was detected. Review the original document; this tool can miss important details."
        }
        val graph = EvidenceGraphBuilder.build(lines)
        val riskLevel = when {
            score >= 60 -> RiskLevel.HIGH
            score > 0 -> RiskLevel.UNCERTAIN
            else -> RiskLevel.NO_CLEAR_INDICATORS
        }
        val warnings = if (riskLevel == RiskLevel.NO_CLEAR_INDICATORS) {
            listOf("No clear indicators does not verify that this document is legitimate.")
        } else emptyList()
        val summaryItems = graph.evidence
            .filter { it.kind in setOf(EvidenceKind.REQUEST, EvidenceKind.DATE, EvidenceKind.CONSEQUENCE, EvidenceKind.AMOUNT) }
            .distinctBy { it.value to it.sourceSpanIds }
            .take(4)
            .map { SummaryItem(it.value, it.sourceSpanIds) }
        val decisionSummary = buildDecisionSummary(sorted, deadlines, issueDates, unique, riskLevel, graph.intents, warnings)
        val summary = decisionSummary.toCompactText().ifBlank { fallbackSummary }
        return Analysis(sorted, deadlines.distinctBy { it.date to it.evidence }, unique, score, summary, graph.intents, graph.evidence, graph.relations, riskLevel, warnings, summaryItems, issueDates.distinctBy { it.date to it.evidence }, decisionSummary)
    }

    private fun buildDecisionSummary(
        actions: List<ActionItem>, deadlines: List<Deadline>, issueDates: List<IssueDate>, findings: List<Finding>,
        riskLevel: RiskLevel, intents: Set<IntentKind>, warnings: List<String>
    ): DecisionSummary {
        val sourceIds = (actions.flatMap { it.sourceSpanIds } + deadlines.flatMap { it.sourceSpanIds } + issueDates.flatMap { it.sourceSpanIds } + findings.flatMap { it.sourceSpanIds }).distinct()
        val actionText = actions.joinToString(" ") { it.instruction }.replace(Regex("\\s+"), " ").trim()
        val appointment = if (IntentKind.APPOINTMENT_OR_ATTENDANCE in intents) {
            appointmentDetails(actionText, deadlines, issueDates)
        } else null
        val what = when {
            IntentKind.PAYMENT_REQUEST in intents && IntentKind.CREDENTIAL_REQUEST in intents -> "Payment and sensitive-information request."
            IntentKind.PAYMENT_REQUEST in intents -> "Payment request."
            IntentKind.CREDENTIAL_REQUEST in intents -> "Request for sensitive account information."
            IntentKind.APPOINTMENT_OR_ATTENDANCE in intents -> appointment?.what ?: "Meeting or appointment requiring attendance."
            IntentKind.ACTION_REQUIRED in intents -> "Action requested."
            else -> "Informational document."
        }
        val next = buildList {
            when {
                IntentKind.CREDENTIAL_REQUEST in intents -> add("Verify the request independently before sharing passwords or codes.")
                IntentKind.PAYMENT_REQUEST in intents -> add("Verify the recipient, amount, and payment method before sending money.")
                IntentKind.APPOINTMENT_OR_ATTENDANCE in intents -> add(appointment?.next ?: "Confirm the meeting details and bring requested materials.")
                actions.isNotEmpty() -> add("Review the requested action against the original before completing it.")
                deadlines.isNotEmpty() -> add("Confirm whether a response is required and by when.")
                else -> add("No clear action was detected; review the original for context.")
            }
        }
        val dates = (deadlines.map {
            "${it.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US))}${if (it.needsReview) " — confirm the date or year" else " — confirm against the original"}"
        } + issueDates.map {
            "${it.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US))} — mentioned date; confirm against the original"
        }).distinct().take(2)
        val why = buildList {
            if (findings.isNotEmpty()) add("Plainly found ${findings.size} possible warning indicator${if (findings.size == 1) "" else "s"}; these are review signals, not a fraud probability.")
            if (deadlines.any { it.needsReview }) add("At least one date needs review because it was incomplete, ambiguous, or calculated from relative wording.")
            if (riskLevel == RiskLevel.NO_CLEAR_INDICATORS) add("No matching warning indicators were found. That does not verify the sender or document.")
        }
        val verify = buildList {
            if (IntentKind.PAYMENT_REQUEST in intents) add("Verify the sender and payment instructions through a trusted channel you find independently.")
            if (IntentKind.CREDENTIAL_REQUEST in intents) add("Do not share passwords or verification codes until you independently confirm the request.")
            if (findings.any { it.label.contains("link", true) || it.label.contains("redirection", true) }) add("Avoid using links, attachments, or contact details supplied only by this document.")
            if (isEmpty()) add("Compare important names, dates, amounts, and instructions with the original document.")
        }
        val qualityNotes = buildList {
            if (deadlines.size > 1 && deadlines.map { it.date }.distinct().size > 1) {
                add("Multiple possible response dates were found; compare them with the original.")
            }
            if (actions.isNotEmpty() && deadlines.isEmpty()) {
                add("No response deadline was identified; do not infer one.")
            }
            if (IntentKind.PAYMENT_REQUEST in intents && IntentKind.CREDENTIAL_REQUEST in intents) {
                add("The document appears to request both money and sensitive information; verify the sender independently.")
            }
            if (sourceIds.isEmpty()) add("No actionable evidence was extracted from the readable text.")
        }
        val uncertainty = (warnings + qualityNotes).distinct()
        return DecisionSummary(what, next, dates, why, verify, uncertainty, sourceIds, qualityNotes)
    }

    private data class AppointmentDetails(val what: String, val next: String)

    private fun appointmentDetails(text: String, deadlines: List<Deadline>, issueDates: List<IssueDate>): AppointmentDetails {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        val event = when {
            Regex("\\bschool meeting\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "School meeting"
            Regex("\\b(parent[- ]teacher meeting|parent[- ]teacher conference)\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Parent-teacher meeting"
            else -> Regex("\\b(hearing|interview|appointment|conference|meeting)\\b", RegexOption.IGNORE_CASE)
                .find(normalized)?.value?.replaceFirstChar { it.uppercase(Locale.US) } ?: "Scheduled event"
        }
        val time = Regex("\\b\\d{1,2}(?::\\d{2})?\\s?(?:AM|PM)\\b", RegexOption.IGNORE_CASE).find(normalized)?.value
        val location = Regex("\\b(?:held|meet|meeting|appointment|hearing)\\s+(?:at|in)\\s+([^.!?;]+)", RegexOption.IGNORE_CASE)
            .find(normalized)?.groupValues?.getOrNull(1)?.trim()
            ?.takeUnless { Regex("^\\d{1,2}(?::\\d{2})?\\s?(?:AM|PM)$", RegexOption.IGNORE_CASE).matches(it) }
        val materials = Regex("\\bbring\\s+(?:the\\s+)?(.+?)(?=\\s+and\\s+(?:attend|go|arrive)\\b|[.!?;]|$)", RegexOption.IGNORE_CASE)
            .find(normalized)?.groupValues?.getOrNull(1)?.trim()
        val dateText = (deadlines.map { it.date } + issueDates.map { it.date }).distinct().firstOrNull()
            ?.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US))
        val schedule = listOfNotNull(dateText, time).joinToString(" at ")
        val what = buildString {
            append(event)
            if (schedule.isNotBlank()) append(" on ").append(schedule)
            if (location != null) append(" at ").append(location)
            append(".")
        }
        val next = buildString {
            append("Attend the ").append(event.lowercase(Locale.US))
            if (schedule.isNotBlank()) append(" ").append(schedule)
            if (location == null) append("; location was not stated")
            if (materials != null) append("; bring ").append(materials)
            append(". Confirm the details with the school or issuing office.")
        }
        return AppointmentDetails(what, next)
    }

    /** Short, synthesized text for history, speech, and local translation; never includes source sentences. */
    fun DecisionSummary.toCompactText(): String = buildString {
        append(whatItIs)
        nextSteps.firstOrNull()?.let { append(" ").append(it) }
        importantDates.firstOrNull()?.let {
            val dateOnly = it.substringBefore(" —")
            if (!contains(dateOnly)) append(" Date: ").append(it)
        }
    }.trim()

    data class GraphResult(val intents: Set<IntentKind>, val evidence: List<GraphEvidence>, val relations: List<EvidenceRelation>)

    /** Conservative sentence-level relationship extraction; this describes document claims, not real-world causation. */
    fun buildEvidenceGraph(lines: List<TextEvidence>): GraphResult {
        val evidence = mutableListOf<GraphEvidence>()
        val relations = mutableListOf<EvidenceRelation>()
        val intents = mutableSetOf<IntentKind>()
        reconstructSentences(lines).forEachIndexed { sentenceIndex, sentence ->
            val text = sentenceText(sentence)
            if (text.isBlank()) return@forEachIndexed
            val spans = sentence.mapIndexed { index, item -> "span-${item.page}-$sentenceIndex-$index" }
            val nodes = mutableListOf<GraphEvidence>()
            fun add(kind: EvidenceKind, value: String = text): GraphEvidence {
                val item = GraphEvidence("evidence-$sentenceIndex-${nodes.size}", kind, spans, value)
                nodes += item; evidence += item; return item
            }
            val request = if (actionWords.containsMatchIn(text) || Regex("\\b(pay|payment|fee|gift\\s*cards?|bitcoin|crypto)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) add(EvidenceKind.REQUEST) else null
            if (actionWords.containsMatchIn(text)) intents += IntentKind.ACTION_REQUIRED
            if (Regex("\\b(pay|payment|fee|gift\\s*cards?|bitcoin|crypto)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) intents += IntentKind.PAYMENT_REQUEST
            if (Regex("\\b(password|verification code|one.time code|PIN|social security)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                intents += IntentKind.CREDENTIAL_REQUEST
                val credential = add(EvidenceKind.REQUEST)
                request?.let { relations += EvidenceRelation(it.id, credential.id, RelationKind.REQUESTS, spans) }
            }
            if (Regex("\\b(appointment|attend|hearing|meeting|schedule|appear)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                intents += IntentKind.APPOINTMENT_OR_ATTENDANCE
            }
            if (Regex("\\b(school|agency|department|government|IRS|court|office)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) add(EvidenceKind.AUTHORITY_CLAIM)
            if (deadlineWords.containsMatchIn(text) || relativeDays.containsMatchIn(text)) {
                val date = add(EvidenceKind.DATE)
                request?.let { relations += EvidenceRelation(it.id, date.id, RelationKind.DUE_BY, spans) }
            }
            if (Regex("\\b(arrest|deportation|imprisonment|penalty|fined)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                val consequence = add(EvidenceKind.CONSEQUENCE)
                request?.let { relations += EvidenceRelation(it.id, consequence.id, RelationKind.THREATENS, spans) }
            }
            val amountPattern = Regex("(?:[$€£]\\s?\\d+(?:[,.]\\d{2})?|\\b\\d+(?:[,.]\\d{2})?\\s?(?:USD|dollars?|euros?|pounds?)\\b)", RegexOption.IGNORE_CASE)
            amountPattern.findAll(text).forEach { match ->
                val amount = add(EvidenceKind.AMOUNT, match.value)
                request?.let { relations += EvidenceRelation(it.id, amount.id, RelationKind.CONDITIONAL_ON, spans) }
            }
            val destinationPattern = Regex("(?:https?://\\S+|www\\.\\S+|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})", RegexOption.IGNORE_CASE)
            destinationPattern.findAll(text).forEach { match ->
                val destination = add(EvidenceKind.DESTINATION, match.value.trimEnd('.', ',', ';'))
                request?.let { relations += EvidenceRelation(it.id, destination.id, RelationKind.DIRECTS_TO, spans) }
            }
        }
        if (intents.isEmpty()) intents += IntentKind.INFORMATIONAL
        return GraphResult(intents, evidence, relations)
    }

    fun readingOrder(lines: List<TextEvidence>): List<TextEvidence> = lines.groupBy { it.page }.toSortedMap().flatMap { (_, pageLines) ->
        val centers = pageLines.mapNotNull { it.box?.let { box -> box.left + box.right } }
        val midpoint = if (centers.isEmpty()) null else (centers.minOrNull()!! + centers.maxOrNull()!!) / 2
        val positioned = pageLines.filter { it.box != null }
        val unpositioned = pageLines.filter { it.box == null }
        val columns = if (midpoint == null) emptyMap() else positioned.groupBy { if (it.box!!.left + it.box.right < midpoint) 0 else 1 }
        val positionedOrder = if (columns.size <= 1) positioned.sortedBy { it.box?.top ?: 0 }
        else columns.toSortedMap().values.flatMap { it.sortedBy { line -> line.box?.top ?: 0 } }
        positionedOrder + unpositioned
    }

    fun reconstructSentences(lines: List<TextEvidence>): List<List<TextEvidence>> {
        val result = mutableListOf<MutableList<TextEvidence>>()
        readingOrder(lines).forEach { line ->
            val current = result.lastOrNull()
            if (current == null || current.last().page != line.page || current.last().text.trimEnd().lastOrNull() in setOf('.', '!', '?', ':')) result += mutableListOf(line)
            else current += line
        }
        return result
    }

    fun sentenceText(sentence: List<TextEvidence>): String = sentence.joinToString(" ") { it.text.trim() }.replace(Regex("\\s+"), " ").trim()

    private fun evidence(sentence: List<TextEvidence>): String = sentence.joinToString(" ") { "Page ${it.page}: ${it.text.trim()}" }

    private fun isNegated(text: String, matchStart: Int): Boolean {
        val prefix = text.substring(0, matchStart).takeLast(40).lowercase(Locale.US)
        return Regex("\\b(no|not|never|without|do not|does not|don't|doesn't)\\b").containsMatchIn(prefix)
    }

    private fun isDeadlineContext(text: String, range: IntRange): Boolean {
        val start = text.substring(0, range.first).lastIndexOfAny(charArrayOf('.', '!', '?')).let { if (it < 0) 0 else it + 1 }
        val end = text.indexOfAny(charArrayOf('.', '!', '?'), range.last + 1).let { if (it < 0) text.length else it }
        return deadlineWords.containsMatchIn(text.substring(start, end))
    }
}
