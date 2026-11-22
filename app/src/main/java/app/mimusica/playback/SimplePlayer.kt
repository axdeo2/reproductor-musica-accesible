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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import app.mimusica.data.DownloadedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Reproductor (provisional hasta la fase 2, que agrega MediaSessionService y pantalla
 * completa). Solo reproduce archivos locales: si la canción tiene video, junta el archivo de
 * video y el de audio con un MergingMediaSource.
 *
 * Reglas del proyecto: la lista se repite y, si un archivo falla, salta al siguiente
 * sin mostrar errores a la niña.
 */
@OptIn(UnstableApi::class)
class SimplePlayer(context: Context) {
    private val dataSource = DefaultDataSource.Factory(context)
    private val progressive = ProgressiveMediaSource.Factory(dataSource)

    /** Expuesto para conectar la superficie de video (PlayerView). */
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_ALL
    }

    private val _currentId = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentId.value = mediaItem?.mediaId
            }

            override fun onPlayerError(error: PlaybackException) {
                if (player.mediaItemCount > 1) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })
    }

    fun playAll(
        items: List<DownloadedItem>,
        start: Int,
        audioOf: (DownloadedItem) -> File,
        videoOf: (DownloadedItem) -> File?,
    ) {
        val sources: List<MediaSource> = items.map { item ->
            val metadata = MediaMetadata.Builder().setTitle(item.title).setArtist(item.artists).build()
            val audio = progressive.createMediaSource(
                MediaItem.Builder()
                    .setMediaId(item.videoId)
                    .setUri(Uri.fromFile(audioOf(item)))
                    .setMediaMetadata(metadata)
                    .build()
            )
            val video = videoOf(item) ?: return@map audio
            // El primer origen define el MediaItem (y su mediaId) del conjunto.
            val videoSource = progressive.createMediaSource(
                MediaItem.Builder()
                    .setMediaId(item.videoId)
                    .setUri(Uri.fromFile(video))
                    .setMediaMetadata(metadata)
                    .build()
            )
            MergingMediaSource(videoSource, audio)
        }
        player.setMediaSources(sources, start, 0L)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() = player.seekToNextMediaItem()
    fun previous() = player.seekToPreviousMediaItem()

    fun release() = player.release()
}
