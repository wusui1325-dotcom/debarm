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
        versionCode = 3
        versionName = "1.1.1"
        ndk {
            abiFilters += "arm64-v8a"
        }
        // Keep default values/ plus a few locales; drop dozens of
        // transitive AppCompat/Material translations from the APK.
        resourceConfigurations += listOf("zh", "zh-rTW", "zh-rHK", "en")
    }

    flavorDimensions += "edition"
    productFlavors {
        create("standard") {
            dimension = "edition"
            isDefault = true
            applicationId = "com.wusper.debarm"
            buildConfigField("boolean", "CODEX_EDITION", "false")
        }
        create("codex") {
            dimension = "edition"
            applicationId = "com.wusper.debarm.codex"
            versionCode = 1
            versionName = "1.0.0"
            buildConfigField("boolean", "CODEX_EDITION", "true")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sideloadable; uses the local Android debug keystore.
            // No project keystore is committed.
            signingConfig = signingConfigs.getByName("debug")
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
            // Extract to nativeLibraryDir so PRoot can be exec'd.
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "win/**",
                "darwin/**",
                "linux/**",
                "freebsd/**",
                "aix/**",
                "org/apache/commons/codec/language/**",
                "org/apache/commons/codec/language/bm/**",
                "META-INF/maven/**",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "kotlin/**/*.kotlin_builtins",
                "DebugProbesKt.bin",
            )
        }
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDir(rootProject.file("prebuilt"))
        }
    }
    lint {
        // Sideload-only on purpose: target 28 is required to exec the
        // Debian rootfs from filesDir (see README).
        disable += "ExpiredTargetSdkVersion"
        disable += "AllFilesAccessPolicy"
        disable += "ScopedStorage"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.apache.commons:commons-compress:1.27.1") {
        exclude(group = "com.github.luben", module = "zstd-jni")
    }
    implementation("org.tukaani:xz:1.10")
    // Pure-Java Zstd (no desktop JNI natives packed into the APK).
    implementation("io.airlift:aircompressor:0.27")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
    testImplementation("junit:junit:4.13.2")
}
