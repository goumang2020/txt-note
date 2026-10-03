pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "txtNote"
include(":composeApp")
if (providers.gradleProperty("desktopOnly").orNull != "true" && providers.gradleProperty("iosOnly").orNull != "true") include(":androidApp")
