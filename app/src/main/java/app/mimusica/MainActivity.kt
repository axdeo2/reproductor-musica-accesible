package app.mimusica

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.OrientationEventListener
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import app.mimusica.data.toPlayable
import app.mimusica.playback.SimplePlayer
import app.mimusica.ui.caregiver.CaregiverScreen
import app.mimusica.ui.home.HomeScreen
import app.mimusica.ui.theme.Colores
import app.mimusica.ui.theme.MiMusicaTheme

/**
 * Única Activity de la app. Maneja ella misma los cambios de orientación
 * (ver `configChanges` en el manifiesto) para que la música no se corte al girar.
 */
class MainActivity : ComponentActivity() {
    private lateinit var player: SimplePlayer
    private var wasFullscreen = false
    private var portraitRelease: OrientationEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val barra = SystemBarStyle.dark(Colores.Fondo.toArgb())
        enableEdgeToEdge(statusBarStyle = barra, navigationBarStyle = barra)

        val container = (application as MusicApp).container
        val library = container.localLibrary
        val recommendations = container.recommendations
        val connectivity = container.connectivity
        player = SimplePlayer(
            context = this,
            scope = lifecycleScope,
            online = container.onlineStreams,
            hasInternet = { connectivity.isOnline.value },
            offlineFallback = { library.items.value.map { it.toPlayable(library) } },
        )

        setContent {
            MiMusicaTheme {
                var caregiver by rememberSaveable { mutableStateOf(false) }
                var fullscreen by rememberSaveable { mutableStateOf(false) }
                val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                val saved by library.items.collectAsState()
                val recs by recommendations.items.collectAsState()
                val showRecs by container.childSettings.showRecommendations.collectAsState()
                val hasInternet by connectivity.isOnline.collectAsState()
                val currentId by player.currentId.collectAsState()
                val isPlaying by player.isPlaying.collectAsState()
                val loadState by player.loadState.collectAsState()
                val hasVideo by player.hasVideo.collectAsState()

                LaunchedEffect(caregiver, showRecs, hasInternet) {
                    if (!caregiver) {
                        library.refresh()
                        // Al volver el internet se actualiza "Para ti" y se prepara el PoToken
                        // para que la primera canción en línea no tenga que esperarlo.
                        if (hasInternet && showRecs) {
                            launch { container.onlineStreams.prewarm() }
                            recommendations.refresh(force = false)
                        }
                    }
                }

                val downloaded = remember(saved) { saved.map { it.toPlayable(library) } }
                // Si una recomendación ya está guardada, se reproduce desde el celular.
                // Sin internet solo se muestran las canciones descargadas.
                val recommended = remember(recs, downloaded, showRecs, hasInternet) {
                    if (!showRecs || !hasInternet) emptyList()
                    else {
                        val byId = downloaded.associateBy { it.id }
                        recs.map { byId[it.id] ?: it }
                    }
                }

                // Girar el celular con una canción sonando entra a pantalla completa;
                // volver a vertical sale (si se entró girando, no con el botón).
                LaunchedEffect(landscape, currentId, caregiver) {
                    if (caregiver) return@LaunchedEffect
                    if (landscape && currentId != null) fullscreen = true
                    else if (!landscape && fullscreen &&
                        requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    ) fullscreen = false
                }
                LaunchedEffect(fullscreen) {
                    if (fullscreen) enterFullscreen() else if (wasFullscreen) exitFullscreen()
                    wasFullscreen = fullscreen
                }

                if (caregiver) {
                    CaregiverScreen(container, onExit = { caregiver = false })
                } else {
                    HomeScreen(
                        recommended = recommended,
                        downloaded = downloaded,
                        current = remember(currentId, recs, downloaded) {
                            downloaded.firstOrNull { it.id == currentId } ?: recs.firstOrNull { it.id == currentId }
                        },
                        isPlaying = isPlaying,
                        loadState = loadState,
                        hasVideo = hasVideo,
                        player = player.player,
                        onPlay = player::playQueue,
                        onTogglePlay = player::togglePlayPause,
                        onNext = player::next,
                        onPrevious = player::previous,
                        onOpenCaregiver = { caregiver = true },
                        fullscreen = fullscreen,
                        onFullscreenChange = { fullscreen = it },
                    )
                }
            }
        }
    }

    /** Horizontal y sin barras del sistema (deslizar desde el borde las muestra un momento). */
    private fun enterFullscreen() {
        portraitRelease?.disable()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * Vuelve a vertical (como YouTube, aunque el celular siga horizontal) y libera la rotación
     * cuando el celular se pone físicamente vertical, para que girarlo de nuevo vuelva a
     * entrar a pantalla completa.
     */
    private fun exitFullscreen() {
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        portraitRelease?.disable()
        portraitRelease = object : OrientationEventListener(this) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees != ORIENTATION_UNKNOWN && (degrees < 25 || degrees > 335)) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    disable()
                }
            }
        }.also {
            if (it.canDetectOrientation()) it.enable()
            else requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    override fun onDestroy() {
        portraitRelease?.disable()
        player.release()
        super.onDestroy()
    }
}
