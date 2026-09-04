plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.bmo00.miga"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bmo00.miga"
        minSdk = 26
        targetSdk = 34
        // El patch de versionName se incrementa en cada commit (1.0.1, 1.0.2...); versionCode sube en paralelo.
        versionCode = 12
        versionName = "1.0.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Firma de release: se lee de variables de entorno (nunca de valores fijos en el repo,
    // a diferencia del keystore de debug). En local, sin esas variables, el build "release"
    // simplemente sale sin firmar; en CI se rellenan desde secretos de GitHub Actions solo
    // cuando existen, así que el workflow no se rompe mientras no se hayan configurado.
    val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")

    signingConfigs {
        getByName("debug") {
            // Keystore de debug fijo y compartido (versionado en el repo) para que todos los
            // APKs debug —también los generados por CI en runners nuevos cada vez— queden
            // firmados igual y se puedan instalar como actualización sin desinstalar antes.
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    // APK independiente por arquitectura (más ligeros) + uno universal, al estilo de las
    // releases de Obtainium. Solo afecta a los APK (assemble*); el AAB de bundleRelease para
    // Play Store no se ve afectado, ya que Play hace su propio reparto por ABI internamente.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)
    implementation(libs.biometric)
}
