@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jreleaser.model.Active
import org.jreleaser.model.Signing

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.jreleaser)
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
        pom {
            name.set("Ikokuko")
            description.set("Reactive, type-safe form validation for Compose Multiplatform (Android & iOS)")
            url.set("https://github.com/quantipixels/ikokuko")
            licenses {
                license {
                    name.set("Apache License 2.0")
                    url.set("https://github.com/quantipixels/ikokuko/LICENSE")
                }
            }
            developers {
                developer {
                    id.set("eosobande")
                    name.set("Olúwáṣeun Ṣóbándé")
                }
            }
            scm {
                connection.set("scm:git:git://github.com/quantipixels/ikokuko.git")
                developerConnection.set("scm:git:ssh://github.com:quantipixels/ikokuko.git")
                url.set("https://github.com/quantipixels/ikokuko")
            }
        }
    }

    repositories {
        maven {
            setUrl(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

jreleaser {
    gitRootSearch = true
    signing {
        active = Active.ALWAYS
        armored = true
        verify = true
        mode = Signing.Mode.MEMORY
        passphrase = System.getenv("SIGNING_KEY_PASSWORD")
        secretKey = System.getenv("SIGNING_KEY")
    }
    release {
        github {
            skipTag = true
            tagName = findProperty("tagName")?.toString()
            sign = true
            branch = "main"
            branchPush = "main"
            overwrite = true
            name = findProperty("versionName")?.toString()
            draft = false
        }
    }
    deploy {
        maven {
            mavenCentral.create("sonatype") {
                active = Active.ALWAYS
                url = "https://central.sonatype.com/api/v1/publisher"
                stagingRepository(layout.buildDirectory.dir("staging-deploy").get().toString())
                setAuthorization("Basic")
                applyMavenCentralRules = true
                sign = true
                checksums = true
                sourceJar = true
                javadocJar = true
                retryDelay = 60
            }
        }
    }
}