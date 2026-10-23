# Plainly

Plainly is an Android app that helps people review letters. It extracts text on the device, summarizes possible actions and dates, and highlights phrases that may deserve a closer look. It does not verify who sent a letter or whether a letter is legitimate.

## What it does

- Opens photos, PDFs, and common text files through Android's document picker.
- Reads images and scanned PDF pages with bundled Latin-script ML Kit OCR. Selectable PDF text is extracted locally.
- Lets the user take, rotate, and crop a camera image before processing.
- Shows extracted text and source-linked evidence alongside possible actions, dates, and warning indicators.
- Offers on-device translation and text-to-speech. Translation models may download the first time they are used.
- Keeps optional history to document metadata and a short summary. Imported document bytes are not copied into app storage.

## Build and run

Requirements: Android Studio, Android SDK 35, and JDK 17. The app supports Android 8.0 (API 26) and later.

Open the project in Android Studio, sync Gradle, and run the `app` configuration on a device or emulator. To build from a terminal:

```sh
./gradlew assembleDebug
```

To run unit tests and Android lint:

```sh
./gradlew testDebugUnitTest lintDebug
```

`./scripts/verify.sh` runs those checks with a debug build and runs instrumentation tests when an Android target is connected. `./scripts/run-device-smoke-test.sh` installs and launches the app on a connected target.

## Limits

Plainly is a prototype, not legal, immigration, financial, or fraud-verification advice. A warning score is not a fraud probability, and no warning does not mean a document is safe. Dates and extracted text should be checked against the original; numeric dates use US month/day ordering and may be marked for review. Reading order and PDF text positions can be approximate. OCR currently supports Latin script. Translation quality and offline behavior depend on the installed models and Android device.

Document OCR and analysis run locally. Internet access is used by ML Kit to acquire optional translation models. Review [privacy details](docs/PRIVACY.md), [evaluation limits](docs/EVALUATION.md), and [AI assistance disclosure](AI-USAGE.md).
