#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <memory>
#include <string>
#include <vector>

#include "chat.h"
#include "common.h"
#include "llama.h"

namespace {

using Clock = std::chrono::steady_clock;
constexpr int output_budget = 20;
constexpr int batch_size = 128;
constexpr char non_thinking_suffix[] = "<|im_start|>assistant\n<think>\n\n</think>\n\n";
constexpr char log_tag[] = "WD2Qwen";

struct Operation {
    std::atomic<bool> canceled{false};
    const Clock::time_point deadline;

    explicit Operation(jlong timeout_ms)
        : deadline(timeout_ms < 0 ? Clock::time_point::max()
                                 : Clock::now() + std::chrono::milliseconds(timeout_ms)) {}

    bool stop() const {
        return canceled.load(std::memory_order_relaxed) || Clock::now() >= deadline;
    }
};

struct Session {
    std::unique_ptr<llama_model, decltype(&llama_model_free)> model{nullptr, llama_model_free};
    std::unique_ptr<llama_context, decltype(&llama_free)> context{nullptr, llama_free};
    common_chat_templates_ptr templates;
};

struct Loader {
    JNIEnv * env;
    jobject progress;
    jmethodID report_method;
    const Operation & operation;

    bool report(float value) const {
        if (operation.stop()) {
            return false;
        }
        const bool keep_loading = env->CallBooleanMethod(progress, report_method, value);
        return keep_loading && !env->ExceptionCheck();
    }
};

std::string utf8(JNIEnv * env, jbyteArray data) {
    std::string value(env->GetArrayLength(data), '\0');
    env->GetByteArrayRegion(data, 0, static_cast<jsize>(value.size()),
                            reinterpret_cast<jbyte *>(value.data()));
    return value;
}

std::string render_prompt(common_chat_templates * templates, const std::string & system,
                          const std::string & user) {
    common_chat_templates_inputs inputs;
    inputs.use_jinja = true;
    inputs.enable_thinking = false;
    inputs.add_generation_prompt = true;
    common_chat_msg system_message;
    system_message.role = "system";
    system_message.content = system;
    common_chat_msg user_message;
    user_message.role = "user";
    user_message.content = user;
    inputs.messages = {std::move(system_message), std::move(user_message)};
    return common_chat_templates_apply(templates, inputs).prompt;
}

std::unique_ptr<Session> load(const std::string & path, int context_tokens, int threads,
                              Loader & loader) {
    auto session = std::make_unique<Session>();
    auto params = llama_model_default_params();
    params.n_gpu_layers = 0;
    params.progress_callback_user_data = &loader;
    params.progress_callback = [](float fraction, void * data) {
        return static_cast<Loader *>(data)->report(fraction);
    };
    session->model.reset(llama_model_load_from_file(path.c_str(), params));
    if (!session->model || !loader.report(-1)) {
        return nullptr;
    }
    char architecture[32]{};
    llama_model_meta_val_str(session->model.get(), "general.architecture", architecture, sizeof(architecture));
    if (std::string(architecture) != "qwen3" || !llama_model_chat_template(session->model.get(), nullptr)) {
        return nullptr;
    }
    session->templates = common_chat_templates_init(session->model.get(), "");
    auto probe = render_prompt(session->templates.get(), "System", "User");
    const std::string suffix = non_thinking_suffix;
    if (probe.size() < suffix.size() || probe.compare(probe.size() - suffix.size(), suffix.size(), suffix) != 0) {
        __android_log_write(ANDROID_LOG_ERROR, log_tag, "Incompatible Qwen non-thinking template");
        return nullptr;
    }

    auto context_params = llama_context_default_params();
    context_params.n_ctx = context_tokens;
    context_params.n_batch = batch_size;
    context_params.n_ubatch = batch_size;
    context_params.n_threads = threads;
    context_params.n_threads_batch = threads;
    context_params.no_perf = true;
    session->context.reset(llama_init_from_model(session->model.get(), context_params));
    if (!session->context || loader.operation.stop()) {
        return nullptr;
    }
    return session;
}

std::string generate(Session & session, const std::string & system, const std::string & user,
                     const std::string & grammar, Operation & operation) {
    auto * context = session.context.get();
    if (operation.stop()) {
        return "Decision canceled or timed out";
    }
    auto prompt = render_prompt(session.templates.get(), system, user);
    const auto * vocab = llama_model_get_vocab(session.model.get());
    auto tokens = common_tokenize(vocab, prompt, true, true);
    if (tokens.empty() || tokens.size() + output_budget > llama_n_ctx(context)) {
        return "Decision exceeds context budget (" + std::to_string(tokens.size()) + " prompt tokens)";
    }

    // Neither the KV cache nor the grammar/sampler state belongs to the next opponent.
    llama_memory_clear(llama_get_memory(context), false);
    auto sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler{
        llama_sampler_chain_init(sampler_params), llama_sampler_free
    };
    auto * constraint = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
    if (!constraint) {
        return "Decision grammar unavailable";
    }
    llama_sampler_chain_add(sampler.get(), constraint);
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_greedy());
    llama_set_abort_callback(context, [](void * data) {
        return static_cast<const Operation *>(data)->stop();
    }, &operation);
    struct ClearCallback {
        llama_context * context;
        ~ClearCallback() { llama_set_abort_callback(context, nullptr, nullptr); }
    } clear_callback{context};

