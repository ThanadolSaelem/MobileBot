// Java binding: com.cfks.goosedroid.ai.local.LlamaBridge
// Robust implementation using llama.h (v0.2.0+)

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include <chrono>

#include "llama.h"

#define TAG "LlamaBridge"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model       * g_model   = nullptr;
static llama_context     * g_context = nullptr;
static std::mutex          g_mutex;
static std::atomic<bool>   g_abort{false};
static bool                g_backends_initialized = false;

static void jni_throw(JNIEnv * env, const char * msg) {
    jclass ex = env->FindClass("java/lang/IllegalStateException");
    if (ex) env->ThrowNew(ex, msg);
}

// UTF-8 validation helper
static bool is_valid_utf8(const char* str, size_t len) {
    for (size_t i = 0; i < len; ) {
        unsigned char c = str[i];
        if (c < 0x80) { i += 1; continue; }
        size_t remaining = len - i;
        if (remaining < 2) return false;
        if ((c & 0xE0) == 0xC0) {
            if ((str[i+1] & 0xC0) != 0x80) return false;
            i += 2;
        } else if ((c & 0xF0) == 0xE0) {
            if (remaining < 3) return false;
            if ((str[i+1] & 0xC0) != 0x80 || (str[i+2] & 0xC0) != 0x80) return false;
            i += 3;
        } else if ((c & 0xF8) == 0xF0) {
            if (remaining < 4) return false;
            if ((str[i+1] & 0xC0) != 0x80 || (str[i+2] & 0xC0) != 0x80 || (str[i+3] & 0xC0) != 0x80) return false;
            i += 4;
        } else {
            return false;
        }
    }
    return true;
}

// Safe NewStringUTF that replaces invalid UTF-8 with replacement character
static jstring safeNewStringUTF(JNIEnv* env, const std::string& s) {
    if (is_valid_utf8(s.c_str(), s.size())) {
        return env->NewStringUTF(s.c_str());
    }
    // Replace invalid sequences with replacement character
    std::string clean;
    clean.reserve(s.size());
    for (size_t i = 0; i < s.size(); ) {
        unsigned char c = s[i];
        if (c < 0x80) {
            clean.push_back(c);
            i += 1;
        } else {
            size_t remaining = s.size() - i;
            bool valid = false;
            size_t seqlen = 0;
            if ((c & 0xE0) == 0xC0 && remaining >= 2) {
                if ((s[i+1] & 0xC0) == 0x80) { valid = true; seqlen = 2; }
            } else if ((c & 0xF0) == 0xE0 && remaining >= 3) {
                if ((s[i+1] & 0xC0) == 0x80 && (s[i+2] & 0xC0) == 0x80) { valid = true; seqlen = 3; }
            } else if ((c & 0xF8) == 0xF0 && remaining >= 4) {
                if ((s[i+1] & 0xC0) == 0x80 && (s[i+2] & 0xC0) == 0x80 && (s[i+3] & 0xC0) == 0x80) { valid = true; seqlen = 4; }
            }
            if (valid) {
                clean.append(s.substr(i, seqlen));
                i += seqlen;
            } else {
                clean.append("\xEF\xBF\xBD"); // UTF-8 replacement character
                i += 1;
            }
        }
    }
    return env->NewStringUTF(clean.c_str());
}

// ---------------------------------------------------------------- init/load

