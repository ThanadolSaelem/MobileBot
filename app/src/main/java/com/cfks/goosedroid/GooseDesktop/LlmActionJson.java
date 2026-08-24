package com.cfks.goosedroid.GooseDesktop;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parser สำหรับ action JSON ของ pet — ยืดหยุ่นตามพฤติกรรมจริงของ
 * Typhoon2.5-Qwen3-4B ที่วัดจาก fc_test.py:
 * - อาจมี <think>...</think> นำหน้า
 * - อาจคลุม markdown fence ```json ... ```
 * - อาจตอบหลาย action ติดกัน (ใช้ตัวแรก)
 */
public final class LlmActionJson {
    public String action;
    public String text;   // input_text
    public String reply;  // chat
    public String pkg;    // open_app ("package" key)
    public int x = -1, y = -1;
    public int x1 = -1, y1 = -1, x2 = -1, y2 = -1;

    private LlmActionJson() {}

    /** @return null ถ้าไม่มี JSON/ไม่มี field "action" */
    public static LlmActionJson parse(String raw) {
        if (raw == null) return null;
        String t = raw;

        // 1) ตัด <think> block (ใช้ lastIndexOf — เอาส่วนท้ายสุดหลัง think)
        int thinkEnd = t.lastIndexOf("</think>");
        if (thinkEnd >= 0) t = t.substring(thinkEnd + "</think>".length());

        // 2) ตัด markdown fence
        if (t.contains("```")) {
            for (String part : t.split("```")) {
                String p = part.trim();
                if (p.startsWith("json")) p = p.substring(4).trim();
                if (p.startsWith("{")) { t = p; break; }
            }
        }

        // 3) หา JSON object แรก
        int s = t.indexOf('{');
        int e = t.indexOf('}', s);
        if (s < 0 || e <= s) return null;

        try {
            JSONObject o = new JSONObject(t.substring(s, e + 1));
            String act = o.optString("action", "");
            if (act.isEmpty()) return null;

            LlmActionJson a = new LlmActionJson();
            a.action = act;
            a.text = o.has("text") ? o.optString("text") : null;
            a.reply = o.has("reply") ? o.optString("reply") : null;
            a.pkg = o.has("package") ? o.optString("package") : null;
            a.x = o.optInt("x", -1);
            a.y = o.optInt("y", -1);
            a.x1 = o.optInt("x1", -1);
            a.y1 = o.optInt("y1", -1);
            a.x2 = o.optInt("x2", -1);
            a.y2 = o.optInt("y2", -1);
            return a;
        } catch (JSONException ex) {
            return null;
        }
    }

    public boolean isChat() { return "chat".equals(action); }
}
