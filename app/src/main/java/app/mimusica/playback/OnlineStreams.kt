package app.mimusica.playback

/** Enlaces para reproducir una canción en línea (expiran en unas horas: se piden al momento). */
class ResolvedStream(
    val audioUrl: String,
    val videoUrl: String?,
    val headers: Map<String, String>,
) {
    override fun toString() = "ResolvedStream(<enlaces ocultos>, video=${videoUrl != null})"
}

/**
 * Lo único que el reproductor sabe de la red: pedir los enlaces de una canción.
 * La implementación vive en `online/` (innertubex + PoToken).
 */
interface OnlineStreams {
    /** null si no hay internet o YouTube no entrega la canción. */
    suspend fun resolve(videoId: String): ResolvedStream?

    /** Prepara por adelantado lo que haga falta (p. ej. el generador de PoToken). */
    suspend fun prewarm() {}
}
