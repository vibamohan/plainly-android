# Implementation roadmap

## CAC MVP: Oct 11–21, 2026
- M0: Open project, compile on Android, run tests and OCR on three sample letters.
- M1: Improve OCR grouping: sentence reconstruction, multi-column reading order, orientation, and confidence review.
- M2: Build date extraction with relative dates, document issue dates, ambiguity handling, and .ics export.
- M3: Improve risk detection with contextual negation, payment-threat combinations, evidence deduplication, and labeled evaluations.
- M4: Build an accessible card-based results UI with evidence taps and Android TextToSpeech.
- M5: Evaluate at least 20 synthetic/redacted letters, measure precision/recall and latency on an actual Android phone; film demo.

## Completed in current prototype
- Card-based results with expandable evidence and original-document warnings.
- Camera capture with EXIF rotation correction and edge crop before OCR.
- On-device TextToSpeech controls, Room metadata history, summary export, and delete.
- Relative deadline review flags, negation-aware warning rules, payment/threat combinations, and user-confirmed calendar-editor export.
- Selectable on-device translation targets: English, Spanish, French, German, and Portuguese.

## After CAC: deeper technical work
- Assemble and label a consent-safe dataset of sentence intents; train/test split by document.
- Fine-tune MobileBERT or a small encoder for multi-label action/threat/payment/urgency intent classification.
- Export to LiteRT (.tflite) only after confirming supported ops, tokenization, and Android runtime.
- Benchmark quantized versus float model size, inference latency, memory, and F1.
- Compare keyword baseline, classifier, and classifier+visual salience; report actual measured outcomes.
- Add visual bold/underline heuristics with a labeled image dataset; no claims of reliable italics detection without testing.
- Add other languages after testing OCR and translation quality on real devices.
