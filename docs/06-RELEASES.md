# Publicar versiones (Releases)

Cada versión se publica como una **Release de GitHub** con la APK adjunta:

- Última versión, enlace fijo:
  <https://github.com/axdeo2/reproductor-musica-accesible/releases/latest/download/musica.apk>
- Todas las versiones: <https://github.com/axdeo2/reproductor-musica-accesible/releases>

## Firma de la APK

Todas las versiones deben firmarse con **la misma clave**. Si cambia la clave, Android no deja
instalar la actualización encima y habría que desinstalar la app, perdiendo lo descargado.

- La clave vive **fuera del repositorio**, en la PC del mantenedor:
  `~/.android-keys/musica-release.jks` + `~/.android-keys/musica-keystore.properties`.
- **Haz una copia de seguridad** de esa carpeta en un lugar seguro (por ejemplo, un gestor de
  contraseñas o un disco cifrado). Si se pierde, no se pueden publicar actualizaciones
  compatibles.
- `app/build.gradle.kts` la busca primero en variables de entorno (CI) y luego en ese archivo.
  Sin clave, la release se firma con la clave debug (útil para forks y pull requests).

## Configurar los secretos en GitHub (una sola vez)

Para que GitHub Actions publique solo, hay que guardar la clave como secretos del repositorio.
Desde la carpeta del proyecto, en PowerShell:

```powershell
$k = "$HOME\.android-keys"
$pw = (Select-String -Path "$k\musica-keystore.properties" -Pattern '^storePassword=(.*)$').Matches[0].Groups[1].Value
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$k\musica-release.jks")) | gh secret set RELEASE_KEYSTORE_BASE64
$pw | gh secret set RELEASE_KEYSTORE_PASSWORD
"musica" | gh secret set RELEASE_KEY_ALIAS
$pw | gh secret set RELEASE_KEY_PASSWORD
```

Mientras falten los secretos, el workflow **Publicar Release** termina con un aviso y no
publica nada (para no subir una APK firmada con otra clave).

## Publicar una versión nueva

1. Subir `versionCode` (+1) y `versionName` en `app/build.gradle.kts`, hacer commit y push.
2. Crear y subir la etiqueta:

   ```powershell
   git tag v0.3.0
   git push origin v0.3.0
   ```

3. GitHub Actions compila, verifica que la firma no sea la de debug y crea la Release con
   `musica-v0.3.0.apk` y `musica.apk`.

**Sin secretos (publicación manual desde la PC):**

```powershell
./gradlew assembleRelease
Copy-Item app/build/outputs/apk/release/app-release.apk musica-v0.3.0.apk
Copy-Item app/build/outputs/apk/release/app-release.apk musica.apk
gh release create v0.3.0 musica-v0.3.0.apk musica.apk --title "Música v0.3.0" --generate-notes
```
