package app.mimusica.data

import android.content.Context
import app.mimusica.playback.PlaybackMemory

/** Última canción que sonaba y en qué segundo iba (para retomarla al abrir la app). */
class PlaybackMemoryStore(context: Context) : PlaybackMemory {
    private val prefs = context.applicationContext.getSharedPreferences("ultima_cancion", Context.MODE_PRIVATE)

    override fun save(id: String, positionMs: Long) {
        prefs.edit().putString(KEY_ID, id).putLong(KEY_POS, positionMs).apply()
    }

    val lastId: String? get() = prefs.getString(KEY_ID, null)
    val lastPositionMs: Long get() = prefs.getLong(KEY_POS, 0L)

    private companion object {
        const val KEY_ID = "id"
        const val KEY_POS = "posicion_ms"
    }
}
