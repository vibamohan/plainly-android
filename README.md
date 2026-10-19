# Plainly — offline-first Android document assistant

A native Kotlin / Jetpack Compose prototype for understanding printed letters without uploading them to a generative AI service.

## Implemented
- Android system document picker for photos and PDFs (no storage permission).
- Plain-text-like files (`.txt`, `.md`, `.csv`, `.json`, `.xml`, and HTML) are accepted for deterministic local demos and are mapped to page-one source spans.
- Read-only URI permission is persisted when the provider supports it; the document is not copied into app storage.
- Bundled ML Kit Latin OCR for images and scanned PDF pages, using Android PdfRenderer one page at a time.
- Explicit OCR language selection for tested Latin-script documents (English, Spanish, French, German, and Portuguese); non-Latin OCR packs are not bundled.
- Camera preprocessing corrects EXIF orientation and lets the user rotate and crop edge margins before OCR.
- Page-linked extracted text with line and word bounding boxes, sentence reconstruction, and geometry-based column ordering.
- Rule-based action and deadline extraction (English month names, US numeric dates, and unambiguous ISO dates).
- Explainable warning phrase scoring; no safety guarantees.
- ML Kit on-device translation with English, Spanish, French, German, and Portuguese targets; detected source language is used when supported (the selected model may download on first use).
- Original extracted text remains visible beside the translated text for direct comparison.
- On-device speech controls support pause, resume, and replay; availability depends on the Android TTS engine.
- Pure Kotlin analysis tests.

## Known limitations
- Trained LiteRT/TFLite intent classifier; a generic model without a trained head is NOT useful.
- Reliable bold/italic/underline recognition; OCR does not provide this directly.
- Non-Latin OCR is not configured; translation targets are separate from OCR language support.
- Relative dates are calculated from the analysis reference date and marked for review.
- Selectable PDF text is extracted locally with PDFBox and approximate line boxes; image-only/scanned PDFs fall back to bounded page rendering and OCR.
- On-device language identification is best-effort and displays a warning when confidence is low; it does not expand the bundled Latin OCR model's script support.

## Run
1. Install Android Studio with Android SDK 35 and JDK 17.
2. Open this folder and let Android Studio sync Gradle dependencies.
3. Run the `app` configuration on an Android device (API 26+) or install `app/build/outputs/apk/debug/app-debug.apk` with `adb install -r`.
4. Select an English-language photo or PDF from the system picker.
5. Run unit tests: `./gradlew testDebugUnitTest`; with an emulator/device connected, run `./gradlew connectedDebugAndroidTest` for the activity launch test.

For automated verification, run `./scripts/verify.sh`. For device behavior, run `./scripts/run-device-smoke-test.sh` with an authorized emulator or phone connected through `adb`.

The Room history stores only user-selected document metadata and a short summary; it does not copy document content.

## Privacy limitations
OCR is bundled and document analysis is local. Translation downloads a model and may make network requests for model acquisition; Internet access is declared for that purpose. This app does not itself upload letter content, but verify SDK telemetry and network behavior before making absolute privacy claims. Room history contains metadata only; imported content is processed in memory and is not copied by Plainly.

## Accuracy limitations
This is a constrained prototype, not legal, immigration, financial, or fraud-verification advice. Numeric dates assume US month/day ordering and are flagged for review. Missing years are assumed for display and flagged for confirmation. Column ordering is heuristic and can fail on unusual layouts, rotated pages, or mixed columns.
