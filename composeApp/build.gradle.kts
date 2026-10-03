import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

val desktopOnly = providers.gradleProperty("desktopOnly").orNull == "true"
val appVersion = providers.gradleProperty("appVersion").orElse("2.0.3").get()
require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(appVersion)) { "appVersion must be major.minor.patch" }
val androidEnabled = !desktopOnly && providers.gradleProperty("iosOnly").orNull != "true"
if (androidEnabled) apply(plugin = "com.android.library")

kotlin {
    jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    if (androidEnabled) androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    if (System.getProperty("os.name").contains("Mac") && !desktopOnly) {
        iosArm64()
        iosSimulatorArm64()
        targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
            binaries.framework { baseName = "TxtNote"; isStatic = true }
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("com.squareup.okio:okio:3.16.0")
        }
        commonTest.dependencies { implementation(kotlin("test")); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2") }
        if (androidEnabled) {
            val androidMain by getting {
                dependencies { implementation("androidx.activity:activity:1.11.0") }
            }
        }
        val desktopMain by getting {
            dependencies { implementation(compose.desktop.currentOs); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2") }
        }
        val desktopTest by getting {
            dependencies { implementation(compose.desktop.uiTestJUnit4) }
        }
    }
}

if (androidEnabled) {
    extensions.configure<com.android.build.gradle.LibraryExtension> {
        namespace = "io.github.goumang.txtnote.shared"
        compileSdk = 36
        defaultConfig { minSdk = 24 }
        compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    }
}

compose.desktop {
    application {
        mainClass = "io.github.goumang.txtnote.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "txtNote"
            packageVersion = appVersion
            description = "Your thoughts, in plain text."
            vendor = "txtNote"
            modules("java.desktop", "jdk.unsupported")
        }
    }
}
