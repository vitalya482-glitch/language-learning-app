#include "lvk/native_ai_engine.h"

#include "llama.h"

#include <algorithm>
#include <chrono>
#include <fstream>
#include <memory>
#include <stdexcept>
#include <string>
#include <thread>
#include <utility>
#include <vector>

namespace lvk::language_learning {
namespace {

constexpr int32_t kContextTokens = 2048;
constexpr int32_t kMaxGeneratedTokens = 128;
constexpr auto kMaxGenerationTime = std::chrono::seconds(25);

int32_t inference_threads() {
    const unsigned int available = std::thread::hardware_concurrency();
    if (available == 0) {
        return 4;
    }
    return static_cast<int32_t>(std::max(1u, std::min(4u, available)));
}

std::string trim_copy(std::string value) {
    const auto first = value.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) {
        return {};
    }
    const auto last = value.find_last_not_of(" \t\r\n");
    return value.substr(first, last - first + 1);
}

std::string remove_qwen_thinking(std::string value) {
    const std::string closing = "</think>";
    const auto close_pos = value.find(closing);
    if (close_pos != std::string::npos) {
        value = value.substr(close_pos + closing.size());
    }

    const std::string opening = "<think>";
    if (value.rfind(opening, 0) == 0) {
        value.erase(0, opening.size());
    }

    return trim_copy(std::move(value));
}

std::string format_chat_prompt(
    const llama_model* model,
    const std::string& system_prompt,
    const std::string& user_text
) {
    const std::string effective_system = system_prompt.empty()
        ? "You are a concise language tutor."
        : system_prompt;

    // Qwen3 supports /no_think in the user message. For a phone tutor we want the
    // final answer, not a long hidden-reasoning style preamble that wastes tokens.
    const std::string effective_user = user_text + "\n/no_think";

    const llama_chat_message messages[] = {
        {"system", effective_system.c_str()},
        {"user", effective_user.c_str()},
    };

    const char* chat_template = llama_model_chat_template(model, nullptr);
    std::vector<char> formatted(4096);
    int32_t length = llama_chat_apply_template(
        chat_template,
        messages,
        2,
        true,
        formatted.data(),
        static_cast<int32_t>(formatted.size())
    );

    if (length > static_cast<int32_t>(formatted.size())) {
        formatted.resize(static_cast<size_t>(length) + 1);
        length = llama_chat_apply_template(
            chat_template,
            messages,
            2,
            true,
            formatted.data(),
            static_cast<int32_t>(formatted.size())
        );
    }

    if (length < 0) {
        throw std::runtime_error("The GGUF chat template could not be applied");
    }

    return std::string(formatted.data(), static_cast<size_t>(length));
}

std::vector<llama_token> tokenize(
    const llama_vocab* vocab,
    const std::string& text,
    bool add_special
) {
    int32_t required = llama_tokenize(
        vocab,
        text.c_str(),
        static_cast<int32_t>(text.size()),
        nullptr,
        0,
        add_special,
        true
    );

    if (required == 0) {
        return {};
    }
    if (required < 0) {
        required = -required;
    }

    std::vector<llama_token> tokens(static_cast<size_t>(required));
    const int32_t written = llama_tokenize(
        vocab,
        text.c_str(),
        static_cast<int32_t>(text.size()),
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        add_special,
        true
    );
    if (written < 0) {
        throw std::runtime_error("Failed to tokenize the prompt");
    }
    tokens.resize(static_cast<size_t>(written));
    return tokens;
}

std::string token_piece(const llama_vocab* vocab, llama_token token) {
    std::vector<char> buffer(256);
    int32_t length = llama_token_to_piece(
        vocab,
        token,
        buffer.data(),
        static_cast<int32_t>(buffer.size()),
        0,
        true
    );

    if (length < 0) {
        buffer.resize(static_cast<size_t>(-length));
        length = llama_token_to_piece(
            vocab,
            token,
            buffer.data(),
            static_cast<int32_t>(buffer.size()),
            0,
            true
        );
    }
    if (length < 0) {
        throw std::runtime_error("Failed to decode a generated token");
    }

    return std::string(buffer.data(), static_cast<size_t>(length));
}

}  // namespace

