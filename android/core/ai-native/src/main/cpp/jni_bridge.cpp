#include <jni.h>

#include <exception>
#include <memory>
#include <stdexcept>
#include <string>

#include "lvk/native_ai_engine.h"

namespace {

using lvk::language_learning::ModelDescriptor;
using lvk::language_learning::NativeAiEngine;

NativeAiEngine* engine_from(jlong handle) {
    return reinterpret_cast<NativeAiEngine*>(handle);
}

void throw_java_exception(JNIEnv* env, const char* class_name, const std::string& message) {
    const jclass exception_class = env->FindClass(class_name);
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message.c_str());
    }
}

std::string to_string(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        throw std::invalid_argument("A required string argument was null");
    }

    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        throw std::runtime_error("Unable to read Java string");
    }

    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

template <typename Action>
void run_or_throw(JNIEnv* env, Action action) {
    try {
        action();
    } catch (const std::invalid_argument& error) {
        throw_java_exception(env, "java/lang/IllegalArgumentException", error.what());
    } catch (const std::exception& error) {
        throw_java_exception(env, "java/lang/IllegalStateException", error.what());
    }
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_kz_lvk_languagelearning_core_ai_nativeengine_NativeLanguageModelEngine_nativeCreate(
    JNIEnv* env,
    jobject
) {
    try {
        return reinterpret_cast<jlong>(new NativeAiEngine());
    } catch (const std::exception& error) {
        throw_java_exception(env, "java/lang/IllegalStateException", error.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_kz_lvk_languagelearning_core_ai_nativeengine_NativeLanguageModelEngine_nativeLoad(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring id,
    jstring display_name,
    jstring local_path
) {
    run_or_throw(env, [&] {
        NativeAiEngine* engine = engine_from(handle);
        if (engine == nullptr) {
            throw std::logic_error("Native engine is closed");
        }
        engine->load(ModelDescriptor{
            to_string(env, id),
            to_string(env, display_name),
            to_string(env, local_path),
        });
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_kz_lvk_languagelearning_core_ai_nativeengine_NativeLanguageModelEngine_nativeGenerate(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring system_prompt,
    jstring user_text
) {
    try {
        NativeAiEngine* engine = engine_from(handle);
        if (engine == nullptr) {
            throw std::logic_error("Native engine is closed");
        }
        const std::string response = engine->generate(
            to_string(env, system_prompt),
            to_string(env, user_text)
        );
        return env->NewStringUTF(response.c_str());
    } catch (const std::invalid_argument& error) {
        throw_java_exception(env, "java/lang/IllegalArgumentException", error.what());
    } catch (const std::exception& error) {
        throw_java_exception(env, "java/lang/IllegalStateException", error.what());
    }
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_kz_lvk_languagelearning_core_ai_nativeengine_NativeLanguageModelEngine_nativeUnload(
    JNIEnv* env,
    jobject,
    jlong handle
) {
    run_or_throw(env, [&] {
        NativeAiEngine* engine = engine_from(handle);
        if (engine == nullptr) {
            throw std::logic_error("Native engine is closed");
        }
        engine->unload();
    });
}

extern "C" JNIEXPORT void JNICALL
Java_kz_lvk_languagelearning_core_ai_nativeengine_NativeLanguageModelEngine_nativeDestroy(
    JNIEnv*,
    jobject,
    jlong handle
) {
    delete engine_from(handle);
}
