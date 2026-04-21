# FarmifAI Signed APK Download

This document points to the official signed APK published in GitHub Releases.

## Direct Download

- Official APK URL:
  https://github.com/Bryan-Andres-Suarez-Sanchez/FarmifAI/releases/latest/download/FarmifAI-release-v1.0-20260420_182313-signed.apk

## Integrity Check (SHA-256)

Latest signed APK generated in this release cycle:

- File: FarmifAI-release-v1.0-20260420_182313-signed.apk
- SHA-256: cc8ad6f275adb0034bf6dd1c09b8d03e374641ee303b63269a88eca2d6118468

Verification command:

```bash
sha256sum FarmifAI-release-v1.0-20260420_182313-signed.apk
```

Expected output prefix:

```text
cc8ad6f275adb0034bf6dd1c09b8d03e374641ee303b63269a88eca2d6118468
```

## Installation

```bash
adb install -r FarmifAI-release-v1.0-20260420_182313-signed.apk
```

If an older version is already installed and signatures differ, uninstall first:

```bash
adb uninstall edu.unicauca.app.agrochat
adb install FarmifAI-release-v1.0-20260420_182313-signed.apk
```
