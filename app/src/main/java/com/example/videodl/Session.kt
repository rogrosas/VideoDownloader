package com.example.videodl

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/**
 * Sesión del usuario para yt-dlp: cookies (formato Netscape) + User-Agent.
 *
 * No resuelve captchas: el usuario los resuelve en un WebView (o importa un cookies.txt de su
 * navegador) y aquí solo se reutiliza esa sesión. El User-Agent se guarda porque cookies como
 * `cf_clearance` (Cloudflare) solo son válidas con el mismo UA con el que se obtuvieron.
 */
object Session {
    private fun cookieFile(ctx: Context) = File(ctx.filesDir, "cookies.txt")
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun hasCookies(ctx: Context) = cookieFile(ctx).length() > 0

    fun applyTo(ctx: Context, request: YoutubeDLRequest) {
        val file = cookieFile(ctx)
        if (file.length() > 0) request.addOption("--cookies", file.absolutePath)
        prefs(ctx).getString("ua", null)?.let { request.addOption("--user-agent", it) }
    }

    /** Vuelca las cookies del WebView para las URLs dadas. Devuelve cuántas se guardaron. */
    fun saveFromWebView(ctx: Context, urls: List<String>, userAgent: String): Int {
        val cm = CookieManager.getInstance()
        cm.flush()
        val expiry = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
        val lines = linkedMapOf<String, String>() // clave dominio+nombre para no duplicar
        for (url in urls.distinct()) {
            val host = Uri.parse(url).host ?: continue
            val domain = "." + host.removePrefix("www.").removePrefix("m.")
            val raw = cm.getCookie(url) ?: continue
            raw.split(";").map { it.trim() }.filter { "=" in it }.forEach { pair ->
                val name = pair.substringBefore("=")
                val value = pair.substringAfter("=")
                lines["$domain|$name"] = listOf(domain, "TRUE", "/", "TRUE", expiry, name, value)
                    .joinToString("\t")
            }
        }
        if (lines.isEmpty()) return 0
        cookieFile(ctx).writeText("# Netscape HTTP Cookie File\n" + lines.values.joinToString("\n") + "\n")
        prefs(ctx).edit().putString("ua", userAgent).apply()
        return lines.size
    }

    /** Importa un cookies.txt exportado desde el navegador de escritorio. */
    fun import(ctx: Context, uri: Uri): Boolean {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: return false
        val valid = text.lineSequence().any { !it.startsWith("#") && it.split("\t").size >= 7 }
        if (!valid) return false
        cookieFile(ctx).writeText(
            if (text.startsWith("# Netscape") || text.startsWith("# HTTP")) text
            else "# Netscape HTTP Cookie File\n$text"
        )
        // El UA del navegador de origen es desconocido: mejor no forzar el del WebView.
        prefs(ctx).edit().remove("ua").apply()
        return true
    }

    fun clear(ctx: Context) {
        cookieFile(ctx).delete()
        prefs(ctx).edit().clear().apply()
        CookieManager.getInstance().removeAllCookies(null)
    }
}
