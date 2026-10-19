package org.plainly.app.model

import org.plainly.app.analysis.BoundingBox
import org.plainly.app.analysis.TextEvidence

enum class SourceType { PDF, IMAGE, CAMERA, UNKNOWN }
enum class SpanOrigin { PDF_TEXT, OCR }
enum class SpanStyle { UNKNOWN }

data class SourceSpan(
    val id: String,
    val pageIndex: Int,
    val text: String,
    val boundingBox: BoundingBox?,
    val readingOrder: Int,
    val origin: SpanOrigin,
    val extractionConfidence: Float? = null,
    val style: SpanStyle? = null
)

data class ParsedDocument(
    val id: String,
    val sourceType: SourceType,
    val spans: List<SourceSpan>,
    val languageTag: String? = null,
    val warnings: List<String> = emptyList()
)

fun List<TextEvidence>.toParsedDocument(id: String, sourceType: SourceType, warnings: List<String> = emptyList()): ParsedDocument =
    ParsedDocument(
        id = id,
        sourceType = sourceType,
        spans = mapIndexed { index, item ->
            SourceSpan("span-${item.page}-$index", item.page, item.text, item.box, index, SpanOrigin.OCR)
        },
        warnings = warnings
    )
