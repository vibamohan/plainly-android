package org.plainly.app.reasoning

import org.plainly.app.analysis.DocumentAnalyzer
import org.plainly.app.analysis.TextEvidence

/** Reasoning boundary. Relations remain document claims inferred from source text, not causal proof. */
object EvidenceGraphBuilder {
    fun build(lines: List<TextEvidence>): DocumentAnalyzer.GraphResult =
        DocumentAnalyzer.buildEvidenceGraph(lines)
}
