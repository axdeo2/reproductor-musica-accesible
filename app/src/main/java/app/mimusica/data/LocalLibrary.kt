package app.mimusica.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Biblioteca de canciones guardadas en el celular.
 *
 * Es lo ÚNICO que leen las pantallas del modo niña: solo archivos locales, nunca la red.
 * (CI verifica que el código del modo niña no importe nada de `app.mimusica.online`.)
 */
class LocalLibrary(val dir: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<DownloadedItem>>(emptyList())
    val items: StateFlow<List<DownloadedItem>> = _items.asStateFlow()

    fun mediaFile(item: DownloadedItem) = File(dir, item.mediaFile)
    fun thumbnailFile(item: DownloadedItem) = item.thumbnailFile?.let { File(dir, it) }
    fun contains(videoId: String) = _items.value.any { it.videoId == videoId }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock {
            dir.mkdirs()
            _items.value = (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
                .mapNotNull { f -> runCatching { json.decodeFromString<DownloadedItem>(f.readText()) }.getOrNull() }
                .filter { File(dir, it.mediaFile).isFile }
                .sortedByDescending { it.addedAt }
        }
    }

    suspend fun save(item: DownloadedItem) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                dir.mkdirs()
                val tmp = File(dir, "${item.videoId}.json.tmp")
                tmp.writeText(json.encodeToString(DownloadedItem.serializer(), item))
                tmp.renameTo(File(dir, "${item.videoId}.json"))
            }
        }
        refresh()
    }

    suspend fun delete(videoId: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                dir.listFiles { f -> f.name.startsWith("$videoId.") }?.forEach { it.delete() }
            }
        }
        refresh()
    }
}
