pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "UnifiedReader"

include(
    ":app",
    ":core:common",
    ":core:model",
    ":core:network",
    ":core:database",
    ":data:kavita",
    ":data:stump",
    ":data:grimmory",
    ":data:repository",
    ":feature:library",
    ":feature:reader-preferences",
    ":feature:reader-core",
    ":feature:reader-comics",
    ":feature:reader-pdf",
    ":feature:reader-epub",
    ":feature:downloads",
    ":feature:library-shelves",
    ":feature:navigation",
    ":feature:settings",
)
