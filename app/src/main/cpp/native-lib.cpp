#include <jni.h>
#include <fstream>
#include <sstream>
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

    std::ifstream model_file(raw_path, std::ios::binary);
    if (!model_file.good()) {
        env->ReleaseStringUTFChars(model_path, raw_path);
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

    std::string tail = prompt_text;
    if (tail.size() > 512) {
        tail = tail.substr(tail.size() - 512);
    }

    const size_t separator_index = g_loaded_model_path.find_last_of("/\\");
    const std::string model_label =
        separator_index == std::string::npos
            ? g_loaded_model_path
            : g_loaded_model_path.substr(separator_index + 1);

    std::ostringstream response_stream;
    response_stream << "Offline native response (" << model_label << "): ";
    response_stream << tail;
    const std::string response = response_stream.str();
    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_alice_ai_data_offline_LlamaJniBridge_nativeIsModelLoaded(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    return g_model_loaded ? JNI_TRUE : JNI_FALSE;
}
