package app.mimusica.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.mimusica.R
import app.mimusica.data.PlayableItem
import app.mimusica.playback.LoadState
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableIntStateOf
import app.mimusica.ui.components.AppLogo
import app.mimusica.ui.components.safeClickable
import app.mimusica.ui.theme.Colores
import app.mimusica.ui.theme.Dimens
import coil3.compose.AsyncImage

/** Tiempo que hay que mantener presionado el logo para abrir la zona del cuidador. */
private const val CAREGIVER_HOLD_MS = 5_000L

/**
 * Pantalla Inicio (modo niña), al estilo YouTube: el video arriba y debajo las miniaturas
 * para cambiar de canción, en dos secciones:
 *  - "Para ti": recomendaciones de la cuenta (en línea; desaparece si no hay internet).
 *  - "En el celular": canciones guardadas (funcionan siempre, sin internet).
 *
 * No conoce nada de la red: recibe listas de [PlayableItem] con miniaturas locales.
 */
@Composable
fun HomeScreen(
    recommended: List<PlayableItem>,
    downloaded: List<PlayableItem>,
    /** Canción que suena (puede ser de "Para ti" aunque la sección esté oculta sin internet). */
    current: PlayableItem?,
    isPlaying: Boolean,
    loadState: LoadState,
    hasVideo: Boolean,
    /** Reproductor para la superficie de video. */
    player: Player,
    /** Reproduce [queue] empezando en [index] (la cola es la sección tocada). */
    onPlay: (queue: List<PlayableItem>, index: Int) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenCaregiver: () -> Unit,
    /** Si hay internet (para mostrar el ícono de "sin conexión"). */
    hasInternet: Boolean,
    /** Pantalla completa (horizontal). */
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
) {
    if (fullscreen && current != null) {
        FullscreenPlayer(
            item = current,
            hasVideo = hasVideo,
            loadState = loadState,
            isPlaying = isPlaying,
            player = player,
            onTogglePlay = onTogglePlay,
            onNext = onNext,
            onPrevious = onPrevious,
            onExit = { onFullscreenChange(false) },
        )
        return
    }

    // En Inicio, Atrás no hace nada: así nunca sale de la app con ese botón.
    BackHandler(enabled = true) { }

    Column(
        Modifier
            .fillMaxSize()
            .background(Colores.Fondo)
            .safeDrawingPadding()
    ) {
        TopBar(onOpenCaregiver, hasInternet)

        val currentId = current?.id
        if (current != null) {
            NowPlaying(current, hasVideo, loadState, player, onFullscreen = { onFullscreenChange(true) })
            Controls(isPlaying, onTogglePlay, onNext, onPrevious)
        }

        Box(Modifier.weight(1f)) {
            if (recommended.isEmpty() && downloaded.isEmpty()) {
                EmptyState()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    contentPadding = PaddingValues(Dimens.Separacion),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Separacion),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Separacion),
                ) {
                    section("para-ti", Icons.Filled.AutoAwesome, "Para ti", recommended, currentId, onPlay)
                    section("celular", Icons.Filled.PhoneAndroid, "En el celular", downloaded, currentId, onPlay)
                    endlessTail(recommended + downloaded, currentId, onPlay)
                }
            }
        }
    }
}

/**
 * Feed infinito, como YouTube: al terminar las secciones, las mismas canciones se repiten en
 * el mismo orden sin fin (la niña desliza hacia arriba sin parar; si la lista se acabara,
 * sentiría que la app no funciona). Es una lista perezosa: solo se dibujan las tarjetas
 * visibles, así que casi un millón de elementos no gastan memoria.
 */
private fun LazyGridScope.endlessTail(
    pool: List<PlayableItem>,
    currentId: String?,
    onPlay: (List<PlayableItem>, Int) -> Unit,
) {
    if (pool.isEmpty()) return
    items(count = ENDLESS_COUNT, key = { i -> "repetir-$i" }) { i ->
        val index = i % pool.size
        val item = pool[index]
        VideoCard(
            item = item,
            isCurrent = item.id == currentId,
            onClick = { onPlay(pool, index) },
        )
    }
}

/** Cantidad "infinita" de tarjetas repetidas (nadie llega al final deslizando). */
private const val ENDLESS_COUNT = 1_000_000

private fun LazyGridScope.section(
    key: String,
    icon: ImageVector,
    label: String,
    items: List<PlayableItem>,
    currentId: String?,
    onPlay: (List<PlayableItem>, Int) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "header-$key", span = { GridItemSpan(maxLineSpan) }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Icon(icon, null, tint = Colores.Rojo, modifier = Modifier.size(Dimens.IconoMinimo))
            Text(label, color = Colores.Texto, style = MaterialTheme.typography.titleLarge)
        }
    }
    itemsIndexed(items, key = { _, it -> "$key-${it.id}" }) { index, item ->
        VideoCard(
            item = item,
            isCurrent = item.id == currentId,
            onClick = { onPlay(items, index) },
        )
    }
}

