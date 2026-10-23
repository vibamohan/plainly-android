# Model and rules

Plainly uses bundled Latin-script ML Kit OCR, deterministic Kotlin analysis rules, and a small bundled word/bigram Naive Bayes model. It does not include a LiteRT model or generative document analysis.

The rules and model can highlight text for review. Their scores are not probabilities and do not establish that a document is fraudulent, authentic, or safe. Evidence links point to extracted text; inferred relationships describe nearby wording, not verified facts.

The Naive Bayes model was developed from email and message data. It produced false warnings on benign school examples, so its output cannot create or increase the app's warning level. When deterministic rules have already found a warning, the model may show an experimental explanation with words that contributed to its score. This is not validation on photographed letters.

Translation uses ML Kit models on the device and may require a first-use download. Translation quality and offline behavior have not been evaluated across supported languages and devices.
