package org.plainly.app.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/** Model downloads on first use; text is translated on device. */
object LocalTranslator {
    suspend fun translate(
        text: String,
        targetLanguage: String = TranslateLanguage.SPANISH,
        sourceLanguage: String = TranslateLanguage.ENGLISH
    ): String {
        if (targetLanguage == sourceLanguage) return text
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()
        val translator = Translation.getClient(options)
        return try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            translator.translate(text).await()
        } finally { translator.close() }
    }
}
