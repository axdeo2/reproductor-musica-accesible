package app.mimusica

import android.content.ComponentName
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
import kotlinx.coroutines.launch
import app.mimusica.data.LocalSearchIndex
import app.mimusica.data.PlayableItem
import app.mimusica.data.toPlayable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import app.mimusica.playback.PlaybackService
import app.mimusica.playback.SimplePlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import app.mimusica.ui.caregiver.CaregiverScreen
import app.mimusica.ui.home.HomeScreen
import app.mimusica.ui.theme.PaletaClara
import app.mimusica.ui.theme.PaletaOscura
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
        applySystemBars(dark = false)

        val container = (application as MusicApp).container
        val library = container.localLibrary
        val recommendations = container.recommendations
        val connectivity = container.connectivity
        player = container.player
        container.volumeLimiter.enforce() // crea el limitador y aplica el límite al abrir
        var restoreTried = false

        setContent {
            val dark by container.childSettings.darkTheme.collectAsState()
            LaunchedEffect(dark) { applySystemBars(dark) }
            MiMusicaTheme(dark = dark) {
                var caregiver by rememberSaveable { mutableStateOf(false) }
                var fullscreen by rememberSaveable { mutableStateOf(false) }
                val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                val saved by library.items.collectAsState()
                val folderItems by container.folderLibrary.items.collectAsState()
                val maxVolume by container.childSettings.maxVolumePercent.collectAsState()
                LaunchedEffect(maxVolume) { container.volumeLimiter.enforce() }
                val recs by recommendations.items.collectAsState()
                val showRecs by container.childSettings.showRecommendations.collectAsState()
                val hasInternet by connectivity.isOnline.collectAsState()
                val currentId by player.currentId.collectAsState()
                val isPlaying by player.isPlaying.collectAsState()
                val loadState by player.loadState.collectAsState()
                val hasVideo by player.hasVideo.collectAsState()
                val repeatOne by player.repeatOne.collectAsState()

                LaunchedEffect(caregiver, showRecs, hasInternet) {
                    if (!caregiver) {
                        library.refresh()
                        container.folderLibrary.refresh()
                        // Al volver el internet se actualiza "Para ti" y se prepara el PoToken
                        // para que la primera canción en línea no tenga que esperarlo.
                        if (hasInternet && showRecs) {
                            launch { container.onlineStreams.prewarm() }
                            recommendations.refresh(force = false)
                        }
                    }
                }

                // "En el celular": lo descargado con la app primero, luego la carpeta del celular.
                val downloaded = remember(saved, folderItems) { saved.map { it.toPlayable(library) } + folderItems }

                // Al abrir la app se deja lista (en pausa) la última canción, donde iba.
                LaunchedEffect(downloaded, recs) {
                    if (restoreTried || player.hasQueue) return@LaunchedEffect
                    val id = container.playbackMemory.lastId ?: run { restoreTried = true; return@LaunchedEffect }
                    val queue = when {
                        downloaded.any { it.id == id } -> downloaded
                        recs.any { it.id == id } && hasInternet -> recs
                        else -> return@LaunchedEffect // quizás "Para ti" aún no cargó
                    }
                    restoreTried = true
                    player.restore(queue, queue.indexOfFirst { it.id == id }, container.playbackMemory.lastPositionMs)
                }
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

                // ---- Buscador en tiempo real ----
                var query by rememberSaveable { mutableStateOf("") }
                // Índice preparado una sola vez por cada cambio de canciones (no por tecla).
                val searchIndex = remember(recommended, downloaded) { LocalSearchIndex(downloaded + recommended) }
                var searchLocal by remember { mutableStateOf(emptyList<PlayableItem>()) }
                var searchOnline by remember { mutableStateOf(emptyList<PlayableItem>()) }
                var searchingOnline by remember { mutableStateOf(false) }
                LaunchedEffect(query, searchIndex) {
                    if (query.isBlank()) { searchLocal = emptyList(); return@LaunchedEffect }
                    delay(60) // agrupa teclas muy seguidas; se cancela sola si llega otra
                    searchLocal = withContext(Dispatchers.Default) { searchIndex.search(query) }
                }
                LaunchedEffect(query, hasInternet, showRecs) {
                    searchOnline = emptyList()
                    val q = query.trim()
                    if (q.length < 2 || !hasInternet || !showRecs) { searchingOnline = false; return@LaunchedEffect }
                    searchingOnline = true
                    try {
                        delay(400) // espera a que deje de escribir; una tecla nueva cancela esta búsqueda
                        val byId = downloaded.associateBy { it.id }
                        val local = searchLocal.map { it.id }.toSet()
                        searchOnline = runCatching { container.onlineSearch.search(q) }.getOrDefault(emptyList())
                            .filterNot { it.id in local }
                            .map { byId[it.id] ?: it }
                    } finally {
                        searchingOnline = false
                    }
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
                        onPlay = { queue, index ->
                            if (query.isBlank()) player.playQueue(queue, index)
                            else {
                                // Desde una búsqueda: primero los resultados y luego el resto de
                                // las canciones en orden aleatorio, para que "siguiente" nunca
                                // repita la misma canción cuando hay pocos resultados.
                                val inQueue = queue.mapTo(HashSet()) { it.id }
                                val rest = (downloaded + recommended)
                                    .distinctBy { it.id }
                                    .filterNot { it.id in inQueue }
                                    .shuffled()
                                player.playQueue(queue + rest, index)
                            }
                        },
                        onTogglePlay = player::togglePlayPause,
                        onNext = player::next,
                        onPrevious = player::previous,
                        onOpenCaregiver = { caregiver = true },
                        hasInternet = hasInternet,
                        fullscreen = fullscreen,
                        onFullscreenChange = { fullscreen = it },
                        query = query,
                        onQueryChange = { query = it },
                        searchLocal = searchLocal,
                        searchOnline = searchOnline,
                        searchingOnline = searchingOnline,
                        repeatOne = repeatOne,
                        onToggleRepeat = player::toggleRepeatOne,
                    )
                }
            }
        }
    }

    /** Barras del sistema del color del fondo, con íconos legibles en modo claro u oscuro. */
    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(PaletaOscura.fondo.toArgb())
        else SystemBarStyle.light(PaletaClara.fondo.toArgb(), PaletaOscura.fondo.toArgb())
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
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

    private var controller: ListenableFuture<MediaController>? = null

    /** Conectarse al servicio de reproducción lo arranca (música con pantalla apagada). */
    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controller = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        // Si está sonando, Media3 mantiene el servicio en primer plano aunque se suelte.
        controller?.let(MediaController::releaseFuture)
        controller = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Al volver a la app se revisa de nuevo si hay internet.
        (application as MusicApp).container.connectivity.refresh()
    }

    override fun onDestroy() {
        portraitRelease?.disable()
        // El reproductor NO se libera aquí: pertenece a la app y sigue sonando en segundo plano.
        super.onDestroy()
    }
}
