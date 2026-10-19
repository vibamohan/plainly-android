# Codex instructions — Plainly Android

- This is an Android-only Kotlin/Jetpack Compose project. Do NOT rewrite as a web app.
- Primary objective: accurate, usable, privacy-preserving document assistance. No cloud document analysis.
- Keep analysis evidence-backed. Never call a risk score a fraud probability or mark a letter verified safe.
- Do not claim OCR line height establishes bold, italic, or underline. Implement style recognition only with explicit evidence and tests.
- Do not claim the project uses TFLite yet. A LiteRT model is a future milestone and must be validated on device.
- Never silently invent deadlines or translate a date into a different date. Flag ambiguity.
- Preserve clear separation between Android OCR/UI and pure Kotlin analysis so unit tests can run.
- Add unit tests for every risk or deadline rule; run tests before committing.
- Log actual AI-assisted contributions in AI-USAGE.md. Do not fabricate student authorship.
- Before each milestone, explain the intended files and acceptance criteria; implement only the requested milestone.
