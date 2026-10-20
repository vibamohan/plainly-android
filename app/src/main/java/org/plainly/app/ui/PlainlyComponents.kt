@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.plainly.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import org.plainly.app.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.BorderStroke
import com.google.mlkit.nl.translate.TranslateLanguage
import org.plainly.app.analysis.ActionItem
import org.plainly.app.analysis.Deadline
import org.plainly.app.analysis.DecisionSummary
import org.plainly.app.analysis.RiskLevel

private val PlainlyLavender = Color(0xFF6558D3)
private val PlainlyMint = Color(0xFF2D8B83)
private val PlainlyCoral = Color(0xFFE76F51)

@Composable
fun PlainlyTheme(content: @Composable () -> Unit) {
    val colors = if (androidx.compose.foundation.isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFFBDB5FF),
            secondary = Color(0xFF9DDBD1),
            tertiary = Color(0xFFFFB59F),
            onPrimary = Color(0xFF2D2368),
            primaryContainer = Color(0xFF443A82), onPrimaryContainer = Color(0xFFEAE7FF),
            onSecondary = Color(0xFF003732),
            secondaryContainer = Color(0xFF20504A), onSecondaryContainer = Color(0xFFDDF4EF),
            onTertiary = Color(0xFF542414),
            tertiaryContainer = Color(0xFF713A28), onTertiaryContainer = Color(0xFFFFE5DD),
            background = Color(0xFF141218), onBackground = Color(0xFFE7E0EA),
            surface = Color(0xFF141218), onSurface = Color(0xFFE7E0EA),
            surfaceVariant = Color(0xFF49454F), onSurfaceVariant = Color(0xFFCAC4D0)
        )
    } else {
        lightColorScheme(
            primary = PlainlyLavender,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEAE7FF),
            onPrimaryContainer = Color(0xFF211A54),
            secondary = PlainlyMint,
            secondaryContainer = Color(0xFFDDF4EF),
            onSecondaryContainer = Color(0xFF073B36),
            tertiary = PlainlyCoral,
            tertiaryContainer = Color(0xFFFFE5DD),
            onTertiaryContainer = Color(0xFF4D1E13),
            background = Color(0xFFFAF9FE),
            surface = Color(0xFFFFFBFF)
        )
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = Shapes(
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
        ),
        content = { Surface(color = colors.background, content = content) }
    )
}

@Composable
fun AppHeader(onInfo: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Text("P", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
                }
            }
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.understand), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = onInfo) { Text(stringResource(R.string.about)) }
    }
}

@Composable
fun StatusChip(text: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    )
}

