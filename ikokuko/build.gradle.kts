@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    id("maven-publish")
    id("signing")
}

group = "com.quantipixels"
version = findProperty("versionName") ?: "0.0.0-SNAPSHOT"

kotlin {

    androidLibrary {
        namespace = "com.quantipixels.ikokuko"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTestBuilder {}

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ikokukoKit"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.runner)
                implementation(libs.androidx.core)
                implementation(libs.androidx.testExt.junit)
            }
        }
    }

}

publishing {
    publications.withType<MavenPublication>().configureEach {
        groupId = project.group.toString()
        version = project.version.toString()
    }

    repositories {
        maven {
            name = "CentralPortal"
            url = uri("https://central.sonatype.com/api/v1/publisher/deploy")
            credentials {
                username = System.getenv("MAVEN_CENTRAL_USERNAME")
                password = System.getenv("MAVEN_CENTRAL_PASSWORD")
            }
        }
    }
}

signing {
    useInMemoryPgpKeys(
        System.getenv("SIGNING_KEY_ID"),
        System.getenv("SIGNING_KEY"),
        System.getenv("SIGNING_KEY_PASSWORD")
    )
    // Sign all created publications (needed for Maven Central)
    sign(publishing.publications)
}