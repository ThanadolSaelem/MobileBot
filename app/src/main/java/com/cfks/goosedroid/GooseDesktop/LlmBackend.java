package com.cfks.goosedroid.GooseDesktop;

/**
 * Backend สำหรับ generate ข้อความจาก prompt — มี 2 implementation:
 * - {@link HttpLlmBackend}: dev-only, ยิงไป llama-server บน PC (เร็ว, ตัดก่อน release)
 * - {@link PetLlama}: on-device GGUF ผ่าน JNI (production)
 *
 * ทุก method เป็น blocking — caller (PetBrain) ต้องเรียกจาก background thread เท่านั้น
 */
public interface LlmBackend {
    /**
     * @param formattedPrompt prompt ที่ format chatml แล้วจาก PromptBuilder
     * @return raw text ที่โมเดลสร้าง หรือ null ถ้าพัง
     */
    String generate(String formattedPrompt, int maxTokens, float temp, float repeatPenalty);

    /** คืน resource (close connection / free model memory) */
    void shutdown();
}
