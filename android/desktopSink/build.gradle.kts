plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
}

application {
    mainClass.set("dev.audiobridge.desktop.MainKt")
}
