# Shared native AI core

Cross-platform C++ inference layer shared by Android and the future iOS client.

Goals:
- one inference/model-management core for Android and iOS;
- JNI bridge for Android;
- Swift/Objective-C++ bridge for iOS;
- no UI or Android-specific dependencies in this directory;
- support multiple local model backends behind a stable API.

## Current implementation

`NativeAiEngine` is the first buildable smoke implementation. It owns the model
lifecycle and returns a deterministic response so the complete
Compose -> Kotlin -> JNI -> C++ path can be verified before a real inference
runtime is introduced.

The Android-specific JNI bridge lives in `android/core/ai-native`; this directory
does not depend on Android headers or APIs.

## Host test

```bash
cmake -S shared/native-ai -B build/native-ai
cmake --build build/native-ai
ctest --test-dir build/native-ai --output-on-failure
```
