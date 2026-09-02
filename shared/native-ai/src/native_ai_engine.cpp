#include "lvk/native_ai_engine.h"

#include <stdexcept>
#include <utility>

namespace lvk::language_learning {

void NativeAiEngine::load(ModelDescriptor model) {
    if (model.id.empty()) {
        throw std::invalid_argument("Model id must not be empty");
    }

    model_ = std::move(model);
    loaded_ = true;
}

std::string NativeAiEngine::generate(
    const std::string& system_prompt,
    const std::string& user_text
) const {
    if (!loaded_) {
        throw std::logic_error("A model must be loaded before generation");
    }
    if (user_text.empty()) {
        throw std::invalid_argument("User text must not be empty");
    }

    static_cast<void>(system_prompt);

    // Smoke implementation: proves that a request crosses the Kotlin/JNI/C++ boundary.
    // A real local inference backend will replace this response without changing the UI.
    return "Native C++ engine is working. Received: " + user_text;
}

void NativeAiEngine::unload() noexcept {
    model_ = {};
    loaded_ = false;
}

bool NativeAiEngine::is_loaded() const noexcept {
    return loaded_;
}

}  // namespace lvk::language_learning
