#include <jni.h>
#include <string>

namespace {
bool g_model_loaded = false;
std::string g_loaded_model_path;
}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_alice_ai_data_offline_LlamaJniBridge_nativeLoadModel(
    JNIEnv* env,
    jobject /* thiz */,
    jstring model_path
) {
    if (model_path == nullptr) {
        g_model_loaded = false;
        g_loaded_model_path.clear();
        return JNI_FALSE;
    }
    const char* raw_path = env->GetStringUTFChars(model_path, nullptr);
    if (raw_path == nullptr || raw_path[0] == '\0') {
        if (raw_path != nullptr) {
            env->ReleaseStringUTFChars(model_path, raw_path);
        }
        g_model_loaded = false;
        g_loaded_model_path.clear();
        return JNI_FALSE;
    }

    g_loaded_model_path = raw_path;
    g_model_loaded = true;
    env->ReleaseStringUTFChars(model_path, raw_path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_alice_ai_data_offline_LlamaJniBridge_nativeUnloadModel(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    g_model_loaded = false;
    g_loaded_model_path.clear();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_alice_ai_data_offline_LlamaJniBridge_nativeGenerateText(
    JNIEnv* env,
    jobject /* thiz */,
    jstring prompt,
    jint /* max_tokens */,
    jfloat /* temperature */
) {
    if (!g_model_loaded) {
        return env->NewStringUTF("Offline model is not loaded.");
    }

    const char* raw_prompt = prompt == nullptr ? "" : env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_text = raw_prompt == nullptr ? "" : raw_prompt;
    if (raw_prompt != nullptr && prompt != nullptr) {
        env->ReleaseStringUTFChars(prompt, raw_prompt);
    }

    const std::string prefix = "Offline engine stub response. Prompt size: ";
    const std::string response = prefix + std::to_string(prompt_text.size()) + " chars.";
    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_alice_ai_data_offline_LlamaJniBridge_nativeIsModelLoaded(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    return g_model_loaded ? JNI_TRUE : JNI_FALSE;
}
