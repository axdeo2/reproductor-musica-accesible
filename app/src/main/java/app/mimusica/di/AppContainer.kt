package app.mimusica.di

import android.content.Context
import app.mimusica.data.LocalLibrary
import app.mimusica.online.AccountStore
import app.mimusica.online.DownloadQueue
import app.mimusica.online.OnlineSession
import app.mimusica.online.potoken.AppTokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Instancias compartidas. La parte en línea (`online`, `downloads`) es perezosa:
 * no se crea ningún cliente de red hasta que el cuidador entra a la zona de descargas.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Lo único que usa el modo niña. */
    val localLibrary = LocalLibrary(File(appContext.filesDir, "descargas"))

    val online: OnlineSession by lazy { OnlineSession(AccountStore(appContext), AppTokenProvider(appContext)) }
    val downloads: DownloadQueue by lazy { DownloadQueue(online, localLibrary, appScope) }
}
