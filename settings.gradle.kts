plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "breakpoint-tracker-ide"
include("ij-server", "ij-plugin")
