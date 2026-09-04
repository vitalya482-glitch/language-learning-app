#pragma once

#include <string>

struct llama_model;
struct llama_context;

namespace lvk::language_learning {

struct ModelDescriptor {
    std::string id;
    std::string display_name;
    std::string local_path;
};

class NativeAiEngine {
public:
    NativeAiEngine() = default;
    ~NativeAiEngine();

    NativeAiEngine(const NativeAiEngine&) = delete;
    NativeAiEngine& operator=(const NativeAiEngine&) = delete;

    void load(ModelDescriptor model);
    [[nodiscard]] std::string generate(
        const std::string& system_prompt,
        const std::string& user_text
    ) const;
    void unload() noexcept;

    [[nodiscard]] bool is_loaded() const noexcept;

private:
    ModelDescriptor model_;
    llama_model* model_handle_ = nullptr;
    llama_context* context_handle_ = nullptr;
};

}  // namespace lvk::language_learning
