package app.mimusica.data

import java.io.File

/**
 * Lo que el modo niña muestra y reproduce. Puede ser una canción guardada en el celular
 * (tiene [audioFile]) o una recomendación en línea de "Para ti" (sin archivos: el reproductor
 * pide el enlace a través de `playback.OnlineStreams` justo antes de reproducirla).
 *
 * La miniatura siempre es un archivo local: el modo niña nunca carga imágenes de internet.
 */
data class PlayableItem(
    val id: String,
    val title: String,
    val artists: String,
    val durationText: String?,
    val thumbnail: File?,
    val audioFile: File? = null,
    val videoFile: File? = null,
) {
    val isOnline: Boolean get() = audioFile == null
}

fun DownloadedItem.toPlayable(library: LocalLibrary) = PlayableItem(
    id = videoId,
    title = title,
    artists = artists,
    durationText = durationText,
    thumbnail = library.thumbnailFile(this),
    audioFile = library.mediaFile(this),
    videoFile = library.videoFile(this),
)
