plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nadidstudio.nexis"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nadidstudio.nexis"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // Local on-device model (llama.cpp). Build for both 64-bit and
        // 32-bit ARM: arm64-v8a alone caused "App not installed"
        // (INSTALL_FAILED_NO_MATCHING_ABIS) on older/budget phones
        // (e.g. Android 10 devices) that are still 32-bit-only.
        ndk {
            abiFilters += "arm64-v8a"
            abiFilters += "armeabi-v7a"
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    ndkVersion = "26.3.11579264"

    // Fixed debug keystore committed to the repo (app/debug.keystore) so every
    // CI build is signed with the SAME key. Without this, each GitHub Actions
    // run generates a brand-new random debug key, and installing a new build
    // over an older one fails with "App not installed" (signature mismatch)
    // on any device that already has a previous build — which is exactly
    // the "works on one phone, not the other" symptom.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // Networking (for AI provider API calls)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Encrypted local storage for API keys / tokens (Android Keystore)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Google Sign-In (Credential Manager)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}
