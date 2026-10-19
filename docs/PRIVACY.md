# Privacy and limitations

- Document OCR and rule analysis run on-device.
- The app does not require an account or broad storage permission.
- When supported by the document provider, the app persists a read-only URI grant so history can refer to the selected item without copying its bytes.
- Camera temporary files are deleted after OCR completes or when a capture is retaken/cancelled.
- Room history stores metadata, URI text, and a short analysis summary; it does not store OCR text or copied document bytes.
- Users can delete individual history entries or clear all local history; these controls do not delete original provider files.
- Deleting history also releases Plainly's persisted read grant for the provider URI when one exists; it does not delete the provider-owned file.
- “Remove current document” clears the extracted text and analysis held in the current app state; it is separate from deleting optional Room history metadata.
- Analysis export is user-initiated through Android's share sheet; Plainly does not upload or automatically transmit the document.
- Translation models may download through ML Kit the first time a language is selected. The app discloses this in the UI; offline-after-download behavior still needs device verification.
- The app declares Internet access only so ML Kit can acquire optional translation models. Plainly does not send document text to a cloud analysis service; SDK/network behavior should still be reviewed before making absolute privacy claims.
- No authenticity or sender-identity verification is performed.
- A zero or low heuristic score is not proof that a document is legitimate.
- Risk states are `HIGH`, `UNCERTAIN`, or `NO_CLEAR_INDICATORS`, not fraud probabilities.
- OCR does not establish bold, italic, underline, or document authenticity.
- OCR language selection currently selects among tested Latin-script labels; it does not claim support for non-Latin scripts.
