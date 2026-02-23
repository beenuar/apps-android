pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://raw.githubusercontent.com/guardianproject/gpmaven/master") }
    }
}

rootProject.name = "DeepfakeShield"

include(":app")
include(":core")
include(":data")
include(":ml")
include(":feature:home")
include(":feature:shield")
include(":feature:alerts")
include(":feature:vault")
include(":feature:settings")
include(":feature:callprotection")
include(":feature:education")
include(":feature:diagnostics")
include(":feature:onboarding")
include(":feature:analytics")
include(":av")
include(":intelligence")
