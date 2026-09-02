# Shared native AI core

Reserved for the future cross-platform C/C++ inference layer.

Goals:
- one inference/model-management core for Android and iOS;
- JNI bridge for Android;
- Swift/Objective-C++ bridge for iOS;
- no UI or Android-specific dependencies in this directory;
- support multiple local model backends behind a stable API.

No native runtime is linked in version 0.1.0.
