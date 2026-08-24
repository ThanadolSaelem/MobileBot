package com.cfks.goosedroid.GooseDesktop;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * สมองจริงของ pet — drop-in replacement ของ GooseLLM (template ปลอม)
 * ทำงาน off-main-thread ตลอด, 1 request ต่อครั้ง (busy guard)
 *
 * Settings ที่พิสูจน์แล้วจาก fc_test.py: temp 0.2 + repeat_penalty 1.15
 */
public final class PetBrain {
    private static final String TAG = "PetBrain";
    public static final float TEMP = 0.2f;
    public static final float REP_PEN = 1.15f;
    public static final int MAX_TOKENS = 160;

    private static volatile LlmBackend backend;
    private static final ExecutorService exec = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile boolean busy = false;

    private PetBrain() {}

    /** ใส่ backend ก่อนใช้งาน (HttpLlmBackend หรือ PetLlama) */
    public static void configure(LlmBackend b) {
        LlmBackend old = backend;
        backend = b;
        if (old != null) { try { old.shutdown(); } catch (Exception ignored) {} }
    }

    public static boolean isReady() { return backend != null && !busy; }

    /**
     * signature เดิมของ GooseLLM.generateResponse — เรียกแทนได้ทันที
     * onReply ถูกเรียกบน main thread; คืน null ถ้าไม่พร้อม/พัง
     */
    public static void generateResponse(String eventOrUserText, Consumer<String> onReply) {
        LlmBackend b = backend;
        if (b == null || busy || onReply == null) { if (onReply != null) onReply.accept(null); return; }
        busy = true;
        final String prompt = PromptBuilder.chatml(PromptBuilder.fromEvent(eventOrUserText));
        exec.execute(() -> {
            String out = null;
            try {
                long t0 = System.currentTimeMillis();
                String raw = b.generate(prompt, MAX_TOKENS, TEMP, REP_PEN);
                Log.d(TAG, "generate " + (System.currentTimeMillis() - t0) + "ms");
                LlmActionJson a = LlmActionJson.parse(raw);
                if (a != null && a.isChat()) out = a.reply;
                else if (a != null) out = describeAction(a);   // bubble ว่ากำลังจะทำอะไร
            } catch (Exception ex) {
                Log.e(TAG, "generate failed", ex);
            }
            busy = false;
            final String f = out;
            if (f != null) main.post(() -> onReply.accept(f));
        });
    }

    /** คำสั่งผู้ใช้ (debug talk box / Phase 3) — callback บน main thread พร้อม action JSON เต็ม */
    public static void act(String userText, Consumer<LlmActionJson> cb) {
        LlmBackend b = backend;
        if (b == null || busy || cb == null) { if (cb != null) cb.accept(null); return; }
        busy = true;
        final String prompt = PromptBuilder.chatml(userText);
        exec.execute(() -> {
            LlmActionJson result = null;
            try {
                result = LlmActionJson.parse(b.generate(prompt, MAX_TOKENS, TEMP, REP_PEN));
            } catch (Exception ex) {
                Log.e(TAG, "act failed", ex);
            } finally {
                busy = false;
                final LlmActionJson a = result;
                main.post(() -> cb.accept(a));
            }
        });
    }

    private static String describeAction(LlmActionJson a) {
        switch (a.action) {
            case "open_app":   return "เดี๋ยวเปิดแอปให้นะ!";
            case "tap":        return "(เดินไปแตะจอ)";
            case "input_text": return "พิมพ์ให้อยู่นะ...";
            case "swipe":      return "(ปัดจอให้)";
            case "back":       return "(กดย้อนกลับ)";
            case "home":       return "(กลับหน้าแรก)";
            default:           return null;
        }
    }
}
