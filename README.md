# Language Learning App

Cross-platform AI-powered language learning app with local models, conversations, corrections, translations and offline support.

## Current milestone — 0.1.0

The first Android build intentionally contains almost no product functionality. It proves the infrastructure that future development depends on:

- displays the installed app version;
- checks for updates;
- downloads a newer APK;
- verifies SHA-256 when the manifest provides it;
- hands the APK to the Android package installer;
- uses a stable development signing key so one dev build can update another;
- builds and publishes the Android dev channel automatically from `main`.

## Repository layout

```text
android/
  app/                 Android application / composition root
  core/
    ai-api/            future local-AI engine contract
    common/            platform-neutral utilities
    designsystem/      Jetpack Compose design system
    network/           transport abstraction
    update/            update client + APK installer
  feature/
    home/               version/update screen
shared/
  native-ai/            future cross-platform C++ AI core
ios/                     future Swift/SwiftUI client
tools/release/           release/manifest tooling
docs/                    architecture and release documentation
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/RELEASING.md](docs/RELEASING.md).

## Android toolchain

- Kotlin
- Jetpack Compose
- Android Gradle Plugin 9.3
- compile/target SDK 37
- minimum SDK 26
- Java 17
- Gradle 9.5 through the committed Gradle Wrapper

## Build locally

With JDK 17 and Android SDK 37 installed:

```bash
./gradlew :android:app:assembleDevRelease
```

On Windows:

```bat
gradlew.bat :android:app:assembleDevRelease
```

APK output:

```text
android/app/build/outputs/apk/dev/release/app-dev-release.apk
```

## Versioning

`version.properties` is the single source of truth:

```properties
APP_VERSION_NAME=0.1.0
ANDROID_VERSION_CODE=1
```

A normal version bump changes only these values. CI generates the APK checksum, build metadata and update manifest automatically.

## Development update channel

The rolling development release is `dev-latest` and exposes stable public assets:

```text
language-learning-dev.apk
language-learning-dev.apk.sha256
language-learning-manifest.json
build-info.json
```

The dev app reads the manifest directly from the rolling GitHub Release. No GitHub access token is embedded in the APK.

## Signing warning

`signing/dev-update-test.keystore` is public **by design** and must only sign development builds. The future commercial production key must be private and provided to CI through protected secrets.
