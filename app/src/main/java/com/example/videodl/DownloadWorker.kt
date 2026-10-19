package com.example.videodl

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.File

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val quality = Quality.valueOf(inputData.getString(KEY_QUALITY) ?: Quality.BEST.name)
        val customHeight = inputData.getInt(KEY_HEIGHT, 0)
        val playlist = inputData.getBoolean(KEY_PLAYLIST, false)
        val items = inputData.getString(KEY_ITEMS)?.takeIf { it.isNotBlank() }

        setForeground(foregroundInfo("Preparando…", -1))
        Engine.ensureReady(applicationContext)

        val tmp = File(applicationContext.cacheDir, "dl/$id").apply { mkdirs() }
        val request = YoutubeDLRequest(url).apply {
            Quality.apply(this, quality, customHeight)
            addOption("-o", "${tmp.absolutePath}/%(playlist_index&{} - |)s%(title).120B [%(id)s].%(ext)s")
            addOption("--windows-filenames") // evita caracteres que rechazan algunos almacenamientos
            addOption("--no-mtime")
            addOption("--no-part")
            // Ritmo "humano": menos peticiones por segundo = menos verificaciones anti-bot y 429.
            addOption("--sleep-requests", "1")
            if (playlist) {
                addOption("--yes-playlist")
                addOption("--ignore-errors") // un vídeo privado/borrado no tumba toda la lista
                addOption("--sleep-interval", "3")
                addOption("--max-sleep-interval", "8")
                items?.let { addOption("--playlist-items", it) }
            } else {
                addOption("--no-playlist")
            }
        }
        Session.applyTo(applicationContext, request)

        val processId = id.toString()
        var lastUpdate = 0L
        var lastLine = ""
        var error: String? = null

        try {
            coroutineScope {
                val job = async(Dispatchers.IO) {
                    YoutubeDL.getInstance().execute(request, processId) { progress, eta, line ->
                        lastLine = line
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 700) {
                            lastUpdate = now
                            setProgressAsync(workDataOf(KEY_PROGRESS to progress, KEY_LINE to line))
                            notify(line, progress.toInt(), eta)
                        }
                    }
                }
                try {
                    job.await()
                } catch (e: CancellationException) {
                    // execute() es bloqueante: hay que matar el proceso de yt-dlp explícitamente.
                    YoutubeDL.getInstance().destroyProcessById(processId)
                    throw e
                }
            }
        } catch (e: CancellationException) {
            tmp.deleteRecursively()
            throw e
        } catch (e: Exception) {
            error = explain(e.message ?: lastLine)
        }

        // Con --ignore-errors puede haber éxito parcial: se publica lo que sí se descargó.
        val saved = try {
            tmp.walkTopDown().filter { it.isFile }.map { Exporter.publish(applicationContext, it) }.toList()
        } finally {
            tmp.deleteRecursively()
        }

        val summary = buildString {
            append("${saved.size} archivo(s) en Descargas/VideoDownloader")
            error?.let { append("\n⚠ ").append(it) }
        }
        return if (saved.isEmpty() && error != null) Result.failure(workDataOf(KEY_MSG to error))
        else Result.success(workDataOf(KEY_MSG to summary))
    }

    private fun explain(msg: String): String = when {
        "confirm you" in msg && "bot" in msg || "not a robot" in msg || "captcha" in msg.lowercase() ->
            "El sitio pide verificar que eres humano. Abre «Verificación / sesión», resuélvela tú " +
                "e inicia sesión si hace falta, guarda la sesión y reintenta."
        "429" in msg -> "Demasiadas peticiones (HTTP 429). Espera unos minutos o usa la sesión verificada."
        "Unsupported URL" in msg -> "Sitio no soportado por yt-dlp."
        "Requested format is not available" in msg ->
            "Esa calidad no existe para este vídeo. Prueba «Máxima» o analiza las calidades."
        "Private video" in msg || "members-only" in msg ->
            "Vídeo privado/restringido: requiere una sesión con acceso."
        else -> msg.lineSequence().lastOrNull { it.isNotBlank() }?.take(300) ?: "Error desconocido"
    }

    private fun buildNotification(text: String, progress: Int) =
        NotificationCompat.Builder(applicationContext, App.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(inputData.getString(KEY_URL))
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress.coerceAtLeast(0), progress < 0)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel, "Cancelar",
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
            )
            .build()

    private fun notificationId() = id.hashCode()

    private fun foregroundInfo(text: String, progress: Int): ForegroundInfo {
        val n = buildNotification(text, progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ForegroundInfo(notificationId(), n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId(), n)
    }

    private fun notify(line: String, progress: Int, eta: Long) {
        val text = if (eta > 0) "$progress% · ${eta}s restantes" else line.take(80)
        setForegroundAsync(foregroundInfo(text, progress))
    }

    companion object {
        const val TAG = "download"
        const val KEY_URL = "url"
        const val KEY_QUALITY = "quality"
        const val KEY_HEIGHT = "height"
        const val KEY_PLAYLIST = "playlist"
        const val KEY_ITEMS = "items"
        const val KEY_PROGRESS = "progress"
        const val KEY_LINE = "line"
        const val KEY_MSG = "msg"
    }
}
