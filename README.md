# Reproductor de Música Accesible

Aplicación Android (APK) que reproduce **videos musicales ya descargados en el celular**, con una
interfaz que imita la experiencia de YouTube en el móvil, pensada para una niña con discapacidad
que ya sabe usar YouTube pero se pierde cuando un anuncio la saca de la aplicación.

## El problema

- Ella sabe usar el celular táctil y su actividad favorita es escuchar música en YouTube.
- Los anuncios de YouTube a veces abren otras páginas o apps; ahí necesita que alguien le ayude
  a volver a su música.
- Fuera de eso, maneja bien la interfaz de YouTube: elige videos, cambia de canción, gira el
  celular para ver en pantalla completa.

## La solución

Un reproductor **100 % sin conexión**, sin anuncios y sin enlaces hacia afuera, que se ve y se usa
como YouTube (lista de videos, reproductor pequeño arriba con más videos abajo, pantalla completa
horizontal) pero solo muestra los videos de **una carpeta elegida por el cuidador**.
Además, se puede **bloquear** para que ella no salga de la app por accidente.

## Documentación

| Documento | Contenido |
|---|---|
| [docs/01-PLAN.md](docs/01-PLAN.md) | Alcance, fases y criterios de "terminado" |
| [docs/02-ARQUITECTURA.md](docs/02-ARQUITECTURA.md) | Stack técnico, módulos, flujo de datos, pantallas |
| [docs/03-ACCESIBILIDAD-Y-DISENO.md](docs/03-ACCESIBILIDAD-Y-DISENO.md) | Reglas de diseño centradas en la usuaria |
| [docs/04-BLOQUEO-KIOSCO.md](docs/04-BLOQUEO-KIOSCO.md) | Evaluación de cómo impedir que salga de la app |

## Stack (resumen)

Kotlin · Jetpack Compose · Media3 (ExoPlayer) · Coil (miniaturas de video) · DataStore ·
Storage Access Framework · Lock Task Mode (kiosco).

## Compilar e instalar

**Sin compilar:** cada push a `main` genera las APK en GitHub Actions
(pestaña *Actions* → último run → artefacto `musica-apk-…`). Descomprimir e instalar
`app-release.apk` en el celular (permitir "instalar apps desconocidas").

**Localmente** (JDK 17 + Android SDK 35):

```
./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

> La APK release todavía se firma con la clave *debug* para que se pueda instalar sin más pasos.
> Antes de distribuirla ampliamente hay que crear una firma propia.

## Proyecto open-source

El proyecto es abierto para que otras familias, terapeutas y fundaciones puedan usarlo y
adaptarlo. No usa ni contiene marcas, logos ni contenido de YouTube/Google: solo imita el
*estilo* de interfaz que la usuaria ya conoce. Ver la nota sobre el ícono en
[docs/03-ACCESIBILIDAD-Y-DISENO.md](docs/03-ACCESIBILIDAD-Y-DISENO.md#8-ícono-de-la-app).

La app no trae videos: cada familia copia al celular la música que ya tiene legítimamente.
