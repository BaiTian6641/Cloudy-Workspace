pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
    versionCatalogs { create("libs") { from(files("../../../gradle/libs.versions.toml")) } }
}
rootProject.name = "CloudyX11BrokerProbe"
include(":app", ":ssh", ":domain")
project(":ssh").projectDir = file("../../../shared/android/ssh")
project(":domain").projectDir = file("../../../shared/android/domain")
