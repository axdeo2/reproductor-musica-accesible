package app.mimusica.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Comprobación REAL de internet: pide una respuesta vacía (HTTP 204) al mismo servidor que
 * usa Android para verificar redes. No depende de lo que el sistema informe, que en algunos
 * celulares sigue diciendo "conectado" con datos y WiFi apagados.
 */
class InternetProbe {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun check(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(URL).header("Cache-Control", "no-cache").build())
                .execute().use { it.code == 204 }
        }.getOrDefault(false)
    }

    private companion object {
        const val URL = "https://www.gstatic.com/generate_204"
    }
}
