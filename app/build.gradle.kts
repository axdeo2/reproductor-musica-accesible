import java.util.Properties

/**
 * Firma de la APK release. Se busca, en orden:
 *  1. Variables de entorno (GitHub Actions, a partir de los secretos RELEASE_*).
 *  2. Un archivo de propiedades local fuera del repositorio, indicado por la variable
 *     MUSICA_KEYSTORE_PROPERTIES o en ~/.android-keys/musica-keystore.properties.
 * Si no hay ninguna, la release se firma con la clave debug (forks, pull requests).
 */
data class ReleaseKey(val file: File, val storePassword: String, val alias: String, val keyPassword: String)

fun findReleaseKey(): ReleaseKey? {
    System.getenv("RELEASE_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { path ->
        return ReleaseKey(
            file(path),
            System.getenv("RELEASE_KEYSTORE_PASSWORD").orEmpty(),
            System.getenv("RELEASE_KEY_ALIAS").orEmpty(),
            System.getenv("RELEASE_KEY_PASSWORD").orEmpty(),
        )
    }
    val propsFile = System.getenv("MUSICA_KEYSTORE_PROPERTIES")?.let(::File)
        ?: File(System.getProperty("user.home"), ".android-keys/musica-keystore.properties")
    if (!propsFile.isFile) return null
    val p = Properties().apply { propsFile.inputStream().use(::load) }
    return ReleaseKey(
        File(p.getProperty("storeFile")),
        p.getProperty("storePassword"),
        p.getProperty("keyAlias"),
        p.getProperty("keyPassword"),
    )
}

val releaseKey = findReleaseKey()

plugins {
    // AGP 9 trae Kotlin integrado: no se aplica org.jetbrains.kotlin.android.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.mimusica"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "app.mimusica"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "0.2.1"
    }

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = releaseKey.file
                storePassword = releaseKey.storePassword
                keyAlias = releaseKey.alias
                keyPassword = releaseKey.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)

    // Zona de descargas (solo cuidador): conexión con YouTube Music y descargas
    implementation(libs.innertubex)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.webkit)

    testImplementation(libs.junit)
}
