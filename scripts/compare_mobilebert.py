#!/usr/bin/env python3
"""Compare a public MobileBERT phishing checkpoint with its domain fine-tune.

Raw examples are read from an external JSONL file and are never copied to output.
The same document-disjoint test split is used for both systems. Scores are
classifier ranking signals, not calibrated probabilities or safety decisions.
"""
import argparse
import json
import re
import sys
import time
from collections import defaultdict
from pathlib import Path

import numpy as np
import torch
from transformers import (AutoModelForSequenceClassification, AutoTokenizer,
                          Trainer, TrainingArguments)

DEFAULT_MODEL = "Perth0603/phishing-email-mobilebert"
LABEL_TO_ID = {"legitimate": 0, "scam": 1}


def read_dataset(path):
    rows = []
    for line_no, line in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        row = json.loads(line)
        required = {"document_id", "split", "label", "text", "authorization"}
        missing = required - row.keys()
        if missing:
            raise ValueError(f"line {line_no}: missing fields {sorted(missing)}")
        if row["split"] not in {"train", "validation", "test"}:
            raise ValueError(f"line {line_no}: split must be train, validation, or test")
        if row["label"] not in LABEL_TO_ID:
            raise ValueError(f"line {line_no}: label must be legitimate or scam")
        if row["authorization"] not in {"consented", "publicly_licensed", "synthetic"}:
            raise ValueError(f"line {line_no}: invalid authorization/data-basis value")
        if not row["document_id"].strip() or not row["text"].strip():
            raise ValueError(f"line {line_no}: document_id and text must be non-empty")
        rows.append(row)

    if not rows:
        raise ValueError("Dataset is empty")
    doc_splits = defaultdict(set)
    text_splits = defaultdict(set)
    for row in rows:
        doc_splits[row["document_id"]].add(row["split"])
        normalized = re.sub(r"\W+", " ", row["text"].lower()).strip()
        text_splits[normalized].add(row["split"])
    if any(len(splits) > 1 for splits in doc_splits.values()):
        raise ValueError("A document_id occurs in multiple splits; split by source document")
    if any(len(splits) > 1 for splits in text_splits.values()):
        raise ValueError("Duplicate normalized text crosses splits; remove the leakage")
    for split in ("train", "validation", "test"):
        labels = {row["label"] for row in rows if row["split"] == split}
        if labels != set(LABEL_TO_ID):
            raise ValueError(f"{split} split must contain both labels")
    return rows


class TextRows(torch.utils.data.Dataset):
    def __init__(self, rows, tokenizer, max_length, label_to_id):
        self.rows = rows
        self.tokenizer = tokenizer
        self.max_length = max_length
        self.label_to_id = label_to_id
        # Tokenize without padding here; the collator pads each batch only to
        # its longest example, which is substantially faster on CPU.
        self.encodings = tokenizer([r["text"] for r in rows], truncation=True,
                                   max_length=max_length)

    def __len__(self):
        return len(self.rows)

    def __getitem__(self, index):
        item = {key: torch.tensor(value[index]) for key, value in self.encodings.items()}
        item["labels"] = torch.tensor(self.label_to_id[self.rows[index]["label"]])
        return item


class DynamicPadCollator:
    def __init__(self, tokenizer):
        self.tokenizer = tokenizer

    def __call__(self, features):
        labels = torch.stack([feature.pop("labels") for feature in features])
        batch = self.tokenizer.pad(features, padding=True, return_tensors="pt")
        batch["labels"] = labels
        return batch


def positive_index(model, explicit):
    if explicit is not None:
        if explicit not in (0, 1):
            raise ValueError("positive index must be 0 or 1")
        return explicit
    labels = model.config.id2label
    matches = [int(index) for index, label in labels.items()
               if any(word in str(label).lower() for word in ("scam", "phish", "fraud", "malicious"))]
    if len(matches) != 1:
        raise ValueError("Could not identify positive checkpoint label; pass --baseline-positive-index 0 or 1")
    return matches[0]


