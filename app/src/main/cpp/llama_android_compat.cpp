#include <jni.h>
#include <android/log.h>
#include <sys/auxv.h>
#include <asm/hwcap.h>

#if defined(__ARM_NEON) || defined(__aarch64__)
#include <arm_neon.h>
#endif

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <mutex>
#include <string>
#include <type_traits>
#include <unordered_map>
#include <vector>
#include <unistd.h>

#include "llama.h"
#include "ggml-backend.h"

#ifndef HWCAP_FP
#define HWCAP_FP (1 << 0)
#endif
#ifndef HWCAP_ASIMD
#define HWCAP_ASIMD (1 << 1)
#endif
#ifndef HWCAP_FPHP
#define HWCAP_FPHP (1 << 9)
#endif
#ifndef HWCAP_ASIMDHP
#define HWCAP_ASIMDHP (1 << 10)
#endif
#ifndef HWCAP_ASIMDDP
#define HWCAP_ASIMDDP (1 << 20)
#endif
#ifndef HWCAP2_I8MM
#define HWCAP2_I8MM (1 << 13)
#endif

namespace {

static constexpr const char * TAG = "llama-android";

struct GenerationState {
    int32_t n_cur = 0;
    std::string utf8_cache;
    std::string recent_window;
    bool stop_sequence_reached = false;
    std::vector<llama_token> system_prefix_tokens;
};

static std::mutex g_state_mutex;
static std::unordered_map<llama_context *, GenerationState> g_states;
static std::unordered_map<llama_batch *, int32_t> g_batch_capacity;
static bool g_backend_initialized = false;
static bool g_has_gpu_backend = false;

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

// Carga aceleradores por hardware (Vulkan / OpenCL / Hexagon) si están disponibles
// y selecciona el backend de CPU SIMD más avanzado soportado por el procesador.
static void load_best_hardware_backends() {
    const char * hw_accelerators[] = {
        "libggml-vulkan.so",
        "libggml-opencl.so",
        "libggml-hexagon.so"
    };
    for (const char * accel_lib : hw_accelerators) {
        if (ggml_backend_load(accel_lib) != nullptr) {
            g_has_gpu_backend = true;
            __android_log_print(ANDROID_LOG_INFO, TAG, "Loaded hardware accelerator backend: %s", accel_lib);
        }
    }

    const unsigned long hwcap = getauxval(AT_HWCAP);
    const unsigned long hwcap2 = getauxval(AT_HWCAP2);

    const bool has_fp16 = (hwcap & HWCAP_FPHP) && (hwcap & HWCAP_ASIMDHP);
    const bool has_dotprod = (hwcap & HWCAP_ASIMDDP) != 0;
    const bool has_i8mm = (hwcap2 & HWCAP2_I8MM) != 0;

    std::vector<const char *> cpu_candidates;
    if (has_fp16 && has_dotprod && has_i8mm) {
        cpu_candidates.push_back("libggml-cpu-android_armv9.2_2.so");
        cpu_candidates.push_back("libggml-cpu-android_armv9.2_1.so");
        cpu_candidates.push_back("libggml-cpu-android_armv9.0_1.so");
        cpu_candidates.push_back("libggml-cpu-android_armv8.6_1.so");
    }
    if (has_fp16 && has_dotprod) {
        cpu_candidates.push_back("libggml-cpu-android_armv8.2_2.so");
    }
    if (has_fp16) {
        cpu_candidates.push_back("libggml-cpu-android_armv8.2_1.so");
    }
    cpu_candidates.push_back("libggml-cpu-android_armv8.0_1.so");

    for (const char * lib_name : cpu_candidates) {
        if (ggml_backend_load(lib_name) != nullptr) {
            __android_log_print(
                    ANDROID_LOG_INFO,
                    TAG,
                    "Loaded GGML CPU SIMD backend: %s (fp16=%d, dotprod=%d, i8mm=%d)",
                    lib_name,
                    has_fp16 ? 1 : 0,
                    has_dotprod ? 1 : 0,
                    has_i8mm ? 1 : 0);
            break;
        }
    }
}

// Detecta el número de núcleos de alto rendimiento (Big/Prime) leyendo sysfs
static int32_t detect_performance_cores() {
    const long cpu_count = sysconf(_SC_NPROCESSORS_ONLN);
    if (cpu_count <= 0) {
        return 4;
    }

    std::vector<long> max_freqs;
    max_freqs.reserve(static_cast<size_t>(cpu_count));
    long min_freq = -1;
    long max_freq = -1;

    for (long i = 0; i < cpu_count; ++i) {
        char path[128];
        std::snprintf(path, sizeof(path), "/sys/devices/system/cpu/cpu%ld/cpufreq/cpuinfo_max_freq", i);
        FILE * fp = std::fopen(path, "r");
        long freq = 0;
        if (fp) {
            if (std::fscanf(fp, "%ld", &freq) != 1) {
                freq = 0;
            }
            std::fclose(fp);
        }
        if (freq > 0) {
            max_freqs.push_back(freq);
            if (min_freq < 0 || freq < min_freq) min_freq = freq;
            if (max_freq < 0 || freq > max_freq) max_freq = freq;
        }
    }

    if (max_freqs.size() >= 4 && max_freq > min_freq * 115 / 100) {
        const long threshold = min_freq + (max_freq - min_freq) / 4;
        int32_t big_cores = 0;
        for (long f : max_freqs) {
            if (f >= threshold) {
                big_cores++;
            }
        }
        if (big_cores >= 2 && big_cores <= 6) {
            return std::min<int32_t>(4, big_cores);
        }
    }

    return (cpu_count >= 8) ? 4 : std::max<int32_t>(2, std::min<int32_t>(4, static_cast<int32_t>(cpu_count - 1)));
}

static int32_t recommended_decode_threads() {
    static const int32_t decode_threads = detect_performance_cores();
    return decode_threads;
}

static int32_t recommended_batch_threads() {
    const long cpu_count = sysconf(_SC_NPROCESSORS_ONLN);
    if (cpu_count <= 0) {
        return 4;
    }
    const int32_t perf_cores = recommended_decode_threads();
    return std::max<int32_t>(perf_cores, std::min<int32_t>(6, static_cast<int32_t>(cpu_count - 1)));
}

// Helpers SFINAE corregidos para Flash Attention
template <typename T, typename = void>
struct has_flash_attn_type : std::false_type {};
template <typename T>
struct has_flash_attn_type<T, std::void_t<decltype(std::declval<T>().flash_attn_type)>> : std::true_type {};

template <typename T, typename = void>
struct has_flash_attn_bool : std::false_type {};
template <typename T>
struct has_flash_attn_bool<T, std::void_t<decltype(std::declval<T>().flash_attn)>> : std::true_type {};

template <typename Params>
static void enable_flash_attention(Params & params) {
    if constexpr (has_flash_attn_type<Params>::value) {
        params.flash_attn_type = static_cast<decltype(params.flash_attn_type)>(1);
    } else if constexpr (has_flash_attn_bool<Params>::value) {
        params.flash_attn = true;
    }
}

template <typename T, typename = void>
struct has_use_mmap : std::false_type {};
template <typename T>
struct has_use_mmap<T, std::void_t<decltype(std::declval<T>().use_mmap)>> : std::true_type {};

template <typename T, typename = void>
struct has_load_mode : std::false_type {};
template <typename T>
struct has_load_mode<T, std::void_t<decltype(std::declval<T>().load_mode)>> : std::true_type {};

template <typename Params>
static void enable_mmap(Params & params) {
    if constexpr (has_load_mode<Params>::value) {
        params.load_mode = LLAMA_LOAD_MODE_MMAP;
    } else if constexpr (has_use_mmap<Params>::value) {
        params.use_mmap = true;
    }
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

static std::string token_to_piece(const llama_vocab * vocab, llama_token token) {
    char stack_buf[128];
    int32_t n = llama_token_to_piece(vocab, token, stack_buf, sizeof(stack_buf), 0, true);
    if (n >= 0) {
        return std::string(stack_buf, static_cast<size_t>(n));
    }

    std::vector<char> buf(static_cast<size_t>(-n) + 8);
    n = llama_token_to_piece(vocab, token, buf.data(), static_cast<int32_t>(buf.size()), 0, true);
    if (n <= 0) {
        return std::string();
    }

    return std::string(buf.data(), static_cast<size_t>(n));
}

static std::vector<llama_token> tokenize_prompt(const llama_vocab * vocab, const std::string & prompt, bool add_special) {
    std::vector<llama_token> tokens(prompt.size() / 2 + 64);

    int32_t n = llama_tokenize(
            vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            add_special,
            true);

    if (n < 0) {
        tokens.resize(static_cast<size_t>(-n));
        n = llama_tokenize(
                vocab,
                prompt.c_str(),
                static_cast<int32_t>(prompt.size()),
                tokens.data(),
                static_cast<int32_t>(tokens.size()),
                add_special,
                true);
    }

    if (n <= 0) {
        return {};
    }

    tokens.resize(static_cast<size_t>(n));
    return tokens;
}

static size_t extract_system_prefix_token_count(
        const llama_vocab * vocab,
        const std::string & prompt,
        const std::vector<llama_token> & full_tokens) {
    size_t user_pos = prompt.find("<|im_start|>user");
    if (user_pos == std::string::npos) {
        user_pos = prompt.find("<|start_header_id|>user");
    }
    if (user_pos == std::string::npos || user_pos == 0) {
        return 0;
    }

    const std::string sys_substr = prompt.substr(0, user_pos);
    std::vector<llama_token> sys_tokens = tokenize_prompt(vocab, sys_substr, true);
    if (sys_tokens.empty() || sys_tokens.size() >= full_tokens.size()) {
        return 0;
    }

    size_t match_len = 0;
    while (match_len < sys_tokens.size() && full_tokens[match_len] == sys_tokens[match_len]) {
        match_len++;
    }
    return match_len;
}

static bool has_closing_answer_tag(const std::string & window) {
    return window.find("</answer>") != std::string::npos ||
           window.find("</ANSWER>") != std::string::npos ||
           window.find("</respuesta>") != std::string::npos;
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_log_1to_1android(JNIEnv * /*env*/, jobject /*thiz*/) {
    llama_log_set(android_log_callback, nullptr);
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_backend_1init(JNIEnv * /*env*/, jobject /*thiz*/) {
    if (!g_backend_initialized) {
        load_best_hardware_backends();
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
    enable_mmap(params);
    params.n_gpu_layers = g_has_gpu_backend ? 99 : 0;

    llama_model * model = llama_model_load_from_file(c_filename, params);
    if (!model && params.n_gpu_layers > 0) {
        params.n_gpu_layers = 0;
        model = llama_model_load_from_file(c_filename, params);
    }
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
    params.n_ctx = 2048;
    params.n_batch = 512;
    params.n_ubatch = 256;
    params.n_seq_max = 1;
    params.n_threads = recommended_decode_threads();
    params.n_threads_batch = recommended_batch_threads();
    params.type_k = GGML_TYPE_Q8_0;
    params.type_v = GGML_TYPE_Q8_0;
    enable_flash_attention(params);

    __android_log_print(
            ANDROID_LOG_INFO,
            TAG,
            "Creating llama_context: n_ctx=%u, n_batch=%u, n_ubatch=%u, n_threads=%d, n_threads_batch=%d, kv=Q8_0",
            params.n_ctx,
            params.n_batch,
            params.n_ubatch,
            params.n_threads,
            params.n_threads_batch);

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
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(0.90f, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(0.10f));
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

    std::vector<llama_token> tokens = tokenize_prompt(vocab, prompt, true);
    if (tokens.empty()) {
        return 0;
    }

    int32_t capacity = 0;
    std::vector<llama_token> cached_sys_tokens;
    {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        auto it = g_batch_capacity.find(b);
        capacity = (it != g_batch_capacity.end()) ? it->second : 0;
        cached_sys_tokens = g_states[ctx].system_prefix_tokens;
    }

    if (capacity <= 0) {
        return 0;
    }

    size_t common_prefix = 0;
    while (common_prefix < cached_sys_tokens.size() &&
           common_prefix + 1 < tokens.size() &&
           tokens[common_prefix] == cached_sys_tokens[common_prefix]) {
        common_prefix++;
    }

    llama_memory_t mem = llama_get_memory(ctx);
    if (common_prefix > 0) {
        llama_memory_seq_rm(mem, 0, static_cast<llama_pos>(common_prefix), -1);
    } else {
        llama_memory_clear(mem, false);
    }

    const int32_t effective_step = std::min<int32_t>(capacity, 512);
    auto decode_from = [&](size_t start_idx) -> bool {
        int32_t pos = static_cast<int32_t>(start_idx);
        for (size_t i = start_idx; i < tokens.size(); i += static_cast<size_t>(effective_step)) {
            const int32_t n_chunk = std::min<int32_t>(effective_step, static_cast<int32_t>(tokens.size() - i));

            batch_clear(*b);
            for (int32_t j = 0; j < n_chunk; ++j) {
                const bool logits = (i + static_cast<size_t>(j) == tokens.size() - 1);
                if (!batch_add(*b, capacity, tokens[i + static_cast<size_t>(j)], pos + j, logits)) {
                    return false;
                }
            }

            if (llama_decode(ctx, *b) != 0) {
                return false;
            }

            pos += n_chunk;
        }
        return true;
    };

    if (!decode_from(common_prefix)) {
        llama_memory_clear(mem, false);
        common_prefix = 0;
        if (!decode_from(0)) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "llama_decode failed during prompt init");
            return 0;
        }
    }

    const size_t sys_len = extract_system_prefix_token_count(vocab, prompt, tokens);
    const int32_t n_cur = static_cast<int32_t>(tokens.size());

    {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        auto & state = g_states[ctx];
        state.n_cur = n_cur;
        state.utf8_cache.clear();
        state.recent_window.clear();
        state.stop_sequence_reached = false;
        if (sys_len > 0) {
            state.system_prefix_tokens.assign(tokens.begin(), tokens.begin() + static_cast<ptrdiff_t>(sys_len));
        } else {
            state.system_prefix_tokens.clear();
        }
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
        jobject /*ncur_obj*/) {
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

        if (state.stop_sequence_reached || state.n_cur >= n_len) {
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

            state.recent_window += out;
            if (state.recent_window.size() > 48) {
                state.recent_window.erase(0, state.recent_window.size() - 48);
            }
            if (has_closing_answer_tag(state.recent_window)) {
                state.stop_sequence_reached = true;
            }
        } else {
            out.clear();
        }
    }

    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_kv_1cache_1clear(JNIEnv * /*env*/, jobject /*thiz*/, jlong context) {
    auto * ctx = reinterpret_cast<llama_context *>(context);
    if (!ctx) {
        return;
    }

    std::lock_guard<std::mutex> lock(g_state_mutex);
    auto & state = g_states[ctx];
    llama_memory_t mem = llama_get_memory(ctx);

    if (!state.system_prefix_tokens.empty()) {
        llama_memory_seq_rm(mem, 0, static_cast<llama_pos>(state.system_prefix_tokens.size()), -1);
        state.n_cur = static_cast<int32_t>(state.system_prefix_tokens.size());
    } else {
        llama_memory_clear(mem, false);
        state.n_cur = 0;
    }

    state.utf8_cache.clear();
    state.recent_window.clear();
    state.stop_sequence_reached = false;
}

// Kernel SIMD ARM NEON para producto punto de la matriz de embeddings
extern "C" JNIEXPORT void JNICALL
Java_edu_unicauca_app_agrochat_rag_NpyFloatMatrix_nativeDotProductAll(
        JNIEnv * env,
        jclass /*clazz*/,
        jobject direct_buffer,
        jint data_offset_bytes,
        jint rows,
        jint cols,
        jfloatArray query_vec,
        jfloatArray out_scores) {
    if (!direct_buffer || !query_vec || !out_scores || rows <= 0 || cols <= 0) {
        return;
    }

    const auto * base_bytes = static_cast<const uint8_t *>(env->GetDirectBufferAddress(direct_buffer));
    if (!base_bytes) {
        return;
    }

    const auto * matrix = reinterpret_cast<const float *>(base_bytes + data_offset_bytes);
    jfloat * q = static_cast<jfloat *>(env->GetPrimitiveArrayCritical(query_vec, nullptr));
    jfloat * scores = static_cast<jfloat *>(env->GetPrimitiveArrayCritical(out_scores, nullptr));

    if (q && scores) {
        for (int32_t r = 0; r < rows; ++r) {
            const float * row_ptr = matrix + static_cast<size_t>(r) * static_cast<size_t>(cols);
            int32_t c = 0;
#if defined(__ARM_NEON) || defined(__aarch64__)
            float32x4_t sum_vec0 = vdupq_n_f32(0.0f);
            float32x4_t sum_vec1 = vdupq_n_f32(0.0f);
            for (; c + 7 < cols; c += 8) {
                float32x4_t m0 = vld1q_f32(row_ptr + c);
                float32x4_t q0 = vld1q_f32(q + c);
                sum_vec0 = vfmaq_f32(sum_vec0, m0, q0);

                float32x4_t m1 = vld1q_f32(row_ptr + c + 4);
                float32x4_t q1 = vld1q_f32(q + c + 4);
                sum_vec1 = vfmaq_f32(sum_vec1, m1, q1);
            }
            float32x4_t sum_vec = vaddq_f32(sum_vec0, sum_vec1);
#if defined(__aarch64__)
            float sum = vaddvq_f32(sum_vec);
#else
            float32x2_t sum_2 = vadd_f32(vget_low_f32(sum_vec), vget_high_f32(sum_vec));
            float sum = vget_lane_f32(vpadd_f32(sum_2, sum_2), 0);
#endif
#else
            float sum = 0.0f;
#endif
            for (; c < cols; ++c) {
                sum += row_ptr[c] * q[c];
            }
            scores[r] = sum;
        }
    }

    if (scores) env->ReleasePrimitiveArrayCritical(out_scores, scores, 0);
    if (q) env->ReleasePrimitiveArrayCritical(query_vec, q, JNI_ABORT);
}
