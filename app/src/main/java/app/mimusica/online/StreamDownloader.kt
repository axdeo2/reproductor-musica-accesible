package app.mimusica.online

import app.mimusica.data.DownloadedItem
import app.mimusica.data.LocalLibrary
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Descarga una canción a la carpeta local.
 *
 * Versión 1: solo AUDIO + miniatura en alta resolución. Probado: el audio se obtiene sin
 * PoToken; el video de música popular devuelve 403 sin PoToken (ver docs/05-ZONA-DESCARGAS.md).
 */
class StreamDownloader(
    private val session: OnlineSession,
    private val library: LocalLibrary,
) {
    suspend fun download(track: RemoteTrack, onProgress: (Float) -> Unit): DownloadedItem =
        withContext(Dispatchers.IO) {
            val hints = ContentHints(wantVideo = false).withStreamCapabilities(
                allowHls = false,   // queremos un archivo, no una lista HLS
                allowSabr = false,  // SABR requiere un transporte especial
                allowBoundedRange = true,
            )
            val stream = session.extractor.extract(track.videoId, hints, audioQuality = AudioQuality.HIGH)
                ?: throw IOException("No se encontró un audio descargable")

            val ext = if (stream.mimeType?.contains("webm") == true) "webm" else "m4a"
            val dir = library.dir.apply { mkdirs() }
            val mediaName = "${track.videoId}.$ext"
            val chunk = stream.rangeChunkSizeBytes.takeIf { it > 0 } ?: DEFAULT_CHUNK
            fetchRanged(
                url = stream.audioUrl,
                headers = stream.headers,
                knownLength = stream.contentLengthBytes,
                chunk = chunk,
                target = File(dir, mediaName),
                onProgress = onProgress,
            )

            val thumbName = "${track.videoId}.jpg"
            val thumbUrl = track.thumbnailUrl ?: "https://i.ytimg.com/vi/${track.videoId}/hqdefault.jpg"
            val thumbOk = runCatching { fetchWhole(thumbUrl, File(dir, thumbName)) }.isSuccess

            val item = DownloadedItem(
                videoId = track.videoId,
                title = track.title,
                artists = track.artists,
                durationText = track.durationText,
                mediaFile = mediaName,
                thumbnailFile = thumbName.takeIf { thumbOk },
                mimeType = stream.mimeType,
                addedAt = System.currentTimeMillis(),
            )
            library.save(item)
            item
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
        const val DEFAULT_CHUNK = 1L shl 20 // 1 MiB
    }
}
