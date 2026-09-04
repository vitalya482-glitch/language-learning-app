#include "lvk/native_ai_engine.h"

#include <cassert>
#include <stdexcept>

int main() {
    using lvk::language_learning::ModelDescriptor;
    using lvk::language_learning::NativeAiEngine;

    NativeAiEngine engine;
    assert(!engine.is_loaded());

    bool rejected_missing_model = false;
    try {
        engine.load(ModelDescriptor{
            "missing-test-model",
            "Missing test model",
            "/definitely/not/a/model.gguf",
        });
    } catch (const std::invalid_argument&) {
        rejected_missing_model = true;
    }
    assert(rejected_missing_model);
    assert(!engine.is_loaded());

    bool rejected_generation = false;
    try {
        static_cast<void>(engine.generate("Be concise", "Hello"));
    } catch (const std::logic_error&) {
        rejected_generation = true;
    }
    assert(rejected_generation);

    return 0;
}
