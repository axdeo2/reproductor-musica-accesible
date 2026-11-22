# Evaluación: bloquear la app (modo kiosco)

**Objetivo:** que la niña **no pueda salir** de la app con los botones del sistema
(Inicio ●, Recientes ▢) ni con gestos, y que como máximo funcione **Atrás ◀** — que dentro de la
app solo navega entre pantallas y en Inicio no hace nada.

## 1. Opciones evaluadas

| # | Opción | Bloquea Inicio | Bloquea Recientes | Bloquea notificaciones | Código | Requiere | Veredicto |
|---|---|---|---|---|---|---|---|
| A | **Fijación de pantalla** (App pinning) del sistema | ✅ | ✅ | ✅ | Ninguno | Activarla en Ajustes | ✅ **Empezar aquí** |
| B | **Lock Task Mode + Device Owner** | ✅ | ✅ | ✅ (configurable) | Medio | Configurar por ADB una vez | ✅ **Solución definitiva** |
| C | App como **launcher (Inicio)** predeterminado | ⚠️ Inicio vuelve a la app | ❌ | ❌ | Bajo | Elegirla como Inicio | Complemento de B |
| D | **Modo inmersivo** (ocultar barras) | ❌ deslizar las muestra | ❌ | ❌ | Bajo | — | Solo estético, ya incluido |
| E | App de kiosco de terceros (Fully Kiosk, SureLock…) | ✅ | ✅ | ✅ | Ninguno | Licencia / anuncios / dar control a un tercero | ❌ No recomendado |
| F | Accesibilidad para "atrapar" botones | Parcial | Parcial | ❌ | Alto | Permiso de accesibilidad | ❌ Frágil y mal visto por Android |

## 2. Opción A — Fijación de pantalla (inmediata, sin código)

Viene en todos los Android desde 5.0.

1. **Ajustes → Seguridad → Más ajustes de seguridad → Fijar app** (el nombre varía por marca:
   "Fijar pantalla", "Anclar ventana", "App pinning"). Activarla.
2. Activar **"Pedir PIN / patrón para dejar de fijar"**. ← importante.
3. Abrir la app → botón **Recientes ▢** → tocar el ícono de la app arriba → **Fijar**.

**Resultado:** Inicio y Recientes quedan sin efecto; la barra de notificaciones no se abre.
Para salir hay que mantener **Atrás + Recientes** a la vez y luego poner el PIN del celular.

**Mejora en la app:** desde la zona del cuidador, un botón "Bloquear" llama a
`startLockTask()`; sin Device Owner, Android muestra el diálogo de confirmación de fijación y el
cuidador solo toca "Fijar". Así no hay que pasar por Recientes.

**Limitaciones:** se pierde al reiniciar el celular; la combinación Atrás + Recientes puede
descubrirse por accidente (por eso el PIN es obligatorio).

## 3. Opción B — Lock Task Mode con Device Owner (definitiva)

Se registra la app como **"propietaria del dispositivo"** (Device Owner). Con eso:

- `startLockTask()` entra en kiosco **sin pedir confirmación**, directamente al abrir la app.
- Con `setLockTaskFeatures(...)` (Android 9+) se elige qué queda visible. Se configura
  `LOCK_TASK_FEATURE_NONE` (o solo `GLOBAL_ACTIONS` para poder apagar el celular): **Inicio y
  Recientes desaparecen**, **Atrás sigue funcionando** dentro de la app. ← exactamente lo pedido.
- La barra de notificaciones queda bloqueada.
- Combinado con la **opción C** (app como Inicio predeterminado, vía
  `addPersistentPreferredActivity`), al **reiniciar** el celular arranca directo en la música.
- Se puede además impedir desinstalar la app (`setUninstallBlocked`).

### Implementación (resumen)

```kotlin
// kiosk/KioskAdminReceiver.kt
class KioskAdminReceiver : DeviceAdminReceiver()

// kiosk/KioskManager.kt
fun enableKiosk(activity: Activity) {
    val dpm = activity.getSystemService(DevicePolicyManager::class.java)
    val admin = ComponentName(activity, KioskAdminReceiver::class.java)
    if (dpm.isDeviceOwnerApp(activity.packageName)) {
        dpm.setLockTaskPackages(admin, arrayOf(activity.packageName))
        if (Build.VERSION.SDK_INT >= 28) {
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS)
        }
        // Arrancar como "Inicio" después de reiniciar
        val home = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME); addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(admin, home,
            ComponentName(activity, MainActivity::class.java))
    }
    activity.startLockTask() // sin Device Owner → cae a la opción A (fijación con confirmación)
}

fun disableKiosk(activity: Activity) {      // solo desde la zona del cuidador (PIN)
    activity.stopLockTask()
}
```

Manifiesto: `KioskAdminReceiver` con `android.permission.BIND_DEVICE_ADMIN` y un
`res/xml/device_admin.xml`; `MainActivity` con `android:lockTaskMode="if_whitelisted"` y un
`intent-filter` con `CATEGORY_HOME`.

### Configuración única (por el cuidador, con una PC)

1. Activar **Opciones de desarrollador** y **Depuración USB** en el celular.
2. **Quitar temporalmente las cuentas** (Google, Samsung, etc.) en Ajustes → Cuentas.
   Android solo permite asignar Device Owner si el dispositivo no tiene cuentas.
3. Instalar la APK y ejecutar desde la PC:
   ```
   adb shell dpm set-device-owner <paquete.de.la.app>/.kiosk.KioskAdminReceiver
   ```
4. Volver a agregar las cuentas si se necesitan.

> Si el paso 3 falla con *"already several accounts"* o *"already provisioned"*, la única
> alternativa es restablecer de fábrica y hacer el paso 3 antes de agregar cuentas.

### Salida de emergencia (obligatoria)

- Zona del cuidador → PIN → **"Desbloquear"** (`stopLockTask()`) y **"Quitar propietario"**
  (`clearDeviceOwnerApp`) para devolver el celular a la normalidad.
- Documentar el PIN en un lugar seguro de la familia.

## 4. Recomendación

1. **Ahora (sin esperar a terminar la app):** usar la **opción A** con PIN. Cero riesgo y
   resuelve el 90 % del problema.
2. **Fase 6 del plan:** implementar **B + C** con caída automática a A si la app no es Device
   Owner. Así la misma APK funciona en ambos escenarios.
3. **No** usar apps de kiosco de terceros ni servicios de accesibilidad para esto.

## 5. Qué ve la niña en cada caso

| Acción de la niña | Sin bloqueo | Opción A | Opción B + C |
|---|---|---|---|
| Toca **Inicio ●** | Sale de la app | No pasa nada | No hay botón |
| Toca **Recientes ▢** | Ve otras apps | No pasa nada | No hay botón |
| Toca **Atrás ◀** en Inicio de la app | Sale de la app | Nada (la app lo intercepta) | Nada |
| Baja la barra de notificaciones | Se abre | Bloqueada | Bloqueada |
| Se reinicia el celular | Pantalla normal del teléfono | Pantalla normal (hay que fijar otra vez) | **Arranca en la app** |
