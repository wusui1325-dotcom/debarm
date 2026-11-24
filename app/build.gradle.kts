plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.wusper.debarm"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.wusper.debarm"
        minSdk = 26
        // target 28 is intentional: untrusted_app_29+ cannot exec
        // guest binaries from the app's writable filesDir (the Debian rootfs).
        // This app is meant to be sideloaded, not Play-distributed.
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        jniLibs {
            useLegacyPackaging = true
        }
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDir(rootProject.file("prebuilt"))
        }
    }
    androidResources {
        noCompress += listOf("so")
    }
    lint {
        // Sideload-only on purpose: target 28 is required to exec the
        // Debian rootfs from filesDir (see README).
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    implementation("com.github.luben:zstd-jni:1.5.6-6")
    testImplementation("junit:junit:4.13.2")
}
