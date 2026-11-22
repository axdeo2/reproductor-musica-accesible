#!/usr/bin/env bash
# Verifica que el código del modo niña no importe nada que use la red.
# Solo `online/`, `ui/caregiver/` y `di/` pueden hacerlo.
set -euo pipefail
cd "$(dirname "$0")/.."
BASE=app/src/main/java/app/mimusica
MODO_NINA=("$BASE/data" "$BASE/playback" "$BASE/ui/home" "$BASE/ui/components" "$BASE/ui/theme")
[ -d "$BASE/ui/watch" ] && MODO_NINA+=("$BASE/ui/watch")
PROHIBIDO='^import (app\.mimusica\.online|app\.mimusica\.ui\.caregiver|app\.mimusica\.di|io\.ktor|okhttp3|android\.webkit|com\.metrolist|java\.net\.(URL|HttpURLConnection|Socket)|coil3\.network)'
if grep -rnE "$PROHIBIDO" "${MODO_NINA[@]}" --include='*.kt'; then
  echo "::error::El modo niña importa código de red (ver líneas de arriba)."
  exit 1
fi
if grep -nE 'coil-network|coil\.network' app/build.gradle.kts gradle/libs.versions.toml; then
  echo "::error::Coil no debe poder descargar imágenes de internet (las miniaturas son archivos locales)."
  exit 1
fi
echo "OK: el modo niña no importa código de red."
