#!/usr/bin/env python3
"""Stage a unique local publication and run its independent binary consumer."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LIBRARY = "ikokuko"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_copy(destination):
    # Copy current tracked and untracked source, never local machine configuration.
    listing = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=ROOT
    ).decode().split("\0")
    for name in listing:
        relative = Path(name)
        if not name or relative.parts[0] in {"verification", "tooling", ".alarina"}:
            continue
        if relative.name == "local.properties" or relative.name.startswith(".env"):
            continue
        source = ROOT / relative
        if source.is_file() and not source.is_symlink():
            target = destination / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)


def publication_manifest(stage, version):
    modules = list(stage.glob("com/quantipixels/*/" + version + "/*.module"))
    if not modules:
        raise RuntimeError("No Gradle module metadata was published")
    for module in modules:
        metadata = json.loads(module.read_text())
        component = metadata["component"]
        if component.get("group") != "com.quantipixels" or component.get("version") != version:
            raise RuntimeError("Unexpected publication coordinates: " + str(module))
        if not component["module"].startswith(LIBRARY) or not metadata.get("variants"):
            raise RuntimeError("Unexpected publication or empty variants: " + str(module))
    files = sorted(p for p in stage.rglob("*") if p.is_file())
    (stage / "staged-files.sha256").write_text("".join(
        digest(p) + "  " + p.relative_to(stage).as_posix() + "\n" for p in files
    ))


def nonzero_reports(directory):
    reports = list(directory.rglob("TEST-*.xml"))
    tests = failures = skipped = 0
    for report in reports:
        node = ET.parse(report).getroot()
        suites = [node] if node.tag == "testsuite" else node.findall("testsuite")
        for suite in suites:
            tests += int(suite.get("tests", 0))
            failures += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
            skipped += int(suite.get("skipped", 0))
    if tests == 0 or failures or skipped:
        raise RuntimeError(f"Invalid consumer result: tests={tests}, failures={failures}, skipped={skipped}")
    return tests


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase", choices=["configure", "plan", "stage", "verify"], default="verify")
    parser.add_argument("--platform", choices=["android-unit", "android-device", "iosSimulatorArm64", "iosX64"], required=True)
    parser.add_argument("--android-serial")
    parser.add_argument("--ios-device", help="UUID of an owner-created disposable simulator")
    args = parser.parse_args()
    apple = args.platform.startswith("ios")
    if apple and platform.system() != "Darwin":
        parser.error("Apple publication and tests require macOS")
    if args.phase == "verify" and apple and not args.ios_device:
        parser.error("--ios-device must identify an owned simulator clone")
    if args.phase == "verify" and args.platform == "android-device" and not args.android_serial:
        parser.error("--android-serial is required; no default device is selected")

    run = ROOT / "build/verification" / uuid.uuid4().hex
    run.mkdir(parents=True)
    stage = run / "maven"
    stage.mkdir()
    version = "0.0.0-verification." + run.name + "-SNAPSHOT"
    source = run / "source"
    source_copy(source)
    consumer = run / "consumer"
    shutil.copytree(ROOT / "verification/consumer", consumer)
    # The catalog stays the single versions source in both the checked-in and generated fixture.
    (run / "gradle").mkdir()
    shutil.copy2(ROOT / "gradle/libs.versions.toml", run / "gradle/libs.versions.toml")
    settings = consumer / "settings.gradle.kts"
    settings.write_text(settings.read_text().replace("../../gradle/libs.versions.toml", "../gradle/libs.versions.toml"))

    # No inherited Gradle properties, signing credentials, init scripts, or Java option injection.
    environment = {key: os.environ[key] for key in (
        "PATH", "JAVA_HOME", "ANDROID_HOME", "ANDROID_SDK_ROOT", "SYSTEMROOT", "TMPDIR"
    ) if key in os.environ}
    if not environment.get("ANDROID_HOME") and not environment.get("ANDROID_SDK_ROOT"):
        candidates = [Path.home() / "Library/Android/sdk", Path.home() / "Android/Sdk"]
        sdk = next((path for path in candidates if (path / "platforms/android-36/android.jar").is_file()), None)
        if sdk:
            environment["ANDROID_HOME"] = str(sdk)
    environment["KONAN_DATA_DIR"] = str(ROOT / "build/verification/konan")
    environment["HOME"] = str(run / "home")
    environment["GRADLE_USER_HOME"] = str(run / "gradle-user-home")
    Path(environment["HOME"]).mkdir()
    Path(environment["GRADLE_USER_HOME"]).mkdir(exist_ok=True)
    for name in ("caches", "wrapper"):
        cache = ROOT / "build/verification/gradle-user-home" / name
        cache.mkdir(parents=True, exist_ok=True)
        (Path(environment["GRADLE_USER_HOME"]) / name).symlink_to(cache, target_is_directory=True)
    if args.android_serial:
        environment["ANDROID_SERIAL"] = args.android_serial
    metadata = {"library": LIBRARY, "version": version, "platform": args.platform,
                "phase": args.phase, "stage": str(stage), "state": "running"}
    record = run / "run.json"
    record.write_text(json.dumps(metadata, indent=2) + "\n")
    print("Evidence: " + str(run), flush=True)

    common = ["--no-daemon", "--no-configuration-cache", "--no-parallel", "--max-workers=1", "--console=plain", "--stacktrace"]
    wrapper = str(source / "gradlew")
    def execute(label, directory, tasks, properties=(), expected_failure=False, expected_marker=None):
        command = [wrapper, "-p", str(directory), *common, *tasks, *properties]
        (run / (label + ".command.json")).write_text(json.dumps(command, indent=2) + "\n")
        with (run / (label + ".log")).open("w") as output:
            try:
                result = subprocess.run(command, cwd=directory, env=environment,
                                        stdout=output, stderr=subprocess.STDOUT,
                                        timeout=300 if args.phase in {"configure", "plan"} else 1200)
            except (subprocess.TimeoutExpired, KeyboardInterrupt):
                # This run has its own daemon registry; do not stop another user's Gradle work.
                subprocess.run([wrapper, "--stop"], cwd=directory, env=environment,
                               stdout=output, stderr=subprocess.STDOUT, timeout=60)
                raise
        if (result.returncode != 0) != expected_failure:
            raise RuntimeError(f"{label}: unexpected exit {result.returncode}; see {label}.log")
        if expected_marker and expected_marker not in (run / (label + ".log")).read_text():
            raise RuntimeError(f"{label}: failed for a different reason; inspect its log")
        print(f"{label}: exit {result.returncode}", flush=True)

    properties = ["-PstagedVersion=" + version, "-PstagedRepository=" + str(stage),
                  "-PverificationBuildDirectory=" + str(run / "consumer-build"),
                  "-PverificationAppleTarget=" + (args.platform if apple else "none")]
    if args.ios_device:
        properties += ["-PverificationIosDevice=" + args.ios_device]
    try:
        if args.phase == "configure":
            execute("configure", consumer, ["help"], properties)
        else:
            # Exact local tasks only. Never accept arbitrary task names or remote publishing tasks.
            target = {"iosSimulatorArm64": "IosSimulatorArm64", "iosX64": "IosX64"}.get(args.platform, "AndroidRelease")
            tasks = [f":{LIBRARY}:publishKotlinMultiplatformPublicationToMavenLocal",
                     f":{LIBRARY}:publish{target}PublicationToMavenLocal"]
            if args.phase == "plan":
                tasks += ["--dry-run"]
            execute("publication-plan" if args.phase == "plan" else "stage", source, tasks,
                    ["-PVERSION_NAME=" + version, "-Dmaven.repo.local=" + str(stage)])
            if args.phase != "plan":
                publication_manifest(stage, version)
            if args.phase == "verify":
                execute("artifact-resolution", consumer, ["verifyStagedArtifacts"], properties)
                empty = run / "empty-maven"
                empty.mkdir()
                negative = [p for p in properties if not p.startswith("-PstagedRepository=")]
                execute("empty-stage-must-fail", consumer, ["verifyStagedArtifacts"],
                        [*negative, "-PstagedRepository=" + str(empty)], expected_failure=True,
                        expected_marker=f"Could not find com.quantipixels:{LIBRARY}")
                wrong = run / "wrong-maven"
                shutil.copytree(stage, wrong)
                artifact = next(p for p in wrong.rglob("*") if p.suffix in {".aar", ".klib"})
                with artifact.open("ab") as output:
                    output.write(b"verification-negative-control")
                execute("wrong-artifact-must-fail", consumer, ["verifyStagedArtifacts"],
                        [*negative, "-PstagedRepository=" + str(wrong)], expected_failure=True,
                        expected_marker="Changed staged artifact")
                tests = [args.platform + "Test"] if apple else ["testDebugUnitTest"]
                if args.platform == "android-device":
                    tests += ["connectedDebugAndroidTest"]
                execute("consumer-tests", consumer, tests, properties)
                metadata["executedTests"] = nonzero_reports(run / "consumer-build")
                if args.platform == "android-device":
                    metadata["instrumentedTests"] = nonzero_reports(run / "consumer-build/outputs/androidTest-results")
        metadata["state"] = "passed"
    except (Exception, KeyboardInterrupt) as error:
        metadata["state"] = "failed"
        metadata["error"] = str(error)
        raise
    finally:
        record.write_text(json.dumps(metadata, indent=2) + "\n")
    print(json.dumps(metadata, indent=2))


if __name__ == "__main__":
    main()
