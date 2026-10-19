package org.plainly.app.ingestion

import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.tasks.await
import org.plainly.app.analysis.TextEvidence

data class LanguageResult(val languageTag: String?, val confidence: Float?, val warning: String? = null)

object OnDeviceLanguageIdentifier {
    suspend fun identify(lines: List<TextEvidence>): LanguageResult {
        val sample = lines.joinToString(" ") { it.text }.trim().take(4000)
        if (sample.isBlank()) return LanguageResult(null, null, "Language could not be identified from blank text.")
        val identifier = LanguageIdentification.getClient()
        return try {
            val candidates = identifier.identifyPossibleLanguages(sample).await()
            val best = candidates.maxByOrNull { it.confidence }
            if (best == null || best.confidence < 0.5f) {
                LanguageResult(null, best?.confidence, "Language identification confidence is low; review the original.")
            } else LanguageResult(best.languageTag, best.confidence)
        } catch (e: Exception) {
            LanguageResult(null, null, "Language identification unavailable: ${e.message ?: "unknown error"}")
        } finally { identifier.close() }
    }
}
