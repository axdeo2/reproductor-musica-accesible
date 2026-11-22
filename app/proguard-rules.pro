# Reglas de R8. Media3, Compose y Coil traen sus propias reglas de consumidor.

# El JavaScript del generador de PoToken (assets/po_token.html) llama a estos métodos
# por nombre a través de PoTokenBridge: R8 no debe renombrarlos ni eliminarlos.
-keepclassmembers class app.mimusica.online.potoken.BotGuardPoTokenMinter {
    @android.webkit.JavascriptInterface <methods>;
}

# Ktor: referencias opcionales a APIs de escritorio (mismas reglas que Metrolist).
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
