// Bouncy Castle comes into the build classpath through the Android Gradle plugin (1.80.2, with
// critical and high advisories GHSA-9pwp-9qqc-pr26 and GHSA-qp49-qgx5-5m26, fixed in 1.85). Raised
// until AGP brings a fixed version itself; the dependency review in CI flags it otherwise.
buildscript {
    dependencies {
        constraints {
            listOf("bcprov", "bcpkix", "bcutil").forEach { module ->
                classpath("org.bouncycastle:$module-jdk18on:1.87") {
                    because("versions before 1.85 have critical and high security advisories")
                }
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
}
