plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "camlock123"
            keyAlias = "camlock"
            keyPassword = "camlock123"
        }
    }
    namespace = "com.raj.camlock"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.raj.camlock"
        minSdk = 29
        targetSdk = 34
        versionCode = 4
        versionName = "4.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
