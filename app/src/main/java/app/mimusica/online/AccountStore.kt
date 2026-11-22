package app.mimusica.online

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Credenciales de la sesión de YouTube Music. Nunca se registran en logs. */
data class AccountSession(
    val cookie: String,
    val visitorData: String,
    val dataSyncId: String?,
    val authUser: String,
) {
    override fun toString() = "AccountSession(authUser=$authUser, <credenciales ocultas>)"
}

private val Context.cuentaStore by preferencesDataStore(name = "cuenta")

/** Guarda la sesión en el almacenamiento privado de la app (no accesible a otras apps). */
class AccountStore(context: Context) {
    private val store = context.applicationContext.cuentaStore

    val session: Flow<AccountSession?> = store.data.map { p ->
        val cookie = p[COOKIE]
        val visitor = p[VISITOR]
        if (cookie.isNullOrBlank() || visitor.isNullOrBlank()) null
        else AccountSession(cookie, visitor, p[DATASYNC], p[AUTHUSER] ?: "0")
    }

    suspend fun save(s: AccountSession) {
        store.edit {
            it[COOKIE] = s.cookie
            it[VISITOR] = s.visitorData
            if (s.dataSyncId != null) it[DATASYNC] = s.dataSyncId else it.remove(DATASYNC)
            it[AUTHUSER] = s.authUser
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val COOKIE = stringPreferencesKey("cookie")
        val VISITOR = stringPreferencesKey("visitor_data")
        val DATASYNC = stringPreferencesKey("data_sync_id")
        val AUTHUSER = stringPreferencesKey("auth_user")
    }
}
