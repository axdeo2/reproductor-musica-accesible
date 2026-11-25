package app.mimusica.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reproductor del modo niña con cola propia.
 *
 * - Canciones guardadas: reproduce los archivos locales (video + audio juntos si hay video).
 * - Recomendaciones "Para ti": pide los enlaces a [online] justo antes de reproducir.
 *
 * Reglas del proyecto: la cola se repite; si una canción falla (archivo dañado, sin internet)
 * salta a la siguiente sin mostrar errores, y se detiene si TODAS fallan (nunca un bucle infinito).
 * Si se corta el internet durante una canción en línea, sigue con las canciones descargadas.
 *
 * Provisional hasta la fase 2 (MediaSessionService, pantalla completa).
 */
@OptIn(UnstableApi::class)
class SimplePlayer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val online: OnlineStreams?,
    /** true si hay internet verificado. */
    private val hasInternet: () -> Boolean = { true },
    /** Canciones guardadas para seguir sonando si se corta el internet. */
    private val offlineFallback: () -> List<PlayableItem> = { emptyList() },
    /** Guarda la última canción y su posición para retomarla al volver a abrir la app. */
    private val memory: PlaybackMemory? = null,
) {
    private val localSources = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))

    /**
     * Búfer amplio, como YouTube: mientras hay señal se guarda hasta 5 minutos por delante
     * (casi toda la canción), así un corte o una baja de velocidad del internet no interrumpe
     * la música. Empieza a sonar con 2 s cargados y, si llega a cortarse, retoma con 4 s.
     */
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ MIN_BUFFER_MS,
            /* maxBufferMs = */ MAX_BUFFER_MS,
            /* bufferForPlaybackMs = */ START_BUFFER_MS.toInt(),
            /* bufferForPlaybackAfterRebufferMs = */ REBUFFER_MS.toInt(),
        )
        .setTargetBufferBytes(TARGET_BUFFER_BYTES)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    /** Expuesto para conectar la superficie de video (PlayerView). */
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        // Pausa si suena una llamada u otra app reproduce audio; reanuda al terminar.
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true,
        )
        // Pausa si se desconectan los audífonos (para que no suene de golpe por el parlante).
        .setHandleAudioBecomingNoisy(true)
        // Mantiene CPU y WiFi despiertos con la pantalla apagada mientras suena.
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    /** Repetir la canción actual en bucle (botón pequeño sobre el video). */
    private val _repeatOne = MutableStateFlow(false)
    val repeatOne: StateFlow<Boolean> = _repeatOne.asStateFlow()

    fun toggleRepeatOne() {
        _repeatOne.value = !_repeatOne.value
        // No se usa REPEAT_MODE_ONE de ExoPlayer: con video y audio unidos (MergingMediaSource)
        // no siempre repetía. El bucle se hace a mano al terminar la canción (ver STATE_ENDED).
        player.repeatMode = Player.REPEAT_MODE_OFF
    }

    /** Al terminar una canción: si el bucle está activo vuelve a empezar; si no, la siguiente. */
    private fun onSongEnded() {
        if (_repeatOne.value) {
            player.seekTo(0)
            player.playWhenReady = true
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
        } else {
            next()
        }
    }

    /** true si hay una cola cargada (para no reemplazarla al restaurar). */
    val hasQueue: Boolean get() = queue.isNotEmpty()
    private var saveJob: Job? = null

    private var queue: List<PlayableItem> = emptyList()
    private var index = 0
    private var consecutiveFailures = 0
    private var loadJob: Job? = null

    private val _currentId = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** Etapa de carga de la canción actual (para mostrar el progreso en pantalla). */
    private val _loadState = MutableStateFlow<LoadState>(LoadState.Idle)
    val loadState: StateFlow<LoadState> = _loadState.asStateFlow()

    /** Enlaces ya obtenidos por adelantado (la siguiente canción), con hora de obtención. */
    private val prefetched = mutableMapOf<String, Pair<ResolvedStream, Long>>()
    private var prefetchJob: Job? = null
    private var bufferWatchJob: Job? = null

    /** Si la canción actual tiene imagen de video (si no, se muestra la miniatura). */
    private val _hasVideo = MutableStateFlow(false)
    val hasVideo: StateFlow<Boolean> = _hasVideo.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) startSaving() else saveNow()
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        stopBufferWatch()
                        _loadState.value = LoadState.Ready
                        consecutiveFailures = 0
                        prefetchNext()
                    }
                    Player.STATE_BUFFERING -> startBufferWatch()
                    Player.STATE_ENDED -> onSongEnded()
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

    /** Deja lista (en pausa) la última canción que sonaba, en el segundo donde iba. */
    fun restore(items: List<PlayableItem>, start: Int, positionMs: Long) {
        if (items.isEmpty() || hasQueue) return
        queue = items
        index = start.coerceIn(items.indices)
        consecutiveFailures = 0
        load(startPositionMs = positionMs, autoPlay = false)
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
        // Sin internet no se intenta la siguiente canción en línea: se pasa a las descargadas.
        if (queue[index].isOnline && !hasInternet()) {
            val saved = offlineFallback()
            if (saved.isNotEmpty()) {
                playQueue(saved, 0)
                return
            }
        }
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
        saveNow()
        saveJob?.cancel()
        loadJob?.cancel()
        prefetchJob?.cancel()
        stopBufferWatch()
        player.release()
    }

    private fun load(startPositionMs: Long = 0L, autoPlay: Boolean = true) {
        loadJob?.cancel()
        val item = queue.getOrNull(index) ?: return
        _currentId.value = item.id
        _hasVideo.value = item.videoFile != null || item.localIsVideo
        _loadState.value = if (item.isOnline) LoadState.Searching else LoadState.Buffering(0)
        stopBufferWatch()
        player.stop()
        loadJob = scope.launch {
            // Si una canción en línea no se consigue en 25 s, se pasa a la siguiente.
            val source = if (item.isOnline) withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { onlineSource(item) }
            else localSource(item)
            if (source == null) {
                skipAfterFailure()
                return@launch
            }
            player.setMediaSource(source, startPositionMs)
            player.prepare()
            player.playWhenReady = autoPlay
            memory?.save(item.id, startPositionMs)
            if (!autoPlay) _loadState.value = LoadState.Ready
        }
    }

    /** Guarda la posición cada 5 s mientras suena. */
    private fun startSaving() {
        if (saveJob?.isActive == true) return
        saveJob = scope.launch {
            while (isActive) {
                delay(SAVE_EVERY_MS)
                saveNow()
            }
        }
    }

    private fun saveNow() {
        val id = _currentId.value ?: return
        memory?.save(id, player.currentPosition.coerceAtLeast(0))
    }

    private fun skipAfterFailure() {
        // Se cortó el internet con una canción en línea: seguir con las descargadas.
        if (queue.getOrNull(index)?.isOnline == true && !hasInternet()) {
            val saved = offlineFallback()
            if (saved.isNotEmpty() && saved !== queue) {
                playQueue(saved, 0)
                return
            }
        }
        consecutiveFailures++
        if (consecutiveFailures >= queue.size) {
            // Todo falló (p. ej. solo hay recomendaciones y no hay internet): detenerse.
            player.stop()
            stopBufferWatch()
            _loadState.value = LoadState.Idle
            consecutiveFailures = 0
            return
        }
        index = (index + 1) % queue.size
        load()
    }

    private fun localSource(item: PlayableItem): MediaSource {
        item.localUri?.let { return localSources.createMediaSource(mediaItem(item, it)) }
        val audio = localSources.createMediaSource(mediaItem(item, Uri.fromFile(item.audioFile!!)))
        val video = item.videoFile ?: return audio
        return MergingMediaSource(localSources.createMediaSource(mediaItem(item, Uri.fromFile(video))), audio)
    }

    private suspend fun onlineSource(item: PlayableItem): MediaSource? {
        val stream = takePrefetched(item.id) ?: online?.resolve(item.id) ?: return null
        _loadState.value = LoadState.Buffering(0)
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

    /**
     * Porcentaje REAL de lo que falta cargar para empezar a sonar: lo almacenado por delante de
     * la posición actual respecto del mínimo que ExoPlayer necesita para arrancar.
     */
    private fun startBufferWatch() {
        if (bufferWatchJob?.isActive == true) return
        bufferWatchJob = scope.launch {
            while (isActive) {
                val ahead = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0)
                val needed = if (player.currentPosition == 0L) START_BUFFER_MS else REBUFFER_MS
                val percent = (ahead * 100 / needed).toInt().coerceIn(0, 99)
                if (_loadState.value !is LoadState.Searching) _loadState.value = LoadState.Buffering(percent)
                delay(250)
            }
        }
    }

    private fun stopBufferWatch() {
        bufferWatchJob?.cancel()
        bufferWatchJob = null
    }

    /** Mientras suena una canción, se piden por adelantado los enlaces de la siguiente en línea. */
    private fun prefetchNext() {
        val streams = online ?: return
        if (queue.size < 2 || !hasInternet()) return
        val next = queue[(index + 1) % queue.size]
        if (!next.isOnline || takePrefetchedPeek(next.id)) return
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            val resolved = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { streams.resolve(next.id) } ?: return@launch
            prefetched.entries.removeAll { System.currentTimeMillis() - it.value.second > PREFETCH_TTL_MS }
            prefetched[next.id] = resolved to System.currentTimeMillis()
        }
    }

    private fun takePrefetchedPeek(id: String): Boolean =
        prefetched[id]?.let { System.currentTimeMillis() - it.second < PREFETCH_TTL_MS } == true

    private fun takePrefetched(id: String): ResolvedStream? {
        val entry = prefetched.remove(id) ?: return null
        return entry.first.takeIf { System.currentTimeMillis() - entry.second < PREFETCH_TTL_MS }
    }

    private fun mediaItem(item: PlayableItem, uri: Uri) = MediaItem.Builder()
        .setMediaId(item.id)
        .setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist(item.artists).build())
        .build()

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
        const val SAVE_EVERY_MS = 5_000L
        const val RESOLVE_TIMEOUT_MS = 25_000L
        /** Lo cargado necesario para empezar a sonar y para retomar tras un corte. */
        const val START_BUFFER_MS = 2_000L
        const val REBUFFER_MS = 4_000L
        /** Se sigue cargando hasta tener 5 min por delante; nunca baja de 1 min si hay señal. */
        const val MIN_BUFFER_MS = 60_000
        const val MAX_BUFFER_MS = 300_000
        /** Hasta 80 MB en memoria (alcanza para video 480p + audio de una canción). */
        const val TARGET_BUFFER_BYTES = 80 * 1024 * 1024
        /** Los enlaces de YouTube duran horas; por seguridad se reutilizan solo 30 min. */
        const val PREFETCH_TTL_MS = 30L * 60_000L
    }
}

/** Dónde se guarda la última canción (implementación en data/). */
interface PlaybackMemory {
    fun save(id: String, positionMs: Long)
}

/** Etapas de carga que se muestran en pantalla. */
sealed interface LoadState {
    data object Idle : LoadState
    /** Pidiendo a YouTube los enlaces de una canción en línea. */
    data object Searching : LoadState
    /** Cargando lo necesario para empezar a sonar, en porcentaje real (0-99). */
    data class Buffering(val percent: Int) : LoadState
    data object Ready : LoadState
}
