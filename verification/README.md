# Published-artifact verification

This standalone consumer proves the packaged `ikokuko` API. It is deliberately absent from the root build: it has its own settings, repositories, Android activity, and platform tests. The same consumer supplies Android restoration/ownership tests and Maven publication proof.

## Commands

Run from the repository root with JDK 17 and an installed Android SDK. Set `JAVA_HOME` to your detected JDK 17 directory and `ANDROID_HOME` or `ANDROID_SDK_ROOT` to your SDK. When no SDK variable is provided, the runner detects an installed SDK 36 at the standard macOS or Linux user SDK location. It reads no configuration file to do this. No `local.properties` file is copied or read by the fixture runner.

```sh
# Configuration only: no publication, compilation, or tests.
python3 tooling/verify-publication.py --phase configure --platform android-unit

# Inspect the exact local publication task graph without building.
python3 tooling/verify-publication.py --phase plan --platform android-unit

# Stage the Android publication without running consumer tests.
python3 tooling/verify-publication.py --phase stage --platform android-unit

# Stage, check provenance and negative controls, then run binary JVM tests.
python3 tooling/verify-publication.py --platform android-unit

# Include real instrumentation on ONE explicitly selected, owned emulator.
python3 tooling/verify-publication.py --platform android-device --android-serial emulator-5580

# macOS: use the UUID of an owned disposable simulator clone.
python3 tooling/verify-publication.py --platform iosSimulatorArm64 --ios-device "$owned_simulator_uuid"
python3 tooling/verify-publication.py --platform iosX64 --ios-device "$owned_simulator_uuid"

# CI entry point: creates, boots, and deletes its own simulator clone.
python3 verification/apple-ci.py iosSimulatorArm64
python3 verification/apple-ci.py iosX64
```

Use the architecture matching the host runner. Do not pass the UUID of a user's original simulator. The helper clones an available, shut-down iPhone simulator and deletes only its own clone in `finally`. Local runs never choose a default Android serial or Apple device. The Android script leaves the selected emulator running; its owner controls its lifetime. The CI emulator job owns its process and stops it in its exit trap.

Every invocation prints its evidence directory under `build/verification/<unique-id>/`. `run.json` records the selected lane and outcome. Exact Gradle commands are stored as `*.command.json`; output is in `*.log`. Commands use `--no-parallel --max-workers=1 --no-daemon --no-configuration-cache`. Configuration and publication dry-runs are bounded to five minutes per Gradle invocation; publication and test invocations are bounded to twenty minutes each. A timeout attempts to stop only that run's isolated Gradle daemon registry.

## Publication and resolution

Each run copies the current working source into its generated directory, excluding local machine configuration, Git metadata, and build outputs. Finish concurrent library edits before staging: a source copy is not an atomic snapshot of a writer's changes. Root release configuration and tracked `VERSION_NAME` stay unchanged. Generated verification invocations disable configuration-cache reuse because each run has fresh source and its own daemon home.

The version is `0.0.0-verification.<random-id>-SNAPSHOT`. The only selected publication tasks are:

```text
:ikokuko:publishKotlinMultiplatformPublicationToMavenLocal
:ikokuko:publishAndroidReleasePublicationToMavenLocal       # Android lanes
:ikokuko:publishIosSimulatorArm64PublicationToMavenLocal    # Apple Silicon lane
:ikokuko:publishIosX64PublicationToMavenLocal               # Intel lane
```

Each invocation passes `-PVERSION_NAME=<unique-version>` and `-Dmaven.repo.local=<run>/maven`. The script accepts no arbitrary task or Gradle arguments. It never calls `publish`, `publishToMavenCentral`, or a remote repository publication task. Signing tasks remain in Gradle's normal task graph.

The standalone settings use `exclusiveContent` for all `com.quantipixels` modules, with Gradle module metadata as the only metadata source. There is no Maven Local, project dependency, composite build, or repository fallback for this group. Other dependencies use Google and Maven Central. The fixture reads `gradle/libs.versions.toml`; it adds no version matrix. Baseline: Kotlin 2.2.20, Compose Multiplatform 1.9.1 (Android Compose 1.9.3), AGP 8.11.2, Gradle 8.14.3, JVM 11. Android compile SDK is 36 and minimum SDK is 24; CI uses the API 35 Google APIs x86_64 emulator image.

After staging, the runner validates actual `.module` coordinates and nonempty variants and records a SHA-256 manifest.

