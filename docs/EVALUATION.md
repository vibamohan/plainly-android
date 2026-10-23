# Evaluation

The analyzer has two JVM regression sets: a 20-example warning-rule fixture and a 30-example fictional benefits-notice fixture. Both use hand-written examples. The benefits-notice fixture currently returns 15 true positives, 0 false positives, 0 false negatives, and 15 true negatives when any nonzero score is treated as a review signal.

These results describe only the supplied examples. They do not measure performance on new letters, OCR output, translated text, or adversarial wording. The examples were written to exercise known rules, so their results are not estimates of real-world accuracy.

The analyzer also has targeted tests for multi-sentence benefits interruption language. Two synthetic warning cases are detected, while one routine renewal/contact example is not. This is a focused regression check, not a general performance measure.

The project has not measured device latency, memory use, OCR accuracy, evidence-span recall, translation quality, or performance on a representative letter set. Those require device testing and a consent-safe evaluation corpus split by source document.
