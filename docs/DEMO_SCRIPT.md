# Local demo script

1. Build and install with `./scripts/run-device-smoke-test.sh`.
2. Import `samples/benign_school_notice.txt` after converting it to an image/PDF if the picker is used; show the appointment/date/action and the cautionary risk language.
3. Import a PDF or image made from `samples/credential_request.txt`; show credential, threat, destination, evidence spans, and the `UNCERTAIN`/high-indicator state.
4. Capture a synthetic page with the camera, preview it, choose **Use photo**, and show OCR results. Retake once to verify cancellation/cleanup.
5. Select Spanish or English translation and show the model-status message. Do not claim airplane-mode success until the model has been downloaded and tested on a device.
6. Open **Privacy & limits**, expand evidence, try TTS, export/delete history, and open calendar confirmation.

All claims in a recording must match measured device behavior; do not present heuristic scores as probabilities or authenticity checks.
