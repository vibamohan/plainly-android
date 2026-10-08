package org.plainly.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.plainly.app.analysis.Analysis
import org.plainly.app.analysis.DocumentAnalyzer
import org.plainly.app.analysis.TextEvidence
import org.plainly.app.history.HistoryEntry
import org.plainly.app.ingestion.OnDeviceDocumentIngestion
import org.plainly.app.ingestion.OnDeviceLanguageIdentifier
import org.plainly.app.history.PlainlyDatabase
import org.plainly.app.translation.LocalTranslator
import com.google.mlkit.nl.translate.TranslateLanguage
import java.io.File
import org.plainly.app.ocr.OcrLanguage
import org.plainly.app.ml.WarningModelLoader

data class PlainlyUiState(
    val busy: Boolean = false,
    val pageProgress: Pair<Int, Int>? = null,
    val error: String? = null,
    val lines: List<TextEvidence> = emptyList(),
    val analysis: Analysis? = null,
    val translated: String? = null,
    val translatedOriginal: String? = null,
    val translatedLanguage: String? = null,
    val originalTranslationLanguage: String? = null,
    val languageTag: String? = null,
    val languageWarning: String? = null,
    val translationStatus: String? = null
    ,val ocrLanguage: OcrLanguage = OcrLanguage.ENGLISH
)

class PlainlyViewModel(application: Application) : AndroidViewModel(application) {
    private val historyDao = PlainlyDatabase.get(application).historyDao()
    private val learnedWarningModel = runCatching { WarningModelLoader.load(application) }.getOrNull()
    private val _state = MutableStateFlow(PlainlyUiState())
    val state: StateFlow<PlainlyUiState> = _state.asStateFlow()

    fun setOcrLanguage(language: OcrLanguage) { _state.value = _state.value.copy(ocrLanguage = language) }

    fun process(uri: Uri, temporaryFiles: List<File> = emptyList()) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                busy = true, error = null, lines = emptyList(), analysis = null,
                translated = null, translatedOriginal = null, translatedLanguage = null, originalTranslationLanguage = null, languageTag = null,
                languageWarning = null, translationStatus = null
            )
            try {
                val extracted = withContext(Dispatchers.IO) {
                    OnDeviceDocumentIngestion.extract(getApplication(), uri, _state.value.ocrLanguage) { page, total ->
                        _state.value = _state.value.copy(pageProgress = page to total)
                    }
                }
                if (extracted.none { it.text.isNotBlank() }) {
                    throw IllegalArgumentException(
                        "No readable text was found. Try a clearer image or check the original document manually."
                    )
                }
                val language = withContext(Dispatchers.Default) { OnDeviceLanguageIdentifier.identify(extracted) }
                val result = withContext(Dispatchers.Default) { DocumentAnalyzer.analyze(extracted, learnedWarningModel = learnedWarningModel) }
                _state.value = _state.value.copy(lines = extracted, analysis = result, languageTag = language.languageTag, languageWarning = language.warning)
                withContext(Dispatchers.IO) {
                    val name = if (temporaryFiles.isNotEmpty()) {
                        "Camera scan, " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a"))
                    } else displayName(uri)
                    historyDao.insert(HistoryEntry(displayName = name, sourceUri = if (temporaryFiles.isEmpty()) uri.toString() else "", summary = result.summary))
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Could not process document")
            } finally {
                temporaryFiles.forEach { it.delete() }
                _state.value = _state.value.copy(busy = false, pageProgress = null)
            }
        }
    }

    fun translate(targetLanguage: String) {
        val current = _state.value.analysis ?: return
        val sourceLanguage = TranslateLanguage.ENGLISH // Synthesized analysis is always English.
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, translated = null, translatedLanguage = null, translatedOriginal = null, originalTranslationLanguage = null, translationStatus = "Preparing on-device translation model…")
            try {
                val decision = current.decisionSummary
                val text = if (decision != null) {
                    org.plainly.app.analysis.DocumentAnalyzer.run { decision.toCompactText() }
                } else {
                    current.summary + "\n" + current.actions.joinToString("\n") { it.instruction }
                }
                val result = withContext(Dispatchers.IO) { LocalTranslator.translate(text, targetLanguage, sourceLanguage) }
                _state.value = _state.value.copy(translated = result, translatedLanguage = targetLanguage, translationStatus = "Translation complete")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Translation unavailable: ${e.message}", translationStatus = null)
            } finally { _state.value = _state.value.copy(busy = false, pageProgress = null) }
        }
    }

    fun translateOriginal(targetLanguage: String) {
        val text = _state.value.lines.joinToString("\n") { "[Page ${it.page}] ${it.text}" }
        if (text.isBlank()) return
        val sourceLanguage = detectedTranslateLanguage()
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, translationStatus = "Preparing on-device translation model…")
            try {
                val result = withContext(Dispatchers.IO) { LocalTranslator.translate(text, targetLanguage, sourceLanguage) }
                _state.value = _state.value.copy(translatedOriginal = result, originalTranslationLanguage = targetLanguage, translationStatus = "Translation complete")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Original-text translation unavailable: ${e.message}", translationStatus = null)
            } finally { _state.value = _state.value.copy(busy = false, pageProgress = null) }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
    fun setError(message: String) { _state.value = _state.value.copy(error = message, busy = false) }
    fun clearCurrentDocument() {
        _state.value = _state.value.copy(
            lines = emptyList(), analysis = null, translated = null, translatedOriginal = null, translatedLanguage = null, originalTranslationLanguage = null,
            languageTag = null, languageWarning = null, translationStatus = null, error = null
        )
    }

    private fun displayName(uri: Uri): String = runCatching {
        getApplication<Application>().contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Imported document"

    private fun detectedTranslateLanguage(): String =
        TranslateLanguage.fromLanguageTag(_state.value.languageTag.orEmpty()) ?: TranslateLanguage.ENGLISH
}
