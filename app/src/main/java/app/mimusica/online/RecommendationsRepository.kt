package app.mimusica.online

import android.util.Log
import app.mimusica.data.ChildSettings
import app.mimusica.data.PlayableItem
import app.mimusica.data.RecommendationsSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * "Para ti" en el modo niña: lee el inicio de YouTube Music de la cuenta conectada y baja
 * las miniaturas a la caché (el modo niña solo muestra imágenes locales).
 */
class RecommendationsRepository(
    private val session: OnlineSession,
    cacheDir: File,
    private val settings: ChildSettings,
) : RecommendationsSource {
    private val thumbsDir = File(cacheDir, "para-ti")
    private val lock = Mutex()
    private var lastRefreshMs = 0L

    private val _items = MutableStateFlow<List<PlayableItem>>(emptyList())
    override val items: StateFlow<List<PlayableItem>> = _items.asStateFlow()

    override suspend fun refresh(force: Boolean) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!settings.showRecommendations.value) {
                _items.value = emptyList()
                return@withLock
            }
            val now = System.currentTimeMillis()
            if (!force && _items.value.isNotEmpty() && now - lastRefreshMs < REFRESH_EVERY_MS) return@withLock
            if (session.ensureRestored() == null) {
                _items.value = emptyList()
                return@withLock
            }
            val tracks = try {
                session.library.homeFeed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sin internet o YouTube no respondió: se conserva lo que había.
                Log.w(TAG, "No se pudo leer 'Para ti' (${e::class.simpleName})")
                return@withLock
            }.take(MAX_ITEMS)

            thumbsDir.mkdirs()
            val items = downloadThumbnails(tracks)
            _items.value = items
            lastRefreshMs = now

            val keep = items.mapNotNull { it.thumbnail?.name }.toSet()
            thumbsDir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        }
    }

    private suspend fun downloadThumbnails(tracks: List<RemoteTrack>): List<PlayableItem> = coroutineScope {
        val permits = Semaphore(4)
        tracks.map { t ->
            async {
                val file = File(thumbsDir, "${t.videoId}.jpg")
                if (!file.isFile) {
                    val url = t.thumbnailUrl ?: "https://i.ytimg.com/vi/${t.videoId}/hqdefault.jpg"
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

    private companion object {
        const val TAG = "ParaTi"
        const val MAX_ITEMS = 60
        const val REFRESH_EVERY_MS = 30L * 60_000L
    }
}
