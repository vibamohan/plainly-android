#!/usr/bin/env python3
"""Evaluate a small offline word/bigram Naive Bayes baseline.

The input is the Zenodo workbook described in docs/EVALUATION.md. The workbook
is intentionally not checked into the repository. Exact normalized-text groups
are kept in one split to reduce duplicate-template leakage.
"""
import argparse
import hashlib
import math
import re
import zipfile
from collections import Counter
from xml.etree import ElementTree

LABELS = re.compile(r"\t(Phishing|Scareware|Baiting|Malware|Pretexting|NOT-Malicious General Class)$")
XML_NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"


def read_workbook(path):
    with zipfile.ZipFile(path) as archive:
        shared_root = ElementTree.fromstring(archive.read("xl/sharedStrings.xml"))
        shared = ["".join(node.text or "" for node in item.iter(XML_NS + "t"))
                  for item in shared_root.findall(XML_NS + "si")]
        sheet = ElementTree.fromstring(archive.read("xl/worksheets/sheet1.xml"))
        examples = []
        for row in sheet.findall(".//" + XML_NS + "row"):
            cell = row.find(XML_NS + "c")
            value = cell.find(XML_NS + "v") if cell is not None else None
            if value is None:
                continue
            text = shared[int(value.text)]
            match = LABELS.search(text)
            if match:
                examples.append((text[:match.start()], match.group(1) != "NOT-Malicious General Class"))
        return examples


def features(text):
    words = re.findall(r"[a-z0-9]+", text.lower())
    return words + ["__bigram__" + left + "_" + right for left, right in zip(words, words[1:])]


def evaluate(examples, alpha=2.0):
    groups = {}
    for index, (text, label) in enumerate(examples):
        normalized = re.sub(r"\W+", " ", text.lower()).strip()
        key = hashlib.sha1(normalized.encode()).hexdigest()
        groups.setdefault(key, []).append((index, text, label))
    test_keys = {key for key in groups if int(hashlib.sha1(key.encode()).hexdigest()[:8], 16) % 5 == 0}
    train = [item for key, group in groups.items() if key not in test_keys for item in group]
    test = [item for key, group in groups.items() if key in test_keys for item in group]

    class_counts = Counter(label for _, _, label in train)
    feature_counts = {label: Counter() for label in class_counts}
    feature_totals = Counter()
    for _, text, label in train:
        for feature in set(features(text)):
            feature_counts[label][feature] += 1
            feature_totals[label] += 1
    vocabulary = set().union(*(set(counter) for counter in feature_counts.values()))
    predictions = []
    for _, text, expected in test:
        scores = {label: math.log(count / len(train)) for label, count in class_counts.items()}
        observed = Counter(features(text))
        for label in scores:
            denominator = feature_totals[label] + alpha * len(vocabulary)
            scores[label] += sum(
                amount * math.log((feature_counts[label][feature] + alpha) / denominator)
                for feature, amount in observed.items()
            )
        predictions.append((max(scores, key=scores.get), expected))

    tp = sum(predicted and expected for predicted, expected in predictions)
    fp = sum(predicted and not expected for predicted, expected in predictions)
    fn = sum(not predicted and expected for predicted, expected in predictions)
    tn = sum(not predicted and not expected for predicted, expected in predictions)
    total = len(predictions)
    return len(train), total, tp, fp, fn, tn


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("workbook", help="Downloaded phishing_nlp_dataset.xlsx")
    args = parser.parse_args()
    result = evaluate(read_workbook(args.workbook))
    train, test, tp, fp, fn, tn = result
    print(f"train={train} test={test}")
    print(f"tp={tp} fp={fp} fn={fn} tn={tn}")
    print(f"accuracy={(tp + tn) / test:.4f}")
    print(f"precision={tp / max(tp + fp, 1):.4f}")
    print(f"recall={tp / max(tp + fn, 1):.4f}")
    print(f"false_positive_rate={fp / max(fp + tn, 1):.4f}")


if __name__ == "__main__":
    main()