    for (size_t offset = 0; offset < tokens.size(); offset += batch_size) {
        if (operation.stop()) {
            return "Decision canceled or timed out";
        }
        const auto count = std::min(static_cast<size_t>(batch_size), tokens.size() - offset);
        if (llama_decode(context, llama_batch_get_one(tokens.data() + offset, count)) != 0) {
            return operation.stop() ? "Decision canceled or timed out" : "Prompt evaluation failed";
        }
    }
    std::string output;
    for (int i = 0; i < output_budget; ++i) {
        if (operation.stop()) {
            return "Decision canceled or timed out";
        }
        auto token = llama_sampler_sample(sampler.get(), context, -1);
        if (llama_vocab_is_eog(vocab, token)) {
            __android_log_print(ANDROID_LOG_DEBUG, log_tag, "Evaluated %zu prompt tokens and %d output tokens",
                                tokens.size(), i);
            return "ok:" + output;
        }
        output += common_token_to_piece(vocab, token, false);
        if (llama_decode(context, llama_batch_get_one(&token, 1)) != 0) {
            return operation.stop() ? "Decision canceled or timed out" : "Decision generation failed";
        }
    }
    return "Decision exceeded output token budget";
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_worlddomination_platform_qwen_QwenNative_newOperation(JNIEnv *, jclass, jlong timeout_ms) {
    return reinterpret_cast<jlong>(new (std::nothrow) Operation(timeout_ms));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_worlddomination_platform_qwen_QwenNative_cancelOperation(JNIEnv *, jclass, jlong handle) {
    reinterpret_cast<Operation *>(handle)->canceled.store(true, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_worlddomination_platform_qwen_QwenNative_freeOperation(JNIEnv *, jclass, jlong handle) {
    delete reinterpret_cast<Operation *>(handle);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_worlddomination_platform_qwen_QwenNative_load(JNIEnv * env, jclass, jbyteArray path,
                                                              jint context_tokens, jint threads,
                                                              jlong operation_handle, jobject progress) {
    try {
        static const bool initialized = [] {
            llama_log_set([](ggml_log_level level, const char * message, void *) {
                if (level >= GGML_LOG_LEVEL_WARN && level <= GGML_LOG_LEVEL_ERROR) {
                    __android_log_write(level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR
                                                                     : ANDROID_LOG_WARN, log_tag, message);
                }
            }, nullptr);
            llama_backend_init();
            return true;
        }();
        (void) initialized;
        auto report = env->GetMethodID(env->GetObjectClass(progress), "report", "(F)Z");
        if (!report) {
            return 0;
        }
        Loader loader{env, progress, report, *reinterpret_cast<const Operation *>(operation_handle)};
        return reinterpret_cast<jlong>(load(utf8(env, path), context_tokens, threads, loader).release());
    } catch (const std::exception & ex) {
        __android_log_write(ANDROID_LOG_ERROR, log_tag, ex.what());
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_worlddomination_platform_qwen_QwenNative_select(JNIEnv * env, jclass, jlong session,
                                                                jlong operation, jbyteArray system,
                                                                jbyteArray user, jbyteArray grammar) {
    try {
        auto result = generate(*reinterpret_cast<Session *>(session), utf8(env, system), utf8(env, user),
                               utf8(env, grammar), *reinterpret_cast<Operation *>(operation));
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception & ex) {
        __android_log_write(ANDROID_LOG_ERROR, log_tag, ex.what());
        return env->NewStringUTF("Native decision failed");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_worlddomination_platform_qwen_QwenNative_release(JNIEnv *, jclass, jlong session) {
    delete reinterpret_cast<Session *>(session);
}
