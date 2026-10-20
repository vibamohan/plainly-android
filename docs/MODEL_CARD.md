# Model and rules card

Plainly ships bundled Latin-script ML Kit OCR, deterministic Kotlin rules, and an optional offline word/bigram Naive Bayes warning model trained from the documented external corpus. It does not ship a LiteRT model or generative summarizer.

The warning score is a capped sum of explainable phrase signals. It is not a probability and does not verify safety, fraud, sender identity, or authenticity. Evidence graph relations are document-level textual claims inferred by proximity; they are not proof of causation.

The learned model contributes one source-linked review signal when its binary classifier predicts the warning class. It exposes no probability or authenticity decision, and the rule-based signals remain available as separate evidence.

Action importance labels are review-priority hints based partly on extracted layout. They are not OCR confidence scores or factual certainty.

Translation uses ML Kit on-device models and may require an explicit first-use download. Supported target choices exposed by the current UI are English, Spanish, French, German, and Portuguese; translation quality and offline behavior require real-device evaluation.

## Letter-domain safeguard (2026-10-19)
The bundled email/SMS model predicts a warning for the three benign school examples in the final-week review. Its output therefore no longer creates or raises the user-facing warning level. It can add a zero-weight, explicitly experimental explanation only when deterministic rules already matched. The explanation lists up to five positive word/bigram log-likelihood contributions present in the input; these are statistical associations, not authenticity evidence. Regression tests load the actual bundled TSV. Historical benchmark numbers below/above describe the earlier model evaluation, not this gated product behavior or photographed-letter performance.
