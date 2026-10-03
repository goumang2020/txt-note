plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}
val appVersion = providers.gradleProperty("appVersion").orElse("2.0.3").get()
require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(appVersion)) { "appVersion must be major.minor.patch" }
val versionParts = appVersion.split('.').map(String::toInt)
require(versionParts[0] in 0..2099 && versionParts[1] in 0..999 && versionParts[2] in 0..999) { "Version exceeds Android version code limits" }
android {
    namespace = "io.github.goumang.txtnote"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.goumang.txtnote"
        minSdk = 24
        targetSdk = 36
        versionCode = versionParts[0] * 1_000_000 + versionParts[1] * 1_000 + versionParts[2]
        versionName = appVersion
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
    val releaseKeyStore = System.getenv("ANDROID_SIGNING_KEYSTORE")
    if (!releaseKeyStore.isNullOrBlank()) {
        signingConfigs.create("release") {
            storeFile = file(releaseKeyStore)
            storePassword = requireNotNull(System.getenv("ANDROID_SIGNING_STORE_PASSWORD"))
            keyAlias = requireNotNull(System.getenv("ANDROID_SIGNING_KEY_ALIAS"))
            keyPassword = requireNotNull(System.getenv("ANDROID_SIGNING_KEY_PASSWORD"))
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (!releaseKeyStore.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
        }
    }
}
dependencies {
    implementation(project(":composeApp"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
