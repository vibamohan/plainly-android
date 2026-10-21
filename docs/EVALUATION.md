# Evaluation

The current warning baseline has a 20-example synthetic/redacted JUnit evaluation in `DocumentAnalyzerEvaluationTest.kt`. It asserts precision and recall of at least 0.8 on that fixed fixture. This is a regression gate, not a general accuracy claim: the sample is small, hand-authored, and not representative of real documents.

## External corpus benchmark — 2026-10-13

The deterministic warning rules were also evaluated against the public [Multiclass NLP Dataset for Phishing and Social Engineering Threat Detection](https://doi.org/10.5281/zenodo.15235123), published on Zenodo under CC BY 4.0. The corpus contains 624 English message-level examples with labels `Phishing`, `Malware`, `Scareware`, `Baiting`, `Pretexting`, and `NOT-Malicious General Class`.

For this benchmark, every non-`NOT-Malicious General Class` label was mapped to “at least one warning indicator expected.” Three rows could not be parsed from the workbook’s inconsistent tab-separated representation, so 621 rows were evaluated. This mapping is only a warning-indicator benchmark; it is not a fraud-probability, authenticity, or document-level accuracy measure.

Baseline results for the original phrase-only score (`score > 0`):

| Examples | TP | FP | FN | TN | Precision | Recall | Accuracy |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 621 | 77 | 0 | 373 | 171 | 1.000 | 0.171 | 0.399 |

The result confirms high conservatism and poor recall on this message corpus. It must not be presented as a general performance claim. The corpus is English email/SMS-like content rather than photographed letters, so OCR, page evidence, dates, translation, and document-layout behavior were not evaluated by it.

After adding the separately tested account-verification, suspicious-link/attachment, and malware/infection indicators, the same fixed corpus produced:

| Examples | TP | FP | FN | TN | Precision | Recall | Accuracy |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 621 | 233 | 0 | 217 | 171 | 1.000 | 0.518 | 0.651 |

This is an improvement on this corpus, not a claim of generalization. The zero false-positive result should be treated cautiously because the corpus and label mapping are limited; a separate held-out, document-level evaluation is still needed.

## Offline learned baseline

`scripts/evaluate_external_dataset.py` provides a reproducible word-unigram/bigram Naive Bayes baseline using only Python’s standard library. It groups exact normalized-text duplicates before making a deterministic 80/20 hold-out split. On the same downloaded corpus, it produced 507 training and 114 test examples: 78 TP, 0 FP, 1 FN, and 35 TN, for 0.991 accuracy, 1.000 precision, and 0.987 recall.

This model is not integrated into the Android app. The split is still derived from one message corpus, and exact-duplicate grouping is weaker than source/document grouping. The 99.1% result therefore demonstrates a candidate baseline for further validation, not a production accuracy claim.

The project does not currently report device latency, peak memory, translation adequacy, OCR accuracy, evidence span recall, or calibration. Those require a labeled, consent-safe corpus and measurements on actual Android hardware.

## Synthetic benefits-notice behavior check — 2026-10-21

`BenefitsNoticeEvaluationTest.kt` is a fixed, hand-authored set of 30 fictional one-snippet examples: 15 routine notices and 15 scam-like requests, covering Medi-Cal/CalFresh renewals, school and utility notices, IRS/SSA language, credentials, gift cards, cryptocurrency, wire transfers, personal data, remote access, links, and threats. No real person's letter is included. Treat any nonzero analyzer score as a review signal (not a fraud verdict). On this fixture the current deterministic analyzer produced TP=15, FP=0, FN=0, TN=15 (30/30 accuracy, 1.00 precision, 1.00 recall). This is a regression check against these exact authored phrases, not an estimate of performance on new, real, OCR-processed, translated, or adversarial letters; the examples are small, synthetic, and may overlap known rule wording. It says nothing about MobileBERT, which remains untrained/unvalidated in this experiment.

The same fixture was also scored by the public MobileBERT checkpoint and its 1-epoch fine-tune on the phishing-email corpus. Each model's threshold was independently selected on the public email validation split to meet a 5% maximum false-positive rate, then applied unchanged to the synthetic benefits fixture:

| System | TP | FP | FN | TN | Accuracy | Precision | Recall |
|---|---:|---:|---:|---:|---:|---:|---:|
| MobileBERT checkpoint | 15 | 4 | 0 | 11 | 0.867 | 0.789 | 1.000 |
| Email fine-tune (1 epoch) | 15 | 7 | 0 | 8 | 0.767 | 0.682 | 1.000 |
| Plainly deterministic rules (`score > 0`) | 15 | 0 | 0 | 15 | 1.000 | 1.000 | 1.000 |

This comparison is exploratory and out-of-domain: 30 hand-authored snippets are not a representative or independent benefits-letter test corpus. The 100% rule result is specifically susceptible to wording/selection bias because the examples were written to exercise known rules. The email fine-tune has more false alarms than its checkpoint on this fixture, so it does not support replacing or promoting the existing rules. Do not call these numbers real-world benefits-letter accuracy.

## Adjacent-sentence benefits interruption rule — 2026-10-21

A focused regression set was added for two synthetic multi-sentence cases: an urgent CalFresh suspension followed by a link instruction, and an urgent coverage termination followed by a verification-code request. The rule produced a warning for both (2/2) and did not warn on one synthetic routine renewal deadline plus county-contact sentence (0/1 false warnings). The pre-change implementation did not produce these specific combined-context findings. These three hand-written examples are a targeted behavior check, not a representative evaluation and not evidence of general performance improvement. The rule adds a 25-point review signal with both source sentences attached; it does not alter the Naive Bayes model or turn the score into a probability.

Required future evaluation:

- Separate templates/documents between development and evaluation.
- Report sample counts, class balance, precision, recall, false positives, and false negatives.
- Include benign notices, credential requests, payment requests, threats, negated phrases, OCR degradation, and bilingual text.
- Review dates, money, negation, threats, and translation manually.
