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
        appendLine("- ${it.date}${if (it.needsReview) " (review required)" else ""}${if (it.isCalculatedFromReferenceDate) " (calculated from analysis date)" else ""}: ${it.evidence}")
    }
    appendLine()
    appendLine("Warning signals (heuristic; review original)")
    if (result.findings.isEmpty()) appendLine("- None matched; this does not verify legitimacy")
    result.findings.forEach { finding ->
        appendLine("- ${finding.label}: ${finding.evidence}")
        appendLine("  Source spans: ${finding.sourceSpanIds.joinToString()}")
    }
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
    val cameraUri = cameraFile?.let { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
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
    var lastSpeech by remember { mutableStateOf("") }
    var targetLanguage by remember { mutableStateOf(TranslateLanguage.SPANISH) }
    var languageMenu by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var ocrLanguageMenu by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showClearHistory by remember { mutableStateOf(false) }
    var calendarConfirmation by remember { mutableStateOf<Deadline?>(null) }
    val tts = remember { TextToSpeech(context) { status -> speechReady = status == TextToSpeech.SUCCESS } }
    DisposableEffect(tts) {
        tts.language = Locale.US
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { }
            override fun onDone(utteranceId: String?) { speaking = false }
            override fun onError(utteranceId: String?) { speaking = false }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) { speechOffset = start }
        })
        onDispose { tts.stop(); tts.shutdown() }
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppHeader { showInfo = true }
        Text("Your private document companion", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("✦", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Start with a document", style = MaterialTheme.typography.titleLarge)
                        Text("Clarity in a few seconds", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Get a plain-language summary, deadlines, actions, and caution signals on your phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !uiState.busy) { Text("Choose file") }
                    OutlinedButton(onClick = ::capture, enabled = !uiState.busy) { Text("Use camera") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("On-device analysis")
            StatusChip("No cloud document analysis")
        }
        Box {
            OutlinedButton(onClick = { ocrLanguageMenu = true }, enabled = !uiState.busy) {
                Text("OCR language: ${uiState.ocrLanguage.label}")
            }
            DropdownMenu(expanded = ocrLanguageMenu, onDismissRequest = { ocrLanguageMenu = false }) {
                OcrLanguage.entries.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.label) },
                        onClick = { plainlyViewModel.setOcrLanguage(language); ocrLanguageMenu = false }
                    )
                }
            }
        }
        Text("OCR uses the bundled Latin-script model. Review every result against the original document.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (showInfo) {
            AlertDialog(
                onDismissRequest = { showInfo = false },
                title = { Text("Privacy and limitations") },
                text = {
                    Text("OCR, document analysis, and camera preprocessing run on this device. Translation models may download the first time you choose a language. No sender identity or document authenticity is verified. A lack of warning signals does not mean a document is safe. OCR supports bundled Latin-script recognition only.")
                },
                confirmButton = { Button(onClick = { showInfo = false }) { Text(" understood ") } }
            )
        }
        if (uiState.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        pendingCameraPath?.let {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Camera photo ready. Check the image before OCR.", style = MaterialTheme.typography.titleMedium)
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
                        preview?.let { Image(it, contentDescription = "Captured document preview", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { cameraRotation = (cameraRotation + 270) % 360 }) { Text("Rotate left") }
                            OutlinedButton(onClick = { cameraRotation = (cameraRotation + 90) % 360 }) { Text("Rotate right") }
                        }
                        Text("Crop edges: $cameraCropPercent%", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = cameraCropPercent.toFloat(),
                            onValueChange = { cameraCropPercent = it.toInt().coerceIn(0, 20) },
                            valueRange = 0f..20f,
                            steps = 3
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ::useCapturedPhoto, enabled = !uiState.busy && !preparingPhoto) { Text("Use photo") }
                        OutlinedButton(onClick = {
                            pendingCameraFile?.delete()
                            pendingCameraPath = null
                            cameraRotation = 0
                            cameraCropPercent = 3
                            capture()
                        }, enabled = !uiState.busy && !preparingPhoto) { Text("Retake") }
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            ResultSection("Local history") {
                OutlinedButton(onClick = { showClearHistory = true }) { Text("Clear all local history") }
                history.take(10).forEach { entry ->
                    Card { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.displayName)
                            Text(entry.summary, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, entry.displayName)
                                putExtra(Intent.EXTRA_TEXT, "${entry.displayName}\n\n${entry.summary}")
                            }
                            context.startActivity(Intent.createChooser(share, "Export summary"))
                        }) { Text("Export") }
                        OutlinedButton(onClick = {
                            releaseHistoryGrant(entry)
                            scope.launch { historyDao.delete(entry.id) }
                        }) { Text("Delete") }
                    } }
                }
            }
        }
        if (showClearHistory) {
            AlertDialog(
                onDismissRequest = { showClearHistory = false },
                title = { Text("Clear local history?") },
                text = { Text("This removes saved names, source references, and summaries from this device. It does not delete the original files.") },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            history.forEach(::releaseHistoryGrant)
                            historyDao.deleteAll()
                        }
                        showClearHistory = false
                    }) { Text("Clear history") }
                },
                dismissButton = { OutlinedButton(onClick = { showClearHistory = false }) { Text("Cancel") } }
            )
        }
        uiState.analysis?.let { result ->
            HorizontalDivider()
            ModernSectionHeader("What we found", "Plain-language guidance first, source evidence always available")
            OutlinedButton(onClick = plainlyViewModel::clearCurrentDocument, enabled = !uiState.busy) {
                Text("Remove current document")
            }
            OutlinedButton(onClick = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Plainly analysis")
                    putExtra(Intent.EXTRA_TEXT, analysisExportText(result))
                }
                context.startActivity(Intent.createChooser(share, "Export analysis"))
            }) { Text("Export analysis") }
            uiState.languageTag?.let { Text("Detected language: $it") }
            uiState.languageWarning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            FraudWhyCard(result.riskLevel, result.findings)
            result.decisionSummary?.let { DecisionSummaryCard(it) }
            if (result.officialContacts.isNotEmpty()) {
                ResultSection("Official contacts") {
                    Text("Bundled directory matches a name in the letter. This does not verify its sender. Confirm that this agency serves you.")
                    result.officialContacts.forEach { match ->
                        Card {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(match.contact.name, style = MaterialTheme.typography.titleMedium)
                                Text(match.contact.purpose)
                                Text(match.contact.phone)
                                Text("Directory checked ${match.contact.checkedOn}", style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${match.contact.phone}"))) }
                                        .onFailure { plainlyViewModel.setError("No phone app is available") }
                                }) { Text("Open agency number in dialer") }
                                OutlinedButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(match.contact.website))) }
                                        .onFailure { plainlyViewModel.setError("No browser is available") }
                                }) { Text("Open official website") }
                                OutlinedButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(match.contact.sourceUrl))) }
                                        .onFailure { plainlyViewModel.setError("No browser is available") }
                                }) { Text("Check directory source") }
                                EvidenceCard("Agency-name match", "Page ${match.page}: ${match.evidence}")
                            }
                        }
                    }
                }
            }
            Box {
                OutlinedButton(onClick = { languageMenu = true }, enabled = !uiState.busy) {
                    Text("Change language · ${languageLabel(targetLanguage)}")
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
            uiState.translated?.let { EvidenceCard("${languageLabel(targetLanguage)} summary", it) }
            OutlinedButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "Hide source & details" else "Review source & details")
            }
            if (showDetails) {
                ReviewWarning()
                SpeechControls(
                    enabled = speechReady,
                    speaking = speaking,
                    paused = speechPaused,
                    onSpeak = {
                        val text = result.decisionSummary?.let { it.whatItIs + " " + it.nextSteps.joinToString(" ") } ?: result.summary
                        lastSpeech = text
                        speechOffset = 0
                        speechPaused = false
                        speaking = true
                        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "plainly-summary")
                    },
                    onPause = { tts.stop(); speaking = false; speechPaused = true },
                    onResume = {
                        if (lastSpeech.isNotBlank()) {
                            val remaining = lastSpeech.substring(speechOffset.coerceIn(0, lastSpeech.length))
                            speechPaused = false
                            speaking = true
                            tts.speak(remaining, TextToSpeech.QUEUE_FLUSH, null, "plainly-resume")
                        }
                    },
                    onReplay = {
                        if (lastSpeech.isNotBlank()) {
                            speechOffset = 0
                            speechPaused = false
                            speaking = true
                            tts.speak(lastSpeech, TextToSpeech.QUEUE_FLUSH, null, "plainly-replay")
                        }
                    }
                )
                ResultSection("Actions and deadlines") {
                    result.actions.take(3).forEach { ActionCard(it) }
                    result.deadlines.forEach { deadline -> DeadlineCard(deadline) { calendarConfirmation = it } }
                    if (result.actions.isEmpty() && result.deadlines.isEmpty()) Text("No clear action or response deadline detected.")
                }
                ResultSection("Warning evidence") {
                    result.findings.forEach { finding ->
                        EvidenceCard("${finding.label} — why to pause", "${riskExplanation(finding.label)}\n\nEvidence: ${finding.evidence}\nSource spans: ${finding.sourceSpanIds.joinToString()}")
                    }
                    if (result.findings.isEmpty()) Text("No matching warning phrases were found.")
                }
                ResultSection("Extracted original text") {
                    uiState.lines.groupBy { it.page }.toSortedMap().forEach { (page, pageLines) -> SourcePageCard(page, pageLines.map { it.text }) }
                }
            }
        }
        calendarConfirmation?.let { deadline ->
            AlertDialog(
                onDismissRequest = { calendarConfirmation = null },
                title = { Text("Confirm this date") },
                text = {
                    Text("Plainly detected ${deadline.date}${if (deadline.isCalculatedFromReferenceDate) " as a calculation from the analysis date" else ""}${if (deadline.needsReview) " and marked it for review" else ""}. Check the original document before adding it to your calendar.\n\n${deadline.reviewReason.orEmpty()}\n${deadline.evidence}")
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
                            runCatching { context.startActivity(intent) }.onFailure { plainlyViewModel.setError("No calendar app is available") }
                        }
                    }) { Text("Confirm and open calendar") }
                },
                dismissButton = { OutlinedButton(onClick = { calendarConfirmation = null }) { Text("Cancel") } }
            )
        }
    }
}
