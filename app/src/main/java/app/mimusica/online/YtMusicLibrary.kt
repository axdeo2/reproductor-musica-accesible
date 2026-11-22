package app.mimusica.online

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

data class RemoteTrack(
    val videoId: String,
    val title: String,
    val artists: String,
    val durationText: String?,
    val thumbnailUrl: String?,
)

data class RemotePlaylist(
    val browseId: String,
    val title: String,
    val subtitle: String?,
    val thumbnailUrl: String?,
)

/**
 * Lee la biblioteca de YouTube Music de la cuenta conectada.
 *
 * innertubex solo hace la petición `browse`; la respuesta se recorre como JSON genérico
 * buscando los "renderers" conocidos (mismo enfoque que Metrolist), lo que tolera mejor
 * los cambios de estructura de YouTube.
 */
class YtMusicLibrary(private val tube: InnerTube) {

    /** Canciones que te gustan (lista especial "LM"). */
    suspend fun likedSongs(maxPages: Int = 30): List<RemoteTrack> = playlistTracks("VLLM", maxPages)

    /** Listas guardadas en la biblioteca. */
    suspend fun libraryPlaylists(maxPages: Int = 10): List<RemotePlaylist> {
        val out = LinkedHashMap<String, RemotePlaylist>()
        paginate("FEmusic_liked_playlists", maxPages) { root -> collectPlaylists(root, out) }
        return out.values.toList()
    }

    /** Canciones de una lista (browseId que empieza con "VL"). */
    suspend fun playlistTracks(browseId: String, maxPages: Int = 30): List<RemoteTrack> {
        val out = LinkedHashMap<String, RemoteTrack>()
        paginate(browseId, maxPages) { root -> collectTracks(root, out) }
        return out.values.toList()
    }

    private suspend fun paginate(browseId: String, maxPages: Int, collect: (JsonElement) -> Unit) {
        var root = browse(browseId = browseId)
        var page = 1
        while (true) {
            collect(root)
            val token = findContinuation(root) ?: break
            if (page++ >= maxPages) break
            root = browse(continuation = token)
        }
    }

    private suspend fun browse(browseId: String? = null, continuation: String? = null): JsonElement {
        val response = tube.browse(
            client = YouTubeClient.WEB_REMIX,
            browseId = browseId,
            continuation = continuation,
            setLogin = true,
        )
        if (!response.status.isSuccess()) {
            throw IOException("YouTube Music respondió HTTP ${response.status.value}")
        }
        return json.parseToJsonElement(response.bodyAsText())
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

// ---------- Recorrido genérico del JSON ----------

internal fun forEachObject(e: JsonElement, visit: (JsonObject) -> Unit) {
    when (e) {
        is JsonObject -> {
            visit(e)
            e.values.forEach { forEachObject(it, visit) }
        }
        is JsonArray -> e.forEach { forEachObject(it, visit) }
        else -> Unit
    }
}

internal fun JsonElement?.path(vararg keys: Any): JsonElement? {
    var cur: JsonElement? = this
    for (k in keys) {
        cur = when {
            k is String && cur is JsonObject -> cur[k]
            k is Int && cur is JsonArray -> cur.getOrNull(k)
            else -> null
        }
        if (cur == null) return null
    }
    return cur
}

internal fun JsonElement?.str(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonElement?.runsText(): String? {
    val runs = this.path("runs") as? JsonArray
    val text = runs?.mapNotNull { it.path("text").str() }?.joinToString("")
    return text?.takeIf { it.isNotBlank() } ?: this.path("simpleText").str()
}

private fun JsonElement?.lastThumbnail(): String? =
    (this as? JsonArray)?.lastOrNull()?.path("url").str()

/** Pide la miniatura en buena resolución (las URLs de googleusercontent llevan el tamaño). */
internal fun upscaleThumbnail(url: String?): String? =
    url?.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")

internal fun findContinuation(root: JsonElement): String? {
    var token: String? = null
    forEachObject(root) { o ->
        if (token == null) {
            token = o.path("nextContinuationData", "continuation").str()
                ?: o.path("continuationCommand", "token").str()
        }
    }
    return token
}

internal fun collectTracks(root: JsonElement, out: MutableMap<String, RemoteTrack>) {
    forEachObject(root) { o ->
        val r = o["musicResponsiveListItemRenderer"] as? JsonObject ?: return@forEachObject
        val videoId = r.path("playlistItemData", "videoId").str()
            ?: r.path(
                "overlay", "musicItemThumbnailOverlayRenderer", "content",
                "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint", "videoId",
            ).str()
            ?: return@forEachObject
        val flex = r["flexColumns"] as? JsonArray ?: return@forEachObject
        val title = flex.path(0, "musicResponsiveListItemFlexColumnRenderer", "text").runsText()
            ?: return@forEachObject
        val artists = flex.path(1, "musicResponsiveListItemFlexColumnRenderer", "text").runsText()
            ?.substringBefore(" • ").orEmpty()
        val duration = r.path("fixedColumns", 0, "musicResponsiveListItemFixedColumnRenderer", "text").runsText()
        val thumb = r.path("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails").lastThumbnail()
        out.putIfAbsent(videoId, RemoteTrack(videoId, title, artists, duration, upscaleThumbnail(thumb)))
    }
}

internal fun collectPlaylists(root: JsonElement, out: MutableMap<String, RemotePlaylist>) {
    forEachObject(root) { o ->
        val two = o["musicTwoRowItemRenderer"] as? JsonObject
        val list = o["musicResponsiveListItemRenderer"] as? JsonObject
        val browseId: String?
        val title: String?
        val subtitle: String?
        val thumb: String?
        when {
            two != null -> {
                browseId = two.path("navigationEndpoint", "browseEndpoint", "browseId").str()
                title = two.path("title").runsText()
                subtitle = two.path("subtitle").runsText()
                thumb = two.path("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails").lastThumbnail()
            }
            list != null -> {
                browseId = list.path("navigationEndpoint", "browseEndpoint", "browseId").str()
                title = list.path("flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text").runsText()
                subtitle = list.path("flexColumns", 1, "musicResponsiveListItemFlexColumnRenderer", "text").runsText()
                thumb = list.path("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails").lastThumbnail()
            }
            else -> return@forEachObject
        }
        if (browseId == null || !browseId.startsWith("VL") || title == null) return@forEachObject
        out.putIfAbsent(browseId, RemotePlaylist(browseId, title, subtitle, upscaleThumbnail(thumb)))
    }
}
