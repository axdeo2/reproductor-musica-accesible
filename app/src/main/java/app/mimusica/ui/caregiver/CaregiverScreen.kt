package app.mimusica.ui.caregiver

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mimusica.data.DownloadedItem
import app.mimusica.di.AppContainer
import app.mimusica.online.DownloadState
import app.mimusica.online.DownloadStatus
import app.mimusica.online.RemotePlaylist
import app.mimusica.online.RemoteTrack
import app.mimusica.ui.theme.Colores
import app.mimusica.ui.theme.Dimens
import kotlinx.coroutines.launch

/**
 * Zona del cuidador / zona de descargas. Es la única parte de la app que usa internet.
 * (La protección con PIN llega en la fase 5; hoy se entra manteniendo presionado el logo 5 s.)
 */
@Composable
fun CaregiverScreen(container: AppContainer, onExit: () -> Unit) {
    val online = container.online
    val downloads = container.downloads
    val scope = rememberCoroutineScope()

    val session by online.sessionFlow.collectAsState(initial = null)
    val downloadStates by downloads.states.collectAsState()
    val localItems by container.localLibrary.items.collectAsState()

    var showLogin by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var liked by remember { mutableStateOf<List<RemoteTrack>?>(null) }
    var playlists by remember { mutableStateOf<List<RemotePlaylist>?>(null) }
    var openPlaylist by remember { mutableStateOf<RemotePlaylist?>(null) }
    var playlistTracks by remember { mutableStateOf<List<RemoteTrack>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(block: suspend () -> Unit) {
        scope.launch {
            loading = true
            error = null
            try {
                block()
            } catch (e: Exception) {
                error = "No se pudo cargar: ${e.message ?: e::class.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { online.restore() }

    BackHandler {
        when {
            showLogin -> showLogin = false
            openPlaylist != null -> { openPlaylist = null; playlistTracks = null }
            else -> onExit()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Colores.Fondo)
            .safeDrawingPadding()
    ) {
        Header(
            title = if (showLogin) "Iniciar sesión" else openPlaylist?.title ?: "Zona del cuidador",
            onBack = {
                when {
                    showLogin -> showLogin = false
                    openPlaylist != null -> { openPlaylist = null; playlistTracks = null }
                    else -> onExit()
                }
            },
        )

        if (showLogin) {
            LoginWebView(
                onLoggedIn = { s ->
                    scope.launch {
                        online.signIn(s)
                        showLogin = false
                        tab = 0
                        load { liked = online.library.likedSongs() }
                        container.recommendations.refresh(force = true)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        LegalNotice()

        AccountRow(
            signedIn = session != null,
            onSignIn = { showLogin = true },
            onSignOut = {
                scope.launch {
                    online.signOut()
                    clearWebLoginCookies()
                    liked = null; playlists = null; openPlaylist = null; playlistTracks = null
                    container.recommendations.refresh(force = true)
                }
            },
        )

        val showRecs by container.childSettings.showRecommendations.collectAsState()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Separacion),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Mostrar \"Para ti\" en el modo niña", color = Colores.Texto)
                Text(
                    "Recomendaciones de la cuenta, reproducidas con internet (sin anuncios).",
                    color = Colores.Gris,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = showRecs,
                onCheckedChange = {
                    container.childSettings.setShowRecommendations(it)
                    scope.launch { container.recommendations.refresh(force = true) }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Colores.Rojo),
            )
        }

        val darkTheme by container.childSettings.darkTheme.collectAsState()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Separacion),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tema oscuro", color = Colores.Texto, modifier = Modifier.weight(1f))
            Switch(
                checked = darkTheme,
                onCheckedChange = { container.childSettings.setDarkTheme(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Colores.Rojo),
            )
        }

        if (openPlaylist != null) {
            TrackList(
                tracks = playlistTracks,
                loading = loading,
                error = error,
                states = downloadStates,
                isSaved = { id -> localItems.any { it.videoId == id } },
                onDownload = { downloads.enqueue(it) },
            )
            return@Column
        }

        PrimaryTabRow(selectedTabIndex = tab, containerColor = Colores.Fondo) {
            listOf("Me gusta", "Mis listas", "En el celular (${localItems.size})").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }

        when (tab) {
            0 -> {
                LaunchedEffect(session) {
                    if (session != null && liked == null) load { liked = online.library.likedSongs() }
                }
                if (session == null) NeedsLogin()
                else TrackList(
                    tracks = liked,
                    loading = loading,
                    error = error,
                    states = downloadStates,
                    isSaved = { id -> localItems.any { it.videoId == id } },
                    onDownload = { downloads.enqueue(it) },
                )
            }
            1 -> {
                LaunchedEffect(session) {
                    if (session != null && playlists == null) load { playlists = online.library.libraryPlaylists() }
                }
                if (session == null) NeedsLogin()
                else PlaylistList(playlists, loading, error) { p ->
                    openPlaylist = p
                    playlistTracks = null
                    load { playlistTracks = online.library.playlistTracks(p.browseId) }
                }
            }
            else -> SavedList(localItems) { item ->
                scope.launch { container.localLibrary.delete(item.videoId) }
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Dimens.TocableMinimo)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(Dimens.TocableMinimo)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Colores.Texto)
        }
        Text(
            title,
            color = Colores.Texto,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LegalNotice() {
    Text(
        "Esta zona usa internet. Descargar de YouTube con una app no oficial puede violar sus " +
            "Términos de Servicio y es bajo tu responsabilidad (incluido el riesgo para tu cuenta). " +
            "Descarga con moderación.",
        color = Colores.Gris,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = Dimens.Separacion, vertical = 4.dp),
    )
}

@Composable
private fun AccountRow(signedIn: Boolean, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Separacion, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (signedIn) "Sesión de Google iniciada" else "Sin sesión de Google",
            color = Colores.Texto,
            modifier = Modifier.weight(1f),
        )
        if (signedIn) {
            OutlinedButton(onClick = onSignOut) { Text("Cerrar sesión") }
        } else {
            Button(
                onClick = onSignIn,
                colors = ButtonDefaults.buttonColors(containerColor = Colores.Rojo),
            ) { Text("Iniciar sesión con Google") }
        }
    }
}

@Composable
private fun NeedsLogin() {
    CenterMessage("Inicia sesión con Google para ver tus \"me gusta\" y tus listas.")
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(Dimens.Separacion), contentAlignment = Alignment.Center) {
        Text(text, color = Colores.Gris)
    }
}

@Composable
private fun LoadingOrError(loading: Boolean, error: String?): Boolean {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Colores.Rojo)
        }
        error != null -> CenterMessage(error)
        else -> return false
    }
    return true
}

