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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
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
        player = SimplePlayer(this)

        setContent {
            MiMusicaTheme {
                var caregiver by rememberSaveable { mutableStateOf(false) }
                val items by library.items.collectAsState()
                val currentId by player.currentId.collectAsState()
                val isPlaying by player.isPlaying.collectAsState()

                LaunchedEffect(caregiver) { if (!caregiver) library.refresh() }

                if (caregiver) {
                    CaregiverScreen(container, onExit = { caregiver = false })
                } else {
                    HomeScreen(
                        items = items,
                        thumbnailOf = library::thumbnailFile,
                        currentId = currentId,
                        isPlaying = isPlaying,
                        player = player.player,
                        hasVideo = { library.videoFile(it) != null },
                        onPlay = { index -> player.playAll(items, index, library::mediaFile, library::videoFile) },
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
