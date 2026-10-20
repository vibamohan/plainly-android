package org.plainly.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Text("P", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
                }
            }
            Column {
                Text("Plainly", style = MaterialTheme.typography.headlineSmall)
                Text("Understand before you act", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(onClick = onInfo) { Text("About") }
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
        Text("Read aloud", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSpeak, enabled = enabled && !speaking) { Text("Read summary") }
            OutlinedButton(onClick = onPause, enabled = enabled && speaking) { Text("Pause") }
            OutlinedButton(onClick = onResume, enabled = enabled && paused && !speaking) { Text("Resume") }
            OutlinedButton(onClick = onReplay, enabled = enabled && !speaking) { Text("Replay") }
        }
        if (!enabled) Text("Text-to-speech is unavailable on this device.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ReviewWarning() = Card {
    Text("Always check the original document. OCR and rule-based analysis can miss, misread, or misinterpret important details.", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
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
                    Text("✦", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(8.dp))
                }
                Column {
                    Text("Plainly's take", style = MaterialTheme.typography.titleLarge)
                    Text("The useful version, not a copy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f))
                }
            }
            SummaryBlock("What this is", summary.whatItIs)
            summary.nextSteps.firstOrNull()?.let { SummaryBlock("Your next move", it) }
            summary.importantDates.firstOrNull()?.let { SummaryBlock("Important date", it) }
            summary.uncertainty.firstOrNull()?.let {
                Text("Note · $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
        RiskLevel.HIGH -> Triple("Fraud warning signals found", findings.take(2).joinToString("; ") { it.label }.ifBlank { "Several document patterns need independent review." }, MaterialTheme.colorScheme.errorContainer)
        RiskLevel.UNCERTAIN -> Triple("Possible warning signal", findings.take(2).joinToString("; ") { it.label }.ifBlank { "A document pattern needs independent review." }, MaterialTheme.colorScheme.tertiaryContainer)
        RiskLevel.NO_CLEAR_INDICATORS -> Triple("No clear fraud warning signals", "No matching warning patterns were found. This does not verify the sender or document.", MaterialTheme.colorScheme.secondaryContainer)
    }
    Card(colors = CardDefaults.cardColors(containerColor = color), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (level == RiskLevel.HIGH) "!" else if (level == RiskLevel.UNCERTAIN) "?" else "✓", style = MaterialTheme.typography.titleLarge)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(why, style = MaterialTheme.typography.bodyMedium)
            Text("Heuristic review only — not a fraud probability or authenticity check.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun RiskBanner(level: RiskLevel, score: Int) {
    val (title, color) = when (level) {
        RiskLevel.HIGH -> "Several caution signals found" to MaterialTheme.colorScheme.errorContainer
        RiskLevel.UNCERTAIN -> "Review before acting" to MaterialTheme.colorScheme.tertiaryContainer
        RiskLevel.NO_CLEAR_INDICATORS -> "No clear caution signals found" to MaterialTheme.colorScheme.secondaryContainer
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(if (level == RiskLevel.NO_CLEAR_INDICATORS) "✓" else "!", style = MaterialTheme.typography.titleMedium)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text("Heuristic review level: ${level.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }} · signal total $score/100")
            Text("This is not a fraud probability, authenticity check, or safety guarantee.", style = MaterialTheme.typography.bodySmall)
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
        OutlinedButton(onClick = { onCalendar(deadline) }) { Text("Confirm and add to calendar") }
    }
}

@Composable
fun EvidenceCard(title: String, evidence: String) {
    var expanded by remember { mutableStateOf(false) }
    Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title)
        OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide evidence" else "Show evidence") }
        if (expanded) Text(evidence, style = MaterialTheme.typography.bodySmall)
    } }
}

@Composable
fun TranslationComparison(original: String, translated: String, targetLabel: String) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Original and $targetLabel", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Original", style = MaterialTheme.typography.labelLarge)
                    Text(original, style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(targetLabel, style = MaterialTheme.typography.labelLarge)
                    Text(translated, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun SourcePageCard(page: Int, lines: List<String>) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Page $page", style = MaterialTheme.typography.titleSmall)
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
    TranslateLanguage.SPANISH to "Spanish", TranslateLanguage.FRENCH to "French",
    TranslateLanguage.GERMAN to "German", TranslateLanguage.PORTUGUESE to "Portuguese"
)

fun languageLabel(code: String): String = supportedLanguages.firstOrNull { it.first == code }?.second ?: code
