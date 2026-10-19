# Model and rules card

Plainly ships bundled Latin-script ML Kit OCR, deterministic Kotlin rules, and an optional offline word/bigram Naive Bayes warning model trained from the documented external corpus. It does not ship a LiteRT model or generative summarizer.

The warning score is a capped sum of explainable phrase signals. It is not a probability and does not verify safety, fraud, sender identity, or authenticity. Evidence graph relations are document-level textual claims inferred by proximity; they are not proof of causation.

The learned model contributes one source-linked review signal when its binary classifier predicts the warning class. It exposes no probability or authenticity decision, and the rule-based signals remain available as separate evidence.

Action importance labels are review-priority hints based partly on extracted layout. They are not OCR confidence scores or factual certainty.

Translation uses ML Kit on-device models and may require an explicit first-use download. Supported target choices exposed by the current UI are English, Spanish, French, German, and Portuguese; translation quality and offline behavior require real-device evaluation.