extern "C" JNIEXPORT void JNICALL
Java_com_cfks_goosedroid_ai_local_LlamaBridge_nativeInit(JNIEnv *, jclass, jstring) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_backends_initialized) {
        llama_backend_init();
        g_backends_initialized = true;
        LOGi("llama.cpp backends initialized");
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_cfks_goosedroid_ai_local_LlamaBridge_loadModel(
        JNIEnv * env, jclass,
        jstring jmodel_path, jint n_ctx, jint n_threads) {

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model)   { llama_model_free(g_model); g_model = nullptr; }

    const auto * path = env->GetStringUTFChars(jmodel_path, nullptr);
    LOGi("Loading model: %s", path);

    llama_model_params mparams = llama_model_default_params();
    // Use ARM optimizations if available

    auto * model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jmodel_path, path);

    if (!model) {
        LOGe("Failed to load model from file");
        return JNI_FALSE;
    }
    g_model = model;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx            = (uint32_t) n_ctx;
    cparams.n_threads        = n_threads;
    cparams.n_threads_batch  = n_threads;
    cparams.n_batch          = 512;

    g_context = llama_init_from_model(model, cparams);
    if (!g_context) {
        LOGe("Failed to init context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGi("Model ready: ctx=%u, threads=%d", n_ctx, n_threads);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_cfks_goosedroid_ai_local_LlamaBridge_freeModel(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model)   { llama_model_free(g_model); g_model = nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_cfks_goosedroid_ai_local_LlamaBridge_stopCompletion(JNIEnv *, jclass) {
    g_abort = true;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_cfks_goosedroid_ai_local_LlamaBridge_systemInfo(JNIEnv * env, jclass) {
    return env->NewStringUTF(llama_version());
}

// ---------------------------------------------------------------- generate

extern "C" JNIEXPORT jstring JNICALL
Java_com_cfks_goosedroid_ai_local_LlamaBridge_nativeGenerateStream(
        JNIEnv * env, jclass,
        jstring jprompt, jint max_tokens, jfloat temp, jfloat top_p, jint top_k, jfloat repeat_penalty,
        jobject jcallback) {

    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_context) {
        jni_throw(env, "Model/Context not initialized");
        return nullptr;
    }

    jmethodID mid_onToken = nullptr;
    if (jcallback) {
        jclass cbClass = env->GetObjectClass(jcallback);
        mid_onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    }

    const auto * prompt_str = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(prompt_str);
    env->ReleaseStringUTFChars(jprompt, prompt_str);

    const auto * vocab = llama_model_get_vocab(g_model);

    // 1. Tokenize prompt
    std::vector<llama_token> tokens(prompt.size() + 8);
    int n_prompt = llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(),
                                   tokens.data(), (int32_t) tokens.size(), true, true);
    if (n_prompt < 0) {
        tokens.resize(-n_prompt);
        n_prompt = llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(),
                                   tokens.data(), (int32_t) tokens.size(), true, true);
    }
    if (n_prompt <= 0) return env->NewStringUTF("");
    tokens.resize(n_prompt);

    // 2. Reset KV cache for sequence 0
    llama_memory_seq_rm(llama_get_memory(g_context), 0, -1, -1);

    // 3. Decode prompt in one batch
    llama_batch batch = llama_batch_init(n_prompt, 0, 1);
    batch.n_tokens = n_prompt;
    for (int i = 0; i < n_prompt; i++) {
        batch.token[i] = tokens[i];
        batch.pos[i]   = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == n_prompt - 1); // Only logits for the last token
    }

    if (llama_decode(g_context, batch)) {
        LOGe("llama_decode prompt failed");
        llama_batch_free(batch);
        return env->NewStringUTF("Error: Initial decode failed");
    }
    llama_batch_free(batch);

    // 4. Initialize samplers
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sparams);

    // Chain samplers in recommended order
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // 5. Generation loop
    g_abort = false;
    std::string response;
    batch = llama_batch_init(1, 0, 1);

    // First token is sampled from prompt logits
    llama_token id = llama_sampler_sample(smpl, g_context, -1);

    auto t_start = std::chrono::high_resolution_clock::now();

    for (int i = 0; i < max_tokens; i++) {
        if (g_abort || llama_vocab_is_eog(vocab, id)) break;

        // Convert to piece and notify Java
        char piece[256];
        int n = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, true);
        if (n > 0) {
            std::string s(piece, (size_t) n);
            response += s;
            if (jcallback && mid_onToken) {
                jstring js = safeNewStringUTF(env, s);
                env->CallVoidMethod(jcallback, mid_onToken, js);
                env->DeleteLocalRef(js);
            }
        }

        // Stop sequences
        if (response.find("<|im_end|>") != std::string::npos) break;

        // Decode the sampled token
        batch.n_tokens = 1;
        batch.token[0]  = id;
        batch.pos[0]    = n_prompt + i;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;

        if (llama_decode(g_context, batch)) break;

        llama_sampler_accept(smpl, id);
        id = llama_sampler_sample(smpl, g_context, -1);
    }

    auto t_end = std::chrono::high_resolution_clock::now();
    double ms = std::chrono::duration<double, std::milli>(t_end - t_start).count();
    LOGi("Stream generated %d tokens in %.1f ms (%.2f t/s)", (int)response.size(), ms, (response.size() / (ms / 1000.0)));

    llama_sampler_free(smpl);
    llama_batch_free(batch);

    return safeNewStringUTF(env, response);
}
