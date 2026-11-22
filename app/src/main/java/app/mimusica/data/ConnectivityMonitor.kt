package app.mimusica.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ¿Hay internet de verdad? Combina dos fuentes:
 *  1. Lo que informa Android (red predeterminada con internet verificado). Si dice que NO,
 *     se marca sin internet al instante.
 *  2. Una comprobación real ([probe], implementada en `online/`): si Android dice que sí, se
 *     confirma pidiendo una respuesta mínima a Google, y se repite cada 15 s. Algunos
 *     celulares siguen informando "conectado" con datos y WiFi apagados (error visto en
 *     v0.3.1 y v0.4.1); la comprobación real lo detecta.
 *
 * Este archivo no abre conexiones: recibe la comprobación como función.
 */
class ConnectivityMonitor(
    context: Context,
    private val probe: suspend () -> Boolean,
) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var systemOnline = systemSaysOnline()

    /** Empieza en lo que dice Android; la primera comprobación real lo corrige en segundos. */
    private val _isOnline = MutableStateFlow(systemOnline)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var checkJob: Job? = null

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                systemOnline = caps.hasInternet()
                refresh()
            }

            override fun onLost(network: Network) {
                systemOnline = false
                refresh()
            }

            override fun onUnavailable() {
                systemOnline = false
                refresh()
            }
        })
        scope.launch {
            while (isActive) {
                refresh()
                delay(CHECK_EVERY_MS)
            }
        }
    }

    /** Vuelve a comprobar ahora (al volver a la app, al cambiar la red, etc.). */
    fun refresh() {
        if (!systemOnline && !systemSaysOnline()) {
            checkJob?.cancel()
            _isOnline.value = false
            return
        }
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            _isOnline.value = probe()
        }
    }

    private fun systemSaysOnline(): Boolean {
        val network = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(network)?.hasInternet() == true
    }

    private fun NetworkCapabilities.hasInternet() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private companion object {
        const val CHECK_EVERY_MS = 15_000L
    }
}
