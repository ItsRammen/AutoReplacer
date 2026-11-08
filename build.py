#!/usr/bin/env python3
"""Build with official Android SDK tools and JDK 17+; no Gradle or app dependencies."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
BUILD = ROOT / "build"
JAVA = ROOT / "app/src/main/java"


def run(*args):
    subprocess.run([str(a) for a in args], check=True, cwd=ROOT)


def tests():
    dest = BUILD / "tests"
    dest.mkdir(parents=True, exist_ok=True)
    run("javac", "--release", "8", "-d", dest,
        JAVA / "dev/rentaldisplay/probe/Wire.java", JAVA / "dev/rentaldisplay/probe/SerialWriter.java",
        JAVA / "dev/rentaldisplay/probe/GridState.java", JAVA / "dev/rentaldisplay/probe/DiagnosticPolicy.java",
        JAVA / "dev/rentaldisplay/probe/LogStore.java", JAVA / "dev/rentaldisplay/probe/ExportFiles.java",
        JAVA / "dev/rentaldisplay/probe/StreamLifecycle.java",
        JAVA / "dev/rentaldisplay/probe/SessionWatchdog.java",
        JAVA / "dev/rentaldisplay/probe/BoundedCommand.java",
        JAVA / "dev/rentaldisplay/probe/InputOrigin.java", JAVA / "dev/rentaldisplay/probe/AdbRequestPolicy.java",
        JAVA / "dev/rentaldisplay/probe/ContactProfile.java", JAVA / "dev/rentaldisplay/probe/ContactTally.java",
        ROOT / "tests/WireTest.java", ROOT / "tests/SerialWriterTest.java", ROOT / "tests/GridStateTest.java",ROOT / "tests/LogStoreTest.java",
        ROOT / "tests/StreamLifecycleTest.java", ROOT / "tests/SessionWatchdogTest.java", ROOT / "tests/BoundedCommandTest.java", ROOT / "tests/InputPolicyTest.java", ROOT / "tests/ContactProfileTest.java")
    run("java", "-cp", dest, "dev.rentaldisplay.probe.WireTest")
    run("java", "-cp", dest, "dev.rentaldisplay.probe.SerialWriterTest")
    run("java", "-cp", dest, "dev.rentaldisplay.probe.GridStateTest")
    run("java", "-cp", dest, "dev.rentaldisplay.probe.LogStoreTest")
    run("java", "-cp", dest, "dev.rentaldisplay.probe.StreamLifecycleTest")
    run("java", "-cp", dest, "dev.rentaldisplay.probe.SessionWatchdogTest")
    run("java", "-cp", dest, "dev.rentaldisplay.probe.BoundedCommandTest")
    run("java", "-cp", dest, "dev.rentaldisplay.probe.InputPolicyTest")
    run("java", "-cp", dest, "dev.rentaldisplay.probe.ContactProfileTest")


def apk():
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk:
        raise SystemExit("Set ANDROID_SDK_ROOT to an Android SDK containing platforms;android-35 and build-tools;35.0.0")
    sdk = Path(sdk)
    tools = sdk / "build-tools/35.0.0"
    android = sdk / "platforms/android-35/android.jar"
    for item in [android, tools / "aapt2", tools / "d8", tools / "zipalign", tools / "apksigner"]:
        if not item.exists():
            raise SystemExit(f"Required Android SDK file missing: {item}")
    classes, dex = BUILD / "classes", BUILD / "dex"
    for directory in [classes, dex]:
        if directory.exists():
            shutil.rmtree(directory)
        directory.mkdir(parents=True)
    resources = BUILD / "resources.zip"
    if resources.exists():
        resources.unlink()
    run(tools / "aapt2", "compile", "--dir", ROOT / "app/src/main/res", "-o", resources)
    unaligned = BUILD / "probe-unaligned.apk"
    run(tools / "aapt2", "link", "-o", unaligned, "-I", android,
        "--manifest", ROOT / "app/src/main/AndroidManifest.xml", resources)
    run("javac", "--release", "8", "-classpath", android, "-d", classes, *sorted(JAVA.rglob("*.java")))
    run(tools / "d8", "--lib", android, "--min-api", "26", "--output", dex, *sorted(classes.rglob("*.class")))
    with zipfile.ZipFile(unaligned, "a", compression=zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(dex.glob("*.dex")):
            archive.write(file, file.name)
    aligned = BUILD / "probe-aligned.apk"
    run(tools / "zipalign", "-f", "4", unaligned, aligned)
    key = BUILD / "local-debug.keystore"
    if not key.exists():
        run("keytool", "-genkeypair", "-keystore", key, "-storepass", "android", "-keypass", "android",
            "-alias", "androiddebugkey", "-dname", "CN=Rental Display Probe Debug,O=Development,C=US",
            "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650")
    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    version = manifest.attrib["{http://schemas.android.com/apk/res/android}versionName"]
    output = BUILD / f"rental-display-probe-{version}.apk"
    run(tools / "apksigner", "sign", "--ks", key, "--ks-key-alias", "androiddebugkey",
        "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", output, aligned)
    run(tools / "apksigner", "verify", "--verbose", output)
    run(tools / "zipalign", "-c", "4", output)
    print(f"APK: {output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--tests-only", action="store_true")
    args = parser.parse_args()
    tests()
    if not args.tests_only:
        apk()