def scores_for(model, dataset, positive_id, batch_size):
    loader = torch.utils.data.DataLoader(
        dataset, batch_size=batch_size,
        collate_fn=DynamicPadCollator(dataset.tokenizer))
    model.eval()
    device = next(model.parameters()).device
    scores = []
    started = time.monotonic()
    with torch.no_grad():
        for batch_index, batch in enumerate(loader, 1):
            inputs = {key: value.to(device) for key, value in batch.items() if key != "labels"}
            logits = model(**inputs).logits
            scores.extend(torch.softmax(logits, dim=-1)[:, positive_id].cpu().tolist())
            if batch_index % 20 == 0 or batch_index == len(loader):
                print(f"Scored {len(scores)}/{len(dataset)} examples in "
                      f"{time.monotonic() - started:.1f}s", flush=True)
    return np.asarray(scores, dtype=float)


def choose_threshold(scores, labels, max_fpr):
    candidates = sorted(set(scores.tolist()), reverse=True) + [1.01]
    feasible = []
    for threshold in candidates:
        predicted = scores >= threshold
        fp = int(np.sum(predicted & (labels == 0)))
        tn = int(np.sum(~predicted & (labels == 0)))
        tp = int(np.sum(predicted & (labels == 1)))
        fn = int(np.sum(~predicted & (labels == 1)))
        fpr = fp / max(fp + tn, 1)
        recall = tp / max(tp + fn, 1)
        if fpr <= max_fpr:
            feasible.append((recall, threshold))
    return max(feasible, key=lambda item: (item[0], item[1]))[1]


def metrics(scores, labels, threshold):
    predicted = scores >= threshold
    tp = int(np.sum(predicted & (labels == 1)))
    fp = int(np.sum(predicted & (labels == 0)))
    fn = int(np.sum(~predicted & (labels == 1)))
    tn = int(np.sum(~predicted & (labels == 0)))
    return {"threshold": float(threshold), "tp": tp, "fp": fp, "fn": fn, "tn": tn,
            "accuracy": (tp + tn) / max(tp + fp + fn + tn, 1),
            "precision": tp / max(tp + fp, 1), "recall": tp / max(tp + fn, 1),
            "false_positive_rate": fp / max(fp + tn, 1)}


