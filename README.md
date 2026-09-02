# Language Learning App

Cross-platform AI-powered language learning app with local models, conversations, corrections, translations and offline support.

## Current milestone — 0.1.0

The first Android build intentionally contains almost no product functionality. It proves the infrastructure that future development depends on:

- displays the installed app version;
- checks the existing **LVK Update Feed**;
- downloads a newer APK;
- optionally verifies SHA-256 when the feed provides it;
- hands the APK to the Android package installer;
- uses a stable development signing key so one dev build can update another;
- builds and publishes `language-learning-dev.apk` automatically from `main`.

## Repository layout

```text
android/
  app/                 Android application / composition root
  core/
    ai-api/            future local-AI engine contract
    common/            platform-neutral utilities
    designsystem/      Jetpack Compose design system
    network/           transport abstraction
    update/            LVK update client + APK installer
  feature/
    home/               version/update screen
shared/
  native-ai/            future cross-platform C++ AI core
ios/                     future Swift/SwiftUI client
docs/                    architecture documentation
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Android toolchain

- Kotlin
- Jetpack Compose
- Android Gradle Plugin 9.3
- compile/target SDK 37
- minimum SDK 26
- Java 17

The project uses Gradle 9.5. Android Studio can import the repository directly; CI installs the required Gradle version explicitly.

## Build locally

With JDK 17, Android SDK 37 and Gradle 9.5 installed:

```bash
gradle :android:app:assembleDevRelease
```

APK output:

```text
android/app/build/outputs/apk/dev/release/app-dev-release.apk
```

## Development update channel

The rolling APK is published at the `dev-latest` GitHub Release. The application reads:

```text
https://raw.githubusercontent.com/vitalya482-glitch/LVK-Update-Feed/main/manifests/language-learning.json
```

`dev` and future `prod` builds have different application IDs and update channels.

## Signing warning

`signing/dev-update-test.keystore` is public **by design** and must only sign development builds. The future commercial production key must be private and provided to CI through secrets.
