package org.plainly.app.analysis

import java.io.BufferedReader
import kotlin.math.ln

/**
 * Small, offline word/bigram Naive Bayes model. Its output is a review signal,
 * never a probability or authenticity decision.
 */
class LearnedWarningModel private constructor(
    private val positiveCounts: Map<String, Int>,
    private val negativeCounts: Map<String, Int>,
    private val positiveTotal: Int,
    private val negativeTotal: Int,
    private val positiveDocuments: Int,
    private val negativeDocuments: Int,
    private val vocabularySize: Int,
    private val alpha: Double = 2.0
) {
    fun predictsWarning(text: String): Boolean {
        val features = features(text)
        val positive = logPrior(positiveDocuments, negativeDocuments) + logLikelihood(features, positiveCounts, positiveTotal)
        val negative = logPrior(negativeDocuments, positiveDocuments) + logLikelihood(features, negativeCounts, negativeTotal)
        return positive > negative
    }

    private fun logPrior(documents: Int, otherDocuments: Int): Double =
        ln(documents.toDouble() / (documents + otherDocuments))

    private fun logLikelihood(features: List<String>, counts: Map<String, Int>, total: Int): Double {
        val denominator = total + alpha * vocabularySize
        return features.groupingBy { it }.eachCount().entries.sumOf { (feature, count) ->
            count * ln((counts[feature].orZero() + alpha) / denominator)
        }
    }

    companion object {
        fun fromTsv(reader: BufferedReader): LearnedWarningModel {
            val positive = mutableMapOf<String, Int>()
            val negative = mutableMapOf<String, Int>()
            var positiveTotal = 0
            var negativeTotal = 0
            var positiveDocuments = 0
            var negativeDocuments = 0
            reader.forEachLine { line ->
                val record = line.trim()
                when {
                    record.startsWith("# positive_docs=") -> positiveDocuments = record.substringAfter('=').toInt()
                    record.startsWith("# negative_docs=") -> negativeDocuments = record.substringAfter('=').toInt()
                    record.startsWith("# positive_features=") -> positiveTotal = record.substringAfter('=').toInt()
                    record.startsWith("# negative_features=") -> negativeTotal = record.substringAfter('=').toInt()
                    record.startsWith("#") || record.isBlank() -> Unit
                    else -> record.split('\t').takeIf { it.size == 3 }?.let { fields ->
                        positive[fields[0]] = fields[1].toInt()
                        negative[fields[0]] = fields[2].toInt()
                    }
                }
            }
            require(positiveDocuments > 0 && negativeDocuments > 0 && positiveTotal > 0 && negativeTotal > 0) {
                "Invalid learned warning model metadata"
            }
            return LearnedWarningModel(
                positive, negative, positiveTotal, negativeTotal,
                positiveDocuments, negativeDocuments, (positive.keys + negative.keys).toSet().size, 2.0
            )
        }

        private fun features(text: String): List<String> {
            val words = Regex("[a-z0-9]+").findAll(text.lowercase()).map { it.value }.toList()
            return words + words.zipWithNext().map { (left, right) -> "__bigram__${left}_$right" }
        }

        private fun Int?.orZero(): Int = this ?: 0
    }
}
