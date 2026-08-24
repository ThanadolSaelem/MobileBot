package com.cfks.goosedroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.cfks.goosedroid.GooseDesktop.LlmActionJson;

public class LlmActionJsonTest {

    @Test
    public void parsesCleanAction() {
        LlmActionJson a = LlmActionJson.parse("{\"action\":\"tap\",\"x\":975,\"y\":55}");
        assertEquals("tap", a.action);
        assertEquals(975, a.x);
        assertEquals(55, a.y);
    }

    @Test
    public void parsesOpenApp() {
        LlmActionJson a = LlmActionJson.parse(
                "{\"action\":\"open_app\",\"package\":\"com.google.android.youtube\"}");
        assertEquals("open_app", a.action);
        assertEquals("com.google.android.youtube", a.pkg);
    }

    @Test
    public void stripsThinkBlock() {
        LlmActionJson a = LlmActionJson.parse(
                "<think>ผมคิดว่า...</think>\n{\"action\":\"input_text\",\"text\":\"แม่จะกลับบ้านเก็บ\"}");
        assertEquals("input_text", a.action);
        assertTrue(a.text.contains("แม่จะกลับบ้าน"));
    }

    @Test
    public void handlesMarkdownFence() {
        LlmActionJson a = LlmActionJson.parse(
                "```json\n{\"action\":\"chat\",\"reply\":\"ฮั่นแน่\"}\n```");
        assertEquals("chat", a.action);
        assertEquals("ฮั่นแน่", a.reply);
    }

    @Test
    public void takesFirstOfMultipleActions() {
        // พฤติกรรมจริงจาก fc_test.py รอบแรก: โมเดลตอบหลาย action ติดกัน
        LlmActionJson a = LlmActionJson.parse(
                "{\"action\":\"tap\",\"x\":400,\"y\":1350}{\"action\":\"input_text\",\"text\":\"x\"}");
        assertEquals("tap", a.action);
        assertEquals(400, a.x);
    }

    @Test
    public void nullWhenNoJson() {
        assertNull(LlmActionJson.parse("ไม่มี json เลยจ้า"));
        assertNull(LlmActionJson.parse(null));
        assertNull(LlmActionJson.parse("{\"no_action\":1}"));
    }
}
