package app.mimusica.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.mimusica.data.DownloadedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Reproductor mínimo (provisional hasta la fase 2, que agrega MediaSessionService y la
 * pantalla "Ver video"). Solo reproduce archivos locales.
 *
 * Reglas del proyecto: la lista se repite y, si un archivo falla, salta al siguiente
 * sin mostrar errores a la niña.
 */
class SimplePlayer(context: Context) {
    private val player = ExoPlayer.Builder(context).build().apply {
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

    fun playAll(items: List<DownloadedItem>, start: Int, fileOf: (DownloadedItem) -> File) {
        val mediaItems = items.map { item ->
            MediaItem.Builder()
                .setMediaId(item.videoId)
                .setUri(android.net.Uri.fromFile(fileOf(item)))
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(item.title).setArtist(item.artists).build()
                )
                .build()
        }
        player.setMediaItems(mediaItems, start, 0L)
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
