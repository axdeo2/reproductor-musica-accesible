package app.mimusica.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionSanitizerTest {
    @Test
    fun `quita los separadores del DATASYNC_ID`() {
        assertEquals("abc123", sanitizeDataSyncId("abc123||"))
        assertEquals("pagina", sanitizeDataSyncId("pagina||usuario"))
        assertEquals("usuario", sanitizeDataSyncId("||usuario"))
        assertEquals("abc", sanitizeDataSyncId("  abc  "))
    }

    @Test
    fun `DATASYNC_ID vacío queda nulo`() {
        assertNull(sanitizeDataSyncId(null))
        assertNull(sanitizeDataSyncId("||"))
        assertNull(sanitizeDataSyncId("   "))
    }

    @Test
    fun `visitorData sin espacios ni saltos de línea`() {
        assertEquals("CgtABC%3D%3D", sanitizeVisitorData(" CgtA\nBC%3D%3D "))
    }
}
