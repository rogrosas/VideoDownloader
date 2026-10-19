package com.example.videodl

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val sharedUrl = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
        setContent { MaterialTheme { MainScreen(sharedUrl) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Extrae la URL de un texto compartido (las apps suelen añadir título + enlace). */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val m = Patterns.WEB_URL.matcher(text)
        if (m.find()) sharedUrl.value = m.group()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainScreen(sharedUrl: MutableState<String>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val wm = remember { WorkManager.getInstance(ctx) }

    var url by sharedUrl
    var quality by remember { mutableStateOf(Quality.BEST) }
    var heights by remember { mutableStateOf<List<Int>>(emptyList()) }
    var customHeight by remember { mutableIntStateOf(0) }
    var playlist by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (needed.isNotEmpty()) permissions.launch(needed.toTypedArray())
    }
    LaunchedEffect(url) { heights = emptyList(); customHeight = 0 }

    val jobs by wm.getWorkInfosByTagFlow(DownloadWorker.TAG).collectAsState(initial = emptyList())

    Scaffold { pad ->
        LazyColumn(
            Modifier.padding(pad).padding(horizontal = 16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Video Downloader", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 12.dp))
            }
            item {
                OutlinedTextField(
                    url, { url = it.trim() }, label = { Text("URL del vídeo o lista") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text("Calidad", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Quality.entries.forEach { q ->
                        FilterChip(
                            selected = quality == q && customHeight == 0,
                            onClick = { quality = q; customHeight = 0 },
                            label = { Text(q.label) }
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(enabled = url.isNotBlank() && !busy, onClick = {
                        busy = true; status = "Analizando…"
                        scope.launch {
                            status = try {
                                heights = Engine.availableHeights(ctx, url)
                                if (heights.isEmpty()) "No se encontraron pistas de vídeo" else null
                            } catch (e: Exception) {
                                "Error al analizar: ${e.message?.lineSequence()?.lastOrNull { it.isNotBlank() }}"
                            }
                            busy = false
                        }
                    }) { Text("Ver calidades disponibles") }
                    if (busy) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(24.dp))
                }
                if (heights.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        heights.forEach { h ->
                            FilterChip(
                                selected = customHeight == h,
                                onClick = { customHeight = h; if (quality == Quality.AUDIO_MP3) quality = Quality.BEST },
                                label = { Text("${h}p") }
                            )
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(playlist, { playlist = it })
                    Text("Descargar la lista completa", Modifier.padding(start = 8.dp))
                }
                if (playlist) {
                    OutlinedTextField(
                        items, { items = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Elementos (opcional), ej. 1-10,15") }
                    )
                }
            }
            item {
                Button(
                    enabled = url.startsWith("http"),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                            .setInputData(workDataOf(
                                DownloadWorker.KEY_URL to url,
                                DownloadWorker.KEY_QUALITY to quality.name,
                                DownloadWorker.KEY_HEIGHT to customHeight,
                                DownloadWorker.KEY_PLAYLIST to playlist,
                                DownloadWorker.KEY_ITEMS to items,
                            ))
                            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                            .addTag(DownloadWorker.TAG)
                            .addTag("url:$url")
                            .build()
                        wm.enqueue(request)
                        status = "Descarga añadida a la cola"
                    }
                ) { Text("Descargar") }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        ctx.startActivity(Intent(ctx, VerifyActivity::class.java).putExtra(VerifyActivity.EXTRA_URL, url))
                    }) { Text(if (Session.hasCookies(ctx)) "Sesión ✓" else "Verificación / sesión") }
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true; status = "Actualizando yt-dlp…"
                        scope.launch {
                            status = try { Engine.update(ctx) } catch (e: Exception) { "Error: ${e.message}" }
                            busy = false
                        }
                    }) { Text("Actualizar motor") }
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (jobs.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Descargas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { wm.pruneWork() }) { Text("Limpiar terminadas") }
                    }
                }
            }
            items(jobs.sortedBy { it.state.isFinished }, key = { it.id }) { info -> JobCard(info, wm) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun JobCard(info: WorkInfo, wm: WorkManager) {
    val url = info.tags.firstOrNull { it.startsWith("url:") }?.removePrefix("url:") ?: ""
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(url, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            when (info.state) {
                WorkInfo.State.RUNNING -> {
                    val p = info.progress.getFloat(DownloadWorker.KEY_PROGRESS, -1f)
                    if (p >= 0) LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(info.progress.getString(DownloadWorker.KEY_LINE).orEmpty(), maxLines = 2,
                        style = MaterialTheme.typography.bodySmall)
                }
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> Text("En cola…")
                WorkInfo.State.SUCCEEDED -> Text("✔ " + info.outputData.getString(DownloadWorker.KEY_MSG).orEmpty())
                WorkInfo.State.FAILED -> Text("✖ " + info.outputData.getString(DownloadWorker.KEY_MSG).orEmpty(),
                    color = MaterialTheme.colorScheme.error)
                WorkInfo.State.CANCELLED -> Text("Cancelada")
            }
            if (!info.state.isFinished) {
                TextButton(onClick = { wm.cancelWorkById(info.id) }) { Text("Cancelar") }
            }
        }
    }
}
