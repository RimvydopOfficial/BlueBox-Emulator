#include <jni.h>
#include <dlfcn.h>
#include <string>
#include <android/log.h>

#define TAG "BlueBoxQEMU"

extern "C" JNIEXPORT jstring JNICALL
Java_com_rimvydop_bluebox_BlueBoxQemuBridge_nativeCheckEngine(
        JNIEnv* env, jobject /* thiz */) {
    void* handle = dlopen("libqemu-system-x86_64.so", RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        const char* error = dlerror();
        std::string message = "QEMU engine load FAILED\n";
        message += error ? error : "Unknown dynamic linker error";
        __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", message.c_str());
        return env->NewStringUTF(message.c_str());
    }

    dlerror();
    void* run = dlsym(handle, "redbox_qemu_run");
    const char* symbolError = dlerror();
    std::string message;
    if (symbolError || !run) {
        message = "QEMU library loaded, but redbox_qemu_run is missing\n";
        message += symbolError ? symbolError : "Symbol not found";
    } else {
        message = "QEMU engine loaded successfully!\nredbox_qemu_run: FOUND\nReady for the next integration step.";
    }
    __android_log_print(ANDROID_LOG_INFO, TAG, "%s", message.c_str());
    dlclose(handle);
    return env->NewStringUTF(message.c_str());
}
