plugins {
    id("com.android.application")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

val ciVersionName = providers.gradleProperty("ciVersionName").orNull
val ciVersionCode = providers.gradleProperty("ciVersionCode").orNull
require((ciVersionName == null) == (ciVersionCode == null)) {
    "ciVersionName and ciVersionCode must be supplied together"
}
require(ciVersionName == null || ciVersionName.matches(Regex("""(0|[1-9]\d*)(\.(0|[1-9]\d*)){2}"""))) {
    "ciVersionName must be a numeric major.minor.patch version"
}
val releaseVersionCode = ciVersionCode?.let {
    requireNotNull(it.toIntOrNull()?.takeIf { code -> code in 1..2_100_000_000 }) {
        "ciVersionCode must be an integer between 1 and 2100000000"
    }
}
val signingVariables = listOf(
    "ANDROID_KEYSTORE_PATH",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
)
val releaseSigning = signingVariables.associateWith {
    providers.environmentVariable(it).orNull
}
val signingRequested = releaseSigning.values.any { it != null }
if (signingRequested) {
    require(releaseSigning.values.all { !it.isNullOrBlank() }) {
        "Release signing requires all of: ${signingVariables.joinToString()}"
    }
    require(file(requireNotNull(releaseSigning["ANDROID_KEYSTORE_PATH"])).isFile) {
        "ANDROID_KEYSTORE_PATH must point to an existing keystore file"
    }
}

android {
    namespace = "net.jooti.bareshell"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.jooti.bareshell"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode ?: 1
        versionName = ciVersionName ?: "0.1"
        testInstrumentationRunner = "net.jooti.bareshell.FirstUseInstrumentation"
    }

    if (signingRequested) {
        val releaseKey = signingConfigs.create("releaseKey") {
            storeFile = file(requireNotNull(releaseSigning["ANDROID_KEYSTORE_PATH"]))
            storePassword = releaseSigning["ANDROID_KEYSTORE_PASSWORD"]
            keyAlias = releaseSigning["ANDROID_KEY_ALIAS"]
            keyPassword = releaseSigning["ANDROID_KEY_PASSWORD"]
        }
        buildTypes.named("release") {
            signingConfig = releaseKey
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
