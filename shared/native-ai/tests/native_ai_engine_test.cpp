#include "lvk/native_ai_engine.h"

#include <cassert>
#include <stdexcept>
#include <string>

int main() {
    using lvk::language_learning::ModelDescriptor;
    using lvk::language_learning::NativeAiEngine;

    NativeAiEngine engine;
    assert(!engine.is_loaded());

    engine.load(ModelDescriptor{
        "native-smoke-test",
        "Native smoke test",
        "native://smoke-test",
    });
    assert(engine.is_loaded());

    const std::string response = engine.generate("Be concise", "Hello");
    assert(response == "Native C++ engine is working. Received: Hello");

    engine.unload();
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
