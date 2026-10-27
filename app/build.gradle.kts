plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["redirectSchemeName"] = "rocketplayer"
        manifestPlaceholders["redirectHostName"] = "spotify-callback"
    }

    buildTypes {
        // Release: minificación con R8 + reducción de recursos + ofuscación + firma
        // Esto hace el APK más pequeño, seguro y listo para distribución
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Firma automática con el keystore de release
            signingConfig = signingConfigs.create("release") {
                storeFile = file("/Users/samuel/.android/rocketplayer-release-key.jks")
                storePassword = "RocketPlayer2026"
                keyAlias = "rocketplayer"
                keyPassword = "RocketPlayer2026"
            }
        }
        // Debug: sin minificación para iteración rápida durante desarrollo
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        // ViewBinding — genera clases seguras para acceder a vistas del XML
        // Elimina la necesidad de findViewById() y previene crashes por IDs inválidos
        viewBinding = true
    }
}

dependencies {
    // ═══════════════════════════════════════════════════════════════
    // Core Android — componentes base del sistema
    // ═══════════════════════════════════════════════════════════════
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // ═══════════════════════════════════════════════════════════════
    // Media3 (ExoPlayer) — motor de reproducción de audio local
    // Maneja decoding PCM, ecualización biquad, y cola de medios
    // ═══════════════════════════════════════════════════════════════
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    // ═══════════════════════════════════════════════════════════════
    // Spotify — SDKs oficiales para auth y control de reproducción
    // ═══════════════════════════════════════════════════════════════
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation(libs.spotify.auth)
    implementation(libs.androidx.browser)

    // ═══════════════════════════════════════════════════════════════
    // Gson — serialización/deserialización JSON para tokens y config
    // ═══════════════════════════════════════════════════════════════
    implementation(libs.google.gson)

    // ═══════════════════════════════════════════════════════════════
    // Testing — solo se incluyen en builds de prueba, no en el APK final
    // ═══════════════════════════════════════════════════════════════
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

// Desarrollado por rcktsm
