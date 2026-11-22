package app.mimusica.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
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
import app.mimusica.data.DownloadedItem
import app.mimusica.ui.components.AppLogo
import app.mimusica.ui.theme.Colores
import app.mimusica.ui.theme.Dimens
import coil3.compose.AsyncImage
import java.io.File

/** Tiempo que hay que mantener presionado el logo para abrir la zona del cuidador. */
private const val CAREGIVER_HOLD_MS = 5_000L

/**
 * Pantalla Inicio (modo niña): miniaturas grandes de lo que está guardado en el celular.
 * Solo recibe datos locales; no conoce nada de la red.
 */
@Composable
fun HomeScreen(
    items: List<DownloadedItem>,
    thumbnailOf: (DownloadedItem) -> File?,
    currentId: String?,
    isPlaying: Boolean,
    /** Reproductor para la superficie de video; el video se muestra solo si la canción lo tiene. */
    player: Player,
    hasVideo: (DownloadedItem) -> Boolean,
    onPlay: (index: Int) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenCaregiver: () -> Unit,
) {
    // En Inicio, Atrás no hace nada: así nunca sale de la app con ese botón.
    BackHandler(enabled = true) { }

    Column(
        Modifier
            .fillMaxSize()
            .background(Colores.Fondo)
            .safeDrawingPadding()
    ) {
        TopBar(onOpenCaregiver)

        // Como YouTube: el video arriba y la lista para cambiar de canción debajo.
        val current = items.firstOrNull { it.videoId == currentId }
        if (current != null) {
            NowPlaying(current, thumbnailOf(current), hasVideo(current), player)
            Controls(isPlaying, onTogglePlay, onNext, onPrevious)
        }

        Box(Modifier.weight(1f)) {
            if (items.isEmpty()) {
                EmptyState()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    contentPadding = PaddingValues(Dimens.Separacion),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Separacion),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Separacion),
                ) {
                    itemsIndexed(items, key = { _, it -> it.videoId }) { index, item ->
                        VideoCard(
                            item = item,
                            thumbnail = thumbnailOf(item),
                            isCurrent = item.videoId == currentId,
                            onClick = { onPlay(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(onOpenCaregiver: () -> Unit) {
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
private fun VideoCard(item: DownloadedItem, thumbnail: File?, isCurrent: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
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
            color = Colores.Blanco,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun NowPlaying(item: DownloadedItem, thumbnail: File?, hasVideo: Boolean, player: Player) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
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
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Solo audio: la miniatura grande ocupa el lugar del video.
            AsyncImage(
                model = thumbnail,
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
        RoundButton(Icons.Filled.SkipPrevious, "Anterior", Colores.Superficie, Dimens.TocableMinimo, onPrevious)
        RoundButton(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (isPlaying) "Pausa" else "Reproducir",
            Colores.Rojo, Dimens.BotonPrincipal, onTogglePlay,
        )
        RoundButton(Icons.Filled.SkipNext, "Siguiente", Colores.Superficie, Dimens.TocableMinimo, onNext)
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    description: String,
    background: Color,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Colores.Blanco, modifier = Modifier.size(size * 0.55f))
    }
}
