pluginManagement {
    repositories { gradlePluginPortal(); google(); mavenCentral() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}


include(
    ":app",
    ":core:designsystem",
    ":core:model",
    ":core:data",
    ":core:network",
    ":core:ble",
    ":core:common",
    ":feature:auth",
    ":feature:home",
    ":feature:console",
    ":feature:settings",
)

rootProject.name = "SenseWayTApp1"
include(":app")
 