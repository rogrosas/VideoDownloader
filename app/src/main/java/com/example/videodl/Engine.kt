package com.example.videodl

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Envoltorio de yt-dlp. La primera inicialización descomprime Python (~segundos), por eso es perezosa. */
object Engine {
    private val mutex = Mutex()
    @Volatile private var ready = false

    suspend fun ensureReady(context: Context) = mutex.withLock {
        if (ready) return@withLock
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            YoutubeDL.getInstance().init(app)
            FFmpeg.getInstance().init(app)
        }
        ready = true
    }

    /** Los sitios cambian a menudo; un yt-dlp desactualizado es la causa nº 1 de fallos. */
    suspend fun update(context: Context): String = withContext(Dispatchers.IO) {
        ensureReady(context)
        val status = YoutubeDL.getInstance()
            .updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel._STABLE)
        "yt-dlp: ${status?.name ?: "sin cambios"} (${YoutubeDL.getInstance().version(context.applicationContext)})"
    }

    /** Alturas (p. ej. 2160, 1080, 720…) realmente disponibles para un vídeo individual. */
    suspend fun availableHeights(context: Context, url: String): List<Int> = withContext(Dispatchers.IO) {
        ensureReady(context)
        val request = YoutubeDLRequest(url).apply { addOption("--no-playlist") }
        Session.applyTo(context, request)
        val info = YoutubeDL.getInstance().getInfo(request)
        info.formats.orEmpty()
            .filter { it.vcodec != "none" }
            .map { it.height }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
    }
}