NativeAiEngine::~NativeAiEngine() {
    unload();
}

void NativeAiEngine::load(ModelDescriptor model) {
    if (model.id.empty()) {
        throw std::invalid_argument("Model id must not be empty");
    }
    if (model.local_path.empty()) {
        throw std::invalid_argument("Model path must not be empty");
    }

    std::ifstream model_file(model.local_path, std::ios::binary);
    if (!model_file.good()) {
        throw std::invalid_argument("Local GGUF model file does not exist: " + model.local_path);
    }
    model_file.close();

    unload();

    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0; // CPU-first baseline; GPU acceleration comes later.

    llama_model* loaded = llama_model_load_from_file(model.local_path.c_str(), params);
    if (loaded == nullptr) {
        throw std::runtime_error("llama.cpp could not load the GGUF model");
    }

    model_ = std::move(model);
    model_handle_ = loaded;
}

std::string NativeAiEngine::generate(
    const std::string& system_prompt,
    const std::string& user_text
) const {
    if (model_handle_ == nullptr) {
        throw std::logic_error("A model must be loaded before generation");
    }
    if (trim_copy(user_text).empty()) {
        throw std::invalid_argument("User text must not be empty");
    }

    const llama_vocab* vocab = llama_model_get_vocab(model_handle_);
    if (vocab == nullptr) {
        throw std::runtime_error("The loaded model has no vocabulary");
    }

    const std::string prompt = format_chat_prompt(model_handle_, system_prompt, user_text);
    std::vector<llama_token> prompt_tokens = tokenize(vocab, prompt, true);
    if (prompt_tokens.empty()) {
        throw std::runtime_error("The model prompt produced no tokens");
    }
    if (prompt_tokens.size() + kMaxGeneratedTokens >= static_cast<size_t>(kContextTokens)) {
        throw std::invalid_argument("The prompt is too long for the mobile inference context");
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = kContextTokens;
    context_params.n_batch = kContextTokens;
    context_params.n_threads = inference_threads();
    context_params.n_threads_batch = inference_threads();
    context_params.no_perf = false;

    std::unique_ptr<llama_context, decltype(&llama_free)> context(
        llama_init_from_model(model_handle_, context_params),
        &llama_free
    );
    if (!context) {
        throw std::runtime_error("llama.cpp could not create an inference context");
    }

    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
        llama_sampler_chain_init(llama_sampler_chain_default_params()),
        &llama_sampler_free
    );
    if (!sampler) {
        throw std::runtime_error("llama.cpp could not create a sampler");
    }
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(0.6f));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    llama_batch batch = llama_batch_get_one(
        prompt_tokens.data(),
        static_cast<int32_t>(prompt_tokens.size())
    );

    std::string response;
    response.reserve(1024);
    llama_token generated_token = LLAMA_TOKEN_NULL;
    const auto started_at = std::chrono::steady_clock::now();
    bool timed_out = false;

    for (int32_t generated = 0; generated < kMaxGeneratedTokens; ++generated) {
        if (std::chrono::steady_clock::now() - started_at >= kMaxGenerationTime) {
            timed_out = true;
            break;
        }

        const int32_t decode_result = llama_decode(context.get(), batch);
        if (decode_result != 0) {
            throw std::runtime_error(
                "llama.cpp decode failed with code " + std::to_string(decode_result)
            );
        }

        generated_token = llama_sampler_sample(sampler.get(), context.get(), -1);
        if (llama_vocab_is_eog(vocab, generated_token)) {
            break;
        }

        response += token_piece(vocab, generated_token);
        batch = llama_batch_get_one(&generated_token, 1);
    }

    response = remove_qwen_thinking(std::move(response));
    if (response.empty()) {
        if (timed_out) {
            throw std::runtime_error("Local generation exceeded the 25 second mobile limit");
        }
        throw std::runtime_error("The local model generated an empty response");
    }

    return response;
}

void NativeAiEngine::unload() noexcept {
    if (model_handle_ != nullptr) {
        llama_model_free(model_handle_);
        model_handle_ = nullptr;
    }
    model_ = {};
}

bool NativeAiEngine::is_loaded() const noexcept {
    return model_handle_ != nullptr;
}

}  // namespace lvk::language_learning
