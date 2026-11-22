package app.mimusica.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colores que cambian entre modo claro y oscuro (docs/03-ACCESIBILIDAD-Y-DISENO.md). */
data class Paleta(
    val fondo: Color,
    val superficie: Color,
    /** Texto e íconos sobre [fondo] o [superficie]. */
    val texto: Color,
    val gris: Color,
    /** Borde del video que está sonando o de la tarjeta presionada. */
    val foco: Color,
)

/** Modo claro (por defecto), como YouTube en modo claro. */
val PaletaClara = Paleta(
    fondo = Color(0xFFFFFFFF),
    superficie = Color(0xFFF2F2F2),
    texto = Color(0xFF0F0F0F),
    gris = Color(0xFF606060),
    foco = Color(0xFFE69500),
)

/** Modo oscuro (opcional, desde la zona del cuidador). */
val PaletaOscura = Paleta(
    fondo = Color(0xFF0F0F0F),
    superficie = Color(0xFF272727),
    texto = Color(0xFFFFFFFF),
    gris = Color(0xFFAAAAAA),
    foco = Color(0xFFFFD600),
)

val LocalPaleta = staticCompositionLocalOf { PaletaClara }

object Colores {
    /** Rojo de la acción principal: igual en ambos modos. */
    val Rojo = Color(0xFFFF0033)

    /** Blanco fijo: íconos sobre el rojo o sobre el video (que siempre es negro). */
    val Blanco = Color(0xFFFFFFFF)

    val Fondo: Color
        @Composable @ReadOnlyComposable get() = LocalPaleta.current.fondo
    val Superficie: Color
        @Composable @ReadOnlyComposable get() = LocalPaleta.current.superficie
    val Texto: Color
        @Composable @ReadOnlyComposable get() = LocalPaleta.current.texto
    val Gris: Color
        @Composable @ReadOnlyComposable get() = LocalPaleta.current.gris
    val Foco: Color
        @Composable @ReadOnlyComposable get() = LocalPaleta.current.foco
}
