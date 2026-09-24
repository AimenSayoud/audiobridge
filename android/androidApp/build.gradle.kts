plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// One version for the whole project, shared with the macOS app: ../../VERSION.
val appVersion = rootProject.file("../VERSION").readText().trim()
val appVersionCode = appVersion.split(".").map { it.toInt() }
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

// Release signing comes from Gradle properties (~/.gradle/gradle.properties or
// -P flags), never from the repository. Without them a release build falls back
// to the debug key, so anyone can still build and install one locally.
val releaseKeystore = providers.gradleProperty("AUDIOBRIDGE_KEYSTORE").orNull

android {
    namespace = "dev.audiobridge.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.audiobridge.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.gradleProperty("AUDIOBRIDGE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("AUDIOBRIDGE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("AUDIOBRIDGE_KEY_PASSWORD").get()
            }
        }
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.zxing.core)
}
