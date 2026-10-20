# AI assistance disclosure log

## Modern Compose UI milestone — 2026-10-18

An AI assistant refreshed the Compose presentation with a branded header, document-start hero card, privacy/status chips, stronger section hierarchy, and more prominent risk-summary styling while preserving the existing Android-only flows and Material 3 dependency set. Unit tests, lint, and the debug build passed; physical-device visual and accessibility review remains required.

## Warning and summary tuning milestone — 2026-10-17

An AI assistant added conservative warning indicators for personal-information requests, bank or wire transfers, remote access/software installation, and additional urgency language. It also changed summary next steps to provide protective guidance before showing source instructions, added risk explanations for the new signals, and added regression tests for negation and summary behavior. A local translation same-language regression test was added; real translated output still requires an Android device/model-download test and human review.

## Decision-summary and local-translation presentation milestone — 2026-10-17

An AI assistant added a deterministic, source-linked decision summary with next steps, important dates, verification guidance, uncertainty notes, and clearer heuristic risk explanations. The Compose results screen now prioritizes these cards visually and keeps original evidence expandable. Existing ML Kit local translation was retained for the explanation and extracted original text; no cloud document-analysis service was added. Unit tests were added for payment, credential, and no-clear-indicator summaries. The student must review wording, test on device, and validate local translation model availability and accessibility.

## Initial scaffold — 2026-10-11
An AI assistant generated the initial Android Kotlin/Compose repository, ML Kit integration scaffold, deterministic analysis logic, and starter tests. The student must inspect, modify, test, and accurately disclose their own contributions. No claim of independent student authorship is made for this scaffold.

## Future sessions
Record date, assistant, prompt summary, files changed, student changes, tests, and decisions.

## Feature roadmap milestone — 2026-10-11
An AI assistant implemented relative deadline detection and tests, negation-aware and payment/threat warning rules and tests, card-based results evidence, on-device TextToSpeech controls, camera EXIF rotation and edge cropping, Room-backed local history with deletion and summary export, and user-confirmed calendar-editor export. Build and device verification remain required.

## Translation languages milestone — 2026-10-11
An AI assistant extended the existing on-device translation flow with user-selectable English, Spanish, French, German, and Portuguese target languages. Models may download through ML Kit on first use; OCR remains the bundled Latin-script recognizer, with language selection limited to tested Latin-script documents.

## Verification milestone — 2026-10-11
An AI assistant fixed a missing-bounding-box crash in reading-order reconstruction and added focused unit coverage for cryptocurrency, threat, credential, numeric-date ambiguity, and missing-year deadline rules. `testDebugUnitTest`, `assembleDebug`, and `lintDebug` passed in the local Gradle environment; physical-device smoke testing remains separate.

## Privacy hardening milestone — 2026-10-11
An AI assistant updated camera processing to delete temporary captured and normalized image files after OCR completes. Unit tests and Android lint passed after this change.

## Camera workflow milestone — 2026-10-11
An AI assistant added a captured-image preview with explicit Use photo and Retake actions before OCR. The implementation was compiled and verified with unit tests and Android lint.

## Evaluation milestone — 2026-10-11
An AI assistant added a 20-example synthetic/redacted warning evaluation with precision and recall thresholds for the deterministic baseline. It is a development evaluation, not evidence that the app verifies fraud or authenticity.

## Architecture refactor milestone — 2026-10-12
An AI assistant introduced `ui.PlainlyScreen` and `PlainlyViewModel`, moving OCR, analysis, translation, loading/error state, and temporary-file cleanup behind a `StateFlow` boundary while preserving the existing behavior. Unit tests and debug APK build passed.

## PDF ingestion milestone — 2026-10-12
An AI assistant added a PDFBox-based selectable-text extraction path with fallback to the existing bounded scanned-page OCR path, plus normalization tests. The implementation does not claim digital PDF text-position boxes where the extractor does not provide them.

## PDF position milestone — 2026-10-12
An AI assistant extended the selectable-PDF path with approximate line bounding boxes from PDFBox `TextPosition` data. Coordinate accuracy remains a device/document-layout validation item.

## Language identification milestone — 2026-10-12
An AI assistant added ML Kit on-device language identification with a low-confidence fallback warning and UI display. This does not claim non-Latin OCR support; the bundled OCR model remains Latin-script only.

## Full-text translation milestone — 2026-10-12
An AI assistant added an explicit full extracted-text translation action alongside summary/explanation translation. Original extracted text remains visible and translation model acquisition remains optional.

