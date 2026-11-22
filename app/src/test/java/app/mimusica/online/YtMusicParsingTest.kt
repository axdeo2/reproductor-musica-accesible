package app.mimusica.online

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Prueba el lector genérico con respuestas mínimas que imitan la forma de YouTube Music. */
class YtMusicParsingTest {

    private val trackPage = """
    {
      "contents": { "twoColumnBrowseResultsRenderer": { "secondaryContents": { "sectionListRenderer": { "contents": [
        { "musicPlaylistShelfRenderer": { "contents": [
          { "musicResponsiveListItemRenderer": {
              "playlistItemData": { "videoId": "abc123" },
              "thumbnail": { "musicThumbnailRenderer": { "thumbnail": { "thumbnails": [
                { "url": "https://lh3.googleusercontent.com/x=w60-h60-l90-rj" },
                { "url": "https://lh3.googleusercontent.com/x=w120-h120-l90-rj" } ] } } },
              "flexColumns": [
                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Canción Uno" } ] } } },
                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [
                  { "text": "Artista A" }, { "text": " & " }, { "text": "Artista B" }, { "text": " • " }, { "text": "Álbum" } ] } } }
              ],
              "fixedColumns": [ { "musicResponsiveListItemFixedColumnRenderer": { "text": { "runs": [ { "text": "3:45" } ] } } } ]
          } },
          { "musicResponsiveListItemRenderer": {
              "overlay": { "musicItemThumbnailOverlayRenderer": { "content": { "musicPlayButtonRenderer": {
                "playNavigationEndpoint": { "watchEndpoint": { "videoId": "def456" } } } } } },
              "flexColumns": [
                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Canción Dos" } ] } } }
              ]
          } },
          { "continuationItemRenderer": { "continuationEndpoint": { "continuationCommand": { "token": "TOKEN-2" } } } }
        ] } }
      ] } } } }
    }
    """

    private val playlistsPage = """
    { "contents": { "gridRenderer": { "items": [
      { "musicTwoRowItemRenderer": {
          "title": { "runs": [ { "text": "Nueva lista" } ] },
          "navigationEndpoint": { "createPlaylistEndpoint": {} } } },
      { "musicTwoRowItemRenderer": {
          "title": { "runs": [ { "text": "Favoritas de la niña" } ] },
          "subtitle": { "runs": [ { "text": "Lista" }, { "text": " • " }, { "text": "20 canciones" } ] },
          "navigationEndpoint": { "browseEndpoint": { "browseId": "VLPL123" } } } },
      { "musicTwoRowItemRenderer": {
          "title": { "runs": [ { "text": "Un artista" } ] },
          "navigationEndpoint": { "browseEndpoint": { "browseId": "UCartista" } } } }
    ] } } }
    """

    @Test
    fun `lee canciones, artistas, duración y miniatura grande`() {
        val out = LinkedHashMap<String, RemoteTrack>()
        collectTracks(Json.parseToJsonElement(trackPage), out)

        assertEquals(listOf("abc123", "def456"), out.keys.toList())
        val first = out.getValue("abc123")
        assertEquals("Canción Uno", first.title)
        assertEquals("Artista A & Artista B", first.artists)
        assertEquals("3:45", first.durationText)
        assertEquals("https://lh3.googleusercontent.com/x=w544-h544-l90-rj", first.thumbnailUrl)
        assertEquals("Canción Dos", out.getValue("def456").title)
    }

    @Test
    fun `encuentra el token de la siguiente página`() {
        assertEquals("TOKEN-2", findContinuation(Json.parseToJsonElement(trackPage)))
        assertNull(findContinuation(Json.parseToJsonElement(playlistsPage)))
    }

    @Test
    fun `solo toma listas reales (VL) y descarta botones y artistas`() {
        val out = LinkedHashMap<String, RemotePlaylist>()
        collectPlaylists(Json.parseToJsonElement(playlistsPage), out)

        assertEquals(1, out.size)
        val p = out.getValue("VLPL123")
        assertEquals("Favoritas de la niña", p.title)
        assertEquals("Lista • 20 canciones", p.subtitle)
    }
}
