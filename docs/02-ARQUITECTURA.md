# Arquitectura

## 1. Principios

1. **El modo niña nunca sale de la app.** Puede reproducir recomendaciones "Para ti" con
   internet, pero su código no toca la red: recibe interfaces (`RecommendationsSource`,
   `OnlineStreams`) y miniaturas ya guardadas como archivos. Todo lo que habla con YouTube
   vive en `online/` (ver [05-ZONA-DESCARGAS.md](05-ZONA-DESCARGAS.md)). No hay anuncios,
   analítica ni enlaces externos, y la app no lanza `Intent`s hacia otras apps.
2. **Nunca un callejón sin salida.** Cualquier error (video dañado, carpeta movida) se resuelve
   solo: se salta al siguiente video o se vuelve a Inicio. La niña nunca ve un diálogo de error.
3. **Siempre suena algo.** Al terminar un video sigue el siguiente; al terminar la lista, vuelve
   a empezar.
4. **Simple de mantener.** Una sola app, un solo módulo Gradle al inicio, sin backend.

## 2. Stack

| Capa | Tecnología | Por qué |
|---|---|---|
| Lenguaje | **Kotlin** | Estándar de Android |
| UI | **Jetpack Compose + Material 3** (tema oscuro propio estilo YouTube) | UI declarativa, fácil de hacer botones grandes y adaptar a rotación |
| Reproducción | **AndroidX Media3 – ExoPlayer** + `PlayerView` (vía `AndroidView`) | Reproduce archivos locales, maneja rotación sin cortar, controla volumen y lista |
| Sesión multimedia | **Media3 `MediaSessionService`** | El audio sigue si se apaga la pantalla; botones de auriculares funcionan |
| Miniaturas | **Coil 3 + `coil-video`** (`VideoFrameDecoder`) con caché en disco | Genera la imagen de cada video sin código propio |
| Acceso a la carpeta | **Storage Access Framework** (`ACTION_OPEN_DOCUMENT_TREE`) con permiso persistente | El cuidador elige *una* carpeta; no se necesita permiso de "todos los archivos" |
| Persistencia | **DataStore (Preferences)** | Carpeta, volumen máximo, PIN (hash), último video y posición, conteo de reproducciones |
| Inyección de dependencias | Manual (un `AppContainer`) | Proyecto pequeño, evita complejidad de Hilt |
| Kiosco | `DevicePolicyManager` + `Activity.startLockTask()` + `DeviceAdminReceiver` | Ver [04-BLOQUEO-KIOSCO.md](04-BLOQUEO-KIOSCO.md) |
| CI | **GitHub Actions** → `assembleDebug`/`assembleRelease`, APK como artefacto | Descargar la APK sin compilar localmente |

**Versiones objetivo:** `minSdk 26` (Android 8.0), `targetSdk`/`compileSdk` = última estable.
Funciones de kiosco avanzadas (`setLockTaskFeatures`) requieren Android 9+ (API 28).

## 3. Estructura del código

```
app/src/main/java/.../mimusica/
├── MainActivity.kt              # Única Activity; aloja Compose; inicia/termina Lock Task
├── MusicApp.kt                  # Application; crea AppContainer
├── di/AppContainer.kt           # Instancias compartidas (repositorios, player)
│
├── data/
│   ├── VideoItem.kt             # id, uri, título, duración, tamaño, fechaModificación
│   ├── VideoLibraryRepository.kt# Escanea la carpeta SAF, expone Flow<List<VideoItem>>
│   ├── SettingsRepository.kt    # DataStore: carpeta, volumen máx., PIN, orden, kiosco
│   └── PlaybackStateStore.kt    # Último video, posición, conteo de reproducciones
│
├── playback/
│   ├── PlaybackService.kt       # MediaSessionService con ExoPlayer
│   ├── PlayerController.kt      # Fachada: play(video), next(), prev(), cola, repetir
│   └── VolumeLimiter.kt         # Limita STREAM_MUSIC al máximo configurado
│
├── ui/
│   ├── theme/                   # Colores, formas, tamaños mínimos (ver doc de diseño)
│   ├── components/              # BigIconButton, VideoThumbnailCard, SafeTapModifier...
│   ├── home/HomeScreen.kt       # Feed de miniaturas
│   ├── watch/WatchScreen.kt     # Vertical: reproductor + "A continuación"
│   ├── watch/FullscreenPlayer.kt# Horizontal inmersivo
│   └── caregiver/               # Puerta oculta, PIN, ajustes
│
└── kiosk/
    ├── KioskAdminReceiver.kt    # DeviceAdminReceiver
    └── KioskManager.kt          # ¿Somos Device Owner? startLockTask, launcher por defecto
```

