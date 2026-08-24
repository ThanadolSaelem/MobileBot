package com.cfks.goosedroid.GooseDesktop;

import android.app.Application;
import android.content.Context;
import android.util.Log;

/**
 * Thin JNI wrapper รอบ libllama-mobile.so (llama.cpp v0.2.0)
 *
 * ใช้เป็น LlmBackend แบบ on-device: blocking calls — เรียกจาก PetBrain executor เท่านั้น
 * โหลดโมเดลครั้งเดียวต่อ process (mmap) แล้ว generate ได้ซ้ำๆ (stateless ต่อ request)
 */
public final class PetLlama implements LlmBackend {
    private static final String TAG = "PetLlama";
    private static boolean libraryLoaded = false;

    private native void nativeInit(String nativeLibDir);
    public  native boolean loadModel(String modelPath, int nCtx, int nThreads);
    public  native void freeModel();
    private native String nativeGenerate(String prompt, int maxTokens, float temp, float repeatPenalty);
    public  native void stopCompletion();
    public  native String systemInfo();

    /** โหลด .so ครั้งเดียว + init backends (เรียกก่อน loadModel) */
    public static synchronized boolean loadLibrary(Context context) {
        if (libraryLoaded) return true;
        try {
            System.loadLibrary("llama-mobile");
            new PetLlama().nativeInit(context.getApplicationInfo().nativeLibraryDir);
            libraryLoaded = true;
            Log.i(TAG, "llama-mobile loaded, dir=" + context.getApplicationInfo().nativeLibraryDir);
            return true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "load library failed", e);
            return false;
        }
    }

    /** helper: เลือจำนวน thread เหมาะสม (เผื่อ headroom 2 core ให้ UI/render) */
    public static int suggestedThreads() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(2, Math.min(4, cores - 2));
    }

    // ---- LlmBackend ----

    @Override
    public String generate(String prompt, int maxTokens, float temp, float repeatPenalty) {
        return nativeGenerate(prompt, maxTokens, temp, repeatPenalty);
    }

    @Override
    public void shutdown() {
        try { freeModel(); } catch (Throwable t) { Log.w(TAG, "freeModel", t); }
    }

    /** static convenience: load model + log result */
    public boolean tryLoad(String path, int ctx) {
        int threads = suggestedThreads();
        boolean ok = loadModel(path, ctx, threads);
        Log.i(TAG, "tryLoad ok=" + ok + " threads=" + threads + " ctx=" + ctx);
        return ok;
    }
}
