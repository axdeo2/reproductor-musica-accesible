package app.mimusica.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.mimusica.MainActivity
import app.mimusica.MusicApp

/**
 * Servicio de reproducción: permite que la música siga sonando con la pantalla apagada,
 * muestra la notificación con controles y responde a los botones de los audífonos.
 *
 * El reproductor vive en la aplicación (AppContainer); aquí solo se publica en una
 * MediaSession. Media3 pone el servicio en primer plano mientras suena.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val simple = (application as MusicApp).container.player
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, QueueForwardingPlayer(simple))
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Si se cierra la app sin música sonando, el servicio se detiene. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.playbackState == Player.STATE_IDLE) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // El reproductor pertenece a la app; aquí solo se libera la sesión.
        session?.release()
        session = null
        super.onDestroy()
    }
}

/**
 * ExoPlayer solo tiene cargada la canción actual (la cola la maneja [SimplePlayer]).
 * Este envoltorio hace que "siguiente"/"anterior" de los audífonos y de la notificación
 * usen la cola propia.
 */
@OptIn(UnstableApi::class)
private class QueueForwardingPlayer(private val simple: SimplePlayer) : ForwardingPlayer(simple.player) {
    private val extra = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        ).build()

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().addAll(extra).build()

    override fun isCommandAvailable(command: Int): Boolean =
        extra.contains(command) || super.isCommandAvailable(command)

    override fun hasNextMediaItem(): Boolean = simple.hasQueue
    override fun hasPreviousMediaItem(): Boolean = simple.hasQueue

    override fun seekToNext() = simple.next()
    override fun seekToNextMediaItem() = simple.next()
    override fun seekToPrevious() = simple.previous()
    override fun seekToPreviousMediaItem() = simple.previous()

    override fun play() {
        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) simple.togglePlayPause()
        else super.play()
    }
}
