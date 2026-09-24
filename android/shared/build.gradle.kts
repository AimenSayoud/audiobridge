plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(17)

    androidTarget()
    jvm()

    applyDefaultHierarchyTemplate()

    sourceSets {
        // android and jvm both have java.net and java.io; the socket transport is
        // written once here rather than twice. The audio sink is not: AudioTrack
        // and javax.sound have nothing in common.
        val jvmCommonMain = create("jvmCommonMain") { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmCommonMain)
        jvmMain.get().dependsOn(jvmCommonMain)

        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "dev.audiobridge.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
