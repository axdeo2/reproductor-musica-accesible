package app.mimusica.data

import kotlinx.serialization.Serializable

/**
 * Canción guardada en el celular. Se escribe como `<videoId>.json` junto a su archivo
 * de audio y su miniatura dentro de la carpeta de descargas de la app.
 */
@Serializable
data class DownloadedItem(
    val videoId: String,
    val title: String,
    val artists: String,
    val durationText: String? = null,
    /** Nombre del archivo multimedia dentro de la carpeta de descargas. */
    val mediaFile: String,
    /** Nombre del archivo de miniatura (jpg) dentro de la carpeta de descargas. */
    val thumbnailFile: String? = null,
    val mimeType: String? = null,
    val addedAt: Long,
)
