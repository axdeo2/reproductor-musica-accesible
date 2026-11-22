package app.mimusica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.mimusica.ui.home.HomeScreen
import app.mimusica.ui.theme.Colores
import app.mimusica.ui.theme.MiMusicaTheme
import androidx.compose.ui.graphics.toArgb

/**
 * Única Activity de la app. Maneja ella misma los cambios de orientación
 * (ver `configChanges` en el manifiesto) para que el video no se corte al girar.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val barra = SystemBarStyle.dark(Colores.Fondo.toArgb())
        enableEdgeToEdge(statusBarStyle = barra, navigationBarStyle = barra)
        setContent {
            MiMusicaTheme {
                HomeScreen()
            }
        }
    }
}
