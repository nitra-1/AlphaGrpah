pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "alphagraph"

include(
    "common",
    "reference",
    "market",
    "financial",
    "ownership",
    "corporate",
    "sector",
    "technical",
    "risk",
    "intelligence",
    "decision",
    "learning",
    "api",
    "scheduler",
    "bootstrap"
)
