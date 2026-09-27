plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// En GitHub Actions cada compilación sube el versionCode, así Android acepta
// instalar el APK nuevo encima del anterior sin desinstalar.
val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "cl.exy.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "cl.exy.app"
        minSdk = 29
        targetSdk = 36
        versionCode = runNumber
        versionName = "1.0.$runNumber"

        // Vosk trae librerías nativas para 4 arquitecturas; los teléfonos reales
        // solo usan ARM. Así el APK pesa bastante menos.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        // Llave de depuración fija (credenciales públicas estándar de Android,
        // no protege nada). Sin ella cada compilación en la nube firmaría con
        // una llave distinta y el teléfono rechazaría las actualizaciones.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Reconocimiento de voz sin conexión. El modelo en español (assets/model-es)
    // lo descarga el CI; ver .github/workflows/build.yml.
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")
}