@Composable
private fun TopBar(onOpenCaregiver: () -> Unit, hasInternet: Boolean) {
    val descripcionLogo = stringResource(R.string.cd_app_logo)
    val open by rememberUpdatedState(onOpenCaregiver)
    Row(
        Modifier
            .fillMaxWidth()
            .height(Dimens.TocableMinimo)
            .padding(horizontal = Dimens.Separacion),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Acceso oculto: mantener presionado 5 s sin soltar ni salirse del logo.
        // Un toque corto no hace nada visible; el anillo solo aparece tras ~1 s de presión.
        var holding by remember { mutableStateOf(false) }
        val progress = remember { Animatable(0f) }
        LaunchedEffect(holding) {
            if (holding) {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(CAREGIVER_HOLD_MS.toInt(), easing = LinearEasing))
            } else {
                progress.snapTo(0f)
            }
        }
        Box(
            Modifier
                .size(Dimens.TocableMinimo)
                .semantics { contentDescription = descripcionLogo }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        holding = true
                        // Solo se abre si se cumplen los 5 s. Soltar o salirse del logo
                        // (waitForUpOrCancellation termina antes) cancela.
                        var completed = true
                        try {
                            withTimeoutOrNull(CAREGIVER_HOLD_MS) {
                                waitForUpOrCancellation()
                                completed = false
                            }
                        } finally {
                            holding = false
                        }
                        if (completed) open()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (progress.value > 0.2f) {
                CircularProgressIndicator(
                    progress = { progress.value },
                    color = Colores.Rojo,
                    strokeWidth = 4.dp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AppLogo(ancho = 40.dp)
        }
        if (!hasInternet) {
            // Sin internet: solo se muestran las canciones descargadas.
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.CloudOff,
                contentDescription = "Sin internet",
                tint = Colores.Gris,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppLogo(ancho = 160.dp)
            Spacer(Modifier.height(Dimens.Separacion))
            Text(
                text = stringResource(R.string.empty_library),
                color = Colores.Gris,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(Dimens.Separacion * 2))
            // Pista para el cuidador; solo se ve mientras no hay canciones guardadas.
            Text(
                text = stringResource(R.string.empty_library_hint),
                color = Colores.Gris,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Dimens.Separacion * 2),
            )
        }
    }
}

@Composable
private fun VideoCard(item: PlayableItem, isCurrent: Boolean, onClick: () -> Unit) {
    val thumbnail = item.thumbnailModel
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "escalaTarjeta")
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .safeClickable(onClick, onPressedChange = { pressed = it })
            .semantics { contentDescription = item.title }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Colores.Superficie)
                .then(
                    if (isCurrent) Modifier.border(Dimens.BordeFoco, Colores.Foco, RoundedCornerShape(12.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Filled.MusicNote, null, tint = Colores.Gris, modifier = Modifier.size(64.dp))
            }
            if (isCurrent) {
                Icon(
                    Icons.Filled.GraphicEq, null, tint = Colores.Foco,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(Dimens.IconoMinimo)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .padding(4.dp),
                )
            }
            item.durationText?.let {
                Text(
                    text = it,
                    color = Colores.Blanco,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            text = item.title,
            color = Colores.Texto,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun NowPlaying(
    item: PlayableItem,
    hasVideo: Boolean,
    loadState: LoadState,
    player: Player,
    onFullscreen: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        PlayerSurface(item, hasVideo, player, Modifier.fillMaxSize())
        LoadingOverlay(loadState)
        // Botón de pantalla completa abajo a la derecha, como en YouTube.
        RoundButton(
            icon = Icons.Filled.Fullscreen,
            description = "Pantalla completa",
            background = Color.Black.copy(alpha = 0.55f),
            size = Dimens.TocableMinimo,
            onClick = onFullscreen,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
        )
    }
}

/** Video (si la canción lo tiene) o la miniatura grande (solo audio). */
@Composable
internal fun PlayerSurface(item: PlayableItem, hasVideo: Boolean, player: Player, modifier: Modifier) {
    if (hasVideo) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false // controles propios, grandes
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = modifier,
        )
    } else {
        AsyncImage(
            model = item.thumbnailModel,
            contentDescription = item.title,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    }
}

@Composable
private fun Controls(
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Colores.Fondo)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Separacion * 2, Alignment.CenterHorizontally),
    ) {
        RoundButton(Icons.Filled.SkipPrevious, "Anterior", Colores.Superficie, Dimens.TocableMinimo, onPrevious, tint = Colores.Texto)
        RoundButton(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (isPlaying) "Pausa" else "Reproducir",
            Colores.Rojo, Dimens.BotonPrincipal, onTogglePlay,
        )
        RoundButton(Icons.Filled.SkipNext, "Siguiente", Colores.Superficie, Dimens.TocableMinimo, onNext, tint = Colores.Texto)
    }
}

@Composable
internal fun RoundButton(
    icon: ImageVector,
    description: String,
    background: Color,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Colores.Blanco,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .safeClickable(onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * Progreso de carga sobre el área del video:
 *  - "Buscando la canción…" (pidiendo los enlaces a YouTube): anillo girando + segundos.
 *  - "Cargando NN %" (porcentaje real de lo necesario para empezar a sonar).
 */
@Composable
internal fun LoadingOverlay(state: LoadState) {
    if (state !is LoadState.Searching && state !is LoadState.Buffering) return
    var seconds by remember(state is LoadState.Searching) { mutableIntStateOf(0) }
    if (state is LoadState.Searching) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                seconds++
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                when (state) {
                    is LoadState.Buffering -> {
                        CircularProgressIndicator(
                            progress = { state.percent / 100f },
                            color = Colores.Rojo,
                            trackColor = Colores.Superficie,
                            strokeWidth = 8.dp,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Text("${state.percent}%", color = Colores.Blanco, style = MaterialTheme.typography.titleLarge)
                    }
                    else -> CircularProgressIndicator(
                        color = Colores.Rojo,
                        trackColor = Colores.Superficie,
                        strokeWidth = 8.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (state) {
                    is LoadState.Buffering -> "Cargando la canción"
                    else -> if (seconds >= 6) "Buscando la canción… ($seconds s)" else "Buscando la canción…"
                },
                color = Colores.Blanco,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
