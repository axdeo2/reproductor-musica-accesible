package app.mimusica.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import app.mimusica.data.PlayableItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Reproductor del modo niña con cola propia.
 *
 * - Canciones guardadas: reproduce los archivos locales (video + audio juntos si hay video).
 * - Recomendaciones "Para ti": pide los enlaces a [online] justo antes de reproducir.
 *
 * Reglas del proyecto: la cola se repite; si una canción falla (archivo dañado, sin internet)
 * salta a la siguiente sin mostrar errores, y se detiene si TODAS fallan (nunca un bucle infinito).
 *
 * Provisional hasta la fase 2 (MediaSessionService, pantalla completa).
 */
@OptIn(UnstableApi::class)
class SimplePlayer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val online: OnlineStreams?,
) {
    private val localSources = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))

    /** Expuesto para conectar la superficie de video (PlayerView). */
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private var queue: List<PlayableItem> = emptyList()
    private var index = 0
    private var consecutiveFailures = 0
    private var loadJob: Job? = null

    private val _currentId = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** true mientras se obtiene el enlace de una canción en línea o se carga el archivo. */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Si la canción actual tiene imagen de video (si no, se muestra la miniatura). */
    private val _hasVideo = MutableStateFlow(false)
    val hasVideo: StateFlow<Boolean> = _hasVideo.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        _isLoading.value = false
                        consecutiveFailures = 0
                    }
                    Player.STATE_BUFFERING -> _isLoading.value = true
                    Player.STATE_ENDED -> next()
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) = skipAfterFailure()
        })
    }

    fun playQueue(items: List<PlayableItem>, start: Int) {
        if (items.isEmpty()) return
        queue = items
        index = start.coerceIn(items.indices)
        consecutiveFailures = 0
        load()
    }

    fun togglePlayPause() {
        if (queue.isEmpty()) return
        if (player.isPlaying) player.pause()
        else if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) load()
        else player.play()
    }

    fun next() {
        if (queue.isEmpty()) return
        index = (index + 1) % queue.size
        load()
    }

    fun previous() {
        if (queue.isEmpty()) return
        if (player.currentPosition > RESTART_THRESHOLD_MS) {
            player.seekTo(0)
            return
        }
        index = (index - 1 + queue.size) % queue.size
        load()
    }

    fun release() {
        loadJob?.cancel()
        player.release()
    }

    private fun load() {
        loadJob?.cancel()
        val item = queue.getOrNull(index) ?: return
        _currentId.value = item.id
        _hasVideo.value = item.videoFile != null
        _isLoading.value = true
        player.stop()
        loadJob = scope.launch {
            val source = if (item.isOnline) onlineSource(item) else localSource(item)
            if (source == null) {
                skipAfterFailure()
                return@launch
            }
            player.setMediaSource(source)
            player.prepare()
            player.play()
        }
    }

    private fun skipAfterFailure() {
        consecutiveFailures++
        if (consecutiveFailures >= queue.size) {
            // Todo falló (p. ej. solo hay recomendaciones y no hay internet): detenerse.
            player.stop()
            _isLoading.value = false
            consecutiveFailures = 0
            return
        }
        index = (index + 1) % queue.size
        load()
    }

    private fun localSource(item: PlayableItem): MediaSource {
        val audio = localSources.createMediaSource(mediaItem(item, Uri.fromFile(item.audioFile!!)))
        val video = item.videoFile ?: return audio
        return MergingMediaSource(localSources.createMediaSource(mediaItem(item, Uri.fromFile(video))), audio)
    }

    private suspend fun onlineSource(item: PlayableItem): MediaSource? {
        val stream = online?.resolve(item.id) ?: return null
        val headers = stream.headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(false)
            .setDefaultRequestProperties(headers)
            .apply { stream.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.let { setUserAgent(it.value) } }
        val sources = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context, http))
        val audio = sources.createMediaSource(mediaItem(item, Uri.parse(stream.audioUrl)))
        val videoUrl = stream.videoUrl ?: return audio
        _hasVideo.value = true
        return MergingMediaSource(sources.createMediaSource(mediaItem(item, Uri.parse(videoUrl))), audio)
    }

    private fun mediaItem(item: PlayableItem, uri: Uri) = MediaItem.Builder()
        .setMediaId(item.id)
        .setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist(item.artists).build())
        .build()

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
