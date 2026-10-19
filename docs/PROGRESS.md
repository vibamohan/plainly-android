# Plainly progress

Updated 2026-10-12.

## Implemented

- SAF import for images and PDFs without broad storage permission.
- The file chooser accepts any provider-backed file; supported PDFs, images, and text are processed locally, while unsupported content returns an actionable error.
- Common text-like extensions (`.txt`, `.md`, `.csv`, `.json`, `.xml`, and HTML) are routed through the local text extractor instead of being sent to image OCR.
- Local `text/plain` import for synthetic fixtures and accessible text documents, with page-one source mapping.
- Bounded one-page-at-a-time PDF rendering and bundled Latin-script ML Kit OCR without a hard page-count truncation.
- Page-linked OCR lines, word boxes, geometry-based reading order, and evidence snippets.
- Conservative action/deadline extraction, relative-date review flags, warning rules, negation handling, and a 20-example synthetic baseline evaluation.
- Payment-plus-threat analysis also checks adjacent reconstructed sentences and retains both source-span groups; it remains a heuristic signal, not a fraud probability.
- Combined payment/threat rules reuse negation handling, so disclaimer language is not promoted to a warning combination.
- Warning rules also cover explicit account-verification redirection, suspicious link/attachment directions, and malware/infection claims, each with source evidence and negation-aware tests.
- Relative deadlines now expose that their displayed date is calculated from the analysis reference date rather than stated verbatim in the document.
- Date extraction also recognizes ISO `YYYY-MM-DD` dates as unambiguous while retaining review flags for numeric and missing-year formats.
- Date results distinguish ordinary issue/document dates from response deadlines and flag ambiguous issue dates for review.
- Source-linked intent/evidence nodes and document-claim relationships. These describe text claims, not real-world causation.
- Evidence graph now extracts money amounts and URL/email destinations and links them to nearby requests with explicit inferred relations.
- Warning findings now carry deterministic source-span IDs in addition to readable evidence text.
- Actions and response deadlines now carry deterministic source-span IDs in their result cards.
- Issue/document dates also carry deterministic source-span IDs for review traceability.
- Compose results cards, expandable evidence, camera preview/retake, EXIF rotation, temporary-file cleanup, TTS, Room metadata history, summary export, and calendar-editor handoff.
- Camera preview now exposes bounded edge cropping (0–20%) in addition to manual 90-degree rotation.
- Calendar handoff now requires an explicit date-confirmation dialog, including for dates marked for review, before opening a calendar editor.
- Local history supports per-entry deletion and confirmation-protected clear-all deletion without touching original provider files.
- Results include an explicit action to clear the current in-memory document and analysis without affecting optional history metadata.
- Blank or unreadable imports now show an actionable extraction error instead of silently producing empty analysis.
- Users can explicitly export the current analysis, including source references and heuristic warnings, through Android's share sheet.
- `PlainlyViewModel` now owns OCR/analysis/translation state and asynchronous orchestration; `MainActivity` hosts the UI entry point.
- On-device translation target selection for English, Spanish, French, German, and Portuguese. The detected source language is used when ML Kit supports it; models may download on first use.
- Translation UI discloses possible first-use model download before the user starts translation.
- The manifest permits Internet access for optional ML Kit translation-model acquisition only; document analysis remains local and offline after required models are available.

## Verification

- `./gradlew testDebugUnitTest` passes with 44 tests, and `./gradlew connectedDebugAndroidTest` includes 8 passing tests on the available emulator: activity launch, manifest permission, provider-backed text/JSON ingestion, ViewModel-to-analysis flow, bundled warning-model loading, camera preprocessing, and Room metadata history (2026-10-14).
- `./scripts/verify.sh` now runs both automated checks and connected instrumentation when an authorized Android target is available.
- Offline smoke check: with emulator Wi-Fi and mobile data disabled, `adb shell am start -n org.plainly.app/.MainActivity` launched successfully without a fatal app exception. This verifies startup only; translation model reuse and document-processing airplane-mode behavior still require feature-level interaction testing.
- Camera validation: the emulator opened the external camera intent and returned to Plainly without a crash. Automated shutter input did not yield a saved capture, so end-to-end camera-to-OCR remains a manual device check.
- `./gradlew assembleDebug` passes.
- `./gradlew lintDebug` passes.
- `./scripts/run-device-smoke-test.sh` installed the debug APK and launched `org.plainly.app` through an authorized Android target on 2026-10-12. Manual feature-by-feature interaction testing remains to be completed.

## Known gaps

- Digital PDF text extraction now uses PDFBox when selectable text is available, with approximate line bounding boxes and OCR fallback for image-only/scanned PDFs. PDF coordinate conversion is heuristic and should be validated on device.
- ML Kit on-device language identification now runs after extraction, exposes the detected tag in results, and falls back with a warning when confidence is low or identification is unavailable.
- Translation UI now offers both explanation translation and full extracted-original-text translation while retaining the original text on screen.
- Full-text translation now renders an explicit original/translated comparison panel.
- Extracted source text is grouped into page-labeled preview cards for easier review against the original.
- Non-Latin OCR and offline-after-download translation verification require additional verified model dependencies and feature-level device testing.
- No trained classifier or LiteRT model is included.
- No accuracy claim is made from the synthetic baseline.
- An external 621-message benchmark is recorded in `docs/EVALUATION.md`; after warning-coverage expansion, its documented result is 1.000 precision / 0.518 recall / 0.651 accuracy, explicitly limited to warning-indicator detection on English message text and not a general app-accuracy claim.
- Competition submission and video require human action.
- Synthetic demo fixtures and a truthful manual demo script are available under `samples/` and `docs/DEMO_SCRIPT.md`.