Si el proyecto crece, se puede separar en módulos `:core:data`, `:core:playback`, `:feature:*`,
pero no se justifica en la v1.

## 4. Flujo de datos

```
 Carpeta SAF ──► VideoLibraryRepository ──► Flow<List<VideoItem>> ──► HomeViewModel ──► HomeScreen
                        │                                                │
                        ▼                                                ▼ (toca miniatura)
                 Coil (miniaturas,                               PlayerController.play(video, cola)
                 caché en disco)                                         │
                                                                         ▼
                                                     PlaybackService (ExoPlayer + MediaSession)
                                                                         │
                                         Player.Listener ──► WatchViewModel ──► WatchScreen / Fullscreen
```

- **Escaneo:** `DocumentFile.fromTreeUri(...)` recorre la carpeta (y opcionalmente subcarpetas),
  filtra por tipo MIME `video/*`, obtiene duración con `MediaMetadataRetriever` en un
  `Dispatchers.IO`. El resultado se guarda en un índice JSON en `filesDir` para que la
  segunda apertura sea instantánea; luego se re-escanea en segundo plano y se actualiza.
- **Cola de reproducción:** al tocar un video se carga **toda la lista** en ExoPlayer
  (`setMediaItems(lista, índiceTocado)`) con `repeatMode = REPEAT_MODE_ALL`. Así "siguiente"
  y "anterior" son nativos y la lista "A continuación" es la misma cola.
- **Errores:** `onPlayerError` → `seekToNextMediaItem()` y se marca el video como dañado
  (no se vuelve a mostrar hasta el próximo escaneo).

## 5. Pantallas y navegación

Solo hay **dos destinos** para la niña (más la zona del cuidador). No hay menú lateral ni pestañas.

```
          ┌──────────────┐  toca miniatura   ┌───────────────────────┐   girar / botón ⛶   ┌──────────────────┐
  abrir ─►│    INICIO    │ ────────────────► │  VER VIDEO (vertical) │ ──────────────────► │ PANTALLA COMPLETA │
          │  (miniaturas)│ ◄──────────────── │ reproductor + lista   │ ◄────────────────── │   (horizontal)    │
          └──────────────┘   atrás / ⌄       └───────────────────────┘   girar / atrás      └──────────────────┘
                 │
     mantener 5 s esquina + PIN
                 ▼
          ┌──────────────┐
          │  CUIDADOR    │
          └──────────────┘
```

| Pantalla | Contenido |
|---|---|
| **Inicio** | Barra superior roja/negra con solo el ícono de la app (sin buscador). Lista de miniaturas grandes 16:9 con duración en la esquina, como el feed de YouTube. Si hay un video sonando, una **mini-barra** abajo con miniatura + ▶/⏸ (como el reproductor minimizado de YouTube). |
| **Ver video (vertical)** | Reproductor 16:9 arriba. Debajo, fila de 3 botones grandes: ⏮ anterior · ▶/⏸ · ⏭ siguiente. Debajo, lista "A continuación" con miniaturas grandes. Botón ⌄ para volver a Inicio sin detener la música. |
| **Pantalla completa** | Video a toda la pantalla, barras del sistema ocultas (modo inmersivo). Un toque muestra los controles grandes 4 s y se ocultan solos. Botón para salir de pantalla completa. |
| **Cuidador** | Elegir carpeta, volumen máximo, orden, incluir subcarpetas, activar/desactivar kiosco, cambiar PIN, salir de la app. |

