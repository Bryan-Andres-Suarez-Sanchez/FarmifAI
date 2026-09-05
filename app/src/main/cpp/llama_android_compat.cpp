#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstdint>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>
#include <unistd.h>

#include "llama.h"
#include "ggml-backend.h"

namespace {

static constexpr const char * TAG = "llama-android";

struct GenerationState {
    int32_t n_cur = 0;
    std::string utf8_cache;
};

static std::mutex g_state_mutex;
static std::unordered_map<llama_context *, GenerationState> g_states;
static std::unordered_map<llama_batch *, int32_t> g_batch_capacity;
static bool g_backend_initialized = false;

static void android_log_callback(ggml_log_level level, const char * text, void * /*user_data*/) {
    if (!text) {
        return;
    }

    int prio = ANDROID_LOG_INFO;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR:
            prio = ANDROID_LOG_ERROR;
            break;
        case GGML_LOG_LEVEL_WARN:
            prio = ANDROID_LOG_WARN;
            break;
        case GGML_LOG_LEVEL_INFO:
            prio = ANDROID_LOG_INFO;
            break;
        default:
            prio = ANDROID_LOG_DEBUG;
            break;
    }

    __android_log_print(prio, TAG, "%s", text);
}

static bool is_valid_utf8(const std::string & s) {
    const unsigned char * bytes = reinterpret_cast<const unsigned char *>(s.data());
    size_t i = 0;

    while (i < s.size()) {
        int n = 0;
        const unsigned char c = bytes[i];

        if ((c & 0x80) == 0x00) {
            n = 1;
        } else if ((c & 0xE0) == 0xC0) {
            n = 2;
        } else if ((c & 0xF0) == 0xE0) {
            n = 3;
        } else if ((c & 0xF8) == 0xF0) {
            n = 4;
        } else {
            return false;
        }

        if (i + n > s.size()) {
            return false;
        }

        for (int j = 1; j < n; ++j) {
            if ((bytes[i + j] & 0xC0) != 0x80) {
                return false;
            }
        }

        i += static_cast<size_t>(n);
    }

    return true;
}

static void batch_clear(llama_batch & batch) {
    batch.n_tokens = 0;
}

static bool batch_add(llama_batch & batch, int32_t capacity, llama_token token, llama_pos pos, bool logits) {
    if (batch.n_tokens >= capacity) {
        return false;
    }

    const int32_t i = batch.n_tokens++;
    batch.token[i] = token;
    batch.pos[i] = pos;
    batch.n_seq_id[i] = 1;
    batch.seq_id[i][0] = 0;
    batch.logits[i] = logits ? 1 : 0;
    return true;
}

static int32_t recommended_threads() {
    const long cpu_count = sysconf(_SC_NPROCESSORS_ONLN);
    if (cpu_count <= 0) {
        return 4;
    }

    const int32_t max_threads = 6;
    const int32_t min_threads = 2;
    return std::max(min_threads, std::min(max_threads, static_cast<int32_t>(cpu_count - 1)));
}

static std::string token_to_piece(const llama_vocab * vocab, llama_token token) {
    std::vector<char> buf(128);

    int32_t n = llama_token_to_piece(vocab, token, buf.data(), static_cast<int32_t>(buf.size()), 0, true);
    if (n < 0) {
        buf.resize(static_cast<size_t>(-n) + 8);
        n = llama_token_to_piece(vocab, token, buf.data(), static_cast<int32_t>(buf.size()), 0, true);
    }

    if (n <= 0) {
        return std::string();
    }

    return std::string(buf.data(), static_cast<size_t>(n));
}

static std::vector<llama_token> tokenize_prompt(const llama_vocab * vocab, const std::string & prompt) {
    std::vector<llama_token> tokens(1024);

    int32_t n = llama_tokenize(
            vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            true,
            true);

    if (n < 0) {
        tokens.resize(static_cast<size_t>(-n));
        n = llama_tokenize(
                vocab,
                prompt.c_str(),
                static_cast<int32_t>(prompt.size()),
                tokens.data(),
                static_cast<int32_t>(tokens.size()),
                true,
                true);
    }

    if (n <= 0) {
        return {};
    }

    tokens.resize(static_cast<size_t>(n));
    return tokens;
}

