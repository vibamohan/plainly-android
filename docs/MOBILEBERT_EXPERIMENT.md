# MobileBERT comparison experiment

This is an offline research workflow, not an app feature. It compares the public `Perth0603/phishing-email-mobilebert` checkpoint with a fine-tune of that checkpoint on the same benefits-letter training set. Nothing is connected to Plainly's user-facing warning score. The source checkpoint's published card identifies a 24.6M-parameter Apache-2.0 MobileBERT classifier, but does not document its training data or evaluation metrics; treat it as a candidate baseline, not a proven improvement.

## Data requirements

Keep the JSONL outside the repository. Do not add personal letters to git, logs, reports, or model artifacts. Obtain explicit permission before using a real letter; redact names, addresses, case numbers, barcodes, and account details before annotation. Synthetic examples are accepted by the script for pipeline checks but cannot establish real-world performance.

Each JSONL row has this schema:

```json
{"document_id":"doc-001","split":"train","label":"legitimate","authorization":"consented","text":"Redacted letter text"}
```

Use `label` values `legitimate` or `scam`, `split` values `train`, `validation`, or `test`, and `authorization` values `consented`, `publicly_licensed`, or `synthetic`. Assign every page/chunk from one source letter to the same split. Keep repeated templates and near-duplicates together. Include both labels in every split. The comparison script rejects duplicate exact text or document IDs crossing splits; manual review is still needed to catch paraphrased/template leakage and label errors.

The public email dataset candidate is [`zefang-liu/phishing-email-dataset`](https://huggingface.co/datasets/zefang-liu/phishing-email-dataset), a copy of a Kaggle phishing-email dataset. Its Hugging Face card lists LGPL-3.0 and 18.7k examples with English `Phishing Email` and `Safe Email` labels. This is an email benchmark, not benefits-letter data. Its text can include personal names and addresses, so keep downloaded and prepared files outside the repository and do not publish examples. `scripts/prepare_public_phishing_email.py` exact-deduplicates and creates deterministic stratified splits. It cannot detect near-duplicate templates; the resulting accuracy may overstate generalization.

The existing `samples/` fixtures are deliberately not training examples. They are AI-authored manual demos and their wording must not leak into the held-out test set.

## Run

Use Python 3.11 in a separate virtual environment, then install `scripts/requirements-mobilebert.txt`. Download `Phishing_Email.csv` from the dataset's Hugging Face files page and prepare an external JSONL file. The first comparison run downloads the public checkpoint and tokenizer; training and scoring run locally after that. Keep source data, prepared JSONL, and model output outside the repo.

```sh
python scripts/prepare_public_phishing_email.py /path/to/Phishing_Email.csv /tmp/plainly-email-data.jsonl
python scripts/compare_mobilebert.py /tmp/plainly-email-data.jsonl \
  --output /tmp/plainly-mobilebert-experiment \
  --max-fpr 0.05 --min-recall-gain 0.05 --max-fpr-increase 0.01 \
  --minimum-accuracy 0.90
```

The validation split selects a separate decision threshold for each model under a 5% maximum false-positive rate. The held-out test split is evaluated once using those thresholds. Exit code 0 means the predeclared gate passed: at least 90% test accuracy, at least 5 percentage points more recall than the checkpoint, no more than 1 percentage point higher false-positive rate, at least 30 examples of each label in validation and test, and authorized held-out examples. Exit code 2 means the gate failed. A passing result is only an email-dataset result; it does not establish benefits-letter accuracy or justify app integration without review of confidence intervals, errors, and source/document diversity.

The report contains counts and test metrics, but no letter text. The fine-tuned weights and report are written under the selected output directory. Do not put private-data-derived artifacts in a public release. `--limit-per-split N` is available only for exploratory pipeline checks; capped results must not be presented as full-dataset accuracy or as evidence for a product decision.

## Current status

The public dataset was downloaded and prepared outside the repository: 17,490 unique messages remained after removing one exact duplicate with conflicting labels. The deterministic split contains 12,242 train, 2,624 validation, and 2,624 test examples. A 1-epoch fine-tune did complete. On the held-out email test split, the checkpoint scored TP=974, FP=8, FN=4, TN=1,638 (99.54% accuracy); the fine-tune scored TP=977, FP=13, FN=1, TN=1,633 (99.47%). The predeclared promotion gate failed because recall gain was only 0.31 percentage points (below 5 points), despite test accuracy exceeding 90%. The original and fine-tuned models were also applied to the 30-example synthetic benefits fixture; see `docs/EVALUATION.md` for those out-of-domain results. This does not validate 90% on benefits letters. The MobileBERT checkpoint and fine-tuned weights remain outside the repository; no model was added to the Android app. Conversion to LiteRT, model-size checks, and physical-device latency/memory tests remain separate gates after a model earns held-out evaluation on consent-safe benefits letters.
