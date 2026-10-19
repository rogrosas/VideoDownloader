package com.example.videodl

import com.yausername.youtubedl_android.YoutubeDLRequest

enum class Quality(val label: String, val maxHeight: Int?) {
    BEST("Máxima", null),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
    P360("360p", 360),
    AUDIO_MP3("Solo audio (MP3)", null);

    companion object {
        /**
         * Aplica la selección de formato. [customHeight] (> 0) gana sobre el preset: viene del
         * análisis del vídeo concreto. "<=" en vez de "==" hace que una playlist con vídeos de
         * distintas resoluciones no falle: toma la mejor que no supere el límite.
         */
        fun apply(request: YoutubeDLRequest, quality: Quality, customHeight: Int) {
            if (quality == AUDIO_MP3) {
                request.addOption("-f", "ba/b")
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "0")
                return
            }
            val h = if (customHeight > 0) customHeight else quality.maxHeight
            val format = if (h == null) "bv*+ba/b" else "bv*[height<=$h]+ba/b[height<=$h]/b"
            request.addOption("-f", format)
            // Prefiere H.264/AAC (reproducible en cualquier Android) a igualdad de resolución.
            request.addOption("-S", "vcodec:h264,acodec:m4a")
            request.addOption("--merge-output-format", "mp4")
        }
    }
}
