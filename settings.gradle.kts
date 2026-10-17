pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "CloudyWorkspace"
include(":console", ":desktop", ":domain", ":platform", ":design-system", ":identity", ":ssh", ":vnc")
project(":console").projectDir = file("apps/console")
project(":desktop").projectDir = file("apps/desktop")
project(":domain").projectDir = file("shared/android/domain")
project(":ssh").projectDir = file("shared/android/ssh")
project(":platform").projectDir = file("shared/android/platform")
project(":design-system").projectDir = file("shared/design-system/android")
project(":identity").projectDir = file("shared/android/identity")
project(":vnc").projectDir = file("adapters/desktop/vnc/android")
