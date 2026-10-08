@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.plainly.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.plainly.app.analysis.*
import org.plainly.app.ocr.OcrEngine
import org.plainly.app.ocr.CameraImageProcessor
import org.plainly.app.ocr.OcrLanguage
import org.plainly.app.translation.LocalTranslator
import com.google.mlkit.nl.translate.TranslateLanguage
import org.plainly.app.history.HistoryEntry
import org.plainly.app.history.PlainlyDatabase
import org.plainly.app.ui.*
import org.plainly.app.viewmodel.PlainlyViewModel
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PlainlyTheme { PlainlyScreen(this) } }
    }
}

private fun analysisExportText(result: Analysis): String = buildString {
    appendLine("Plainly analysis")
    appendLine()
    appendLine("Summary: ${result.summary}")
    appendLine()
    appendLine("Actions")
    if (result.actions.isEmpty()) appendLine("- None identified")
    result.actions.forEach { appendLine("- ${it.instruction}\n  Evidence: ${it.evidence}") }
    appendLine()
    appendLine("Response deadlines")
    if (result.deadlines.isEmpty()) appendLine("- None identified")
    result.deadlines.forEach {
        appendLine("- ${it.date}${if (it.needsReview) " (review required)" else ""}${if (it.isCalculatedFromReferenceDate) " (calculated from analysis date)" else ""}: ${it.evidence} ${it.reviewReason.orEmpty()}")
    }
    appendLine()
    appendLine("Warning signals (heuristic; review original)")
    if (result.findings.isEmpty()) appendLine("- None matched; this does not verify legitimacy")
    result.findings.forEach { finding ->
        appendLine("- ${finding.label}: ${finding.evidence}")
        appendLine("  Source spans: ${finding.sourceSpanIds.joinToString()}")
    }
    appendLine()
    appendLine("Official contacts (directory matches do not verify the sender)")
    result.officialContacts.forEach { appendLine("- ${it.contact.name}: ${it.contact.phone} · ${it.contact.website} (directory checked ${it.contact.checkedOn})") }
    appendLine()
    appendLine("Important: Check the original document. Plainly does not verify authenticity or provide a fraud probability.")
}

private fun riskExplanation(label: String): String = when {
    label.contains("Payment", true) || label.contains("Cryptocurrency", true) || label.contains("Gift-card", true) ->
        "Payment methods in this document deserve independent verification before you send money."
    label.contains("Credential", true) ->
        "Sensitive account information can be misused. Confirm the request through an official channel before sharing anything."
    label.contains("Personal information", true) ->
        "Personal and banking details can be misused for identity theft. Confirm why the information is needed and who will receive it."
    label.contains("Bank or wire", true) ->
        "Wire and bank transfers can be difficult to reverse. Independently verify the recipient and payment instructions."
    label.contains("Remote access", true) ->
        "Remote-access tools can expose files and accounts. Do not install software or share your screen unless you initiated and verified the support request."
    label.contains("Threat", true) || label.contains("Urgent", true) ->
        "Pressure or consequences can make it harder to check a request carefully. Slow down and verify the sender."
    label.contains("link", true) || label.contains("redirection", true) || label.contains("attachment", true) ->
        "Links and attachments can lead to the wrong destination. Find the official website or contact method independently."
    label.contains("Malware", true) ->
        "Claims about infection can be used to pressure you into installing software or paying. Verify through trusted support."
    else -> "This is a heuristic review signal. Compare it with the original document and its surrounding context."
}

