package app.mimusica.online

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFeedParsingTest {
    private val homePage = """
    { "contents": { "singleColumnBrowseResultsRenderer": { "tabs": [ { "tabRenderer": { "content": {
      "sectionListRenderer": { "contents": [
        { "musicCarouselShelfRenderer": { "contents": [
          { "musicTwoRowItemRenderer": {
              "title": { "runs": [ { "text": "Video recomendado" } ] },
              "subtitle": { "runs": [ { "text": "Video" }, { "text": " • " }, { "text": "Artista X" }, { "text": " • " }, { "text": "2 M vistas" } ] },
              "navigationEndpoint": { "watchEndpoint": { "videoId": "vid001" } },
              "thumbnailRenderer": { "musicThumbnailRenderer": { "thumbnail": { "thumbnails": [ { "url": "https://i.ytimg.com/vi/vid001/hq.jpg" } ] } } } } },
          { "musicTwoRowItemRenderer": {
              "title": { "runs": [ { "text": "Un álbum" } ] },
              "navigationEndpoint": { "browseEndpoint": { "browseId": "MPREb_album" } } } }
        ] } },
        { "musicCarouselShelfRenderer": { "contents": [
          { "musicResponsiveListItemRenderer": {
              "playlistItemData": { "videoId": "song02" },
              "flexColumns": [
                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Selección rápida" } ] } } },
                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Artista Y" } ] } } }
              ] } }
        ] } }
      ] } } } } ] } } }
    """

    @Test
    fun `toma videos y canciones reproducibles e ignora álbumes`() {
        val out = LinkedHashMap<String, RemoteTrack>()
        collectHomeItems(Json.parseToJsonElement(homePage), out)
        assertEquals(listOf("vid001", "song02"), out.keys.toList())
        assertEquals("Artista X", out.getValue("vid001").artists)
        assertEquals("Artista Y", out.getValue("song02").artists)
    }

    @Test
    fun `limpia el subtítulo`() {
        assertEquals("Artista", cleanSubtitle("Canción • Artista • 1,2 M reproducciones"))
        assertEquals("Artista", cleanSubtitle("Artista • 3 M vistas"))
        assertEquals("", cleanSubtitle(""))
    }
}
