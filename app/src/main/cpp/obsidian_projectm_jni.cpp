#include <jni.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <string.h>
#include <signal.h>
#include <setjmp.h>
#include <unwind.h>
#include <dlfcn.h>
#include <pthread.h>
#include <string>
#include <sstream>
#include <iomanip>
#include <mutex>

#include "projectM-4/projectM.h"

#define LOG_TAG "ObsidianProjectM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static projectm_handle pm_instance = nullptr;
static std::mutex g_pm_mutex;
static bool last_preset_switch_failed = false;

// Global JVM references for Crashlytics logging
static JavaVM* g_jvm = nullptr;
static jclass g_crash_logger_class = nullptr;
static jmethodID g_log_native_crash_method = nullptr;

// Previous OS signal actions
static struct sigaction g_old_sigsegv;
static struct sigaction g_old_sigbus;
static struct sigaction g_old_sigfpe;
static struct sigaction g_old_sigill;
static struct sigaction g_old_sigabrt;

// Thread-local jump buffers for safe native recovery
static thread_local sigjmp_buf g_native_jump_buf;
static thread_local bool g_has_jump_point = false;
static thread_local bool g_alt_stack_installed = false;
static thread_local char g_alt_stack[SIGSTKSZ * 4];

// Stack Unwinding & Backtrace Capture
struct BacktraceState {
    void** current;
    void** end;
};

static _Unwind_Reason_Code unwindCallback(struct _Unwind_Context* context, void* arg) {
    BacktraceState* state = static_cast<BacktraceState*>(arg);
    uintptr_t pc = _Unwind_GetIP(context);
    if (pc) {
        if (state->current == state->end) {
            return _URC_END_OF_STACK;
        } else {
            *state->current++ = reinterpret_cast<void*>(pc);
        }
    }
    return _URC_NO_REASON;
}

static size_t captureBacktrace(void** buffer, size_t max) {
    BacktraceState state = {buffer, buffer + max};
    _Unwind_Backtrace(unwindCallback, &state);
    return state.current - buffer;
}

static std::string formatBacktrace(void** buffer, size_t count) {
    std::ostringstream os;
    for (size_t idx = 0; idx < count; ++idx) {
        const void* addr = buffer[idx];
        const char* symbol = "";
        const char* fname = "";
        void* offset = nullptr;

        Dl_info info;
        if (dladdr(addr, &info) && info.dli_sname) {
            symbol = info.dli_sname;
            fname = info.dli_fname;
            offset = reinterpret_cast<void*>((uintptr_t)addr - (uintptr_t)info.dli_fbase);
        }

        os << "  #" << std::setw(2) << std::setfill('0') << idx << ": "
           << addr << " " << (fname ? fname : "unknown") << " ("
           << (symbol ? symbol : "unknown") << "+" << offset << ")\n";
    }
    return os.str();
}

static void ensure_alt_stack() {
    if (!g_alt_stack_installed) {
        stack_t ss;
        memset(&ss, 0, sizeof(ss));
        ss.ss_sp = g_alt_stack;
        ss.ss_size = sizeof(g_alt_stack);
        ss.ss_flags = 0;
        if (sigaltstack(&ss, nullptr) == 0) {
            g_alt_stack_installed = true;
        }
    }
}

// Custom Native Signal Handler: Intercepts SIGSEGV, SIGBUS, SIGFPE, SIGILL, SIGABRT
static void native_signal_handler(int sig, siginfo_t* info, void* context) {
    const char* sigName = "UNKNOWN_SIGNAL";
    switch (sig) {
        case SIGSEGV: sigName = "SIGSEGV (Segmentation Fault)"; break;
        case SIGBUS:  sigName = "SIGBUS (Bus Error)"; break;
        case SIGFPE:  sigName = "SIGFPE (Floating Point Exception)"; break;
        case SIGILL:  sigName = "SIGILL (Illegal Instruction)"; break;
        case SIGABRT: sigName = "SIGABRT (Native Abort)"; break;
    }

    void* fault_addr = info ? info->si_addr : nullptr;
    void* buffer[32];
    size_t count = captureBacktrace(buffer, 32);
    std::string bt = formatBacktrace(buffer, count);

    LOGE("================================================================================");
    LOGE("CRITICAL NATIVE SIGNAL INTERCEPTED: %s (code=%d, addr=%p)",
         sigName, info ? info->si_code : 0, fault_addr);
    LOGE("Native Stack Backtrace:\n%s", bt.c_str());
    LOGE("================================================================================");

    // Send native crash report directly to Firebase Crashlytics via JNI
    if (g_jvm && g_crash_logger_class && g_log_native_crash_method) {
        JNIEnv* env = nullptr;
        bool attached = false;
        if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
            if (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                attached = true;
            }
        }
        if (env) {
            jstring jSigName = env->NewStringUTF(sigName);
            jint jCode = info ? info->si_code : 0;
            char addrBuf[32];
            snprintf(addrBuf, sizeof(addrBuf), "%p", fault_addr);
            jstring jAddr = env->NewStringUTF(addrBuf);
            jstring jBt = env->NewStringUTF(bt.c_str());

            env->CallStaticVoidMethod(g_crash_logger_class, g_log_native_crash_method, jSigName, jCode, jAddr, jBt);

            env->DeleteLocalRef(jSigName);
            env->DeleteLocalRef(jAddr);
            env->DeleteLocalRef(jBt);

            if (attached) {
                g_jvm->DetachCurrentThread();
            }
        }
    }

    if (g_has_jump_point) {
        LOGW("Obsidian signal handler successfully jumping out of native fault to protect JVM!");
        siglongjmp(g_native_jump_buf, 1);
    } else {
        // Fallback to chained signal handler if not in a guarded section
        struct sigaction* old_act = nullptr;
        switch (sig) {
            case SIGSEGV: old_act = &g_old_sigsegv; break;
            case SIGBUS:  old_act = &g_old_sigbus; break;
            case SIGFPE:  old_act = &g_old_sigfpe; break;
            case SIGILL:  old_act = &g_old_sigill; break;
            case SIGABRT: old_act = &g_old_sigabrt; break;
        }
        if (old_act && old_act->sa_sigaction) {
            old_act->sa_sigaction(sig, info, context);
        } else if (old_act && old_act->sa_handler && old_act->sa_handler != SIG_DFL && old_act->sa_handler != SIG_IGN) {
            old_act->sa_handler(sig);
        } else {
            signal(sig, SIG_DFL);
            raise(sig);
        }
    }
}

