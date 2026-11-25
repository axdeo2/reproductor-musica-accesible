package app.mimusica.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.mimusica.ui.components.safeClickable
import app.mimusica.ui.theme.Colores
import app.mimusica.ui.theme.Dimens

/**
 * Buscador compacto en la fila del logo (arriba), para no quitarle altura al video.
 * Busca mientras se escribe (sin botón "Buscar"). Quien lo usa ([modifier]) decide el ancho.
 */
@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Colores.Superficie),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = Colores.Gris,
            modifier = Modifier.padding(start = 14.dp, end = 8.dp).size(24.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Buscar", color = Colores.Gris, style = MaterialTheme.typography.titleMedium)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Colores.Texto),
                cursorBrush = SolidColor(Colores.Rojo),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Search,
                ),
                // Los resultados ya se ven mientras se escribe: "Buscar" solo cierra el teclado.
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .size(48.dp)
                    .safeClickable(onClick = {
                        onQueryChange("")
                        focus.clearFocus()
                    }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Borrar búsqueda", tint = Colores.Texto, modifier = Modifier.size(24.dp))
            }
        }
    }
}
