package org.plainly.app

import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.plainly.app.translation.LocalTranslator

class LocalTranslatorTest {
    @Test fun sameLanguageTranslationIsStableWithoutModelDownload() = runBlocking {
        val text = "Confirm the date against the original document."
        assertEquals(text, LocalTranslator.translate(text, TranslateLanguage.ENGLISH, TranslateLanguage.ENGLISH))
    }
}