@Composable
fun ModernSectionHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun SpeechControls(enabled: Boolean, speaking: Boolean, paused: Boolean, onSpeak: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onReplay: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.read_aloud), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSpeak, enabled = enabled && !speaking) { Text(stringResource(R.string.read_summary)) }
            OutlinedButton(onClick = onPause, enabled = enabled && speaking) { Text(stringResource(R.string.pause)) }
            OutlinedButton(onClick = onResume, enabled = enabled && paused && !speaking) { Text(stringResource(R.string.resume)) }
            OutlinedButton(onClick = onReplay, enabled = enabled && !speaking) { Text(stringResource(R.string.replay)) }
        }
        if (!enabled) Text(stringResource(R.string.speech_unavailable), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ReviewWarning() = Card {
    Text(stringResource(R.string.review_warning), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
}

@Composable
fun DecisionSummaryCard(summary: DecisionSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Text("✦", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(8.dp).clearAndSetSemantics {})
                }
                Column {
                    Text(stringResource(R.string.plainly_take), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.useful_version), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f))
                }
            }
            SummaryBlock(stringResource(R.string.what_is), summary.whatItIs)
            summary.nextSteps.firstOrNull()?.let { SummaryBlock(stringResource(R.string.next_move), it) }
            summary.importantDates.firstOrNull()?.let { SummaryBlock(stringResource(R.string.important_date), it) }
            summary.uncertainty.firstOrNull()?.let {
                Text(stringResource(R.string.note, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun SummaryBlock(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun FraudWhyCard(level: RiskLevel, findings: List<org.plainly.app.analysis.Finding>) {
    val (title, why, color) = when (level) {
        RiskLevel.HIGH -> Triple(stringResource(R.string.high_warning), findings.take(2).joinToString("; ") { it.label }.ifBlank { "Several document patterns need independent review." }, MaterialTheme.colorScheme.errorContainer)
        RiskLevel.UNCERTAIN -> Triple(stringResource(R.string.possible_warning), findings.take(2).joinToString("; ") { it.label }.ifBlank { "A document pattern needs independent review." }, MaterialTheme.colorScheme.tertiaryContainer)
        RiskLevel.NO_CLEAR_INDICATORS -> Triple(stringResource(R.string.no_clear_warning), stringResource(R.string.no_verification), MaterialTheme.colorScheme.secondaryContainer)
    }
    Card(colors = CardDefaults.cardColors(containerColor = color), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (level == RiskLevel.HIGH) "!" else if (level == RiskLevel.UNCERTAIN) "?" else "✓", modifier = Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge)
                Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            }
            Text(why, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.heuristic_notice), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ResultSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}

@Composable
fun ActionCard(action: ActionItem) = EvidenceCard(
    "${action.instruction}\nReview label: ${confidenceLabel(action.importance)}",
    "${action.evidence}\nSource spans: ${action.sourceSpanIds.joinToString()}\nThis is a layout-assisted review priority, not a confidence measurement or factual certainty."
)

@Composable
fun DeadlineCard(deadline: Deadline, onCalendar: (Deadline) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val dateLabel = if (deadline.isCalculatedFromReferenceDate) {
            "Calculated from the analysis date; not stated verbatim"
        } else if (deadline.needsReview) {
            "Review required: confirm the date/year"
        } else {
            "Date detected; confirm against the original"
        }
        EvidenceCard("${deadline.date}\n$dateLabel", "${deadline.reviewReason?.let { "$it\n" }.orEmpty()}${deadline.evidence}\nSource spans: ${deadline.sourceSpanIds.joinToString()}")
        OutlinedButton(onClick = { onCalendar(deadline) }) { Text(stringResource(R.string.confirm_add)) }
    }
}

@Composable
fun EvidenceCard(title: String, evidence: String) {
    var expanded by remember { mutableStateOf(false) }
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title)
        OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) stringResource(R.string.hide_evidence) else stringResource(R.string.show_evidence)) }
        if (expanded) Text(evidence, style = MaterialTheme.typography.bodySmall)
    } }
}

@Composable
fun TranslationComparison(original: String, translated: String, targetLabel: String) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.original_and, targetLabel), style = MaterialTheme.typography.titleMedium)
            BoxWithConstraints {
                if (maxWidth < 600.dp || LocalDensity.current.fontScale > 1.3f) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.original), style = MaterialTheme.typography.labelLarge)
                        Text(original)
                        Text(targetLabel, style = MaterialTheme.typography.labelLarge)
                        Text(translated)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) { Text(stringResource(R.string.original)); Text(original) }
                        Column(Modifier.weight(1f)) { Text(targetLabel); Text(translated) }
                    }
                }
            }
        }
    }
}

@Composable
fun SourcePageCard(page: Int, lines: List<String>) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.page_number, page), style = MaterialTheme.typography.titleSmall)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

fun confidenceLabel(importance: Double) = when {
    importance >= 1.3 -> "higher review priority"
    importance > 1.0 -> "review priority"
    else -> "standard review"
}

val supportedLanguages = listOf(
    TranslateLanguage.ENGLISH to "English",
    TranslateLanguage.SPANISH to "Español", TranslateLanguage.FRENCH to "Français",
    TranslateLanguage.GERMAN to "Deutsch", TranslateLanguage.PORTUGUESE to "Português",
    TranslateLanguage.CHINESE to "中文", TranslateLanguage.HINDI to "हिन्दी",
    TranslateLanguage.TAMIL to "தமிழ்", TranslateLanguage.TELUGU to "తెలుగు",
    TranslateLanguage.KANNADA to "ಕನ್ನಡ", TranslateLanguage.VIETNAMESE to "Tiếng Việt",
    TranslateLanguage.KOREAN to "한국어", TranslateLanguage.RUSSIAN to "Русский",
    TranslateLanguage.TAGALOG to "Tagalog"
)

fun languageLabel(code: String): String = supportedLanguages.firstOrNull { it.first == code }?.second ?: code