## Bidirectional translation milestone — 2026-10-12
An AI assistant changed translation to use the detected source language when ML Kit supports it and added English as a selectable target, enabling the intended English↔Spanish path without assuming every document is English.

## Amount and destination milestone — 2026-10-12
An AI assistant added source-linked amount and URL/email destination nodes and `DIRECTS_TO`/`CONDITIONAL_ON` relations to the evidence graph, with regression coverage.

## Camera rotation milestone — 2026-10-12
An AI assistant added explicit 90-degree left/right rotation controls to the camera preview and passed the selected rotation through local preprocessing before OCR. Verification was rerun with `scripts/verify.sh`.

## Import persistence and smoke milestone — 2026-10-12
An AI assistant added best-effort read-only persistable URI permissions for system-picker imports without copying document bytes, corrected contradictory privacy documentation, and ran the device smoke script. The debug APK installed and launched successfully through `adb`; individual camera, OCR, translation, TTS, and calendar interactions still require manual exercising.

## Plain-text fixture ingestion milestone — 2026-10-12
An AI assistant added local `text/plain` ingestion for synthetic/demo documents, preserving page-one source spans and adding a unit test for blank-line handling. This complements, rather than replaces, PDF and image OCR ingestion.

## OCR language selection milestone — 2026-10-12
An AI assistant added an explicit English/Spanish/French/German/Portuguese OCR selector and passed the choice through the ingestion boundary. The choices intentionally share the bundled tested Latin model; no unsupported non-Latin model support is claimed.

## History deletion milestone — 2026-10-12
An AI assistant added a confirmation-protected clear-all control for Room history metadata and documented that it does not delete original user files.

## Translation comparison milestone — 2026-10-12
An AI assistant added a reusable Compose comparison panel that keeps the page-labeled original extracted text visible beside its on-device translation.

## Android instrumentation milestone — 2026-10-12
An AI assistant added a minimal `MainActivity` launch test, fixed the missing AndroidX test runner dependency discovered by the first connected run, and verified `connectedDebugAndroidTest` with one passing test on the available emulator.

## Page preview milestone — 2026-10-12
An AI assistant grouped extracted lines into page-labeled Compose cards while retaining the complete original text for review and translation.

## Camera crop milestone — 2026-10-12
An AI assistant added a bounded 0–20% edge-crop slider to the camera preview and passed the selected value through local image preprocessing before OCR, alongside the existing rotation control.

## TTS pause milestone — 2026-10-12
An AI assistant added range tracking and pause/resume controls for Android text-to-speech while retaining full replay behavior. The implementation remains dependent on the device TTS engine exposing range callbacks.

## Date-role milestone — 2026-10-12
An AI assistant added a separate issue/document-date result alongside response deadlines, with conservative review flags for missing-year and numeric dates, plus a pure Kotlin regression test.

## Date-context regression milestone — 2026-10-12
An AI assistant narrowed date-role detection to the punctuation-delimited clause containing each date and added a mixed issue-date/response-deadline regression test to prevent nearby dates from being mislabeled.

## Current-document privacy milestone — 2026-10-12
An AI assistant added a UI action that clears the current extracted text, analysis, translations, and language state while leaving optional Room metadata history under its separate deletion controls.

## Verification script milestone — 2026-10-12
An AI assistant updated `scripts/verify.sh` to run connected Android instrumentation tests automatically when an authorized target is available and to skip that step with an explicit message otherwise.

## Certainty wording milestone — 2026-10-12
An AI assistant changed the action-card label from “Confidence” to “Review label” and marked warning cards as heuristic signals requiring source review, preventing layout ranking from being presented as measured certainty.

## Warning provenance milestone — 2026-10-12
An AI assistant added deterministic source-span IDs to warning findings and surfaced those IDs in expandable warning evidence cards, with a regression assertion.

## Date-boundary regression milestone — 2026-10-12
An AI assistant corrected date-role clause boundaries so colon-form labels such as “Deadline: October 20” retain their deadline keyword, with a dedicated unit test.

## Analysis export milestone — 2026-10-12
An AI assistant added a user-initiated Android share-sheet export for the current analysis, including actions, response deadlines, warning evidence, source-span IDs, and privacy warnings.

## Camera device-check milestone — 2026-10-12
An AI assistant opened the external camera intent on the available emulator and confirmed return to Plainly without a crash. The emulator shutter automation did not produce a saved capture; no end-to-end camera OCR success is claimed.

