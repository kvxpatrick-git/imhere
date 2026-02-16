pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "imhere"

include(":app")
include(":core-model")
include(":core-domain")
include(":core-data")
include(":feature-listening")
include(":feature-settings")
include(":platform-voice")
include(":platform-audio")
include(":platform-monitoring")

includeBuild("build-logic")
