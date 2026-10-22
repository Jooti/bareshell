plugins {
    id("com.android.application")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

android {
    namespace = "net.jooti.bareshell"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.jooti.bareshell"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
