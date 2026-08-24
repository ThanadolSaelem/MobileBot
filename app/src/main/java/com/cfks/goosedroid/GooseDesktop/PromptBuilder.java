package com.cfks.goosedroid.GooseDesktop;

/**
 * System prompt ที่พิสูจน์แล้ว 5/5 PASS บน Typhoon2.5-Qwen3-4B (fc_test.py)
 * + Qwen3 chatml formatter (non-thinking mode ด้วย empty think block)
 *
 * ห้ามแก้กฎโดยไม่รัน fc_test.py ใหม่ — กฎพวกนี้คือสิ่งที่ทำให้ผ่าน
 */
public final class PromptBuilder {
    private PromptBuilder() {}

    public static final String SYSTEM =
        "คุณคือ \"น้องหมี\" ผู้ช่วย AI ที่อาศัยอยู่บนหน้าจอมือถือ Android ของผู้ใช้ น่ารักและเป็นมิตร\n"
      + "หน้าที่ของคุณคือช่วยผู้ใช้ควบคุมมือถือด้วยคำสั่งภาษาไทย\n\n"
      + "actions (ตอบ JSON บรรทัดเดียว ไม่มี markdown):\n"
      + "{\"action\":\"open_app\",\"package\":\"<pkg>\"} เปิดแอป\n"
      + "{\"action\":\"tap\",\"x\":<พิกัด>,\"y\":<พิกัด>} แตะจอ\n"
      + "{\"action\":\"input_text\",\"text\":\"..\"} พิมพ์ข้อความ\n"
      + "{\"action\":\"swipe\",\"x1\":<> ,\"y1\":<> ,\"x2\":<> ,\"y2\":<>} ปัดจอ\n"
      + "{\"action\":\"back\"} หรือ {\"action\":\"home\"}\n"
      + "{\"action\":\"chat\",\"reply\":\"..\"} ตอบบทสนทนาทั่วไป\n\n"
      + "กฎสำคัญ: ตอบ action เดียวต่อครั้ง ถ้างานหลายขั้นให้ทำทีละขั้น\n"
      + "ถ้าผู้ใช้สั่งพิมพ์ ตอบ input_text ทันที ไม่ต้อง tap หาช่อง (ระบบ focus ให้)\n"
      + "ถ้ามี UI elements ให้ใช้พิกัดกึ่งกลาง bounds [x1,y1,x2,y2]";

    /** Qwen3 chatml format + force non-thinking mode */
    public static String chatml(String userText) {
        return "<|im_start|>system\n" + SYSTEM + "<|im_end|>\n"
             + "<|im_start|>user\n" + userText + "<|im_end|>\n"
             + "<|im_start|>assistant\n<think>\n\n</think>\n\n";
    }

    /** event จากระบบ gesture/behavior -> ข้อความให้โมเดล generate bubble น่ารักๆ */
    public static String fromEvent(String eventDescription) {
        return "สิ่งที่เกิดขึ้นกับน้องหมี: " + eventDescription
             + "\nตอบด้วย {\"action\":\"chat\",\"reply\":\"...\"} สั้นๆ น่ารักๆ ไม่เกิน 2 ประโยค";
    }
}
