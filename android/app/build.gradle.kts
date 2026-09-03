plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.searchhub.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.searchhub.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.6.1"
    }

    val releaseStoreFile = providers.gradleProperty("searchhubReleaseStoreFile")
        .orElse(System.getenv("SEARCHHUB_STORE_FILE") ?: "")
    val releaseStorePassword = providers.gradleProperty("searchhubReleaseStorePassword")
        .orElse(System.getenv("SEARCHHUB_STORE_PASSWORD") ?: "")
    val releaseKeyAlias = providers.gradleProperty("searchhubReleaseKeyAlias")
        .orElse(System.getenv("SEARCHHUB_KEY_ALIAS") ?: "")
    val releaseKeyPassword = providers.gradleProperty("searchhubReleaseKeyPassword")
        .orElse(System.getenv("SEARCHHUB_KEY_PASSWORD") ?: "")

    signingConfigs {
        create("release") {
            if (releaseStoreFile.get().isNotBlank()) {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseStoreFile.get().isNotBlank()) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.03.01"))
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.ui:ui:1.10.6")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}