static void on_preset_switch_failed(const char* preset_filename, const char* message, void* user_data) {
    last_preset_switch_failed = true;
    LOGE("ProjectM preset switch failed for '%s': %s", preset_filename ? preset_filename : "(in-memory data)", message ? message : "(unknown)");
}

extern "C"
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOGE("JNI_OnLoad: GetEnv failed");
        return JNI_ERR;
    }

    jclass localClass = env->FindClass("com/anuj/obsidian/ObsidianCrashLogger");
    if (localClass) {
        g_crash_logger_class = reinterpret_cast<jclass>(env->NewGlobalRef(localClass));
        g_log_native_crash_method = env->GetStaticMethodID(
            g_crash_logger_class,
            "logNativeCrash",
            "(Ljava/lang/String;ILjava/lang/String;Ljava/lang/String;)V"
        );
        LOGI("Found ObsidianCrashLogger.logNativeCrash method in JNI");
    } else {
        LOGW("ObsidianCrashLogger class not found during JNI_OnLoad");
    }

    // Install POSIX Signal Interceptors for all critical signals
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = native_signal_handler;
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
    sigemptyset(&sa.sa_mask);

    sigaction(SIGSEGV, &sa, &g_old_sigsegv);
    sigaction(SIGBUS,  &sa, &g_old_sigbus);
    sigaction(SIGFPE,  &sa, &g_old_sigfpe);
    sigaction(SIGILL,  &sa, &g_old_sigill);
    sigaction(SIGABRT, &sa, &g_old_sigabrt);

    LOGI("Obsidian Native Signal Handlers (SIGSEGV/SIGBUS/SIGFPE/SIGILL/SIGABRT) armed successfully.");
    return JNI_VERSION_1_6;
}

#define GUARD_NATIVE_CALL(CODE, ON_FAULT) \
    ensure_alt_stack(); \
    if (sigsetjmp(g_native_jump_buf, 1) == 0) { \
        g_has_jump_point = true; \
        CODE; \
        g_has_jump_point = false; \
    } else { \
        g_has_jump_point = false; \
        LOGE("Native crash intercepted and recovered safely via GUARD_NATIVE_CALL!"); \
        ON_FAULT; \
    }

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_anuj_obsidian_ui_visualizer_ProjectMNative_nativeInit(JNIEnv *env, jobject thiz, jint width, jint height, jstring presetPath) {
    std::lock_guard<std::mutex> lock(g_pm_mutex);
    jboolean result = JNI_FALSE;

    GUARD_NATIVE_CALL({
        if (pm_instance) {
            projectm_destroy(pm_instance);
            pm_instance = nullptr;
        }

        pm_instance = projectm_create();
        if (!pm_instance) {
            LOGE("projectm_create failed");
            result = JNI_FALSE;
        } else {
            int w = width > 0 ? width : 1;
            int h = height > 0 ? height : 1;
            projectm_set_window_size(pm_instance, (size_t)w, (size_t)h);
            glViewport(0, 0, w, h);
            projectm_set_preset_locked(pm_instance, true);
            projectm_set_preset_switch_failed_event_callback(pm_instance, on_preset_switch_failed, nullptr);

            if (presetPath != nullptr) {
                const char *c_preset = env->GetStringUTFChars(presetPath, 0);
                if (c_preset && strlen(c_preset) > 0) {
                    LOGI("nativeInit loading preset: %s", c_preset);
                    last_preset_switch_failed = false;
                    projectm_load_preset_file(pm_instance, c_preset, false);
                }
                env->ReleaseStringUTFChars(presetPath, c_preset);
            }

            LOGI("projectM initialized successfully (%dx%d).", w, h);
            result = JNI_TRUE;
        }
    }, {
        result = JNI_FALSE;
    })

    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_anuj_obsidian_ui_visualizer_ProjectMNative_nativeResize(JNIEnv *env, jobject thiz, jint width, jint height) {
    std::lock_guard<std::mutex> lock(g_pm_mutex);
    GUARD_NATIVE_CALL({
        if (pm_instance && width > 0 && height > 0) {
            projectm_set_window_size(pm_instance, (size_t)width, (size_t)height);
            glViewport(0, 0, width, height);
        }
    }, {})
}

extern "C"
JNIEXPORT void JNICALL
Java_com_anuj_obsidian_ui_visualizer_ProjectMNative_nativeRender(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_pm_mutex);
    GUARD_NATIVE_CALL({
        if (pm_instance) {
            projectm_opengl_render_frame(pm_instance);
        } else {
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
        }
    }, {
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
    })
}

