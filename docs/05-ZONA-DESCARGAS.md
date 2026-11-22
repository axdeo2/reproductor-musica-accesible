# Zona de descargas (cuidador)

Permite al cuidador **iniciar sesión con su cuenta de Google**, ver sus **"me gusta"** y sus
**listas** de YouTube Music, y **descargar** canciones al celular para que la niña las escuche
**sin internet**.

> **Aviso.** Usar un cliente no oficial de YouTube y descargar contenido puede violar los
> Términos de Servicio de YouTube. Es bajo responsabilidad de quien lo use, incluido el riesgo
> para su cuenta de Google. Recomendación: descargar con moderación (no miles de canciones de
> golpe).

## 1. Separación modo niña / zona de descargas

```
          MODO NIÑA (sin red)                         ZONA DE DESCARGAS (con red)
 ┌──────────────────────────────────┐        ┌────────────────────────────────────────┐
 │ ui/home, ui/watch, ui/components │        │ ui/caregiver  (login WebView, listas)  │
 │ playback/  (ExoPlayer, archivos) │        │ online/       (innertubex, OkHttp)     │
 │ data/LocalLibrary                │◄───────│   DownloadQueue → StreamDownloader     │
 └──────────────────────────────────┘ escribe └────────────────────────────────────────┘
          lee filesDir/descargas/          archivos en filesDir/descargas/
```

- El modo niña **solo** lee `filesDir/descargas/` (archivos de audio + `.jpg` + `.json`).
- Todo el código de red está en `online/` y `ui/caregiver/`. Se crea de forma perezosa: no hay
  ningún cliente de red hasta que el cuidador entra a la zona.
- Coil **no** tiene módulo de red: las miniaturas del modo niña son archivos locales.
- **CI** ejecuta [`scripts/verificar-modo-nina-sin-red.sh`](../scripts/verificar-modo-nina-sin-red.sh),
  que falla si el código del modo niña importa `online`, Ktor, OkHttp, WebView, innertubex,
  `java.net` o `coil-network`.
- Hasta la fase 5 (PIN), se entra manteniendo presionado el logo de Inicio **5 segundos**.

## 2. Componentes

| Archivo | Qué hace |
|---|---|
| `online/OnlineSession.kt` | Crea el `HttpClient` (Ktor + OkHttp), `InnerTube` y el extractor de streams de innertubex. Aplica/borra la sesión. |
| `online/AccountStore.kt` | Guarda cookie, `visitorData`, `dataSyncId` y `authUser` en DataStore privado de la app. |
| `ui/caregiver/LoginWebView.kt` | Página oficial de inicio de sesión de Google en un WebView. Al llegar a `music.youtube.com` con sesión, lee la cookie y `yt.config_` (mismo método que Metrolist). La app nunca ve la contraseña. La navegación se limita a dominios de Google/YouTube. |
| `online/YtMusicLibrary.kt` | `browse` con el cliente `WEB_REMIX`: "me gusta" (`VLLM`), listas de la biblioteca (`FEmusic_liked_playlists`), canciones de una lista (`VL…`), con paginación. Recorre el JSON buscando los *renderers* conocidos. |
| `online/StreamDownloader.kt` | Pide video (máx. 480p) + audio a `InnerTubeExtractor` (sin HLS ni SABR) y los descarga **por rangos** a `<videoId>.video.webm/mp4` y `<videoId>.webm/m4a`, más la miniatura y el `.json`. Si el video no está disponible, guarda solo el audio. |
| `online/potoken/` | Generador de **PoToken** adaptado de Metrolist: `AppTokenProvider` (caché y reintentos), `RemotePoTokenMinter` + `PoTokenService` (proceso aparte `:potoken`, se cierra solo) y `BotGuardPoTokenMinter` (ejecuta el desafío BotGuard de YouTube en un WebView con `assets/po_token.html`). |
| `playback/SimplePlayer.kt` | Reproduce archivos locales; si hay video, lo junta con el audio (`MergingMediaSource`). Inicio muestra el video arriba y la lista debajo. |
| `online/DownloadQueue.kt` | Cola: una descarga a la vez, pausa de 1,5 s entre canciones, estado por canción (en cola / descargando % / lista / error). |
| `data/LocalLibrary.kt` | Lee y borra lo guardado. Es lo único que ve el modo niña. |