@Composable
private fun TrackList(
    tracks: List<RemoteTrack>?,
    loading: Boolean,
    error: String?,
    states: Map<String, DownloadState>,
    isSaved: (String) -> Boolean,
    onDownload: (List<RemoteTrack>) -> Unit,
) {
    if (LoadingOrError(loading, error)) return
    if (tracks == null) return
    if (tracks.isEmpty()) return CenterMessage("No hay canciones.")

    val pending = tracks.filterNot { isSaved(it.videoId) }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.Separacion, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${tracks.size} canciones · ${tracks.size - pending.size} guardadas", color = Colores.Gris, modifier = Modifier.weight(1f))
            Button(
                onClick = { onDownload(pending) },
                enabled = pending.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Colores.Rojo),
            ) {
                Icon(Icons.Filled.Download, null)
                Spacer(Modifier.size(8.dp))
                Text("Descargar todo (${pending.size})")
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(tracks, key = { it.videoId }) { t ->
                TrackRow(t, if (isSaved(t.videoId)) DownloadState(DownloadStatus.Listo, 1f) else states[t.videoId]) {
                    onDownload(listOf(t))
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: RemoteTrack, state: DownloadState?, onDownload: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Dimens.TocableMinimo)
            .padding(horizontal = Dimens.Separacion),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(track.title, color = Colores.Texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(track.artists.takeIf { it.isNotBlank() }, track.durationText).joinToString(" · "),
                color = Colores.Gris,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state?.status == DownloadStatus.Error && state.error != null) {
                Text(state.error, color = Colores.Rojo, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        Box(Modifier.size(Dimens.TocableMinimo), contentAlignment = Alignment.Center) {
            when (state?.status) {
                DownloadStatus.Listo -> Icon(Icons.Filled.CheckCircle, "Guardada", tint = Colores.Foco)
                DownloadStatus.EnCola -> Icon(Icons.Filled.Schedule, "En cola", tint = Colores.Gris)
                DownloadStatus.Descargando -> CircularProgressIndicator(
                    progress = { state.progress },
                    color = Colores.Rojo,
                    modifier = Modifier.size(32.dp),
                )
                DownloadStatus.Error -> IconButton(onClick = onDownload) {
                    Icon(Icons.Filled.ErrorOutline, "Reintentar", tint = Colores.Rojo)
                }
                null -> IconButton(onClick = onDownload) {
                    Icon(Icons.Filled.Download, "Descargar", tint = Colores.Texto)
                }
            }
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<RemotePlaylist>?,
    loading: Boolean,
    error: String?,
    onOpen: (RemotePlaylist) -> Unit,
) {
    if (LoadingOrError(loading, error)) return
    if (playlists == null) return
    if (playlists.isEmpty()) return CenterMessage("No hay listas en tu biblioteca.")
    LazyColumn(Modifier.fillMaxSize()) {
        items(playlists, key = { it.browseId }) { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(Dimens.TocableMinimo)
                    .clickable { onOpen(p) }
                    .padding(horizontal = Dimens.Separacion),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = Colores.Texto)
                Column(Modifier.weight(1f)) {
                    Text(p.title, color = Colores.Texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    p.subtitle?.let {
                        Text(it, color = Colores.Gris, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedList(items: List<DownloadedItem>, onDelete: (DownloadedItem) -> Unit) {
    if (items.isEmpty()) return CenterMessage("Todavía no hay canciones guardadas en el celular.")
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.videoId }) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(Dimens.TocableMinimo)
                    .padding(horizontal = Dimens.Separacion),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = Colores.Texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.artists, color = Colores.Gris, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                IconButton(onClick = { onDelete(item) }, modifier = Modifier.size(Dimens.TocableMinimo)) {
                    Icon(Icons.Filled.Delete, "Borrar del celular", tint = Colores.Gris)
                }
            }
        }
    }
}
