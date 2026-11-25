package app.mimusica.data

import java.text.Normalizer
import java.util.Locale

/**
 * Búsqueda en tiempo real sobre las canciones que ya conoce la app (sin red).
 *
 * Optimización: el texto de cada canción se normaliza UNA vez al crear el índice (sin tildes,
 * en minúsculas); cada búsqueda solo recorre cadenas ya preparadas. Con miles de canciones
 * tarda pocos milisegundos y se ejecuta fuera del hilo de la interfaz.
 */
class LocalSearchIndex(items: List<PlayableItem>) {
    private class Entry(val item: PlayableItem, val title: String, val all: String, val words: List<String>)

    private val entries: List<Entry> = items.distinctBy { it.id }.map { item ->
        val title = normalizeForSearch(item.title)
        val all = "$title ${normalizeForSearch(item.artists)}"
        Entry(item, title, all, all.split(' ').filter { it.isNotEmpty() })
    }

    /**
     * Todas las palabras buscadas deben aparecer (en cualquier orden). Orden de resultados:
     *  0. el título empieza con lo buscado,
     *  1. alguna palabra empieza con la primera palabra buscada,
     *  2. el resto de coincidencias.
     */
    fun search(query: String, limit: Int = 200): List<PlayableItem> {
        val q = normalizeForSearch(query)
        val tokens = q.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val first = tokens.first()
        return entries.asSequence()
            .filter { e -> tokens.all { e.all.contains(it) } }
            .map { e ->
                val rank = when {
                    e.title.startsWith(q) -> 0
                    e.words.any { it.startsWith(first) } -> 1
                    else -> 2
                }
                rank to e
            }
            .sortedBy { it.first } // estable: dentro de cada grupo se respeta el orden original
            .take(limit)
            .map { it.second.item }
            .toList()
    }
}

private val MARKS = Regex("\\p{Mn}+")
private val SPACES = Regex("\\s+")

/** "Canción  ÁÑO" → "cancion ano" (sin tildes, minúsculas, espacios simples). */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(SPACES, " ")
        .trim()

/** Búsqueda en YouTube Music para el modo niña (la implementación con red vive en `online/`). */
interface OnlineSearch {
    /** Resultados reproducibles con miniatura local; lista vacía si no hay internet. */
    suspend fun search(query: String): List<PlayableItem>
}
