plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase config lives outside git. CI writes it from the GOOGLE_SERVICES_JSON secret.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "app.birthbaby"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.birthbaby"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    // One fixed signing key, so each new build installs over the last one without losing data.
    // CI decodes it from the ANDROID_KEYSTORE_B64 secret; without it, builds use a throwaway debug key.
    val keystore = System.getenv("KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        create("family") {
            if (keystore != null) {
                storeFile = keystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "birthbaby"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystore != null) signingConfigs.getByName("family") else signingConfigs.getByName("debug")
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
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