static void try_inc_ncur(JNIEnv * env, jobject ncur_obj) {
    if (!env || !ncur_obj) {
        return;
    }

    jclass cls = env->GetObjectClass(ncur_obj);
    if (!cls) {
        return;
    }

    jmethodID inc = env->GetMethodID(cls, "inc", "()V");
    if (inc) {
        env->CallVoidMethod(ncur_obj, inc);
    }

    env->DeleteLocalRef(cls);
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_log_1to_1android(JNIEnv * /*env*/, jobject /*thiz*/) {
    llama_log_set(android_log_callback, nullptr);
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_backend_1init(JNIEnv * /*env*/, jobject /*thiz*/) {
    if (!g_backend_initialized) {
        // Las versiones recientes de llama.cpp distribuyen los backends como
        // bibliotecas dinámicas separadas. Deben registrarse antes de cargar
        // el GGUF; llama_backend_init() por sí solo ya no lo hace.
        // En Android las .so permanecen dentro del APK, por lo que el escaneo
        // de directorios de load_all() no puede encontrarlas. El cargador
        // nativo sí resuelve la biblioteca empaquetada por su nombre SONAME.
        ggml_backend_load("libggml-cpu-android_armv8.0_1.so");
        ggml_backend_load_all();
        llama_backend_init();
        g_backend_initialized = true;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_backend_1free(JNIEnv * /*env*/, jobject /*thiz*/) {
    if (g_backend_initialized) {
        llama_backend_free();
        g_backend_initialized = false;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_load_1model(JNIEnv * env, jobject /*thiz*/, jstring filename) {
    if (!filename) {
        return 0;
    }

    const char * c_filename = env->GetStringUTFChars(filename, nullptr);
    if (!c_filename) {
        return 0;
    }

    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0;

    llama_model * model = llama_model_load_from_file(c_filename, params);
    env->ReleaseStringUTFChars(filename, c_filename);

    return reinterpret_cast<jlong>(model);
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_free_1model(JNIEnv * /*env*/, jobject /*thiz*/, jlong model) {
    auto * m = reinterpret_cast<llama_model *>(model);
    if (m) {
        llama_model_free(m);
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_new_1context(JNIEnv * /*env*/, jobject /*thiz*/, jlong model) {
    auto * m = reinterpret_cast<llama_model *>(model);
    if (!m) {
        return 0;
    }

    llama_context_params params = llama_context_default_params();
    params.n_ctx = 4096;
    params.n_batch = 1024;
    params.n_ubatch = 512;
    params.n_seq_max = 1;
    params.n_threads = recommended_threads();
    params.n_threads_batch = params.n_threads;

    llama_context * ctx = llama_init_from_model(m, params);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_free_1context(JNIEnv * /*env*/, jobject /*thiz*/, jlong context) {
    auto * ctx = reinterpret_cast<llama_context *>(context);
    if (!ctx) {
        return;
    }

    {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        g_states.erase(ctx);
    }

    llama_free(ctx);
}

extern "C" JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_new_1batch(JNIEnv * /*env*/, jobject /*thiz*/, jint n_tokens, jint embd, jint n_seq_max) {
    if (n_tokens <= 0) {
        return 0;
    }

    auto * batch = new llama_batch;
    *batch = llama_batch_init(n_tokens, embd, n_seq_max);

    {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        g_batch_capacity[batch] = n_tokens;
    }

    return reinterpret_cast<jlong>(batch);
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_free_1batch(JNIEnv * /*env*/, jobject /*thiz*/, jlong batch) {
    auto * b = reinterpret_cast<llama_batch *>(batch);
    if (!b) {
        return;
    }

    llama_batch_free(*b);

    {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        g_batch_capacity.erase(b);
    }

    delete b;
}

extern "C" JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_new_1sampler(JNIEnv * /*env*/, jobject /*thiz*/) {
    auto chain_params = llama_sampler_chain_default_params();
    chain_params.no_perf = true;

    llama_sampler * chain = llama_sampler_chain_init(chain_params);
    if (!chain) {
        return 0;
    }

    llama_sampler_chain_add(chain, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(1234));

    return reinterpret_cast<jlong>(chain);
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_free_1sampler(JNIEnv * /*env*/, jobject /*thiz*/, jlong sampler) {
    auto * s = reinterpret_cast<llama_sampler *>(sampler);
    if (s) {
        llama_sampler_free(s);
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_android_llama_cpp_LLamaAndroid_system_1info(JNIEnv * env, jobject /*thiz*/) {
    const char * info = llama_print_system_info();
    return env->NewStringUTF(info ? info : "unknown");
}

extern "C" JNIEXPORT jint JNICALL
Java_android_llama_cpp_LLamaAndroid_completion_1init(
        JNIEnv * env,
        jobject /*thiz*/,
        jlong context,
        jlong batch,
        jstring text,
        jboolean format_chat,
        jint /*n_len*/) {
    auto * ctx = reinterpret_cast<llama_context *>(context);
    auto * b = reinterpret_cast<llama_batch *>(batch);
    if (!ctx || !b || !text) {
        return 0;
    }

    const char * c_text = env->GetStringUTFChars(text, nullptr);
    if (!c_text) {
        return 0;
    }

    std::string prompt(c_text);
    env->ReleaseStringUTFChars(text, c_text);

    if (format_chat) {
        const llama_model * model = llama_get_model(ctx);
        const char * tmpl = model ? llama_model_chat_template(model, nullptr) : nullptr;
        if (tmpl && tmpl[0] != '\0') {
            llama_chat_message msg = {"user", prompt.c_str()};
            int32_t needed = llama_chat_apply_template(tmpl, &msg, 1, true, nullptr, 0);
            if (needed > 0) {
                std::string formatted(static_cast<size_t>(needed), '\0');
                const int32_t written = llama_chat_apply_template(
                        tmpl,
                        &msg,
                        1,
                        true,
                        formatted.data(),
                        static_cast<int32_t>(formatted.size()));
                if (written > 0) {
                    if (!formatted.empty() && formatted.back() == '\0') {
                        formatted.pop_back();
                    }
                    prompt = std::move(formatted);
                }
            }
        }
    }

    const llama_vocab * vocab = llama_model_get_vocab(llama_get_model(ctx));
    if (!vocab) {
        return 0;
    }

    std::vector<llama_token> tokens = tokenize_prompt(vocab, prompt);
    if (tokens.empty()) {
        return 0;
    }

    int32_t capacity = 0;
    {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        auto it = g_batch_capacity.find(b);
        capacity = (it != g_batch_capacity.end()) ? it->second : 0;
    }

    if (capacity <= 0) {
        return 0;
    }

    int32_t n_cur = 0;

    for (size_t i = 0; i < tokens.size(); i += static_cast<size_t>(capacity)) {
        const int32_t n_chunk = std::min<int32_t>(capacity, static_cast<int32_t>(tokens.size() - i));

        batch_clear(*b);
        for (int32_t j = 0; j < n_chunk; ++j) {
            const bool logits = (i + static_cast<size_t>(j) == tokens.size() - 1);
            if (!batch_add(*b, capacity, tokens[i + static_cast<size_t>(j)], n_cur + j, logits)) {
                return 0;
            }
        }

        if (llama_decode(ctx, *b) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "llama_decode failed during prompt init");
            return 0;
        }

        n_cur += n_chunk;
    }

    {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        auto & state = g_states[ctx];
        state.n_cur = n_cur;
        state.utf8_cache.clear();
    }

    return n_cur;
}

extern "C" JNIEXPORT jstring JNICALL
Java_android_llama_cpp_LLamaAndroid_completion_1loop(
        JNIEnv * env,
        jobject /*thiz*/,
        jlong context,
        jlong batch,
        jlong sampler,
        jint n_len,
        jobject ncur_obj) {
    auto * ctx = reinterpret_cast<llama_context *>(context);
    auto * b = reinterpret_cast<llama_batch *>(batch);
    auto * s = reinterpret_cast<llama_sampler *>(sampler);

    if (!ctx || !b || !s) {
        return nullptr;
    }

    int32_t capacity = 0;
    {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        auto it = g_batch_capacity.find(b);
        capacity = (it != g_batch_capacity.end()) ? it->second : 0;
    }

    if (capacity <= 0) {
        return nullptr;
    }

    llama_token token;
    std::string out;

    {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        auto it = g_states.find(ctx);
        if (it == g_states.end()) {
            return nullptr;
        }

        GenerationState & state = it->second;

        if (state.n_cur >= n_len) {
            return nullptr;
        }

        token = llama_sampler_sample(s, ctx, -1);
        llama_sampler_accept(s, token);

        const llama_vocab * vocab = llama_model_get_vocab(llama_get_model(ctx));
        if (!vocab || llama_vocab_is_eog(vocab, token)) {
            return nullptr;
        }

        batch_clear(*b);
        if (!batch_add(*b, capacity, token, state.n_cur, true)) {
            return nullptr;
        }

        if (llama_decode(ctx, *b) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "llama_decode failed during token loop");
            return nullptr;
        }

        state.n_cur += 1;

        const std::string piece = token_to_piece(vocab, token);
        state.utf8_cache += piece;

        if (is_valid_utf8(state.utf8_cache)) {
            out = state.utf8_cache;
            state.utf8_cache.clear();
        } else {
            out.clear();
        }
    }

    try_inc_ncur(env, ncur_obj);

    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_kv_1cache_1clear(JNIEnv * /*env*/, jobject /*thiz*/, jlong context) {
    auto * ctx = reinterpret_cast<llama_context *>(context);
    if (!ctx) {
        return;
    }

    llama_memory_clear(llama_get_memory(ctx), false);

    std::lock_guard<std::mutex> lock(g_state_mutex);
    auto & state = g_states[ctx];
    state.n_cur = 0;
    state.utf8_cache.clear();
}
