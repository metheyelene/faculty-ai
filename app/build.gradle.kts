import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Load signing config from keystore.properties (gitignored). See
// keystore.properties.example for the expected shape. If the file is missing,
// release builds still work but produce unsigned artifacts.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.bits.facultyai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bits.facultyai"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.5.2"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                // Paths in keystore.properties are relative to the project root.
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // R8 code shrinking + resource shrinking for a small, obfuscated
            // release artifact.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProperties.isNotEmpty()) {
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // On-device text recognition for timetable photo import (bundled, offline capable)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Event photos / note attachments image loading.
    implementation("io.coil-kt:coil-compose:2.6.0")
    // EXIF orientation read for photo compression.
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    // Liquid Glass: real backdrop blur via RenderEffect (API 31+),
    // graceful fallback below. 1.x line matches our Kotlin 2.0 toolchain.
    implementation(libs.haze)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
