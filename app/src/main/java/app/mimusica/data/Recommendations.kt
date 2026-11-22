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

    private companion object {
        const val KEY_RECS = "mostrar_para_ti"
    }
}
