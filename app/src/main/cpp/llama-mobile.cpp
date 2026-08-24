// llama-mobile.cpp — JNI bridge สำหรับ MobileBot PetLlama
// โครงสร้าง port จาก llama.cpp v0.2.0 examples/llama.android/lib (ai_chat.cpp)
// ปรับเป็น single-shot generate: ทุก call เคลียร์ state ใหม่ (stateless ต่อ request)
//
// Java binding: com.cfks.goosedroid.GooseDesktop.PetLlama

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>

#include "llama.h"
#include "common.h"
#include "sampling.h"

#define TAG "PetLlama"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model       * g_model   = nullptr;
static llama_context     * g_context = nullptr;
static std::mutex          g_mutex;
static std::atomic<bool>   g_abort{false};

static void android_log_callback(ggml_log_level level, const char * text, void * /*user*/) {
    if (level == GGML_LOG_LEVEL_ERROR) LOGe("ggml: %s", text);
}

static void jni_throw(JNIEnv * env, const char * msg) {
    jclass ex = env->FindClass("java/lang/IllegalStateException");
    if (ex) env->ThrowNew(ex, msg);
}

// ---------------------------------------------------------------- init/load

extern "C" JNIEXPORT void JNICALL
Java_com_cfks_goosedroid_GooseDesktop_PetLlama_nativeInit(JNIEnv * env, jclass, jstring nativeLibDir) {
    std::lock_guard<std::mutex> lock(g_mutex);
    llama_log_set(android_log_callback, nullptr);

    const auto * dir = env->GetStringUTFChars(nativeLibDir, nullptr);
    ggml_backend_load_all_from_path(dir);   // load CPU backend variants shipped in apk
    env->ReleaseStringUTFChars(nativeLibDir, dir);

    llama_backend_init();
    LOGi("backends initialised");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_cfks_goosedroid_GooseDesktop_PetLlama_loadModel(
        JNIEnv * env, jclass,
        jstring jmodel_path, jint n_ctx, jint n_threads) {

    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_context) { /* fallthrough to fresh load */ }
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model)   { llama_model_free(g_model); g_model = nullptr; }

    const auto * path = env->GetStringUTFChars(jmodel_path, nullptr);
    LOGi("loading model: %s", path);
    llama_model_params mparams = llama_model_default_params();
    // use mmap — RAM-friendly on mobile (lazy paging) [v0.2.0 API]
    mparams.load_mode = LLAMA_LOAD_MODE_MMAP;
    auto * model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jmodel_path, path);
    if (!model) { LOGe("model load FAILED"); return JNI_FALSE; }
    g_model = model;

    int n_ctx_train = llama_model_n_ctx_train(model);
    if (n_ctx > n_ctx_train) {
        LOGi("clamping ctx %d -> trained %d", n_ctx, n_ctx_train);
        n_ctx = n_ctx_train;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx            = (uint32_t) n_ctx;
    cparams.n_batch          = 512;
    cparams.n_ubatch         = 512;
    cparams.n_threads        = n_threads;
    cparams.n_threads_batch  = n_threads;
    g_context = llama_init_from_model(model, cparams);
    if (!g_context) { LOGe("context init FAILED"); return JNI_FALSE; }

    LOGi("model ready (ctx=%d threads=%d)", n_ctx, n_threads);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_cfks_goosedroid_GooseDesktop_PetLlama_freeModel(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model)   { llama_model_free(g_model); g_model = nullptr; }
    LOGi("model freed");
}

extern "C" JNIEXPORT void JNICALL
Java_com_cfks_goosedroid_GooseDesktop_PetLlama_stopCompletion(JNIEnv *, jclass) {
    g_abort = true;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_cfks_goosedroid_GooseDesktop_PetLlama_systemInfo(JNIEnv * env, jclass) {
    return env->NewStringUTF(llama_print_system_info());
}

// ---------------------------------------------------------------- generate

// single-shot: tokenize full chatml prompt -> decode -> sample loop -> string
extern "C" JNIEXPORT jstring JNICALL
Java_com_cfks_goosedroid_GooseDesktop_PetLlama_nativeGenerate(
        JNIEnv * env, jclass,
        jstring jprompt, jint max_tokens, jfloat temp, jfloat repeat_penalty) {

    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_context) { jni_throw(env, "model not loaded"); return nullptr; }

    const auto * prompt = env->GetStringUTFChars(jprompt, nullptr);
    std::string formatted(prompt);
    env->ReleaseStringUTFChars(jprompt, prompt);

    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    // tokenize without BOS/add-special (prompt already fully formatted chatml)
    int n_prompt = -llama_tokenize(vocab, formatted.c_str(), (int32_t) formatted.size(),
                                   nullptr, 0, false, false);
    if (n_prompt < 0) { jni_throw(env, "tokenize failed"); return nullptr; }
    std::vector<llama_token> tokens(n_prompt);
    if (llama_tokenize(vocab, formatted.c_str(), (int32_t) formatted.size(),
                       tokens.data(), (int32_t) tokens.size(), false, false) < 0) {
        jni_throw(env, "tokenize failed");
        return nullptr;
    }

    const uint32_t n_ctx = llama_n_ctx(g_context);
    if ((uint32_t)(n_prompt + max_tokens + 8) > n_ctx) {
        jni_throw(env, ("context overflow: prompt=" + std::to_string(n_prompt)
                + " ctx=" + std::to_string(n_ctx)).c_str());
        return nullptr;
    }

    // sampler chain: penalties + temp/dist (ตาม settings ที่พิสูจน์แล้วจาก fc_test.py)
    common_params_sampling sparams;
    sparams.temp          = temp;
    sparams.penalty_repeat = repeat_penalty;
    sparams.penalty_last_n = 64;
    common_sampler * smpl = common_sampler_init(g_model, sparams);
    if (!smpl) { jni_throw(env, "sampler init failed"); return nullptr; }

    // decode prompt
    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t) tokens.size());
    g_abort = false;
    std::string out;
    out.reserve(max_tokens * 3);   // Thai ~3 bytes/char

    for (int i = 0; i < max_tokens; i++) {
        if (g_abort) break;

        int n_eval = (int) tokens.size() > 0 ? (int) tokens.size() : 1;
        if (llama_decode(g_context, batch)) {
            common_sampler_free(smpl);
            jni_throw(env, "decode failed");
            return nullptr;
        }
        tokens.clear();   // prompt decoded once; subsequent loops eval 1 token

        llama_token id = common_sampler_sample(smpl, g_context, -1);
        common_sampler_accept(smpl, id, true);

        if (llama_vocab_is_eog(vocab, id)) break;

        char piece[64];
        int n = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, true);
        if (n > 0) out.append(piece, (size_t) n);

        // stop at end-of-turn marker (chatml) — model sometimes emits raw
        if (out.find("<|im_end|>") != std::string::npos) {
            out.resize(out.find("<|im_end|>"));
            break;
        }

        batch = llama_batch_get_one(&id, 1);
    }

    common_sampler_free(smpl);

    // clear KV cache so next single-shot request starts clean
    llama_memory_clear(llama_get_memory(g_context), true);

    return env->NewStringUTF(out.c_str());
}
