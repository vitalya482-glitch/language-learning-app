# Release and update pipeline

## Single source of version truth

Application versions live in the repository-root `version.properties` file:

```properties
APP_VERSION_NAME=0.1.0
ANDROID_VERSION_CODE=1
```

For a normal Android version bump, edit only these two values. Do not hard-code versions in Gradle files or update manifests.

`APP_VERSION_NAME` is the user-facing semantic version. `ANDROID_VERSION_CODE` is Android's monotonically increasing integer and must increase for every installable update.

## Development channel

Every push to `main` runs the Android workflow. Pull requests run the same build verification but do not publish a release.

The workflow:

1. uses the committed Gradle Wrapper;
2. runs unit tests;
3. builds the signed `devRelease` APK;
4. verifies the APK metadata against `version.properties`;
5. computes SHA-256 and file size;
6. generates an LVK-compatible update manifest;
7. uploads the complete bundle as a GitHub Actions artifact;
8. for `main`, updates the rolling `dev-latest` GitHub Release.

Stable public URLs are therefore:

```text
https://github.com/vitalya482-glitch/language-learning-app/releases/download/dev-latest/language-learning-dev.apk
https://github.com/vitalya482-glitch/language-learning-app/releases/download/dev-latest/language-learning-manifest.json
```

The Android dev flavor reads the second URL directly. No GitHub token is embedded in the app.

## Signing

The repository contains a public development keystore only so test builds can update one another on Android devices. It must never be used for a production build.

A production signing key will be created separately and stored outside the repository, with CI receiving it through protected secrets.

## Promoting a new test version

1. Change `APP_VERSION_NAME` and increment `ANDROID_VERSION_CODE` in `version.properties`.
2. Commit and push to `main`.
3. Wait for `Android CI & Dev Release` to pass.
4. The existing app will see the generated manifest and offer the new APK.

No manual SHA-256 calculation, manifest editing, or APK upload is required.

## Legacy bridge from 0.1.0

The originally installed 0.1.0 build points to the older manifest in `LVK-Update-Feed`. When 0.1.1 is released, that old manifest must be updated once so 0.1.0 can discover 0.1.1. Version 0.1.1 and later use the automatically generated release manifest above, so future dev updates no longer need cross-repository writes.
