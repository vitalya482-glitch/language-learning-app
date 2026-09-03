pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "language-learning-app"

include(
    ":android:app",
    ":android:core:common",
    ":android:core:network",
    ":android:core:update",
    ":android:core:designsystem",
    ":android:core:ai-api",
    ":android:core:ai-native",
    ":android:core:speech",
    ":android:feature:conversation",
    ":android:feature:home",
)
