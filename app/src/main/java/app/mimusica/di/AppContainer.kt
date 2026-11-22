package app.mimusica.di

import android.content.Context
import app.mimusica.data.ChildSettings
import app.mimusica.data.ConnectivityMonitor
import app.mimusica.data.LocalLibrary
import app.mimusica.data.RecommendationsSource
import app.mimusica.online.AccountStore
import app.mimusica.online.DownloadQueue
import app.mimusica.online.OnlineSession
import app.mimusica.online.OnlineStreamResolver
import app.mimusica.online.RecommendationsRepository
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
    val connectivity = ConnectivityMonitor(appContext)

    val online: OnlineSession by lazy { OnlineSession(AccountStore(appContext), AppTokenProvider(appContext)) }
    val downloads: DownloadQueue by lazy { DownloadQueue(online, localLibrary, appScope) }

    val recommendations: RecommendationsSource by lazy {
        RecommendationsRepository(online, appContext.cacheDir, childSettings)
    }
    val onlineStreams: OnlineStreams by lazy { OnlineStreamResolver(online) }
}
