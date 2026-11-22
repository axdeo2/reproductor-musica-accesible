package app.mimusica

import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val barra = SystemBarStyle.dark(Colores.Fondo.toArgb())
        enableEdgeToEdge(statusBarStyle = barra, navigationBarStyle = barra)

        val container = (application as MusicApp).container
        val library = container.localLibrary
        val recommendations = container.recommendations
        player = SimplePlayer(this, lifecycleScope, container.onlineStreams)

        setContent {
            MiMusicaTheme {
                var caregiver by rememberSaveable { mutableStateOf(false) }
                val saved by library.items.collectAsState()
                val recs by recommendations.items.collectAsState()
                val showRecs by container.childSettings.showRecommendations.collectAsState()
                val currentId by player.currentId.collectAsState()
                val isPlaying by player.isPlaying.collectAsState()
                val isLoading by player.isLoading.collectAsState()
                val hasVideo by player.hasVideo.collectAsState()

                LaunchedEffect(caregiver, showRecs) {
                    if (!caregiver) {
                        library.refresh()
                        recommendations.refresh(force = false)
                    }
                }

                val downloaded = remember(saved) { saved.map { it.toPlayable(library) } }
                // Si una recomendación ya está guardada, se reproduce desde el celular.
                val recommended = remember(recs, downloaded, showRecs) {
                    if (!showRecs) emptyList()
                    else {
                        val byId = downloaded.associateBy { it.id }
                        recs.map { byId[it.id] ?: it }
                    }
                }

                if (caregiver) {
                    CaregiverScreen(container, onExit = { caregiver = false })
                } else {
                    HomeScreen(
                        recommended = recommended,
                        downloaded = downloaded,
                        currentId = currentId,
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        hasVideo = hasVideo,
                        player = player.player,
                        onPlay = player::playQueue,
                        onTogglePlay = player::togglePlayPause,
                        onNext = player::next,
                        onPrevious = player::previous,
                        onOpenCaregiver = { caregiver = true },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
