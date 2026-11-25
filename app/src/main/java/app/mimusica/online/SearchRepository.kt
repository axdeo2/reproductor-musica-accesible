package app.mimusica.online

import app.mimusica.data.OnlineSearch
import app.mimusica.data.PlayableItem
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Búsqueda de videos en YouTube Music para el modo niña.
 *
 * Optimizaciones: caché de las últimas [CACHE_SIZE] búsquedas, máximo [MAX_RESULTS]
 * resultados, miniaturas descargadas en paralelo (4 a la vez) y reutilizadas entre búsquedas.
 * La pantalla además espera a que se deje de escribir y cancela búsquedas viejas.
 */
class SearchRepository(
    private val session: OnlineSession,
    cacheDir: File,
) : OnlineSearch {
    private val thumbsDir = File(cacheDir, "busqueda")
    private val cache = object : LinkedHashMap<String, List<PlayableItem>>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<PlayableItem>>) = size > CACHE_SIZE
    }

    override suspend fun search(query: String): List<PlayableItem> = withContext(Dispatchers.IO) {
        val key = query.trim().lowercase()
        if (key.isEmpty()) return@withContext emptyList()
        synchronized(cache) { cache[key] }?.let { return@withContext it }

        session.ensureRestored()
        val response = session.innerTube.search(
            client = YouTubeClient.WEB_REMIX,
            query = query.trim(),
            params = VIDEOS_FILTER,
        )
        if (!response.status.isSuccess()) throw IOException("Búsqueda: HTTP ${response.status.value}")
        val out = LinkedHashMap<String, RemoteTrack>()
        collectTracks(json.parseToJsonElement(response.bodyAsText()), out)
        val items = withThumbnails(out.values.take(MAX_RESULTS))
        synchronized(cache) { cache[key] = items }
        pruneThumbnails()
        items
    }

    private suspend fun withThumbnails(tracks: List<RemoteTrack>): List<PlayableItem> = coroutineScope {
        thumbsDir.mkdirs()
        val permits = Semaphore(4)
        tracks.map { t ->
            async {
                val file = File(thumbsDir, "${t.videoId}.jpg")
                if (!file.isFile) {
                    // Los videos tienen miniatura 16:9 en i.ytimg.com (mejor que la cuadrada).
                    val url = "https://i.ytimg.com/vi/${t.videoId}/hqdefault.jpg"
                    permits.withPermit { runCatching { fetch(url, file) } }
                }
                PlayableItem(
                    id = t.videoId,
                    title = t.title,
                    artists = t.artists,
                    durationText = t.durationText,
                    thumbnail = file.takeIf { it.isFile },
                )
            }
        }.awaitAll()
    }

    private fun fetch(url: String, target: File) {
        val tmp = File(target.parentFile, target.name + ".part")
        session.okHttp.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return
            tmp.outputStream().use { resp.body.byteStream().copyTo(it) }
        }
        tmp.renameTo(target)
    }

    /** Mantiene la caché de miniaturas acotada (borra las más viejas). */
    private fun pruneThumbnails() {
        val files = thumbsDir.listFiles() ?: return
        if (files.size <= MAX_THUMBS) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_THUMBS).forEach { it.delete() }
    }

    private companion object {
        /** Filtro "Videos" de YouTube Music (mismo valor que usa Metrolist). */
        const val VIDEOS_FILTER = "EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"
        const val MAX_RESULTS = 20
        const val CACHE_SIZE = 20
        const val MAX_THUMBS = 300
        val json = Json { ignoreUnknownKeys = true }
    }
}
