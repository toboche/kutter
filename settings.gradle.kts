rootProject.name = "Pi4J-Kotlin"
//include("lib")
include("example")

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}