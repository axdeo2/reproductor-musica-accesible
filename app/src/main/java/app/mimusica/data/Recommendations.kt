package app.mimusica.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Recomendaciones "Para ti" de la cuenta de YouTube Music. El modo niña solo conoce esta
 * interfaz; la implementación (con red) vive en `online/`.
 */
interface RecommendationsSource {
    /** Lista vacía si no hay sesión, no hay internet o el cuidador desactivó la sección. */
    val items: StateFlow<List<PlayableItem>>

    suspend fun refresh(force: Boolean = false)
}

/** Ajustes que el cuidador controla y el modo niña respeta. */
class ChildSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ajustes_nina", Context.MODE_PRIVATE)

    private val _showRecommendations = MutableStateFlow(prefs.getBoolean(KEY_RECS, true))
    val showRecommendations: StateFlow<Boolean> = _showRecommendations.asStateFlow()

    fun setShowRecommendations(value: Boolean) {
        prefs.edit().putBoolean(KEY_RECS, value).apply()
        _showRecommendations.value = value
    }

    /** Modo oscuro (por defecto la app usa modo claro). */
    private val _darkTheme = MutableStateFlow(prefs.getBoolean(KEY_DARK, false))
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    fun setDarkTheme(value: Boolean) {
        prefs.edit().putBoolean(KEY_DARK, value).apply()
        _darkTheme.value = value
    }

    /** Volumen máximo permitido (porcentaje del volumen del celular). 100 = sin límite. */
    private val _maxVolumePercent = MutableStateFlow(prefs.getInt(KEY_MAX_VOL, 100))
    val maxVolumePercent: StateFlow<Int> = _maxVolumePercent.asStateFlow()

    fun setMaxVolumePercent(value: Int) {
        val v = value.coerceIn(10, 100)
        prefs.edit().putInt(KEY_MAX_VOL, v).apply()
        _maxVolumePercent.value = v
    }

    /** Carpeta del celular con videos/música descargados por otros medios (URI de SAF). */
    private val _folderUri = MutableStateFlow(prefs.getString(KEY_FOLDER, null))
    val folderUri: StateFlow<String?> = _folderUri.asStateFlow()

    fun setFolderUri(value: String?) {
        prefs.edit().putString(KEY_FOLDER, value).apply()
        _folderUri.value = value
    }

    private companion object {
        const val KEY_RECS = "mostrar_para_ti"
        const val KEY_DARK = "tema_oscuro"
        const val KEY_MAX_VOL = "volumen_maximo"
        const val KEY_FOLDER = "carpeta_uri"
    }
}
