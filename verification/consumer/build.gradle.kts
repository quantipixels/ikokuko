import java.security.MessageDigest
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.testing.AbstractTestTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

layout.buildDirectory.set(file(providers.gradleProperty("verificationBuildDirectory").get()))
val stagedVersion = providers.gradleProperty("stagedVersion").get()
val stagedRepository = file(providers.gradleProperty("stagedRepository").get())
val appleTarget = providers.gradleProperty("verificationAppleTarget").orElse("none").get()
require(appleTarget in setOf("none", "iosSimulatorArm64", "iosX64"))

kotlin {
    androidTarget {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }
    when (appleTarget) {
        "iosSimulatorArm64" -> iosSimulatorArm64()
        "iosX64" -> iosX64()
    }
    sourceSets {
        commonMain.dependencies {
            implementation("com.quantipixels:ikokuko:$stagedVersion")
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.verification.test.runner)
            implementation(libs.verification.test.core)
            implementation(libs.verification.compose.test)
        }
    }
}

android {
    namespace = "com.quantipixels.ikokuko.verification"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.quantipixels.ikokuko.verification"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.compileSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes.getByName("debug") { matchingFallbacks += "release" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies { debugImplementation(libs.verification.compose.manifest) }

// Native tests must use a simulator chosen by the owner, never a user's default device.
tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    providers.gradleProperty("verificationIosDevice").orNull?.let { device.set(it) }
    doFirst {
        require(providers.gradleProperty("verificationIosDevice").isPresent) {
            "Pass -PverificationIosDevice=<owned simulator UUID>"
        }
    }
}

tasks.withType<AbstractTestTask>().configureEach {
    afterSuite(KotlinClosure2<org.gradle.api.tasks.testing.TestDescriptor, org.gradle.api.tasks.testing.TestResult, Unit>({ descriptor, result ->
        if (descriptor.parent == null) {
            check(result.testCount > 0 && result.skippedTestCount == 0L) {
                "Consumer tests must execute nonzero tests with no skips"
            }
        }
    }))
}

fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes()).joinToString("") { "%02x".format(it) }

// Gradle caches artifacts outside the repository. Compare bytes, not cache path names.
tasks.register("verifyStagedArtifacts") {
    group = "verification"
    doLast {
        val requiredConfigurations = if (appleTarget == "none") {
            setOf("debugRuntimeClasspath", "debugAndroidTestRuntimeClasspath")
        } else {
            setOf("${appleTarget}CompileKlibraries")
        }
        val selected = configurations.filter {
            it.isCanBeResolved && it.name in requiredConfigurations
        }
        check(selected.map { it.name }.toSet() == requiredConfigurations) {
            "Missing resolvable verification configurations: $requiredConfigurations"
        }
        // Resolve before checking the manifest so an empty repository proves resolution fails.
        val resolved = selected.associateWith { it.incoming.artifacts.artifacts }
        val manifest = stagedRepository.resolve("staged-files.sha256")
        check(manifest.isFile) { "Missing staged publication manifest" }
        val expected = manifest.readLines().associate { line ->
            val parts = line.split("  ", limit = 2)
            check(parts.size == 2)
            parts[1] to parts[0]
        }
        expected.forEach { (path, hash) ->
            val stagedFile = stagedRepository.resolve(path)
            check(stagedFile.isFile && sha256(stagedFile) == hash) { "Changed staged artifact: $path" }
        }
        val report = mutableListOf<String>()
        resolved.forEach { (configuration, resolvedArtifacts) ->
            val artifacts = resolvedArtifacts.filter {
                (it.id.componentIdentifier as? ModuleComponentIdentifier)?.group == "com.quantipixels"
            }
            // The instrumentation APK drives the tested app and has separate dependencies.
            // Require the library in the app or native configuration; check provenance in both APKs.
            if (configuration.name != "debugAndroidTestRuntimeClasspath") {
                check(artifacts.isNotEmpty()) { "No published artifact in ${configuration.name}" }
            }
            artifacts.forEach { artifact ->
                val id = artifact.id.componentIdentifier as ModuleComponentIdentifier
                check(id.version == stagedVersion && id.module.startsWith("ikokuko")) { "Wrong artifact: $id" }
                val directory = stagedRepository.resolve("com/quantipixels/${id.module}/${id.version}")
                check(directory.resolve("${id.module}-${id.version}.module").isFile) { "Missing Gradle metadata: $id" }
                val matching = directory.listFiles().orEmpty().filter {
                    it.isFile && it.extension in setOf("aar", "jar", "klib") && sha256(it) == sha256(artifact.file)
                }
                check(matching.isNotEmpty()) { "Resolved bytes differ from staged publication: ${artifact.file}" }
                report += "${configuration.name}: $id ${artifact.variant.attributes} ${sha256(artifact.file)} ${matching.first()}"
            }
        }
        layout.buildDirectory.file("reports/staged-artifacts.txt").get().asFile.apply {
            parentFile.mkdirs()
            writeText(report.joinToString("\n", postfix = "\n"))
        }
        report.forEach(::println)
    }
}
