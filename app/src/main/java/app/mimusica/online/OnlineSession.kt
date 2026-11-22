package app.mimusica.online

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Todo lo que usa internet vive aquí y SOLO se crea al entrar a la zona de descargas.
 * Conexión con YouTube Music mediante innertubex (GPL-3.0, de Metrolist).
 */
class OnlineSession(
    private val accounts: AccountStore,
    tokenProvider: TokenProvider? = null,
) {
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val http = HttpClient(OkHttp) {
        engine { preconfigured = okHttp }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; explicitNulls = false })
        }
    }

    val innerTube = InnerTube(http)

    private val configStore = RemotePlayerConfigStore(
        httpClient = http,
        repository = PlayerConfigRepository.disabled(),
    )
    private val cipher = YouTubeCipherService(http, configStore)

    val extractor = InnerTubeExtractor(
        configParser = YtConfigParserImpl(http, innerTube, configStore, cipherService = cipher),
        cipherService = cipher,
        innerTube = innerTube,
        // Genera PoToken con BotGuard en un WebView (ver online/potoken); sin él, YouTube
        // bloquea el video de la música popular.
        tokenProvider = tokenProvider,
    )

    val library = YtMusicLibrary(innerTube)

    val sessionFlow = accounts.session

    private val restoreLock = Mutex()
    private var restored = false
    private var current: AccountSession? = null

    /** Carga la sesión guardada (si existe) en el cliente. */
    suspend fun restore(): AccountSession? = restoreLock.withLock {
        accounts.session.first().also {
            applySession(it)
            restored = true
        }
    }

    /** Como [restore], pero solo la primera vez (para el modo niña, que lo llama a menudo). */
    suspend fun ensureRestored(): AccountSession? {
        if (restored) return current
        return restore()
    }

    suspend fun signIn(session: AccountSession) {
        accounts.save(session)
        applySession(session)
        restored = true
    }

    suspend fun signOut() {
        accounts.clear()
        applySession(null)
        restored = true
    }

    private fun applySession(s: AccountSession?) {
        current = s
        innerTube.replaceSession(
            cookie = s?.cookie,
            visitorData = s?.visitorData?.let(::sanitizeVisitorData),
            // Se limpia aquí (no solo al iniciar sesión) para arreglar también sesiones ya guardadas.
            dataSyncId = sanitizeDataSyncId(s?.dataSyncId),
            authUser = s?.authUser ?: "0",
            useLoginForBrowse = s != null,
        )
    }
}
