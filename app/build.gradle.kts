plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.terralock"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.terralock"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

configurations.configureEach {
    exclude(group = "xpp3", module = "xpp3")
}

kotlin {
    jvmToolchain(17)
    androidTarget()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.store5)
            implementation(libs.ktor.client.core)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)

            implementation(libs.nav3.runtime)
            implementation(libs.nav3.ui)
            implementation(libs.koin.lib.core)
            implementation(libs.koin.lib.compose)

            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
            implementation(libs.maplibre.sdk)
            implementation(libs.play.services.location)
            implementation(libs.play.services.auth)
            implementation(libs.auth.credentials)
            implementation(libs.auth.credentials.play)
            implementation(libs.auth.googleid)
            implementation(libs.car.app)
            implementation(libs.car.app.projected)
            implementation(libs.smack.android)
            implementation(libs.smack.tcp)
            implementation(libs.mqtt.paho)
            implementation(libs.koin.lib.android)
            implementation(libs.suncalc)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

tasks.matching { it.name.contains("AarMetadata") }.configureEach {
    enabled = false
}