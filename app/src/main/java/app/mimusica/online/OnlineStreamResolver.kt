package app.mimusica.online

import android.util.Log
import app.mimusica.playback.OnlineStreams
import app.mimusica.playback.ResolvedStream
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Obtiene los enlaces para reproducir en línea una recomendación de "Para ti".
 *
 * Se piden clientes que entreguen archivos directos sin rangos acotados obligatorios
 * (`allowBoundedRange = false`), para que ExoPlayer los reproduzca tal cual.
 * Primero video + audio (con PoToken); si no se puede, solo audio.
 */
class OnlineStreamResolver(private val session: OnlineSession) : OnlineStreams {

    override suspend fun resolve(videoId: String): ResolvedStream? = withContext(Dispatchers.IO) {
        session.ensureRestored()
        tryExtract(videoId, wantVideo = true)?.takeIf { it.videoUrl != null }
            ?: tryExtract(videoId, wantVideo = false)
    }

    private suspend fun tryExtract(videoId: String, wantVideo: Boolean): ResolvedStream? = try {
        val hints = ContentHints(wantVideo = wantVideo, maxVideoHeight = MAX_VIDEO_HEIGHT.takeIf { wantVideo })
            .withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = false)
        session.extractor.extract(videoId, hints, audioQuality = AudioQuality.HIGH)?.let {
            ResolvedStream(audioUrl = it.audioUrl, videoUrl = if (wantVideo) it.videoUrl else null, headers = it.headers)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Sin enlace (video=$wantVideo): ${e::class.simpleName}")
        null
    }

    private companion object {
        const val TAG = "OnlineStreamResolver"
        const val MAX_VIDEO_HEIGHT = 480
    }
}
