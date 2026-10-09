package com.rimvydop.bluebox

object BlueBoxQemuBridge {
    init {
        System.loadLibrary("bluebox_qemu_bridge")
    }

    private external fun nativeCheckEngine(): String

    fun checkEngine(): String = try {
        nativeCheckEngine()
    } catch (e: Throwable) {
        "QEMU engine check FAILED: ${e.message ?: e.javaClass.simpleName}"
    }
}