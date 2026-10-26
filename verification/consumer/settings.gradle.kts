pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val stagedRepository = providers.gradleProperty("stagedRepository").orNull
    ?: error("Pass -PstagedRepository=<isolated Maven directory>")
val stage = file(stagedRepository)
require(stage.isDirectory) { "Staged Maven directory does not exist: $stage" }
require(providers.gradleProperty("stagedVersion").isPresent) { "Pass -PstagedVersion=<unique version>" }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    versionCatalogs {
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "verificationStage"
                    url = stage.toURI()
                    metadataSources { gradleMetadata() }
                }
            }
            filter { includeGroup("com.quantipixels") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "ikokuko-publication-consumer"
