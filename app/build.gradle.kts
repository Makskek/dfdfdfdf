plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android { namespace = "com.courieranalytics.nativeapp"; compileSdk = 35
    defaultConfig { applicationId = "com.courieranalytics.nativeapp"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0.0" }
}

kotlin { jvmToolchain(17) }
