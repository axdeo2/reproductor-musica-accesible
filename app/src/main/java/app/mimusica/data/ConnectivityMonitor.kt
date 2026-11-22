package app.mimusica.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Indica si hay internet de verdad (red con acceso a internet VERIFICADO por Android, no solo
 * "conectado a un WiFi"). Solo observa el estado: no abre conexiones.
 *
 * El modo niña lo usa para mostrar únicamente lo descargado cuando no hay internet.
 */
class ConnectivityMonitor(context: Context) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(currentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _isOnline.value = caps.hasInternet()
            }

            override fun onLost(network: Network) {
                _isOnline.value = currentlyOnline()
            }

            override fun onUnavailable() {
                _isOnline.value = false
            }
        })
    }

    private fun currentlyOnline(): Boolean =
        cm.activeNetwork?.let(cm::getNetworkCapabilities)?.hasInternet() == true

    private fun NetworkCapabilities.hasInternet() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
