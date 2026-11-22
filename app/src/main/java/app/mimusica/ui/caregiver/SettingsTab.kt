package app.mimusica.ui.caregiver

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import app.mimusica.di.AppContainer
import app.mimusica.ui.theme.Colores
import app.mimusica.ui.theme.Dimens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Pestaña "Ajustes" de la zona del cuidador. */
@Composable
fun SettingsTab(container: AppContainer) {
    val settings = container.childSettings
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val showRecs by settings.showRecommendations.collectAsState()
    val darkTheme by settings.darkTheme.collectAsState()
    val maxVolume by settings.maxVolumePercent.collectAsState()
    val folderUri by settings.folderUri.collectAsState()
    val folderItems by container.folderLibrary.items.collectAsState()

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Permiso permanente de lectura (sigue valiendo tras reiniciar el celular).
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        settings.setFolderUri(uri.toString())
        scope.launch { container.folderLibrary.refresh() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.Separacion),
        verticalArrangement = Arrangement.spacedBy(Dimens.Separacion),
    ) {
        SwitchRow(
            title = "Mostrar \"Para ti\" en el modo niña",
            subtitle = "Recomendaciones de la cuenta, reproducidas con internet (sin anuncios). " +
                "Sin internet solo se ven las canciones del celular.",
            checked = showRecs,
        ) {
            settings.setShowRecommendations(it)
            scope.launch { container.recommendations.refresh(force = true) }
        }

        SwitchRow(title = "Tema oscuro", subtitle = "Por defecto la app usa el tema claro.", checked = darkTheme) {
            settings.setDarkTheme(it)
        }

        HorizontalDivider()

        // Volumen máximo
        var sliderValue by remember(maxVolume) { mutableFloatStateOf(maxVolume.toFloat()) }
        Column {
            Text("Volumen máximo: ${sliderValue.roundToInt()} %", color = Colores.Texto)
            Text(
                if (sliderValue >= 100f) "Sin límite." else
                    "Si alguien sube el volumen con los botones del celular, se baja a este límite.",
                color = Colores.Gris,
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    settings.setMaxVolumePercent(sliderValue.roundToInt())
                    container.volumeLimiter.enforce()
                },
                valueRange = 10f..100f,
                steps = 8,
                colors = SliderDefaults.colors(thumbColor = Colores.Rojo, activeTrackColor = Colores.Rojo),
            )
        }

        HorizontalDivider()

        // Carpeta del celular
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Carpeta del celular", color = Colores.Texto)
            val folderName = folderUri?.let {
                runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }.getOrNull()
            }
            Text(
                if (folderUri == null) "Videos o música copiados al celular por otros medios (desde la PC u otra app). " +
                    "Aparecen en \"En el celular\", después de lo descargado con la app."
                else "Carpeta: ${folderName ?: "seleccionada"} · ${folderItems.size} archivos",
                color = Colores.Gris,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { pickFolder.launch(folderUri?.let(Uri::parse)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Colores.Rojo),
                ) { Text(if (folderUri == null) "Elegir carpeta" else "Cambiar carpeta") }
                if (folderUri != null) {
                    OutlinedButton(onClick = {
                        settings.setFolderUri(null)
                        scope.launch { container.folderLibrary.refresh() }
                    }) { Text("Quitar") }
                }
            }
        }

        HorizontalDivider()

        Info(
            "Protección contra toques accidentales",
            "Siempre activa: los botones actúan al soltar, ignoran dos dedos o la palma y no " +
                "repiten si se tocan muy seguido.",
        )
        Info(
            "Que no pueda salir de la app",
            "Usa la fijación de pantalla de Android: Ajustes → Seguridad → Fijar app (actívala con " +
                "\"Pedir PIN para dejar de fijar\"). Luego abre esta app, toca Recientes ▢, el ícono " +
                "de la app y \"Fijar\". Para salir: mantén Atrás + Recientes y escribe el PIN.",
        )
        Info(
            "Entrar a esta zona",
            "Mantén presionado el logo rojo de Inicio durante 5 segundos.",
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Colores.Texto)
            Text(subtitle, color = Colores.Gris, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Colores.Rojo),
        )
    }
}

@Composable
private fun Info(title: String, text: String) {
    Column {
        Text(title, color = Colores.Texto)
        Text(text, color = Colores.Gris, style = MaterialTheme.typography.bodySmall)
    }
}