`verifyStagedArtifacts` resolves both `debugRuntimeClasspath` and `debugAndroidTestRuntimeClasspath` for Android, or the selected native compile klibraries for Apple. The published library must be present in the app runtime or selected native configuration. The instrumentation APK has separate dependencies and drives the tested app, so it need not contain the library. Every `com.quantipixels` artifact found in either Android configuration still receives the same provenance checks.

The task checks the exact version, published platform coordinates, corresponding staged module metadata, manifest hashes, and resolved artifact bytes against staged files. The report includes selected variant attributes, hashes, and staged paths. Gradle may cache downloaded files elsewhere; matching bytes establish correspondence without assuming the cache path is the Maven path.

The Android debug consumer selects the published release library using `matchingFallbacks`. Apple lanes select `iosSimulatorArm64` or `iosX64` through actual Gradle metadata. Only those requested platform publications are built.

## Signing policy

[Vanniktech 0.35.0 source, `signAllPublications`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.35.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/MavenPublishBaseExtension.kt#L138-L152) sets signing required to `!version.endsWith("-SNAPSHOT")`. This applies to publications signed for Maven Local too. A non-SNAPSHOT fixture therefore needs a signing key. The unique SNAPSHOT version uses the existing policy: no hook, task, signature requirement, or tracked `signAllPublications()` is disabled or changed.

The runner builds a small allowlist of environment variables for Java/SDK discovery, uses generated Gradle user homes and project directories, and does not inherit signing, Maven Central, Gradle project-property, or Java option variables. It does not inspect a user's Gradle properties, init scripts, keyring, or environment secrets. Repository-local dependency caches and wrapper downloads are reused, while each run has a separate daemon registry. Failure evidence is retained; delete only the owned `build/verification` outputs when no proof is needed.

## Discriminating failures and test counts

A successful `verify` run must first resolve the real publication, then reject both negative controls:

1. An empty staged repository must fail with `Could not find com.quantipixels:ikokuko`. Resolution happens before manifest checking, so a manifest-only error cannot satisfy this control.
2. A copied stage with one altered AAR or KLIB must fail with `Changed staged artifact`. Its original manifest is kept so the altered artifact cannot establish its own expected hash.

The runner then executes `testDebugUnitTest`, adds `connectedDebugAndroidTest` for the device lane, or executes the selected `iosSimulatorArm64Test`/`iosX64Test`. Gradle test listeners and XML report checks reject zero tests, skipped tests, and failures. The device lane also requires nonzero instrumentation XML results independently of the JVM test count. Fresh generated build directories prevent old test reports from satisfying the checks.

Expected tests: two common binary tests on each platform; three additional Android instrumentation tests. Negative controls are separate failed Gradle invocations, not skipped tests.

## Native boundary and limits

The Android tests use a real `ComponentActivity`, Compose input, and `ActivityScenario.recreate()`:

- Enter text and select `second-id`; assert both preconditions before recreation, then assert restored values and submit in one event.
- Edit and submit in the same callback, first invalid and then valid, without allowing the validation effect to run between those operations.
- Focus a multiline field, inject Unicode text through Compose's text-input service, invoke its Done IME action, and reject an over-limit value without truncating it. The limit counts UTF-16 code units.

The common binary tests compose `Form` and `ValidationEffect` against the published API, then edit and submit synchronously. A second test checks Unicode length behavior. They run on Android's JVM and each selected Apple simulator architecture.

Compose text-input injection exercises the host's input connection and semantics, not a particular installed keyboard. This fixture does not prove a system clipboard paste gesture, composing-region behavior from a real keyboard, process death, or every field type.

## CI evidence

`.github/workflows/verification.yml` runs the two Apple consumer lanes and one Android instrumentation lane on pull requests or manual dispatch. New runs on the same workflow and ref cancel earlier in-progress runs. The Apple helper also runs the release simulator/sample checks and unsigned Xcode sample build. Release triggers and publication/signing policy are unchanged.

CI uploads `ikokuko-<Apple target>-publication-evidence` and `ikokuko-android-publication-evidence` from each workflow run, including on failure. Find these artifacts in the workflow run's artifact list. They contain command logs, command JSON, `run.json`, the staged SHA-256 manifest, provenance reports, and test reports. Android uploads also include emulator output and instrumentation XML results.

Interpret `run.json` together with its `phase`: a passed `configure`, `plan`, or `stage` run does not prove consumer tests. A complete `verify` result requires successful artifact resolution, both negative controls failing with their expected markers, and nonzero test reports with no failures or skips. Check the instrumentation reports separately for an Android device run. A dry-run task marked `SKIPPED` proves only task selection, not publication, signing, or test execution.

Use CI artifacts to assess a specific run. Generated local evidence directories are retained for diagnosis, but are not committed verification records or a standing claim that the tests passed.
