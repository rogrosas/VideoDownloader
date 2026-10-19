package com.example.videodl

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * El usuario abre el sitio, resuelve él mismo la verificación (y/o inicia sesión) y pulsa
 * «Guardar sesión». La app no interactúa con el captcha en ningún momento.
 */
class VerifyActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startUrl = intent.getStringExtra(EXTRA_URL)?.takeIf { it.startsWith("http") }
            ?: "https://www.youtube.com"

        setContent {
            MaterialTheme {
                var address by remember { mutableStateOf(startUrl) }
                var webView by remember { mutableStateOf<WebView?>(null) }
                val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    if (uri != null) {
                        val ok = Session.import(this, uri)
                        toast(if (ok) "cookies.txt importado" else "Archivo no válido (formato Netscape)")
                        if (ok) finish()
                    }
                }

                Scaffold { pad ->
                    Column(Modifier.padding(pad).fillMaxSize()) {
                        Text(
                            "Resuelve la verificación tú mismo en la página (e inicia sesión si el sitio " +
                                "lo pide). Luego pulsa «Guardar sesión».",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp, 8.dp)
                        )
                        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                address, { address = it }, singleLine = true,
                                modifier = Modifier.weight(1f), label = { Text("Dirección") }
                            )
                            Button(onClick = { webView?.loadUrl(address) }, Modifier.align(androidx.compose.ui.Alignment.CenterVertically)) {
                                Text("Ir")
                            }
                        }
                        AndroidView(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView, url: String) {
                                            address = url
                                        }
                                    }
                                    loadUrl(startUrl)
                                    webView = this
                                }
                            }
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = {
                                val wv = webView ?: return@Button
                                val urls = listOfNotNull(startUrl, wv.url)
                                val n = Session.saveFromWebView(this@VerifyActivity, urls, wv.settings.userAgentString)
                                toast(if (n > 0) "Sesión guardada ($n cookies)" else "No hay cookies que guardar")
                                if (n > 0) finish()
                            }, Modifier.weight(1f)) { Text("Guardar sesión") }
                            OutlinedButton(onClick = { importer.launch("*/*") }) { Text("Importar cookies.txt") }
                        }
                        TextButton(
                            onClick = { Session.clear(this@VerifyActivity); toast("Sesión borrada") },
                            modifier = Modifier.padding(start = 8.dp)
                        ) { Text("Borrar sesión guardada") }
                    }
                }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_URL = "url"
    }
}
