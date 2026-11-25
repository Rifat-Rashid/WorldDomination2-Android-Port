package com.example.worlddomination.platform.qwen;

import androidx.annotation.Keep;

@Keep
final class QwenNative {
    static {
        System.loadLibrary("wd2_qwen");
    }

    @Keep
    interface Progress {
        boolean report(float fraction);
    }

    private QwenNative() {}

    static native long newOperation(long timeoutMillis);
    static native void cancelOperation(long operation);
    static native void freeOperation(long operation);
    static native long load(byte[] modelPath, int contextTokens, int threads, long operation,
                            Progress progress);
    static native String select(long session, long operation, byte[] system, byte[] user, byte[] grammar);
    static native void release(long session);
}
