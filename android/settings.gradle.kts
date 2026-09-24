pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "tethertone"

// Everything that is not a screen and not a speaker: the wire protocol, the
// pairing payload, the jitter buffer, the connection state machine. Targets
// android + jvm, so all of it is exercised by plain JVM tests and by the
// desktop sink — and so the macOS app can reuse it when Xcode is available.
include(":shared")

// The Android sink.
include(":androidApp")

// A headless JVM sink: the same shared code, played through javax.sound.
// This is how the protocol gets tested without a phone in the loop.
include(":desktopSink")
