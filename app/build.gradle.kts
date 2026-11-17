plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "my.klean.scalebridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "my.klean.scalebridge"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Put your Hercules recycler dashboard address here before building.
        // It can also be changed later on the tablet in Settings (hold the gear icon).
        buildConfigField("String", "DEFAULT_DASHBOARD_URL", "\"https://YOUR-HERCULES-APP-URL\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
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
}

dependencies {
    // Reads FTDI, Prolific (PL2303), CH340/CH341 and Silicon Labs CP210x USB-serial adapters
    implementation("com.github.mik3y:usb-serial-for-android:3.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
