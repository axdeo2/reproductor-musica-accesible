package app.mimusica.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Videos y música que ya estaban en una carpeta del celular (copiados desde la PC o
 * descargados con otra app). El cuidador elige la carpeta en Ajustes (Storage Access
 * Framework: la app solo ve esa carpeta). Se incluyen subcarpetas hasta 3 niveles.
 */
class FolderLibrary(context: Context, private val settings: ChildSettings) {
    private val appContext = context.applicationContext
    private val _items = MutableStateFlow<List<PlayableItem>>(emptyList())
    val items: StateFlow<List<PlayableItem>> = _items.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val uri = settings.folderUri.value?.let(Uri::parse)
        val root = uri?.let { runCatching { DocumentFile.fromTreeUri(appContext, it) }.getOrNull() }
        if (root == null || !root.canRead()) {
            _items.value = emptyList()
            return@withContext
        }
        val found = mutableListOf<PlayableItem>()
        scan(root, depth = 0, out = found)
        _items.value = found.sortedBy { it.title.lowercase() }
    }

    private fun scan(dir: DocumentFile, depth: Int, out: MutableList<PlayableItem>) {
        for (f in runCatching { dir.listFiles() }.getOrDefault(emptyArray())) {
            if (f.isDirectory) {
                if (depth < MAX_DEPTH) scan(f, depth + 1, out)
                continue
            }
            val type = f.type.orEmpty()
            val isVideo = type.startsWith("video/")
            if (!isVideo && !type.startsWith("audio/")) continue
            val name = f.name ?: continue
            out += PlayableItem(
                id = "carpeta:${f.uri}",
                title = name.substringBeforeLast('.'),
                artists = "",
                durationText = null,
                thumbnail = null,
                localUri = f.uri,
                localIsVideo = isVideo,
            )
        }
    }

    private companion object {
        const val MAX_DEPTH = 3
    }
}
