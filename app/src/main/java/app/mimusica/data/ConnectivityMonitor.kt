package app.mimusica.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Indica si hay internet de verdad (red con acceso a internet VERIFICADO por Android, no solo
 * "conectado a un WiFi"). Solo observa el estado: no abre conexiones.
 *
 * El modo niña lo usa para mostrar únicamente lo descargado cuando no hay internet.
 *
 * Importante: al recibir `onLost` NO se vuelve a consultar `activeNetwork`, porque en ese
 * instante Android todavía puede devolver la red que se acaba de perder (era el error de
 * v0.3.1: con datos y WiFi apagados seguía mostrando "Para ti").
 */
class ConnectivityMonitor(context: Context) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _isOnline = MutableStateFlow(currentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    /** Red predeterminada actual según el callback (null = ninguna). */
    @Volatile
    private var defaultNetwork: Network? = null

    private val periodicCheck = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, CHECK_EVERY_MS)
        }
    }

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                defaultNetwork = network
                // Aún no se sabe si tiene internet verificado: se espera onCapabilitiesChanged.
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                defaultNetwork = network
                _isOnline.value = caps.hasInternet()
            }

            override fun onLost(network: Network) {
                if (defaultNetwork == null || defaultNetwork == network) defaultNetwork = null
                _isOnline.value = false
            }

            override fun onUnavailable() {
                defaultNetwork = null
                _isOnline.value = false
            }
        })
        handler.postDelayed(periodicCheck, CHECK_EVERY_MS)
    }

    /** Vuelve a consultar el estado (al volver a la app y cada [CHECK_EVERY_MS] por seguridad). */
    fun refresh() {
        _isOnline.value = currentlyOnline()
    }

    private fun currentlyOnline(): Boolean {
        val network = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(network)?.hasInternet() == true
    }

    private fun NetworkCapabilities.hasInternet() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private companion object {
        const val CHECK_EVERY_MS = 10_000L
    }
}
