#!/usr/bin/env python3
"""Prepare a deterministic, exact-deduplicated split from the public HF CSV."""
import argparse
import csv
import hashlib
import json
import random
import re
import sys
from collections import defaultdict
from pathlib import Path


def normalize(text):
    return re.sub(r"\W+", " ", text.lower()).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv_path", help="Downloaded Phishing_Email.csv")
    parser.add_argument("jsonl_path", help="Write processed data outside the repo")
    parser.add_argument("--text-column", default="Email Text")
    parser.add_argument("--label-column", default="Email Type")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    unique = {}
    ambiguous = set()
    csv.field_size_limit(sys.maxsize)
    with Path(args.csv_path).open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames or args.text_column not in reader.fieldnames or args.label_column not in reader.fieldnames:
            raise ValueError(f"CSV needs columns {args.text_column!r} and {args.label_column!r}; found {reader.fieldnames}")
        for row_number, row in enumerate(reader, 2):
            text = (row.get(args.text_column) or "").strip()
            label_text = (row.get(args.label_column) or "").strip().lower()
            if not text or text.lower() == "null":
                continue
            if label_text == "phishing email":
                label = "scam"
            elif label_text == "safe email":
                label = "legitimate"
            else:
                raise ValueError(f"Unexpected label at CSV row {row_number}: {label_text!r}")
            key = normalize(text)
            previous = unique.get(key)
            if previous and previous["label"] != label:
                ambiguous.add(key)
            else:
                unique.setdefault(key, {"text": text, "label": label})

    for key in ambiguous:
        unique.pop(key, None)

    by_label = defaultdict(list)
    for key, row in unique.items():
        by_label[row["label"]].append((key, row))
    if set(by_label) != {"legitimate", "scam"}:
        raise ValueError("Expected both safe and phishing examples")
    rng = random.Random(args.seed)
    split_rows = {"train": [], "validation": [], "test": []}
    for label, examples in by_label.items():
        rng.shuffle(examples)
        test_end = max(1, round(len(examples) * 0.15))
        validation_end = test_end + max(1, round(len(examples) * 0.15))
        for index, (key, row) in enumerate(examples):
            split = "test" if index < test_end else "validation" if index < validation_end else "train"
            split_rows[split].append({
                "document_id": hashlib.sha256(key.encode("utf-8")).hexdigest(),
                "split": split,
                "label": row["label"],
                "authorization": "publicly_licensed",
                "text": row["text"],
            })
    output = Path(args.jsonl_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        for split in ("train", "validation", "test"):
            for row in split_rows[split]:
                handle.write(json.dumps(row, ensure_ascii=False) + "\n")
    print(json.dumps({"exact_duplicates_with_conflicting_labels_removed": len(ambiguous),
                      "unique_examples": len(unique),
                      "splits": {name: len(rows) for name, rows in split_rows.items()}}, indent=2))


if __name__ == "__main__":
    main()
