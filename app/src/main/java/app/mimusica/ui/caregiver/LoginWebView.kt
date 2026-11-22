package app.mimusica.ui.caregiver

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.mimusica.online.AccountSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

/** Solo se permite navegar dentro de dominios de Google/YouTube durante el inicio de sesión. */
private val ALLOWED_HOST = Regex(
    "(^|\\.)(google|youtube|gstatic|googleusercontent|ggpht|ytimg)\\.[a-z]{2,3}(\\.[a-z]{2})?$"
)

/** Lee la configuración de la página de YouTube Music ya con la sesión iniciada. */
private const val READ_CONFIG_JS = """
(function () {
  var c = window.yt && window.yt.config_;
  if (!c) return null;
  return JSON.stringify({
    v: c.VISITOR_DATA || null,
    d: c.DATASYNC_ID || null,
    s: (c.SESSION_INDEX !== undefined && c.SESSION_INDEX !== null) ? String(c.SESSION_INDEX) : null
  });
})()
"""

/**
 * Inicio de sesión con Google en un WebView (mismo método que Metrolist): el cuidador
 * escribe sus datos en la página oficial de Google; al llegar a music.youtube.com con
 * sesión se leen la cookie y la configuración de la página.
 *
 * La app nunca ve ni guarda la contraseña.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginWebView(
    onLoggedIn: (AccountSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                val cookies = CookieManager.getInstance()
                cookies.setAcceptCookie(true)
                cookies.setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    private var done = false

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val host = request.url.host ?: return true
                        return !ALLOWED_HOST.containsMatchIn(host)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        if (done || url == null || Uri.parse(url).host != "music.youtube.com") return
                        val cookie = cookies.getCookie("https://music.youtube.com") ?: return
                        val loggedIn = cookie.contains("SAPISID=") || cookie.contains("__Secure-3PAPISID=")
                        if (!loggedIn) return
                        view.evaluateJavascript(READ_CONFIG_JS) { raw ->
                            val config = parseConfig(raw) ?: return@evaluateJavascript
                            val visitor = config["v"] ?: return@evaluateJavascript
                            if (done) return@evaluateJavascript
                            done = true
                            cookies.flush()
                            onLoggedIn(
                                AccountSession(
                                    cookie = cookie,
                                    visitorData = visitor,
                                    dataSyncId = config["d"],
                                    authUser = config["s"] ?: "0",
                                )
                            )
                        }
                    }
                }
                loadUrl(LOGIN_URL)
            }
        },
    )
}

/** `evaluateJavascript` devuelve el resultado codificado como JSON (un string entre comillas). */
private fun parseConfig(raw: String?): Map<String, String?>? = runCatching {
    if (raw == null || raw == "null") return null
    val inner = Json.decodeFromString<String>(raw)
    val obj = Json.parseToJsonElement(inner) as JsonObject
    obj.mapValues { (_, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content }
}.getOrNull()

/** Borra las cookies de Google del WebView (al cerrar sesión). */
fun clearWebLoginCookies() {
    CookieManager.getInstance().apply {
        removeAllCookies(null)
        flush()
    }
}
