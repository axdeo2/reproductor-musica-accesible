package app.mimusica.ui.components

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Desplazamiento máximo (temblor) que todavía cuenta como toque. */
private val ToleranciaMovimiento = 24.dp

/**
 * Reemplazo de `clickable` con protección contra toques accidentales
 * (docs/03-ACCESIBILIDAD-Y-DISENO.md, sección 5):
 *  - Actúa al **soltar**; si suelta fuera del elemento, no hace nada.
 *  - Un temblor de hasta 24 dp sigue siendo un toque. Si la lista se desplaza
 *    (el contenedor consume el gesto), se cancela. No consume el "down" ni los
 *    movimientos, así el desplazamiento de la grilla sigue funcionando normal.
 *  - Si aparece un segundo dedo (o la palma) durante el gesto, se cancela.
 *  - Anti-rebote: tras un toque aceptado, ignora toques durante [debounceMs].
 *  - Vibración corta al aceptar el toque (si [haptics]).
 *  - [onPressedChange] avisa si está presionado, para dar respuesta visual.
 *  - Mantiene la semántica de botón para TalkBack.
 */
fun Modifier.safeClickable(
    onClick: () -> Unit,
    debounceMs: Long = 600,
    haptics: Boolean = true,
    onPressedChange: (Boolean) -> Unit = {},
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnPressedChange by rememberUpdatedState(onPressedChange)
    val currentDebounce by rememberUpdatedState(debounceMs)
    val currentHaptics by rememberUpdatedState(haptics)
    // Momento (uptime) del último toque aceptado; compartido con la acción de TalkBack.
    val ultimoToque = remember { longArrayOf(Long.MIN_VALUE / 2) }

    fun intentarClick() {
        val ahora = SystemClock.uptimeMillis()
        if (ahora - ultimoToque[0] < currentDebounce) return
        ultimoToque[0] = ahora
        if (currentHaptics) haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        currentOnClick()
    }

    this
        .semantics(mergeDescendants = true) {
            role = Role.Button
            onClick { intentarClick(); true }
        }
        .pointerInput(Unit) {
            val tolerancia = ToleranciaMovimiento.toPx()
            awaitEachGesture {
                // No se consume el "down": la grilla padre debe poder desplazarse.
                val down = awaitFirstDown(requireUnconsumed = true)
                currentOnPressedChange(true)
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        // Multitoque: dos dedos o la palma cancelan el toque.
                        if (event.changes.any { it.id != down.id && it.pressed }) return@awaitEachGesture
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: return@awaitEachGesture
                        if (change.isConsumed) return@awaitEachGesture
                        if ((change.position - down.position).getDistance() > tolerancia) {
                            return@awaitEachGesture
                        }
                        if (!change.pressed) {
                            // Al soltar: solo cuenta si el dedo sigue dentro del elemento.
                            val p = change.position
                            val dentro = p.x >= 0f && p.y >= 0f &&
                                p.x <= size.width && p.y <= size.height
                            if (dentro) {
                                change.consume()
                                intentarClick()
                            }
                            return@awaitEachGesture
                        }
                        // Si un contenedor que se desplaza consumió el movimiento, cancelar.
                        val final = awaitPointerEvent(PointerEventPass.Final)
                        if (final.changes.any { it.id == down.id && it.isConsumed }) {
                            return@awaitEachGesture
                        }
                    }
                } finally {
                    currentOnPressedChange(false)
                }
            }
        }
}
