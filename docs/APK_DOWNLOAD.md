# FarmifAI Signed APK Download

This document points to the official signed APK published in GitHub Releases.

## Direct Download

- Official APK URL:
  https://github.com/Bryan-Andres-Suarez-Sanchez/FarmifAI/releases/latest/download/FarmifAI-release-v1.0-20260416_193105-signed.apk

## Integrity Check (SHA-256)

Latest signed APK generated in this release cycle:

- File: FarmifAI-release-v1.0-20260416_193105-signed.apk
- SHA-256: 29eb2eee30c1e9c7064779e763cf3146045201a84297aecc7acdc745f596904e

Verification command:

```bash
sha256sum FarmifAI-release-v1.0-20260416_193105-signed.apk
```

Expected output prefix:

```text
29eb2eee30c1e9c7064779e763cf3146045201a84297aecc7acdc745f596904e
```

## Installation

```bash
adb install -r FarmifAI-release-v1.0-20260416_193105-signed.apk
```

If an older version is already installed and signatures differ, uninstall first:

```bash
adb uninstall edu.unicauca.app.agrochat
adb install FarmifAI-release-v1.0-20260416_193105-signed.apk
```
