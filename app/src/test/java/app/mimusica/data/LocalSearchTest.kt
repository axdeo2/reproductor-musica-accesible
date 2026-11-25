package app.mimusica.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSearchTest {
    private fun item(id: String, title: String, artists: String = "") =
        PlayableItem(id = id, title = title, artists = artists, durationText = null, thumbnail = null)

    private val index = LocalSearchIndex(
        listOf(
            item("1", "La Canción del Sol", "Grupo Alegría"),
            item("2", "Cantemos juntos", "Coro Infantil"),
            item("3", "Mi amigo el sol", "Canciones Felices"),
            item("4", "Baile del Pingüino", "Grupo Alegría"),
        )
    )

    @Test
    fun `ignora tildes y mayúsculas`() {
        assertEquals(listOf("4"), index.search("PINGUINO").map { it.id })
        assertEquals(listOf("1", "4"), index.search("alegria").map { it.id })
    }

    @Test
    fun `varias palabras en cualquier orden`() {
        // "Canciones Felices" también contiene "cancion": se encuentra mientras se escribe.
        assertEquals(listOf("1", "3"), index.search("sol cancion").map { it.id })
        assertEquals(listOf("1"), index.search("sol cancion del").map { it.id })
    }

    @Test
    fun `primero los títulos que empiezan con lo buscado`() {
        // "can": empieza el título de 2; "La Canción…" y "Canciones Felices" tienen palabras que empiezan con "can".
        val ids = index.search("can").map { it.id }
        assertEquals("2", ids.first())
        assertTrue(ids.containsAll(listOf("1", "3")))
    }

    @Test
    fun `búsqueda vacía no devuelve nada`() {
        assertTrue(index.search("   ").isEmpty())
    }

    @Test
    fun `normaliza espacios y signos`() {
        assertEquals("cancion ano", normalizeForSearch("  Canción   AÑO "))
    }
}
