package app.mimusica.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val esquemaOscuro = darkColorScheme(
    primary = Colores.Rojo,
    onPrimary = Colores.Blanco,
    secondary = Colores.Foco,
    background = Colores.Fondo,
    onBackground = Colores.Blanco,
    surface = Colores.Fondo,
    onSurface = Colores.Blanco,
    surfaceVariant = Colores.Superficie,
    onSurfaceVariant = Colores.Gris,
)

/** Siempre oscuro, como YouTube en modo oscuro; no depende del ajuste del sistema. */
@Composable
fun MiMusicaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = esquemaOscuro, content = content)
}
