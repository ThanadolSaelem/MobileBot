package com.cfks.goosedroid.GooseDesktop;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * DEV ONLY — เชื่อมไปยัง llama-server บน PC (เช่น http://192.168.1.x:8080)
 * ใช้ endpoint /completion (raw prompt) เพื่อคุม chatml format เอง
 * ต้องถูกตัดออกก่อนปล่อย release (Phase 7 จะสลับเป็น PetLlama)
 */
public final class HttpLlmBackend implements LlmBackend {
    private final String baseUrl;
    private volatile HttpURLConnection lastConnection;

    public HttpLlmBackend(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public String generate(String prompt, int maxTokens, float temp, float repeatPenalty) {
        try {
            JSONObject body = new JSONObject()
                    .put("prompt", prompt)
                    .put("n_predict", maxTokens)
                    .put("temperature", temp)
                    .put("repeat_penalty", repeatPenalty)
                    .put("stop", new JSONArray().put("<|im_end|>"));

            HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + "/completion").openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setConnectTimeout(4000);
            c.setReadTimeout(60000);
            synchronized (this) { lastConnection = c; }

            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = c.getResponseCode();
            if (code != 200) { c.disconnect(); return null; }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            c.disconnect();
            return new JSONObject(sb.toString()).optString("content", null);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void shutdown() {
        try { HttpURLConnection c = lastConnection; if (c != null) c.disconnect(); } catch (Exception ignored) {}
    }
}