def evaluate_model(model, tokenizer, splits, max_length, batch_size, max_fpr, positive_id):
    validation = TextRows(splits["validation"], tokenizer, max_length, LABEL_TO_ID)
    test = TextRows(splits["test"], tokenizer, max_length, LABEL_TO_ID)
    val_labels = np.asarray([int(r["label"] == "scam") for r in splits["validation"]])
    test_labels = np.asarray([int(r["label"] == "scam") for r in splits["test"]])
    val_scores = scores_for(model, validation, positive_id, batch_size)
    threshold = choose_threshold(val_scores, val_labels, max_fpr)
    test_scores = scores_for(model, test, positive_id, batch_size)
    return metrics(test_scores, test_labels, threshold)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", help="External JSONL path; keep raw letters out of git")
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--output", default="/tmp/plainly-mobilebert-finetuned")
    parser.add_argument("--baseline-positive-index", type=int)
    parser.add_argument("--max-fpr", type=float, default=0.05)
    parser.add_argument("--min-recall-gain", type=float, default=0.05)
    parser.add_argument("--max-fpr-increase", type=float, default=0.01)
    parser.add_argument("--minimum-accuracy", type=float, default=0.90)
    parser.add_argument("--epochs", type=float, default=3)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--max-length", type=int, default=256)
    parser.add_argument("--limit-per-split", type=int,
                        help="Optional exploratory cap; do not use capped results as full-dataset evidence")
    parser.add_argument("--minimum-class-count", type=int, default=30,
                        help="Minimum per-label count in validation/test needed for promotion gate")
    args = parser.parse_args()

    rows = read_dataset(args.dataset)
    splits = {name: [r for r in rows if r["split"] == name]
              for name in ("train", "validation", "test")}
    if args.limit_per_split:
        if args.limit_per_split < 1:
            raise ValueError("limit-per-split must be positive")
        splits = {name: part[:args.limit_per_split] for name, part in splits.items()}
    if not 0 <= args.max_fpr <= 1 or args.minimum_class_count < 1:
        raise ValueError("max-fpr must be in [0,1] and minimum-class-count must be positive")
    tokenizer = AutoTokenizer.from_pretrained(args.model)
    baseline = AutoModelForSequenceClassification.from_pretrained(args.model)
    baseline_positive = positive_index(baseline, args.baseline_positive_index)
    baseline_eval = evaluate_model(baseline, tokenizer, splits, args.max_length,
                                   args.batch_size, args.max_fpr, baseline_positive)
    baseline.cpu()

    train_label_to_id = {"scam": baseline_positive, "legitimate": 1 - baseline_positive}
    id2label = {index: label for label, index in train_label_to_id.items()}
    model = AutoModelForSequenceClassification.from_pretrained(
        args.model, num_labels=2, ignore_mismatched_sizes=True,
        id2label=id2label, label2id=train_label_to_id)
    train = TextRows(splits["train"], tokenizer, args.max_length, train_label_to_id)
    validation = TextRows(splits["validation"], tokenizer, args.max_length, train_label_to_id)
    output = Path(args.output)
    training = TrainingArguments(
        output_dir=str(output / "trainer"), num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=args.batch_size, learning_rate=2e-5,
        weight_decay=0.01, evaluation_strategy="epoch", save_strategy="no",
        logging_strategy="epoch", report_to=[], seed=42)
    trainer = Trainer(model=model, args=training, train_dataset=train,
                      eval_dataset=validation, tokenizer=tokenizer,
                      data_collator=DynamicPadCollator(tokenizer))
    trainer.train()
    trainer.save_model(str(output / "model"))
    tokenizer.save_pretrained(str(output / "model"))
    finetuned_eval = evaluate_model(trainer.model, tokenizer, splits, args.max_length,
                                    args.batch_size, args.max_fpr, baseline_positive)
    recall_gain = finetuned_eval["recall"] - baseline_eval["recall"]
    fpr_increase = finetuned_eval["false_positive_rate"] - baseline_eval["false_positive_rate"]
    class_counts = {name: {label: sum(r["label"] == label for r in part)
                           for label in LABEL_TO_ID} for name, part in splits.items()}
    enough_evaluation_data = all(
        class_counts[split][label] >= args.minimum_class_count
        for split in ("validation", "test") for label in LABEL_TO_ID
    )
    heldout_is_authorized = all(r["authorization"] in {"consented", "publicly_licensed"} for r in splits["test"])
    passed = (recall_gain >= args.min_recall_gain and
              fpr_increase <= args.max_fpr_increase and
              finetuned_eval["accuracy"] >= args.minimum_accuracy and
              enough_evaluation_data and heldout_is_authorized)
    report = {
        "model_reference": args.model,
        "split_counts": {name: len(part) for name, part in splits.items()},
        "class_counts": class_counts,
        "validation_max_false_positive_rate": args.max_fpr,
        "baseline_test": baseline_eval,
        "finetuned_test": finetuned_eval,
        "recall_gain": recall_gain,
        "false_positive_rate_increase": fpr_increase,
        "promotion_gate_requirements": {
            "minimum_per_label_validation_and_test": args.minimum_class_count,
            "minimum_test_accuracy": args.minimum_accuracy,
            "heldout_examples_have_authorized_basis": heldout_is_authorized,
            "enough_evaluation_data": enough_evaluation_data,
        },
        "promotion_gate_passed": passed,
        "interpretation": "Experimental review signals only; scores are not probabilities or safety verdicts.",
    }
    output.mkdir(parents=True, exist_ok=True)
    (output / "comparison.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0 if passed else 2


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"error: {error}", file=sys.stderr)
        raise