@Composable
internal fun PlainlyApp(context: Context) {
    val scope = rememberCoroutineScope()
    val plainlyViewModel: PlainlyViewModel = viewModel()
    val uiState by plainlyViewModel.state.collectAsState()
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraFile = cameraPath?.let(::File)
    val pendingCameraFile = pendingCameraPath?.let(::File)
    var cameraRotation by rememberSaveable { mutableIntStateOf(0) }
    var cameraCropPercent by rememberSaveable { mutableIntStateOf(3) }
    var preparingPhoto by remember { mutableStateOf(false) }
    val historyDao = remember { PlainlyDatabase.get(context).historyDao() }
    var history by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    LaunchedEffect(Unit) { historyDao.observeAll().collect { history = it } }
    var speechReady by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var speechPaused by remember { mutableStateOf(false) }
    var speechOffset by remember { mutableIntStateOf(0) }
    var speechBase by remember { mutableIntStateOf(0) }
    var speechError by remember { mutableStateOf<String?>(null) }
    var lastSpeech by remember { mutableStateOf("") }
    var targetLanguage by rememberSaveable { mutableStateOf(TranslateLanguage.SPANISH) }
    var languageMenu by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val preferences = remember { context.getSharedPreferences("plainly-onboarding", Context.MODE_PRIVATE) }
    var firstRun by rememberSaveable { mutableStateOf(!preferences.getBoolean("seen", false)) }
    var showClearHistory by remember { mutableStateOf(false) }
    var calendarConfirmation by remember { mutableStateOf<Deadline?>(null) }
    val tts = remember { TextToSpeech(context) { status -> speechReady = status == TextToSpeech.SUCCESS } }
    DisposableEffect(tts) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { }
            override fun onDone(utteranceId: String?) { speaking = false }
            override fun onError(utteranceId: String?) { speaking = false }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) { speechOffset = speechBase + start }
        })
        onDispose { tts.stop(); tts.shutdown() }
    }

    LaunchedEffect(uiState.analysis, uiState.translatedLanguage, uiState.translated) {
        tts.stop()
        speaking = false
        speechPaused = false
        lastSpeech = ""
        speechOffset = 0
        speechBase = 0
    }

    val pdfExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destination ->
        val result = uiState.analysis
        if (destination != null && result != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(destination)?.use {
                        org.plainly.app.export.AnalysisPdfWriter.write(analysisExportText(result), it)
                    } ?: error("Could not open export destination")
                }
            } catch (e: Exception) { plainlyViewModel.setError(e.message ?: "Could not export PDF") }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // Keep only the provider grant, never a private copy of the document. Some
        // providers do not support persistence, so processing still proceeds either way.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        plainlyViewModel.process(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) {
            pendingCameraPath = cameraPath
        } else {
            cameraFile?.delete()
            cameraPath = null
        }
    }
    fun capture() {
        cameraRotation = 0
        val file = File(context.cacheDir, "camera-${System.currentTimeMillis()}.jpg")
        cameraPath = file.absolutePath
        camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }
    fun useCapturedPhoto() {
        val source = pendingCameraFile ?: return
        if (preparingPhoto) return
        preparingPhoto = true
        scope.launch {
            plainlyViewModel.clearError()
            try {
                val normalized = withContext(Dispatchers.IO) {
                    CameraImageProcessor.normalize(context, source, cameraRotation, cameraCropPercent)
                }
                pendingCameraPath = null
                cameraPath = null
                plainlyViewModel.process(Uri.fromFile(normalized), listOf(source, normalized))
            } catch (e: Exception) {
                plainlyViewModel.setError(e.message ?: "Could not prepare camera image")
            } finally { preparingPhoto = false }
        }
    }
    fun releaseHistoryGrant(entry: HistoryEntry) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(entry.sourceUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppHeader { showInfo = true }
        Text(context.getString(R.string.tagline), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("✦", modifier = Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(context.getString(R.string.start_document), style = MaterialTheme.typography.titleLarge)
                        Text(context.getString(R.string.clarity), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(context.getString(R.string.intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !uiState.busy) { Text(context.getString(R.string.choose_file)) }
                    OutlinedButton(onClick = ::capture, enabled = !uiState.busy) { Text(context.getString(R.string.use_camera)) }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(context.getString(R.string.local_analysis))
            StatusChip(context.getString(R.string.no_cloud))
        }
        Text(context.getString(R.string.english_support), style = MaterialTheme.typography.bodySmall)
        if (firstRun) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(context.getString(R.string.first_title)) },
                text = { Text(context.getString(R.string.first_body), Modifier.verticalScroll(rememberScrollState())) },
                confirmButton = { Button(onClick = { preferences.edit().putBoolean("seen", true).apply(); firstRun = false }) { Text(context.getString(R.string.get_started)) } }
            )
        }
        if (showInfo) {
            AlertDialog(
                onDismissRequest = { showInfo = false },
                title = { Text(context.getString(R.string.privacy_title)) },
                text = {
                    Text(context.getString(R.string.privacy_body))
                },
                confirmButton = { Button(onClick = { showInfo = false }) { Text(context.getString(R.string.understood)) } }
            )
        }
        if (uiState.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        uiState.pageProgress?.let { Text(context.getString(R.string.page_progress, it.first, it.second)) }
        uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        pendingCameraPath?.let {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(context.getString(R.string.photo_ready), style = MaterialTheme.typography.titleMedium)
                    pendingCameraFile?.let { file ->
                        val preview by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file.absolutePath, cameraRotation, cameraCropPercent) {
                            value = null
                            try {
                                value = withContext(Dispatchers.Default) {
                                    CameraImageProcessor.preview(file, cameraRotation, cameraCropPercent).asImageBitmap()
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                plainlyViewModel.setError(e.message ?: "Could not load preview")
                            }
                        }
                        preview?.let { Image(it, contentDescription = context.getString(R.string.photo_preview), modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { cameraRotation = (cameraRotation + 270) % 360 }) { Text(context.getString(R.string.rotate_left)) }
                            OutlinedButton(onClick = { cameraRotation = (cameraRotation + 90) % 360 }) { Text(context.getString(R.string.rotate_right)) }
                        }
                        Text(context.getString(R.string.crop_edges, cameraCropPercent), style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = cameraCropPercent.toFloat(),
                            onValueChange = { cameraCropPercent = it.toInt().coerceIn(0, 20) },
                            valueRange = 0f..20f,
                            steps = 3
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ::useCapturedPhoto, enabled = !uiState.busy && !preparingPhoto) { Text(context.getString(R.string.use_photo)) }
                        OutlinedButton(onClick = {
                            pendingCameraFile?.delete()
                            pendingCameraPath = null
                            cameraRotation = 0
                            cameraCropPercent = 3
                            capture()
                        }, enabled = !uiState.busy && !preparingPhoto) { Text(context.getString(R.string.retake)) }
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            ResultSection(context.getString(R.string.local_history)) {
                OutlinedButton(onClick = { showClearHistory = true }) { Text(context.getString(R.string.clear_all)) }
                history.take(10).forEach { entry ->
                    Card { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column {
                            Text(entry.displayName)
                            Text(entry.summary, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, entry.displayName)
                                putExtra(Intent.EXTRA_TEXT, "${entry.displayName}\n\n${entry.summary}")
                            }
                            context.startActivity(Intent.createChooser(share, context.getString(R.string.export_summary)))
                        }) { Text(context.getString(R.string.export)) }
                        OutlinedButton(onClick = {
                            releaseHistoryGrant(entry)
                            scope.launch { historyDao.delete(entry.id) }
                        }) { Text(context.getString(R.string.delete)) }
                    } }
                }
            }
        }
        if (showClearHistory) {
            AlertDialog(
                onDismissRequest = { showClearHistory = false },
                title = { Text(context.getString(R.string.clear_question)) },
                text = { Text(context.getString(R.string.clear_body)) },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            history.forEach(::releaseHistoryGrant)
                            historyDao.deleteAll()
                        }
                        showClearHistory = false
                    }) { Text(context.getString(R.string.clear_history)) }
                },
                dismissButton = { OutlinedButton(onClick = { showClearHistory = false }) { Text(context.getString(R.string.cancel)) } }
            )
        }
        uiState.analysis?.let { result ->
            HorizontalDivider()
            ModernSectionHeader(context.getString(R.string.found), context.getString(R.string.source_guidance))
            OutlinedButton(onClick = plainlyViewModel::clearCurrentDocument, enabled = !uiState.busy) {
                Text(context.getString(R.string.remove_document))
            }
            OutlinedButton(onClick = { pdfExport.launch("Plainly-analysis.pdf") }, enabled = !uiState.busy) {
                Text(context.getString(R.string.export_analysis))
            }
            uiState.languageTag?.let { Text(context.getString(R.string.detected_language, it)) }
            uiState.languageWarning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            FraudWhyCard(result.riskLevel, result.findings)
            result.decisionSummary?.let { DecisionSummaryCard(it) }
            if (result.officialContacts.isNotEmpty()) {
                ResultSection(context.getString(R.string.contacts)) {
                    Text(context.getString(R.string.contacts_notice))
                    result.officialContacts.forEach { match ->
                        Card {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(match.contact.name, style = MaterialTheme.typography.titleMedium)
                                Text(match.contact.purpose)
                                Text(match.contact.phone)
                                Text(context.getString(R.string.directory_checked, match.contact.checkedOn), style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${match.contact.phone}"))) }
                                        .onFailure { plainlyViewModel.setError(context.getString(R.string.no_phone)) }
                                }) { Text(context.getString(R.string.dial_agency)) }
                                OutlinedButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(match.contact.website))) }
                                        .onFailure { plainlyViewModel.setError(context.getString(R.string.no_browser)) }
                                }) { Text(context.getString(R.string.agency_website)) }
                                OutlinedButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(match.contact.sourceUrl))) }
                                        .onFailure { plainlyViewModel.setError(context.getString(R.string.no_browser)) }
                                }) { Text(context.getString(R.string.source_directory)) }
                                EvidenceCard(context.getString(R.string.agency_match), "Page ${match.page}: ${match.evidence}")
                            }
                        }
                    }
                }
            }
            Box {
                OutlinedButton(onClick = { languageMenu = true }, enabled = !uiState.busy) {
                    Text(context.getString(R.string.change_language, languageLabel(targetLanguage)))
                }
                DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                    supportedLanguages.forEach { (code, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            targetLanguage = code
                            languageMenu = false
                            plainlyViewModel.translate(code)
                        })
                    }
                }
            }
            uiState.translationStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(context.getString(R.string.translation_notice), style = MaterialTheme.typography.bodySmall)
            uiState.translated?.let { EvidenceCard(context.getString(R.string.summary_language, languageLabel(uiState.translatedLanguage ?: targetLanguage)), it) }
            OutlinedButton(onClick = { plainlyViewModel.translateOriginal(targetLanguage) }, enabled = !uiState.busy) { Text(context.getString(R.string.translate_letter)) }
            uiState.translatedOriginal?.let {
                TranslationComparison(uiState.lines.joinToString("\n") { line -> "[Page ${line.page}] ${line.text}" }, it,
                    languageLabel(uiState.originalTranslationLanguage ?: targetLanguage))
            }
            OutlinedButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) context.getString(R.string.hide_details) else context.getString(R.string.show_details))
            }
            speechError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            SpeechControls(
                    enabled = speechReady && !uiState.busy,
                    speaking = speaking,
                    paused = speechPaused,
                    onSpeak = {
                        val language = uiState.translatedLanguage ?: TranslateLanguage.ENGLISH
                        val locale = Locale.forLanguageTag(if (language == "tl") "fil" else language)
                        val voice = tts.voices?.firstOrNull { !it.isNetworkConnectionRequired && (it.locale.language == locale.language || it.locale.language == language) }
                        if (voice == null) {
                            speechError = context.getString(R.string.missing_voice, languageLabel(language))
                        } else {
                            tts.voice = voice
                            speechError = null
                            val text = uiState.translated ?: result.summary
                            lastSpeech = text
                            speechOffset = 0
                            speechBase = 0
                            speechPaused = false
                            speaking = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "plainly-summary") == TextToSpeech.SUCCESS
                            if (!speaking) speechError = context.getString(R.string.speech_failed)
                        }
                    },
                    onPause = { tts.stop(); speaking = false; speechPaused = true },
                    onResume = {
                        if (lastSpeech.isNotBlank()) {
                            speechBase = speechOffset.coerceIn(0, lastSpeech.length)
                            val remaining = lastSpeech.substring(speechBase)
                            speechPaused = false
                            speaking = true
                            tts.speak(remaining, TextToSpeech.QUEUE_FLUSH, null, "plainly-resume")
                        }
                    },
                    onReplay = {
                        if (lastSpeech.isNotBlank()) {
                            speechOffset = 0
                            speechBase = 0
                            speechPaused = false
                            speaking = true
                            tts.speak(lastSpeech, TextToSpeech.QUEUE_FLUSH, null, "plainly-replay")
                        }
                    }
                )
            if (showDetails) {
                ReviewWarning()
                ResultSection(context.getString(R.string.actions_dates)) {
                    result.actions.take(3).forEach { ActionCard(it) }
                    result.deadlines.forEach { deadline -> DeadlineCard(deadline) { calendarConfirmation = it } }
                    if (result.actions.isEmpty() && result.deadlines.isEmpty()) Text(context.getString(R.string.no_actions))
                }
                ResultSection(context.getString(R.string.warning_evidence)) {
                    result.findings.forEach { finding ->
                        EvidenceCard("${finding.label} — why to pause", "${riskExplanation(finding.label)}\n\nEvidence: ${finding.evidence}\nSource spans: ${finding.sourceSpanIds.joinToString()}")
                    }
                    if (result.findings.isEmpty()) Text(context.getString(R.string.no_phrases))
                }
                ResultSection(context.getString(R.string.original_text)) {
                    uiState.lines.groupBy { it.page }.toSortedMap().forEach { (page, pageLines) -> SourcePageCard(page, pageLines.map { it.text }) }
                }
            }
        }
        calendarConfirmation?.let { deadline ->
            AlertDialog(
                onDismissRequest = { calendarConfirmation = null },
                title = { Text(context.getString(R.string.confirm_date)) },
                text = {
                    Text(context.getString(R.string.confirm_date_body, deadline.date.toString(), (if (deadline.isCalculatedFromReferenceDate) "Calculated from the analysis date; confirm the reference date. " else "") + deadline.reviewReason.orEmpty(), deadline.evidence))
                },
                confirmButton = {
                    Button(onClick = {
                        calendarConfirmation = null
                        if (org.plainly.app.analysis.CalendarExportPolicy.canExport(deadline, true)) {
                            val intent = Intent(Intent.ACTION_INSERT).apply {
                                data = CalendarContract.Events.CONTENT_URI
                                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, deadline.date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, deadline.date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                                putExtra(CalendarContract.Events.TITLE, "Review Plainly document deadline")
                                putExtra(CalendarContract.Events.DESCRIPTION, "Possible deadline detected by Plainly. Confirmed by user; still check the original document.\n${deadline.reviewReason.orEmpty()}\n${deadline.evidence}")
                            }
                            runCatching { context.startActivity(intent) }.onFailure { plainlyViewModel.setError(context.getString(R.string.no_calendar)) }
                        }
                    }) { Text(context.getString(R.string.open_calendar)) }
                },
                dismissButton = { OutlinedButton(onClick = { calendarConfirmation = null }) { Text(context.getString(R.string.cancel)) } }
            )
        }
    }
}