## 3. Dependencia: innertubex

- [`MetrolistGroup/innertubex`](https://github.com/MetrolistGroup/innertubex) `v0.13.0`, vía
  JitPack, licencia **GPL-3.0** (por eso este proyecto es GPL-3.0).
- Requiere **Kotlin 2.4.x** (el proyecto usa 2.4.21) y compila con AGP 9.5 / SDK 37.1.
- innertubex hace las peticiones y la extracción de streams (cifrado de firma y parámetro `n`
  sin motor de JavaScript). El análisis de la biblioteca y el descargador son código propio.

## 4. Resultado de las pruebas de extracción (22-nov-2026, desde una PC)

| Prueba | Resultado |
|---|---|
| Video corto público, audio + video 240p | ✅ cliente `WEB_EMBEDDED_PLAYER`, ambos descargables |
| Video musical popular, **audio + video 720p** | ❌ URLs con **HTTP 403** (exigen *PoToken*) |
| Video musical popular, **solo audio** | ✅ cliente `VISIONOS`, opus/webm descargable |

Por eso la descarga de video usa un **generador de PoToken** (sección 2, `online/potoken/`),
igual que Metrolist. Funciona solo en Android (necesita WebView), así que **aún no está
probado en un celular**. Si el PoToken falla, la canción se guarda solo con audio y en el modo
niña se ve la miniatura grande.

Tamaño aproximado por canción de 4 minutos: audio ~4 MB; video 480p ~15–25 MB.

## 4b. "Para ti" en el modo niña (desde v0.3.0)

- `online/RecommendationsRepository` lee el inicio de YouTube Music de la cuenta
  (`FEmusic_home`, 3 páginas, máx. 60 elementos reproducibles), baja las miniaturas a
  `cacheDir/para-ti/` y entrega `PlayableItem` con miniatura local. Se refresca cada 30 min.
- `online/OnlineStreamResolver` pide los enlaces al tocar una canción: video + audio (con
  PoToken) o solo audio. Usa clientes sin rangos obligatorios para que ExoPlayer los
  reproduzca directo (probado en PC: audio en línea OK con el cliente `VISIONOS`).
- `playback/SimplePlayer` lleva su propia cola: si una canción falla (sin internet) salta a la
  siguiente y se detiene si todas fallan.
- Si una recomendación ya está guardada, se reproduce desde el celular.
- Sin sesión, sin internet o con el interruptor del cuidador apagado, la sección no aparece.
- **Detección automática de internet** (`data/ConnectivityMonitor`, desde v0.3.1): usa la red
  con internet *verificado* por Android. Al perderse, "Para ti" se oculta al instante; la
  canción en línea que suena termina lo que ya cargó y el reproductor sigue con las canciones
  descargadas. Al volver el internet, "Para ti" reaparece y se actualiza.
- El contenido depende de la cuenta conectada: conviene usar una cuenta cuyo historial sea
  apropiado para la niña.

## 5. Pendiente / siguientes pasos

1. **Probar en un celular**: inicio de sesión real, "me gusta", listas, descarga con video
   (PoToken) y que la APK release funcione igual que la debug.
2. **Descargas en segundo plano** con WorkManager / servicio en primer plano (hoy la cola corre
   mientras la app está abierta, que es lo normal en modo kiosco).
3. **PIN** para entrar a la zona (fase 5).
4. Opción del cuidador para elegir calidad (solo audio / 360p / 480p / 720p) según el espacio.
