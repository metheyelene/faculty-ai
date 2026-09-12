import java.util.Properties

// Firebase config is intentionally not committed (it carries project
// identifiers): local builds read app/google-services.json from disk, CI
// injects it from the GOOGLE_SERVICES_JSON secret. The google-services plugin
// only applies when the config exists, so builds stay green without it and
// auth simply reports "not configured" at runtime.
if (file("google-services.json").exists()) {
    pluginManager.apply("com.google.gms.google-services")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // google-services is NOT applied here: the config file is not committed,
    // and the plugins block runs before project layout evaluation. It is
    // conditionally applied below via `pluginManager.apply`.
}

// Firebase config (google-services.json) is intentionally not committed: CI
// supplies it via the GOOGLE_SERVICES_JSON secret, local builds read it from
// disk. Builds stay green without it — the google-services plugin only
// applies when the config is present.
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
        versionCode = 15
        versionName = "1.6.0"
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
    // Auth (Firebase). Auth code is compiled unconditionally but fails fast at
    // runtime if the Firebase config is absent (see FirebaseAuthSource).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // Google Sign-In via Credential Manager (GetGoogleIdOption).
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.play.services.auth)
    implementation(libs.googleid)
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
