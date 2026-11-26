plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "org.calamares.miga"
    // Compiled against Android 16 QPR2 (API 37.2), which the current AndroidX libraries need;
    // targetSdk stays at 36, so the app's runtime behaviour does not change.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "org.calamares.miga"
        minSdk = 26
        targetSdk = 36
        // versionCode goes up on every commit (Play requires it to always grow). versionName is the
        // public version and only changes when a new version is released (1.0.0, 1.0.1, 1.1.0...),
        // with release notes in assets/changelogs and fastlane/.../changelogs (<versionCode>.txt).
        versionCode = 255
        versionName = "1.0.57"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    /**
     * Release signing is read from environment variables, never from values in the repository.
     * Locally, without them, the release build is simply unsigned; CI fills them from GitHub
     * Actions secrets only when they exist, so the workflow keeps working before they are
     * configured. Debug builds use each machine's own debug key: a key in this public repository
     * would let anyone sign an APK that updates an installed copy.
     */
    val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    // Two builds of the same app, which differ only in how they learn about a new version:
    // - play: the AAB for Google Play, with Play's in-app updates (knows the user's track, so
    //   testers hear about betas and everyone else about public versions);
    // - fdroid: the APKs on GitHub and F-Droid, without any Google library; they look at the latest
    //   stable GitHub Release (never a prerelease).
    // Both share applicationId and everything else.
    flavorDimensions += "store"
    productFlavors {
        create("play") { dimension = "store" }
        create("fdroid") { dimension = "store" }
    }

    // One APK per ABI (smaller) plus a universal one, like Obtainium-style releases. Only for
    // APKs (assemble*): the AAB for the Play Store is split by Play itself, and with ABI splits on
    // bundleRelease fails ("Sequence contains more than one matching element" in
    // PerModuleBundleTask), so they are off when the build asks for a bundle. CI builds APKs and
    // the AAB in separate Gradle runs.
    val buildingBundle = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
    splits {
        abi {
            isEnable = !buildingBundle
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    // AGP's dependency report is encrypted with Google's key, so F-Droid and IzzyOnDroid flag it as
    // an opaque blob. Keep it out of the APKs (GitHub, Obtainium, F-Droid) but in the AAB, where
    // Play Console uses it for SDK insights.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    // Play splits an AAB by language and only installs the device's languages, so on a phone in
    // English the Spanish strings never reached it and choosing Español in Settings changed
    // nothing. The app has its own language picker, so every language ships in the base APK.
    bundle {
        language {
            enableSplit = false
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
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Room schemas, one JSON per database version, read by the migration tests (see room {} below).
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// AGP 9 compiles Kotlin itself (no org.jetbrains.kotlin.android plugin); its options live here.
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

// Screenshot tests use Roborazzi's options API, still marked experimental.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (name.contains("UnitTest")) {
        compilerOptions.optIn.add("com.github.takahirom.roborazzi.ExperimentalRoborazziApi")
    }
}

/**
 * Room's Gradle plugin exports the schema of every database version to schemas/ (committed): CI
 * checks they are, and the migration tests open old versions from them. Unlike the plain
 * room.schemaLocation KSP option, it writes per variant and copies afterwards, so building debug
 * and release together no longer races on the same file.
 */
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    "playImplementation"(libs.play.app.update.ktx)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    // Installs the baseline profile (src/main/baseline-prof.txt) so start-up code runs compiled.
    implementation(libs.profileinstaller)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
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
    implementation(libs.exifinterface)
    implementation(libs.work.runtime.ktx)
    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)

    testImplementation(libs.junit)
    // Database migration tests (MigrationTestHelper) run on the JVM with Robolectric.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.room.testing)
    // Interface tests of whole screens, also on the JVM with Robolectric, and their reference
    // screenshots (src/test/snapshots; ./gradlew recordRoborazziDebug / verifyRoborazziDebug).
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    debugImplementation(libs.compose.ui.test.manifest)
}
