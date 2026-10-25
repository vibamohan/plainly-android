#!/usr/bin/env python3
"""Score a fixed synthetic benefits fixture with original and fine-tuned MobileBERT."""
import json
from pathlib import Path

import numpy as np
from transformers import AutoModelForSequenceClassification, AutoTokenizer

from compare_mobilebert import (LABEL_TO_ID, TextRows, choose_threshold,
                                positive_index, scores_for)

EXAMPLES = [
    (False, "Your Medi-Cal renewal packet is due November 18. Return the signed form to the county office."),
    (False, "Your CalFresh periodic report is due December 3. Submit your household update through the county portal."),
    (False, "Please open the attached file for the school meal-program schedule."),
    (False, "Log in to your ParentSquare account to confirm your contact information."),
    (False, "Your PG&E bill is due November 12. Pay using the payment methods listed on your regular statement."),
    (False, "Attend your county benefits appointment on November 9 at 10:30 a.m. Bring proof of residency."),
    (False, "Social Security sent a benefits notice. Call the number printed on your official statement with questions."),
    (False, "Please check the attached file for the updated tax form and mailing instructions."),
    (False, "Your benefits renewal is due November 5, 2026. Contact the county office if you have questions."),
    (False, "Return the requested proof of income by November 21 using the enclosed return envelope."),
    (False, "Your IEP meeting is scheduled for November 14. Reply to the school office to confirm attendance."),
    (False, "We received your application. Check your case status through the county website when convenient."),
    (False, "Your tax account statement lists a balance due by December 15. Review payment options on IRS.gov."),
    (False, "Please update your mailing address with the benefits office so future notices reach you."),
    (False, "No later than November 30, send the completed renewal form to the address shown on page two."),
    (True, "FINAL NOTICE: Pay your overdue benefits fee with gift cards today or face arrest."),
    (True, "Respond no later than Friday with your password and verification code to keep your case open."),
    (True, "Your CalFresh account is suspended. Click here within 24 hours and enter your password to restore benefits."),
    (True, "Send bitcoin to the listed wallet today to release your delayed tax refund."),
    (True, "Wire transfer the processing fee immediately or your Medi-Cal coverage will be terminated."),
    (True, "Reply with your Social Security number and date of birth to avoid losing your benefits."),
    (True, "The IRS will arrest you unless you pay this final tax penalty with iTunes cards."),
    (True, "Install this software and share your screen now so our benefits agent can verify your bank account."),
    (True, "Your coverage ends today. Visit this link and enter your verification code to prevent termination."),
    (True, "Text your one-time code and password to this number within 24 hours or your claim will be closed."),
    (True, "Send your bank account number and routing number to receive the promised emergency grant."),
    (True, "Pay a $75 wire transfer fee immediately to unlock your Social Security refund."),
    (True, "Final notice: pay with gift cards now or you will be deported."),
    (True, "Your benefits application will be canceled today. Open this website and provide your password."),
    (True, "Transfer cryptocurrency within 24 hours or law enforcement will arrest you."),
]


def scorecard(scores, truth):
    pred = np.asarray(scores)
    truth = np.asarray(truth)
    tp = int(np.sum(pred & truth))
    fp = int(np.sum(pred & ~truth))
    fn = int(np.sum(~pred & truth))
    tn = int(np.sum(~pred & ~truth))
    n = len(truth)
    return {"n": n, "tp": tp, "fp": fp, "fn": fn, "tn": tn,
            "accuracy": (tp + tn) / n, "precision": tp / max(tp + fp, 1),
            "recall": tp / max(tp + fn, 1)}


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", default="Perth0603/phishing-email-mobilebert")
    parser.add_argument("--finetuned", required=True)
    parser.add_argument("--validation-jsonl", required=True,
                        help="Public email validation rows used only for threshold selection")
    parser.add_argument("--max-fpr", type=float, default=0.05)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--max-length", type=int, default=160)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    rows = [json.loads(line) for line in Path(args.validation_jsonl).read_text(encoding="utf-8").splitlines() if line.strip()]
    validation_rows = [row for row in rows if row["split"] == "validation"]
    validation_labels = np.asarray([row["label"] == "scam" for row in validation_rows], dtype=bool)
    truth = np.asarray([label for label, _ in EXAMPLES], dtype=bool)
    fixture_rows = [{"text": text, "label": "scam" if label else "legitimate"} for label, text in EXAMPLES]

    results = {}
    for name, model_path in (("baseline", args.baseline), ("fine_tuned", args.finetuned)):
        tokenizer = AutoTokenizer.from_pretrained(model_path)
        model = AutoModelForSequenceClassification.from_pretrained(model_path)
        pos_id = positive_index(model, None)
        val_ds = TextRows(validation_rows, tokenizer, args.max_length, LABEL_TO_ID)
        fixture_ds = TextRows(fixture_rows, tokenizer, args.max_length, LABEL_TO_ID)
        val_scores = scores_for(model, val_ds, pos_id, args.batch_size)
        threshold = choose_threshold(val_scores, validation_labels.astype(int), args.max_fpr)
        fixture_scores = scores_for(model, fixture_ds, pos_id, args.batch_size)
        predicted = fixture_scores >= threshold
        results[name] = {"threshold_from_email_validation": float(threshold),
                         "synthetic_benefits_scorecard": scorecard(predicted, truth)}
        del model

    output = {"fixture": "30 hand-authored fictional snippets from BenefitsNoticeEvaluationTest.kt",
              "kind": "synthetic out-of-domain behavior check, not a real-world accuracy estimate",
              "positive_class": "scam-like review signal; not a fraud verdict",
              "threshold_policy": f"selected independently on public email validation data at max FPR {args.max_fpr}",
              "results": results}
    Path(args.output).write_text(json.dumps(output, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(output, indent=2))


if __name__ == "__main__":
    main()
