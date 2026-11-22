package app.mimusica.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.mimusica.R
import app.mimusica.ui.components.AppLogo
import app.mimusica.ui.theme.Colores
import app.mimusica.ui.theme.Dimens
import app.mimusica.ui.theme.MiMusicaTheme

/**
 * Pantalla Inicio. En la fase 0 solo muestra la barra superior y un estado vacío;
 * la fase 1 agrega el feed de miniaturas.
 */
@Composable
fun HomeScreen() {
    // En Inicio, Atrás no hace nada: así nunca sale de la app con ese botón.
    BackHandler(enabled = true) { }

    val descripcionLogo = stringResource(R.string.cd_app_logo)
    Column(
        Modifier
            .fillMaxSize()
            .background(Colores.Fondo)
            .safeDrawingPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(Dimens.TocableMinimo)
                .padding(horizontal = Dimens.Separacion),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppLogo(Modifier.semantics { contentDescription = descripcionLogo }, ancho = 48.dp)
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
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
}

@Preview(showBackground = true, backgroundColor = 0xFF0F0F0F)
@Composable
private fun HomeScreenPreview() {
    MiMusicaTheme { HomeScreen() }
}
