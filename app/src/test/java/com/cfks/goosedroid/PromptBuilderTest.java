package com.cfks.goosedroid;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.cfks.goosedroid.GooseDesktop.PromptBuilder;

public class PromptBuilderTest {

    @Test
    public void systemPromptHasVerifiedRules() {
        String s = PromptBuilder.SYSTEM;
        // กฎ 3 ข้อที่พิสูจน์แล้วจาก fc_test.py (5/5 PASS)
        assertTrue("ต้องมีกฎ action เดียว",
                s.contains("action เดียว"));
        assertTrue("ต้องมีกฎ input_text ตรง",
                s.contains("input_text ทันที"));
        assertTrue("ต้องมีวิธีอ่าน bounds",
                s.contains("bounds [x1,y1,x2,y2]"));
        // actions ครบ 7 แบบ
        for (String act : new String[]{"open_app", "tap", "input_text", "swipe", "back", "home", "chat"}) {
            assertTrue("ต้องมี action: " + act, s.contains("\"" + act + "\""));
        }
    }

    @Test
    public void chatmlWrapsCorrectly() {
        String p = PromptBuilder.chatml("เปิด YouTube");
        assertTrue(p.startsWith("<|im_start|>system\n"));
        assertTrue(p.contains(PromptBuilder.SYSTEM));
        assertTrue(p.contains("<|im_start|>user\nเปิด YouTube<|im_end|>"));
        // Qwen3 non-thinking mode = empty think block
        assertTrue(p.endsWith("<|im_start|>assistant\n<think>\n\n</think>\n\n"));
    }

    @Test
    public void fromEventAsksForShortCuteReply() {
        String e = PromptBuilder.fromEvent("ถูกลูบหัว");
        assertTrue(e.contains("ถูกลูบหัว"));
        assertTrue(e.contains("\"action\":\"chat\""));
        assertTrue(e.contains("2 ประโยค"));
    }
}