extern "C"
JNIEXPORT void JNICALL
Java_com_anuj_obsidian_ui_visualizer_ProjectMNative_nativeAddPcmF32(JNIEnv *env, jobject thiz, jfloatArray samples, jint channels) {
    std::lock_guard<std::mutex> lock(g_pm_mutex);
    if (!pm_instance || !samples || channels <= 0) return;

    jsize len = env->GetArrayLength(samples);
    if (len <= 0) return;

    GUARD_NATIVE_CALL({
        jfloat *c_samples = env->GetFloatArrayElements(samples, 0);
        if (c_samples) {
            projectm_pcm_add_float(pm_instance, c_samples, len / channels, channels == 1 ? PROJECTM_MONO : PROJECTM_STEREO);
            env->ReleaseFloatArrayElements(samples, c_samples, JNI_ABORT);
        }
    }, {})
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_anuj_obsidian_ui_visualizer_ProjectMNative_nativeLoadPreset(JNIEnv *env, jobject thiz, jstring path) {
    std::lock_guard<std::mutex> lock(g_pm_mutex);
    if (!pm_instance || !path) {
        LOGE("nativeLoadPreset: pm_instance or path is null");
        return JNI_FALSE;
    }

    jboolean result = JNI_FALSE;
    const char *c_path = env->GetStringUTFChars(path, 0);
    if (!c_path) return JNI_FALSE;

    GUARD_NATIVE_CALL({
        LOGI("nativeLoadPreset loading file: %s", c_path);
        last_preset_switch_failed = false;
        projectm_set_preset_locked(pm_instance, true);
        projectm_load_preset_file(pm_instance, c_path, false);
        result = last_preset_switch_failed ? JNI_FALSE : JNI_TRUE;
    }, {
        result = JNI_FALSE;
    })

    env->ReleaseStringUTFChars(path, c_path);
    return result;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_anuj_obsidian_ui_visualizer_ProjectMNative_nativeLoadPresetData(JNIEnv *env, jobject thiz, jstring data) {
    std::lock_guard<std::mutex> lock(g_pm_mutex);
    if (!pm_instance || !data) {
        LOGE("nativeLoadPresetData: pm_instance or data is null");
        return JNI_FALSE;
    }

    jboolean result = JNI_FALSE;
    const char *c_data = env->GetStringUTFChars(data, 0);
    if (!c_data) return JNI_FALSE;

    GUARD_NATIVE_CALL({
        LOGI("nativeLoadPresetData loading %zu bytes of MilkDrop data", strlen(c_data));
        last_preset_switch_failed = false;
        projectm_set_preset_locked(pm_instance, true);
        projectm_load_preset_data(pm_instance, c_data, false);
        result = last_preset_switch_failed ? JNI_FALSE : JNI_TRUE;
    }, {
        result = JNI_FALSE;
    })

    env->ReleaseStringUTFChars(data, c_data);
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_anuj_obsidian_ui_visualizer_ProjectMNative_nativeSetBeatSensitivity(JNIEnv *env, jobject thiz, jfloat value) {
    std::lock_guard<std::mutex> lock(g_pm_mutex);
    GUARD_NATIVE_CALL({
        if (pm_instance) {
            projectm_set_beat_sensitivity(pm_instance, value);
        }
    }, {})
}

extern "C"
JNIEXPORT void JNICALL
Java_com_anuj_obsidian_ui_visualizer_ProjectMNative_nativeDestroy(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_pm_mutex);
    GUARD_NATIVE_CALL({
        if (pm_instance) {
            projectm_destroy(pm_instance);
            pm_instance = nullptr;
        }
    }, {
        pm_instance = nullptr;
    })
}
