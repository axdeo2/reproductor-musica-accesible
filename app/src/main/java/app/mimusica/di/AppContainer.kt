package app.mimusica.di

import android.content.Context
import app.mimusica.data.ChildSettings
import app.mimusica.data.ConnectivityMonitor
import app.mimusica.data.FolderLibrary
import app.mimusica.data.LocalLibrary
import app.mimusica.data.OnlineSearch
import app.mimusica.data.PlaybackMemoryStore
import app.mimusica.data.toPlayable
import app.mimusica.playback.SimplePlayer
import app.mimusica.playback.VolumeLimiter
import app.mimusica.data.RecommendationsSource
import app.mimusica.online.AccountStore
import app.mimusica.online.DownloadQueue
import app.mimusica.online.InternetProbe
import app.mimusica.online.OnlineSession
import app.mimusica.online.OnlineStreamResolver
import app.mimusica.online.RecommendationsRepository
import app.mimusica.online.SearchRepository
import app.mimusica.online.potoken.AppTokenProvider
import app.mimusica.playback.OnlineStreams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Instancias compartidas. El modo niña recibe solo interfaces ([RecommendationsSource],
 * [OnlineStreams]) y la biblioteca local; todo lo que usa la red vive en `online/` y se
 * crea de forma perezosa.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val localLibrary = LocalLibrary(File(appContext.filesDir, "descargas"))
    val childSettings = ChildSettings(appContext)
    /** Perezoso: el proceso del PoToken también crea este contenedor y no lo necesita. */
    val connectivity: ConnectivityMonitor by lazy {
        val probe = InternetProbe()
        ConnectivityMonitor(appContext, probe = probe::check)
    }

    val online: OnlineSession by lazy { OnlineSession(AccountStore(appContext), AppTokenProvider(appContext)) }
    val downloads: DownloadQueue by lazy { DownloadQueue(online, localLibrary, appScope) }

    val recommendations: RecommendationsSource by lazy {
        RecommendationsRepository(online, appContext.cacheDir, childSettings)
    }
    val onlineStreams: OnlineStreams by lazy { OnlineStreamResolver(online) }
    val onlineSearch: OnlineSearch by lazy { SearchRepository(online, appContext.cacheDir) }

    val playbackMemory = PlaybackMemoryStore(appContext)
    val folderLibrary: FolderLibrary by lazy { FolderLibrary(appContext, childSettings) }
    val volumeLimiter: VolumeLimiter by lazy {
        VolumeLimiter(appContext) { childSettings.maxVolumePercent.value }
    }

    /**
     * El reproductor vive en la aplicación (no en la pantalla) para seguir sonando con la
     * pantalla apagada; lo publica PlaybackService. ExoPlayer se usa en el hilo principal.
     */
    val player: SimplePlayer by lazy {
        SimplePlayer(
            context = appContext,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            online = onlineStreams,
            hasInternet = { connectivity.isOnline.value },
            offlineFallback = {
                localLibrary.items.value.map { it.toPlayable(localLibrary) } + folderLibrary.items.value
            },
            memory = playbackMemory,
        )
    }
}
