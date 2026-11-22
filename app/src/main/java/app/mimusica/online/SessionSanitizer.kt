package app.mimusica.online

/*
 * Limpieza de los datos de sesión que entrega music.youtube.com.
 * Misma lógica que Metrolist (shared/.../ui/TokenLoginScreen.kt, GPL-3.0).
 */

/**
 * `yt.config_.DATASYNC_ID` llega como `"<id>||"` o `"<idPagina>||<idUsuario>"`.
 * innertubex lo envía como `onBehalfOfUser`; con los `||` YouTube responde HTTP 500.
 */
internal fun sanitizeDataSyncId(dataSyncId: String?): String? {
    val raw = dataSyncId?.trim() ?: return null
    return raw.substringBefore("||")
        .ifBlank { raw.substringAfter("||", "").trim() }
        .takeIf { it.isNotBlank() }
}

/** El visitorData no puede llevar espacios ni saltos de línea (va en una cabecera HTTP). */
internal fun sanitizeVisitorData(visitorData: String): String =
    visitorData.filterNot(Char::isWhitespace).removePrefix("=")
