package app.mimusica.online

import app.mimusica.data.LocalLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DownloadStatus { EnCola, Descargando, Listo, Error }

data class DownloadState(val status: DownloadStatus, val progress: Float = 0f, val error: String? = null)

/** Cola de descargas: una a la vez, con una pausa entre canciones para no saturar a YouTube. */
class DownloadQueue(
    session: OnlineSession,
    private val library: LocalLibrary,
    scope: CoroutineScope,
) {
    private val downloader = StreamDownloader(session, library)
    private val queue = Channel<RemoteTrack>(Channel.UNLIMITED)
    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    init {
        scope.launch {
            for (track in queue) process(track)
        }
    }

    /** Agrega a la cola las canciones que aún no están guardadas ni en cola. Devuelve cuántas. */
    fun enqueue(tracks: List<RemoteTrack>): Int {
        var added = 0
        for (t in tracks) {
            if (library.contains(t.videoId)) continue
            val current = _states.value[t.videoId]?.status
            if (current == DownloadStatus.EnCola || current == DownloadStatus.Descargando) continue
            set(t.videoId, DownloadState(DownloadStatus.EnCola))
            queue.trySend(t)
            added++
        }
        return added
    }

    private suspend fun process(track: RemoteTrack) {
        set(track.videoId, DownloadState(DownloadStatus.Descargando))
        try {
            downloader.download(track) { p ->
                set(track.videoId, DownloadState(DownloadStatus.Descargando, p))
            }
            set(track.videoId, DownloadState(DownloadStatus.Listo, 1f))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            set(track.videoId, DownloadState(DownloadStatus.Error, error = e.message ?: e::class.simpleName))
        }
        delay(PAUSE_BETWEEN_MS)
    }

    private fun set(id: String, state: DownloadState) = _states.update { it + (id to state) }

    private companion object {
        const val PAUSE_BETWEEN_MS = 1_500L
    }
}