## Camera preview crop milestone — 2026-10-12
An AI assistant updated the captured-image preview to recompute its bounded crop when the user changes the crop slider, keeping the preview consistent with the preprocessing choice.

## Empty-extraction milestone — 2026-10-12
An AI assistant added an explicit ViewModel error for blank or unreadable extraction results, directing the user to retry with a clearer image or check the original document.

## Translation disclosure milestone — 2026-10-12
An AI assistant made the on-device translation model-download disclosure persistent beside the translation action, rather than showing it only after work begins.

## URI-grant cleanup milestone — 2026-10-12
An AI assistant added release of persisted read-only provider URI grants when individual or all local history entries are deleted, without deleting provider-owned files.

## Action-deadline provenance milestone — 2026-10-12
An AI assistant added deterministic source-span IDs to action and response-deadline results and exposed them in expandable cards, with unit assertions for both result types.

## Issue-date provenance milestone — 2026-10-12
An AI assistant added deterministic source-span IDs to issue/document dates and exposed them in date evidence cards, with a unit assertion.

## Provider-ingestion instrumentation milestone — 2026-10-12
An AI assistant added a connected Android test that writes a temporary app-local text fixture, opens it through the configured `FileProvider`, and verifies local plain-text extraction and page mapping. The fixture is deleted after the test.

## Offline startup milestone — 2026-10-12
An AI assistant disabled emulator Wi-Fi and mobile data, launched `MainActivity` explicitly, and observed successful startup without a fatal app exception. This is only an offline startup check, not a claim that all model downloads or document flows work without prior setup.

## OCR pipeline milestone — 2026-10-11
An AI assistant inspected the repository, repaired invalid multiline Kotlin string literals, and implemented the requested baseline OCR improvements in `DocumentAnalyzer.kt`, `OcrEngine.kt`, `MainActivity.kt`, and `DocumentAnalyzerTest.kt`: line/word bounding-box retention, geometry-based column ordering, sentence reconstruction, evidence aggregation, and unit coverage. The student must review the heuristic assumptions and validate the Android build and OCR behavior on a device.

## Any-file chooser milestone — 2026-10-12
An AI assistant widened the Android Storage Access Framework chooser to accept any provider-backed file. Existing local PDF, image, and plain-text routes remain unchanged; unsupported content is expected to produce an actionable processing error rather than being treated as a valid document.

## Calendar confirmation milestone — 2026-10-12
An AI assistant added a pure Kotlin calendar-export policy and a confirmation dialog so detected or ambiguous dates are never handed to a calendar editor until the user explicitly confirms the date. Unit tests cover both the rejected and confirmed paths.

## Camera and history instrumentation milestone — 2026-10-12
An AI assistant added connected Android tests for rotation/crop output dimensions and for Room history insertion/deletion using metadata fields only. The tests do not claim camera hardware capture or prove provider/SDK network behavior.

## Text-like import routing milestone — 2026-10-12
An AI assistant expanded local ingestion routing for common text-like file types, including Markdown, CSV, JSON, XML, and HTML, so they are read as text rather than incorrectly passed to image OCR. The existing pure Kotlin text extraction coverage was extended with a JSON-like fixture.

## ISO date rule milestone — 2026-10-12
An AI assistant added pure Kotlin extraction for ISO `YYYY-MM-DD` dates, classified them using the existing deadline/issue-date context rule, and added tests for both roles. ISO dates are treated as unambiguous; existing numeric and missing-year formats remain review-required.

## Adjacent warning rule milestone — 2026-10-12
An AI assistant added a conservative pure Kotlin rule for payment language in one reconstructed sentence and threat language in the adjacent sentence, with combined source-span IDs and a regression test. This adds evidence coverage without changing the warning score into a probability.

## Negated combination milestone — 2026-10-12
An AI assistant made same- and adjacent-sentence payment/threat combinations reuse the analyzer's negation check, and added regression tests for disclaimer wording. The rule remains conservative and source-grounded.

## Relative-date provenance milestone — 2026-10-12
An AI assistant added explicit provenance for relative deadlines calculated from the analysis reference date. The UI, export, and unit test now distinguish that calculation from a date stated verbatim in the document.

## Translation model permission milestone — 2026-10-12
An AI assistant aligned the manifest with the already-disclosed ML Kit translation model download path by declaring Internet access. Documentation explicitly limits that permission to optional model acquisition and continues to prohibit cloud document analysis claims.

