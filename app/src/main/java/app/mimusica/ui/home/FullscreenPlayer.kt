package app.mimusica.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.mimusica.data.PlayableItem
import app.mimusica.playback.LoadState
import app.mimusica.ui.theme.Colores
import app.mimusica.ui.theme.Dimens
import kotlinx.coroutines.delay

/** Tiempo que los controles quedan visibles en pantalla completa. */
private const val CONTROLS_VISIBLE_MS = 4_000L

/**
 * Pantalla completa (horizontal). Como en YouTube: un toque muestra los controles y se
 * ocultan solos. Para evitar acciones accidentales, el primer toque SOLO muestra los
 * controles; recién con ellos visibles los botones actúan.
 */
@Composable
fun FullscreenPlayer(
    item: PlayableItem,
    hasVideo: Boolean,
    loadState: LoadState,
    isPlaying: Boolean,
    player: Player,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExit: () -> Unit,
) {
    BackHandler(onBack = onExit)

    var controlsVisible by remember { mutableStateOf(true) }
    // Cada interacción reinicia la cuenta para ocultar los controles.
    var interactions by remember { mutableIntStateOf(0) }
    LaunchedEffect(controlsVisible, interactions, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(CONTROLS_VISIBLE_MS)
            controlsVisible = false
        }
    }
    fun act(block: () -> Unit): () -> Unit = {
        block()
        interactions += 1
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                controlsVisible = !controlsVisible
                interactions++
            },
        contentAlignment = Alignment.Center,
    ) {
        PlayerSurface(item, hasVideo, player, Modifier.fillMaxSize())
        LoadingOverlay(loadState)

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Separacion * 3),
                ) {
                    RoundButton(Icons.Filled.SkipPrevious, "Anterior", Color.Black.copy(alpha = 0.55f), Dimens.TocableMinimo, act(onPrevious))
                    RoundButton(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (isPlaying) "Pausa" else "Reproducir",
                        Colores.Rojo, Dimens.BotonPrincipal, act(onTogglePlay),
                    )
                    RoundButton(Icons.Filled.SkipNext, "Siguiente", Color.Black.copy(alpha = 0.55f), Dimens.TocableMinimo, act(onNext))
                }
                // Salir de pantalla completa: mismo lugar que el botón para entrar.
                RoundButton(
                    icon = Icons.Filled.FullscreenExit,
                    description = "Salir de pantalla completa",
                    background = Color.Black.copy(alpha = 0.55f),
                    size = Dimens.TocableMinimo,
                    onClick = onExit,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }
        }
    }
}
