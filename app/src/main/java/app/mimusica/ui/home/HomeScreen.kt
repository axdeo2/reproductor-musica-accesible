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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.Composable
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
import app.mimusica.R
import app.mimusica.data.DownloadedItem
import app.mimusica.ui.components.AppLogo
import app.mimusica.ui.theme.Colores
import app.mimusica.ui.theme.Dimens
import coil3.compose.AsyncImage
import kotlinx.coroutines.withTimeoutOrNull
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

        val current = items.firstOrNull { it.videoId == currentId }
        if (current != null) {
            MiniPlayer(current, thumbnailOf(current), isPlaying, onTogglePlay, onNext, onPrevious)
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
        // Acceso oculto: mantener presionado 5 s. Un toque corto no hace nada visible.
        Box(
            Modifier
                .size(Dimens.TocableMinimo)
                .semantics { contentDescription = descripcionLogo }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val released = withTimeoutOrNull(CAREGIVER_HOLD_MS) { waitForUpOrCancellation() }
                        if (released == null) open()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AppLogo(ancho = 48.dp)
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
private fun MiniPlayer(
    item: DownloadedItem,
    thumbnail: File?,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Colores.Superficie)
            .padding(horizontal = Dimens.Separacion, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Separacion),
    ) {
        AsyncImage(
            model = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.weight(1f))
        RoundButton(Icons.Filled.SkipPrevious, "Anterior", Colores.Fondo, Dimens.TocableMinimo, onPrevious)
        RoundButton(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (isPlaying) "Pausa" else "Reproducir",
            Colores.Rojo, Dimens.BotonPrincipal, onTogglePlay,
        )
        RoundButton(Icons.Filled.SkipNext, "Siguiente", Colores.Fondo, Dimens.TocableMinimo, onNext)
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