**Rotación:** la Activity maneja `configChanges` (`orientation|screenSize|...`) para que el
`PlayerView` no se recree y el video no se corte al girar. Girar a horizontal en "Ver video" entra
a pantalla completa; girar a vertical sale.

**Botón Atrás:** Pantalla completa → Ver video → Inicio. En Inicio, **Atrás no hace nada**
(se intercepta con `BackHandler`), así nunca sale de la app con el botón Atrás.

## 6. Permisos del manifiesto

| Permiso | Uso |
|---|---|
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Servicio de reproducción |
| `WAKE_LOCK` | Mantener reproducción con pantalla apagada |
| `POST_NOTIFICATIONS` (Android 13+) | Notificación obligatoria del servicio de medios |
| `BIND_DEVICE_ADMIN` (en el receiver) | Modo kiosco |
| `INTERNET` | Solo lo usa el paquete `online/` (zona de descargas). Una prueba de CI impide que el código del modo niña lo importe. |
| ~~`READ_EXTERNAL_STORAGE` / `READ_MEDIA_VIDEO`~~ | No hacen falta: el acceso es por SAF |

## 7. Pruebas

- **Unitarias:** filtrado/orden de la biblioteca, lógica de PIN, `VolumeLimiter`.
- **Instrumentadas (Compose UI tests):** tocar miniatura → reproduce; Atrás en Inicio no sale;
  la zona del cuidador no se abre con toques cortos.
- **Manual con la usuaria:** observación de sesiones reales (ver fase 7 del plan).

## 8. Estado actual (v0.5.0)

- **Reproductor en la aplicación** (`AppContainer.player`) publicado por
  `playback/PlaybackService` (Media3 `MediaSessionService`): sigue sonando con la pantalla
  apagada, notificación con controles, botones de audífonos (siguiente/anterior usan la cola
  propia mediante un `ForwardingPlayer`), pausa con llamadas y al desconectar audífonos.
- **Última canción**: `data/PlaybackMemoryStore` guarda id + posición cada 5 s; al abrir la app
  queda lista en pausa.
- **Internet**: `data/ConnectivityMonitor` combina el estado de Android con una comprobación real
  (`online/InternetProbe`, `generate_204`, cada 15 s). Sin internet, Inicio solo muestra
  "En el celular" y aparece un ícono de nube tachada junto al logo.
- **Volumen máximo**: `playback/VolumeLimiter` baja el volumen del sistema al límite elegido.
- **Carpeta del celular**: `data/FolderLibrary` (SAF, hasta 3 niveles), miniaturas con
  `coil-video` (cuadro del video).
- **Anti-toques**: `ui/components/SafeClickable` (al soltar, tolerancia 24 dp, sin multitoque,
  anti-rebote 600 ms, vibración).

## 9. Buscador (v0.6.0)

- Buscador compacto en la fila del logo, arriba de Inicio (`ui/home/SearchBar.kt`), para no quitarle altura al video.
  Busca mientras se escribe; Atrás borra la búsqueda.
- **Local** (`data/LocalSearchIndex`): normaliza títulos y artistas una sola vez (sin tildes,
  minúsculas); todas las palabras deben aparecer, en cualquier orden; primero los títulos que
  empiezan con lo buscado. Se calcula en `Dispatchers.Default` con 60 ms de agrupación de teclas.
- **YouTube** (`online/SearchRepository`, filtro "Videos"): solo con internet y "Para ti"
  encendido. Espera 400 ms sin escribir, cancela búsquedas viejas, caché de 20 búsquedas,
  20 resultados, miniaturas en paralelo guardadas en `cacheDir/busqueda` (máx. 300).
  Probado en PC: 20 resultados en ~1,5 s.
