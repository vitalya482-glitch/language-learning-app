#pragma once

#include <string>

namespace lvk::language_learning {

struct ModelDescriptor {
    std::string id;
    std::string display_name;
    std::string local_path;
};

class NativeAiEngine {
public:
    void load(ModelDescriptor model);
    [[nodiscard]] std::string generate(
        const std::string& system_prompt,
        const std::string& user_text
    ) const;
    void unload() noexcept;

    [[nodiscard]] bool is_loaded() const noexcept;

private:
    bool loaded_ = false;
    ModelDescriptor model_;
};

}  // namespace lvk::language_learning
