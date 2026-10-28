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
        // Pleos SDK 저장소는 설명회 자료 받은 뒤 여기에 추가
    }
}

rootProject.name = "majung-vehicle"
include(":app")
