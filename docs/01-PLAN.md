# Plan del proyecto

## 1. Objetivo

Que la niña pueda escuchar y ver su música **sola, sin interrupciones y sin quedarse atrapada en
otra app**, usando los mismos gestos y la misma "forma" de pantalla que ya conoce de YouTube.

## 2. Usuarios

| Usuario | Qué necesita |
|---|---|
| **La niña (usuaria principal)** | Ver miniaturas, tocar una, que suene; cambiar de canción; girar el celular para pantalla completa. Nada de texto obligatorio, nada que la saque de la app. |
| **Cuidador / familia** | Elegir la carpeta de videos, poner un volumen máximo, activar/desactivar el bloqueo, salir de la app cuando haga falta. Todo detrás de un acceso oculto. |

## 3. Alcance

### Incluido (versión 1)
- Leer videos (`.mp4`, `.mkv`, `.webm`, `.3gp`) de **una carpeta** elegida por el cuidador.
- Pantalla **Inicio**: cuadrícula/lista de miniaturas grandes al estilo del feed de YouTube.
- Pantalla **Ver video (vertical)**: reproductor 16:9 arriba, controles grandes, y abajo la lista
  "A continuación" para cambiar de video.
- **Pantalla completa (horizontal)**: al girar el celular o tocar el botón de pantalla completa.
- **Reproducción automática** del siguiente video y repetición de la lista al terminar
  (nunca se queda en silencio ni en una pantalla "vacía").
- Recordar el último video y la posición al volver a abrir la app.
- **Zona del cuidador** oculta (mantener presionado + PIN) con: carpeta, volumen máximo,
  orden (aleatorio / alfabético / más reproducidos), bloqueo kiosco, salir.
- **Modo kiosco** (ver [04-BLOQUEO-KIOSCO.md](04-BLOQUEO-KIOSCO.md)).
- Funciona **sin internet**: la app ni siquiera pide el permiso `INTERNET`.

### Fuera de alcance (por ahora)
- Descargar videos desde la app (se copian a la carpeta desde la PC o con otra app).
- Búsqueda por texto, comentarios, likes, suscripciones, cuentas.
- Listas de reproducción múltiples (se puede evaluar en v2 con subcarpetas = "canales").

## 4. Fases

| Fase | Entregable | Criterio de "terminado" |
|---|---|---|
| **0. Base** | Proyecto Android (Gradle, Compose, Media3), CI en GitHub Actions que genera la APK debug | `./gradlew assembleDebug` pasa en CI y la APK queda como artefacto descargable |
| **1. Biblioteca** | Elegir carpeta (SAF), escanear videos, generar miniaturas, pantalla Inicio | Se ven las miniaturas de la carpeta real del celular en < 2 s después del primer escaneo |
| **2. Reproductor** | Pantalla Ver video vertical + lista "A continuación", reproducción automática, repetir | Tocar una miniatura reproduce; al terminar sigue el siguiente; tocar otro de la lista cambia |
| **3. Pantalla completa** | Rotación horizontal, modo inmersivo, controles que aparecen y se ocultan | Girar el celular entra a pantalla completa sin cortar el audio; volver a vertical regresa |
| **4. Accesibilidad** | Botones grandes, anti-toques accidentales, volumen máximo, retroalimentación háptica | Prueba con la niña: usa la app 15 min sin pedir ayuda |
| **5. Zona del cuidador** | Acceso oculto + PIN, ajustes, persistencia | El cuidador cambia carpeta y volumen; la niña no encuentra la zona por accidente |
| **6. Bloqueo kiosco** | Fijación de pantalla → luego Lock Task con Device Owner + app como "Inicio" | Botones Inicio y Recientes no hacen nada; solo el cuidador puede salir |
| **7. Pruebas con la usuaria** | Ajustes finos según observación (tamaños, colores, gestos que confunden) | Uso autónomo durante varios días seguidos |

## 5. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Formatos de video que el celular no decodifica | Media3 cubre los comunes; la app **salta automáticamente** al siguiente video si uno falla, nunca muestra un error a la niña |
| Carpeta con muchos videos (cientos) → lenta | Escaneo en segundo plano, caché de miniaturas en disco, listas perezosas (`LazyVerticalGrid`) |
| Toques accidentales (palma, varios dedos) | Ignorar multitoque, "debounce" de toques, sin gestos de doble toque que salten la canción |
| Que ella cierre la app o se vaya a Inicio | Modo kiosco (fase 6) |
| Configurar Device Owner exige quitar cuentas Google o restablecer el celular | Empezar con **fijación de pantalla** (cero código, cero riesgo); Device Owner solo si hace falta, con guía paso a paso |
| Volumen muy alto | Límite de volumen configurable por el cuidador |

## 6. Decisiones abiertas (para validar con la familia)

1. ¿Modelo y versión de Android del celular? (define si se puede usar todo lo de Android 9+.)
2. ¿Ella prefiere la lista de Inicio en **cuadrícula** (2 columnas) o **lista** de una columna como YouTube?
3. ¿Se mantiene el **doble toque para adelantar/retroceder 10 s** como en YouTube, o se quita para evitar saltos accidentales?
4. ¿Se activa bloqueo kiosco total (Device Owner) o basta con la fijación de pantalla?
