package app.mimusica.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlin.math.ceil

/**
 * Volumen máximo fijado por el cuidador (protección auditiva). Si alguien sube el volumen
 * por encima del límite con los botones del celular, se baja de inmediato al límite.
 */
class VolumeLimiter(context: Context, private val maxPercent: () -> Int) {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    init {
        // Cambios de volumen: se escuchan por dos vías porque cada fabricante avisa distinto.
        appContext.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI, true,
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) = enforce()
            },
        )
        ContextCompat.registerReceiver(
            appContext,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = enforce()
            },
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        enforce()
    }

    fun enforce() {
        val percent = maxPercent()
        if (percent >= 100) return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cap = ceil(max * percent / 100.0).toInt().coerceIn(1, max)
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) > cap) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0) }
        }
    }
}
