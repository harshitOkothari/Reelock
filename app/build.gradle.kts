plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing: CI injects the upload key via env vars (GitHub secrets). Locally / without secrets we use the dev key.
val ksFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "app.reelock"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.reelock"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "2.4"
    }
    signingConfigs {
        create("reelock") {
            if (ksFile != null) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            } else {
                storeFile = file("reelock.jks")
                storePassword = "reelock123"
                keyAlias = "reelock"
                keyPassword = "reelock123"
            }
        }
    }
    buildTypes {
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("reelock") }
        debug { signingConfig = signingConfigs.getByName("reelock") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint { abortOnError = false; checkReleaseBuilds = false }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.core:core-ktx:1.13.1")
}
