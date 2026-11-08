plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "it.lspin.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "it.lspin.tv"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        // Chiave TMDB opzionale (copertine film): secret di GitHub "TMDB_KEY" oppure si imposta dall'app
        buildConfigField("String", "TMDB_KEY", "\"${System.getenv("TMDB_KEY")?.takeIf { it.isNotBlank() } ?: "2c9a5094d522560fc53793a5d43167e1"}\"")
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { checkReleaseBuilds = false; abortOnError = false }
}
