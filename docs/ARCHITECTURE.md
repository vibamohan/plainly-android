# Architecture

Plainly keeps document analysis in pure Kotlin and Android integration at the edges.

```text
SAF/camera URI
  -> OnDeviceDocumentIngestion → DigitalPdfTextExtractor or OcrEngine (Android)
  -> TextEvidence: page, text, boxes, words
  -> DocumentAnalyzer (pure Kotlin)
  -> Analysis: actions, deadlines, findings, intents, evidence graph, risk state
  -> PlainlyViewModel StateFlow (Android orchestration)
  -> Compose UI / TTS / translation / Room history / calendar intent (Android)
```

`DocumentAnalyzer` is deterministic and does not access Android APIs, network services, or files. Its rules are covered by JVM unit tests. `PlainlyViewModel` owns asynchronous OCR, analysis, translation, and UI state through `StateFlow`; `MainActivity` only hosts `ui.PlainlyScreen`. OCR, translation, TTS, Room, calendar, and camera behavior require Android verification.

Evidence IDs are deterministic within an analysis and point to page-derived source span IDs. PDFBox line boxes are approximate page coordinates; OCR boxes come from ML Kit. `RULE_INFERRED` provenance means the app inferred a relationship from nearby text; it does not mean the relationship is true in the outside world.
