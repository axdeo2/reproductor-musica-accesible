package app.mimusica.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mimusica.ui.theme.Colores

/** Logo de la app (mismo dibujo que el ícono del lanzador), relación 62:44. */
@Composable
fun AppLogo(modifier: Modifier = Modifier, ancho: Dp = 48.dp) {
    Canvas(modifier.size(width = ancho, height = ancho * 44f / 62f)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = Colores.Rojo,
            size = Size(w, h),
            cornerRadius = CornerRadius(w * 12f / 62f),
        )
        val triangulo = Path().apply {
            moveTo(w * 24f / 62f, h * 10f / 44f)
            lineTo(w * 43f / 62f, h * 22f / 44f)
            lineTo(w * 24f / 62f, h * 34f / 44f)
            close()
        }
        drawPath(triangulo, Colores.Blanco)
    }
}