## ViewModel integration milestone — 2026-10-12
An AI assistant added a connected Android test that passes a provider-backed text URI through `PlainlyViewModel` and verifies that analysis state and a deadline are published. This test covers orchestration, not camera hardware or translation-model availability.

## External dataset evaluation milestone — 2026-10-13
An AI assistant downloaded the public Zenodo multiclass phishing/social-engineering corpus outside the repository, applied a documented warning-indicator mapping, and recorded results for 621 parseable examples. The current heuristic measured precision 1.000, recall 0.171, and accuracy 0.399 (77 TP, 0 FP, 373 FN, 171 TN). This is an external message-level benchmark, not evidence of fraud probability, authenticity verification, or document-level accuracy.

## Warning coverage expansion milestone — 2026-10-13
An AI assistant added tested, source-grounded heuristic signals for account-verification redirection, suspicious link/attachment direction, and malware/infection claims after reviewing external-corpus false negatives. On the same 621-row benchmark, the documented mirror of the rules improved recall from 0.171 to 0.518 and accuracy from 0.399 to 0.651 while precision remained 1.000 (233 TP, 0 FP, 217 FN, 171 TN). The rules retain explicit negation handling and are not treated as probability estimates.

## Offline learned-baseline milestone — 2026-10-13
An AI assistant added `scripts/evaluate_external_dataset.py`, a standard-library-only word/bigram Naive Bayes benchmark with duplicate-grouped deterministic hold-out evaluation. The current corpus split produced 0.991 accuracy, 1.000 precision, and 0.987 recall (78 TP, 0 FP, 1 FN, 35 TN). The result is explicitly limited to this corpus.

## Android learned-model integration milestone — 2026-10-14
An AI assistant generated a versioned TSV count artifact from the same documented training split, added pure Kotlin Naive Bayes inference with unit tests, added an Android asset loader, and wired the optional source-linked model signal into `PlainlyViewModel`. The model remains a binary review signal, not a probability or authenticity classifier; its benchmark result does not transfer automatically to photographed documents.
## Concise results milestone — 2026-10-18

An AI assistant simplified the results experience into a concise fraud-warning card, a non-repetitive summary card, and one on-device language-change control. Detailed source evidence, OCR text, and action/deadline review are now behind an explicit review control. The analyzer no longer injects raw document sentences into the primary decision summary and formats referenced dates for readability.
## On-device response quality milestone — 2026-10-18

An AI assistant added a deterministic local response-synthesis pass. It now produces shorter intent-aware guidance, formats dates for people, flags multiple possible response dates, explicitly avoids inventing deadlines, detects mixed payment-and-sensitive-information requests, and keeps uncertainty separate from factual extraction. The compact history/translation text is synthesized from structured evidence and does not copy source sentences. Added unit coverage for these behaviors; no cloud document analysis was introduced.
## Specific appointment synthesis milestone — 2026-10-18

An AI assistant updated the pure Kotlin analyzer to synthesize appointment details from extracted evidence, including event type, date/time, materials, and an explicit missing-location note. Tests, lint, and the debug build passed.
## Visual polish milestone — 2026-10-18

An AI assistant updated the Android Compose presentation with a cohesive color system, branded header, rounded surfaces, clearer summary hierarchy, and more prominent but evidence-preserving risk cards. The existing privacy, uncertainty, and on-device language was preserved. Tests, lint, and the debug build passed.

## Camera and theme reliability — 2026-10-19
Codex implemented the review-requested shared sampled camera decoder (including mirrored EXIF orientations), background preview generation, saveable camera paths/rotation/crop, and theme-based backgrounds with explicit dark colors. This is AI-authored work, not student-authored work. `testDebugUnitTest`, `assembleDebug`, `lintDebug`, and all 10 connected instrumentation tests passed on the API 37 emulator, including new EXIF/dimension and large-image checks. Physical phone capture, process death during the external camera, and visual contrast review remain manual checks. Lint completed with existing advisory warnings.

## Warning and date regressions — 2026-10-19
Codex narrowed account/link warnings, removed standalone “immediately” pressure, scoped negation to clauses with urgency-idiom exceptions, and stopped treating the agency name Social Security alone as a personal-information request. It added abbreviated months, review-required two-digit years, explicitly explained missing-year deadline rollover, dotted appointment times, appointment/permission priority over ordinary fees, and displayed amounts. Regression tests cover the supplied examples, explicit past years, invalid dates, and retained negated warnings. All 62 JVM tests, debug assembly, and lint passed. These are AI-authored changes; no real-letter evaluation or student authorship is claimed.
