package app.mimusica.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Tema de la app. Claro por defecto; el cuidador puede activar el oscuro. No depende del
 * ajuste del sistema, para que la niña siempre vea la app igual.
 */
@Composable
fun MiMusicaTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val paleta = if (dark) PaletaOscura else PaletaClara
    val scheme = if (dark) {
        darkColorScheme(
            primary = Colores.Rojo, onPrimary = Colores.Blanco, secondary = paleta.foco,
            background = paleta.fondo, onBackground = paleta.texto,
            surface = paleta.fondo, onSurface = paleta.texto,
            surfaceVariant = paleta.superficie, onSurfaceVariant = paleta.gris,
        )
    } else {
        lightColorScheme(
            primary = Colores.Rojo, onPrimary = Colores.Blanco, secondary = paleta.foco,
            background = paleta.fondo, onBackground = paleta.texto,
            surface = paleta.fondo, onSurface = paleta.texto,
            surfaceVariant = paleta.superficie, onSurfaceVariant = paleta.gris,
        )
    }
    CompositionLocalProvider(LocalPaleta provides paleta) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
