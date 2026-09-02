# Architecture

The repository is intentionally a monorepo so Android, iOS and shared native AI code can evolve together without coupling UI code to a specific inference engine.

## Current modules

- `android/app` — Android composition root, application lifecycle, build flavors and wiring.
- `android/core/common` — platform-independent utility code.
- `android/core/network` — minimal transport abstraction; future HTTP clients can replace it without touching features.
- `android/core/update` — LVK Update Feed client, APK download, checksum verification and Android installer handoff.
- `android/core/designsystem` — shared Android Compose theme/components.
- `android/core/ai-api` — platform-neutral contract for future local AI engines.
- `android/core/ai-native` — Android JNI adapter for the shared C++ AI core.
- `android/feature/conversation` — first end-to-end conversation and native-engine smoke-test UI.
- `android/feature/home` — first feature module; currently only version/update UI.
- `shared/native-ai` — platform-neutral C++ model lifecycle/inference core reusable by Android (JNI/NDK), iOS (Swift bridge) and potentially desktop. The current deterministic implementation proves the integration boundary before a real model runtime is linked.
- `ios` — reserved for the future Swift/SwiftUI application.

## Dependency direction

UI feature -> domain/API contracts -> infrastructure implementation.

Feature modules must not directly depend on concrete AI runtimes, model file formats or update transport details.

## Build flavors

### dev

- Application ID: `kz.lvk.languagelearning.dev`
- Uses the public test signing key in `signing/dev-update-test.keystore`.
- Uses `manifests/language-learning.json` from LVK Update Feed.
- Intended only for sideloaded development builds.

### prod

- Application ID: `kz.lvk.languagelearning`
- No production signing secret is committed.
- Future commercial releases must use a private key supplied through CI secrets.
- Stable update feed is kept separate from dev.

The dev key is deliberately not suitable for production. This lets us test Android in-place updates now without ever exposing the future commercial signing key.

## Future AI architecture

A future implementation can provide multiple implementations of `LanguageModelEngine`:

1. Native C++ local runtime (for example a GGUF-compatible engine).
2. Android system/on-device AI adapter when supported by the device.
3. Vendor-specific acceleration adapters.
4. iOS adapter over the same shared native C++ core.

Model selection, download and compatibility checks will sit above the engine implementation so Lite/Standard/Advanced models can be swapped without changing the conversation UI.
