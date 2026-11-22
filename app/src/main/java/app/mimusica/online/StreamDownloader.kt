package app.mimusica.online

import android.util.Log
import app.mimusica.data.DownloadedItem
import app.mimusica.data.LocalLibrary
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Descarga una canción a la carpeta local: VIDEO (sin sonido) + AUDIO por separado, que el
 * reproductor junta. Si el video no se puede obtener, guarda solo el audio.
 *
 * El video de la música popular exige PoToken; lo genera `online/potoken` (BotGuard en un
 * WebView). Ver docs/05-ZONA-DESCARGAS.md.
 */
class StreamDownloader(
    private val session: OnlineSession,
    private val library: LocalLibrary,
) {
    suspend fun download(track: RemoteTrack, onProgress: (Float) -> Unit): DownloadedItem =
        withContext(Dispatchers.IO) {
            val dir = library.dir.apply { mkdirs() }
            val saved = try {
                downloadVideoAndAudio(track, dir, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Video no disponible, se guarda solo audio (${e::class.simpleName})")
                null
            } ?: downloadAudioOnly(track, dir, onProgress)

            val thumbName = "${track.videoId}.jpg"
            val thumbUrl = track.thumbnailUrl ?: "https://i.ytimg.com/vi/${track.videoId}/hqdefault.jpg"
            val thumbOk = runCatching { fetchWhole(thumbUrl, File(dir, thumbName)) }.isSuccess

            val item = DownloadedItem(
                videoId = track.videoId,
                title = track.title,
                artists = track.artists,
                durationText = track.durationText,
                mediaFile = saved.audioFile,
                videoFile = saved.videoFile,
                videoHeight = saved.videoHeight,
                thumbnailFile = thumbName.takeIf { thumbOk },
                mimeType = saved.audioMime,
                addedAt = System.currentTimeMillis(),
            )
            library.save(item)
            item
        }

    private data class Saved(
        val audioFile: String,
        val audioMime: String?,
        val videoFile: String? = null,
        val videoHeight: Int? = null,
    )

    /** Devuelve null si YouTube no entrega un video descargable para esta canción. */
    private suspend fun downloadVideoAndAudio(track: RemoteTrack, dir: File, onProgress: (Float) -> Unit): Saved? {
        val hints = ContentHints(wantVideo = true, maxVideoHeight = MAX_VIDEO_HEIGHT).withStreamCapabilities(
            allowHls = false,
            allowSabr = false,
            allowBoundedRange = true,
        )
        val s = session.extractor.extract(track.videoId, hints, audioQuality = AudioQuality.HIGH) ?: return null
        val videoUrl = s.videoUrl ?: return null
        val chunk = s.rangeChunkSizeBytes.takeIf { it > 0 } ?: DEFAULT_CHUNK

        val audioBytes = s.contentLengthBytes ?: 0L
        val videoBytes = s.videoContentLengthBytes ?: 0L
        val total = (audioBytes + videoBytes).toFloat().takeIf { it > 0f }
        val videoShare = if (total != null) videoBytes / total else 0.8f

        val videoName = "${track.videoId}.video.${extensionOf(s.videoMimeType, video = true)}"
        val audioName = "${track.videoId}.${extensionOf(s.mimeType, video = false)}"
        try {
            fetchRanged(videoUrl, s.headers, s.videoContentLengthBytes, chunk, File(dir, videoName)) { p ->
                onProgress(p * videoShare)
            }
            fetchRanged(s.audioUrl, s.headers, s.contentLengthBytes, chunk, File(dir, audioName)) { p ->
                onProgress(videoShare + p * (1f - videoShare))
            }
        } catch (e: Exception) {
            File(dir, videoName).delete()
            File(dir, audioName).delete()
            throw e
        }
        return Saved(audioName, s.mimeType, videoName, s.videoHeight)
    }

    private suspend fun downloadAudioOnly(track: RemoteTrack, dir: File, onProgress: (Float) -> Unit): Saved {
        val hints = ContentHints(wantVideo = false).withStreamCapabilities(
            allowHls = false,   // queremos un archivo, no una lista HLS
            allowSabr = false,  // SABR requiere un transporte especial
            allowBoundedRange = true,
        )
        val s = session.extractor.extract(track.videoId, hints, audioQuality = AudioQuality.HIGH)
            ?: throw IOException("No se encontró un audio descargable")
        val audioName = "${track.videoId}.${extensionOf(s.mimeType, video = false)}"
        val chunk = s.rangeChunkSizeBytes.takeIf { it > 0 } ?: DEFAULT_CHUNK
        fetchRanged(s.audioUrl, s.headers, s.contentLengthBytes, chunk, File(dir, audioName), onProgress)
        return Saved(audioName, s.mimeType)
    }

    private fun extensionOf(mime: String?, video: Boolean): String = when {
        mime?.contains("webm") == true -> "webm"
        video -> "mp4"
        else -> "m4a"
    }

    /** Descarga por rangos (YouTube limita la velocidad de las descargas sin rango). */
    private suspend fun fetchRanged(
        url: String,
        headers: Map<String, String>,
        knownLength: Long?,
        chunk: Long,
        target: File,
        onProgress: (Float) -> Unit,
    ) {
        val part = File(target.parentFile, target.name + ".part")
        part.delete()
        var total = knownLength ?: -1L
        var pos = 0L
        part.outputStream().use { out ->
            while (total < 0 || pos < total) {
                currentCoroutineContext().ensureActive()
                val end = if (total > 0) minOf(pos + chunk, total) - 1 else pos + chunk - 1
                var attempt = 0
                while (true) {
                    try {
                        val req = Request.Builder().url(url).apply {
                            headers.forEach { (k, v) -> header(k, v) }
                            header("Range", "bytes=$pos-$end")
                        }.build()
                        session.okHttp.newCall(req).execute().use { resp ->
                            if (resp.code !in 200..299) throw IOException("HTTP ${resp.code}")
                            if (total < 0) {
                                total = resp.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                                    ?: throw IOException("Tamaño desconocido")
                            }
                            val bytes = resp.body.bytes()
                            if (bytes.isEmpty()) throw IOException("Respuesta vacía")
                            out.write(bytes)
                            pos += bytes.size
                        }
                        break
                    } catch (e: IOException) {
                        if (++attempt >= 3) {
                            part.delete()
                            throw e
                        }
                        delay(1_000L * attempt)
                    }
                }
                if (total > 0) onProgress(pos.toFloat() / total)
            }
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("No se pudo guardar el archivo")
    }

    private fun fetchWhole(url: String, target: File) {
        val req = Request.Builder().url(url).build()
        session.okHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            target.outputStream().use { resp.body.byteStream().copyTo(it) }
        }
    }

    private companion object {
        const val TAG = "StreamDownloader"
        const val DEFAULT_CHUNK = 1L shl 20 // 1 MiB

        /** 480p: se ve bien en un celular y ocupa ~3-4 veces menos que 720p. */
        const val MAX_VIDEO_HEIGHT = 480
    }
}
