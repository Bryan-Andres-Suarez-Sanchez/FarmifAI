# FarmifAI Signed APK Download

This document points to the latest signed APK that can be installed directly.

## Direct Download

- Latest signed release APK:
  https://raw.githubusercontent.com/Bryan-Andres-Suarez-Sanchez/FarmifAI/apk-builds/apk-artifacts/release/latest-release.apk

## Artifact History

- Browse all published signed APKs:
  https://github.com/Bryan-Andres-Suarez-Sanchez/FarmifAI/tree/apk-builds/apk-artifacts/release

## Integrity Check (SHA-256)

Latest signed APK generated in this release cycle:

- File: FarmifAI-release-v1.0-20260416_193105-signed.apk
- SHA-256: 29eb2eee30c1e9c7064779e763cf3146045201a84297aecc7acdc745f596904e

Verification command:

```bash
sha256sum latest-release.apk
```

Expected output prefix:

```text
29eb2eee30c1e9c7064779e763cf3146045201a84297aecc7acdc745f596904e
```

## Installation

```bash
adb install -r latest-release.apk
```

If an older version is already installed and signatures differ, uninstall first:

```bash
adb uninstall edu.unicauca.app.agrochat
adb install latest-release.apk
```
