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

Una app con **dos partes**:

- **Modo niña (sin conexión):** se ve y se usa como YouTube (lista de videos, reproductor pequeño
  arriba con más videos abajo, pantalla completa horizontal), sin anuncios ni enlaces hacia
  afuera. Solo reproduce videos que ya están guardados en el celular.
- **Zona de descargas (cuidador, con PIN):** el cuidador inicia sesión con su cuenta de Google,
  ve su biblioteca y sus "me gusta" de YouTube Music y descarga al celular las canciones que
  quiera. También puede agregar una carpeta con videos ya descargados por otros medios.

Además, la app se puede **bloquear** para que ella no salga por accidente.

## Documentación

| Documento | Contenido |
|---|---|
| [docs/01-PLAN.md](docs/01-PLAN.md) | Alcance, fases y criterios de "terminado" |
| [docs/02-ARQUITECTURA.md](docs/02-ARQUITECTURA.md) | Stack técnico, módulos, flujo de datos, pantallas |
| [docs/03-ACCESIBILIDAD-Y-DISENO.md](docs/03-ACCESIBILIDAD-Y-DISENO.md) | Reglas de diseño centradas en la usuaria |
| [docs/04-BLOQUEO-KIOSCO.md](docs/04-BLOQUEO-KIOSCO.md) | Evaluación de cómo impedir que salga de la app |
| [docs/05-ZONA-DESCARGAS.md](docs/05-ZONA-DESCARGAS.md) | Inicio de sesión con Google, biblioteca, "me gusta" y descargas (solo cuidador) |
| [docs/06-RELEASES.md](docs/06-RELEASES.md) | Firma de la APK y cómo publicar versiones |

## Stack (resumen)

Kotlin · Jetpack Compose · Media3 (ExoPlayer) · Coil (miniaturas de video) · DataStore ·
Storage Access Framework · innertubex (YouTube Music, solo zona de descargas) · WebView (inicio
de sesión) · Lock Task Mode (kiosco).

## Descargar

**[⬇ Descargar la última versión (musica.apk)](https://github.com/axdeo2/reproductor-musica-accesible/releases/latest/download/musica.apk)**

Abrir el archivo en el celular y permitir "instalar apps desconocidas". Todas las versiones
están en [Releases](https://github.com/axdeo2/reproductor-musica-accesible/releases).
Cómo se publican: [docs/06-RELEASES.md](docs/06-RELEASES.md).

## Compilar e instalar

**Versiones de prueba:** cada push a `main` también genera las APK en GitHub Actions
(pestaña *Actions* → último run → artefacto `musica-apk-…`, requiere cuenta de GitHub).

**Localmente** (JDK 17 + Android SDK 35):

```
./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

> Sin la clave de firma del proyecto, la APK release se firma con la clave *debug*
> (ver [docs/06-RELEASES.md](docs/06-RELEASES.md)).

## Proyecto open-source

El proyecto es abierto para que otras familias, terapeutas y fundaciones puedan usarlo y
adaptarlo. Licencia **GPL-3.0** (ver [LICENSE](LICENSE)), la misma de
[Metrolist](https://github.com/MetrolistGroup/Metrolist) e
[innertubex](https://github.com/MetrolistGroup/innertubex), de los que se reutiliza la conexión
con YouTube Music.

La app no contiene marcas ni logos de YouTube/Google: solo imita el *estilo* de interfaz que la
usuaria ya conoce. Ver la nota sobre el ícono en
[docs/03-ACCESIBILIDAD-Y-DISENO.md](docs/03-ACCESIBILIDAD-Y-DISENO.md#8-ícono-de-la-app).

> **Aviso legal.** Este proyecto no está afiliado, autorizado ni respaldado por YouTube ni
> Google LLC. Acceder a YouTube Music con un cliente no oficial y descargar contenido **puede
> violar los Términos de Servicio de YouTube**. Quien use la zona de descargas lo hace bajo su
> propia responsabilidad, incluido el riesgo para su cuenta de Google.
