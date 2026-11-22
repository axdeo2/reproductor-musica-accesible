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
| `online/StreamDownloader.kt` | Obtiene la URL del audio con `InnerTubeExtractor` (sin HLS ni SABR) y la descarga **por rangos** a `<videoId>.m4a/.webm`, más la miniatura y el `.json`. |
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

Por eso la **versión 1 descarga solo audio** + miniatura en alta resolución (544×544). En el
modo niña se ve la miniatura grande mientras suena la música.

## 5. Pendiente / siguientes pasos

1. **Video descargado (PoToken).** Portar de Metrolist el generador de PoToken
   (`AndroidTokenProvider`, `PoTokenService`, `BotGuardPoTokenMinter`, `RemotePoTokenMinter`;
   ~1.000 líneas, usa un WebView en un proceso aparte) e inyectarlo como `tokenProvider` en
   `InnerTubeExtractor`. Luego descargar video + audio por separado y reproducirlos juntos con
   `MergingMediaSource`. Solo se puede probar en un celular real.
2. **Descargas en segundo plano** con WorkManager / servicio en primer plano (hoy la cola corre
   mientras la app está abierta, que es lo normal en modo kiosco).
3. **PIN** para entrar a la zona (fase 5).
4. Probar en el celular: inicio de sesión real, "me gusta", listas y que R8 no rompa nada en
   la APK release.
