# Phase 1: เชื่อม LLM (Typhoon GGUF) เข้า GooseDroid — Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** แอป Android (base = GooseDroid) ที่ห่าน/pet คิด-พูดได้จริงจาก Typhoon2.5-Qwen3-4B GGUF รัน on-device ผ่าน llama.cpp JNI ฝั่ง Java โดยตรง (ไม่ใช่ RN)

**Architecture:** Copy GooseDroid มาเป็นฐาน → แทนจุดเชื่อม `GooseLLM` (template ปลอม) ด้วย `PetBrain` ที่มี 2 backend: (A) `HttpBackend` ชี้มาที่ llama-server บน PC สำหรับ dev-loop เร็ว (temp, ตัดก่อน release) และ (B) `JniBackend` = llama.cpp compile ผ่าน NDK + JNI wrapper Java (`PetLlama`) — เป้าหมายสุดท้ายตามที่ user สั่ง ระบบ gesture/behavior tree/bubble เดิมของ GooseDroid ใช้ต่อทั้งหมด แค่เปลี่ยน "สมอง"

**Tech Stack:** Java 11 · minSdk 24 · AGP 8.12 · llama.cpp (vendored, JNI) · NDK+CMake · Typhoon2.5-Qwen3-4B Q4_K_M (2.5GB, พิสูจน์แล้ว 5/5 FC PASS)

---

## Current Context / Assumptions

- Repo: `C:/Users/Mynew/MobileBot` (private, branch main, push สำเร็จแล้ว — commit 7551c95)
- Reference clone: `C:/Users/Mynew/GooseDroid` (all-rights-reserved ⚠️ ใช้แก้/ทดลองส่วนตัวได้ตาม README เขา ห้ามแจกจ่าย/ขายจนกว่า Phase 2 จะ rewrite เป็นโค้ดตัวเอง)
- Reference clone: `C:/Users/Mynew/pocketpal-ai`, `C:/Users/Mynew/mobile-use`
- โมเดล: `C:/Users/Mynew/models/typhoon2.5-qwen3-4b-q4_k_m.gguf` (2497276128 bytes, ตรงเป๊ะกับ HF)
- ผลทดสอบบน PC (llama-server + fc_test.py): 5/5 PASS, 13.6 tok/s CPU, settings ชนะ = temp 0.2 + repeat_penalty 1.15 + กฎ "1 action/turn", "พิมพ์→input_text เลย"
- เครื่อง dev: Windows, RAM 42GB (ว่าง ~30GB), มี winget, มี git, gh auth แล้ว — **ยังไม่ยืนยัน JDK17/Android SDK/NDK**

## ⚠️ License Risk (อ่านก่อน)

Phase 1 ทั้งหมดเป็นการทดลองส่วนตัว (sideload เครื่องตัวเอง/เพื่อนสนิทเพื่อทดสอบ) — **ยังไม่โพสต์แจก APK จนกว่า Phase 2** (rewrite character/rendering เป็นโค้ดของเราเอง) ส่วนโค้ดที่เราเขียนใหม่ทั้งหมด (PetLlama/PetBrain/prompt/tests) เป็นของเรา 100%

---

## Proposed Approach (สั้น)

1. Import GooseDroid snapshot เข้า MobileBot (commit เดียวจบ ไม่ merge history)
2. ตั้ง build environment ให้ `assembleDebug` ผ่านก่อนแตะอะไร
3. เขียน `PetBrain` + `HttpBackend` ก่อน → validate end-to-end กับ llama-server บน PC ภายในวันเดียว (bubble ภาษาไทยจริง)
4. Vendor llama.cpp + เขียน JNI wrapper (`PetLlama.java` + `cpp/llama-mobile.cpp`) → สลับเป็น `JniBackend`
5. TDD ส่วน logic บริสุทธิ์ (JSON extractor, prompt builder) ด้วย JUnit ธรรมดา — ไม่ต้อง emulator
6. Manual verification บนมือถือจริง + เก็บ metric RAM/latency

---

## Step-by-step Plan

### Task 1: Import GooseDroid snapshot

**Objective:** มีโค้ดฐานครบใน MobileBot repo พร้อม commit mark จุดเริ่ม

**Files:**
- Create: `app/**` (จาก `C:/Users/Mynew/GooseDroid/app`)
- Create: `settings.gradle`, `build.gradle`, `gradle.properties`, `gradlew*`, `gradle/` (จาก GooseDroid root)

**Step 1:** Copy โดยเว้น .git/.androidide:

```bash
cd /c/Users/Mynew
rsync -a --exclude '.git' --exclude '.androidide' GooseDroid/ MobileBot/
```

(ถ้าไม่มี rsync: `cp -r` แล้วลบ `MobileBot/.git` ซ้ำ — ระวังอย่าทับ .git ของ MobileBot)

**Step 2:** ตรวจว่า .git ของ MobileBot ยังอยู่: `ls MobileBot/.git/HEAD` → ต้องมีไฟล์

**Step 3:** Commit:

```bash
cd MobileBot && git add -A && git commit -m "chore: import GooseDroid reference snapshot as Phase 1 base"
```

---

### Task 2: Build environment ให้ baseline ผ่าน

**Objective:** `gradlew assembleDebug` สร้าง APK ได้ ก่อนแก้โค้ดแม้แต่บรรทัดเดียว

**Files:** ไม่แก้โค้ด — แค่ env (local.properties ไม่ commit อยู่แล้วใน .gitignore)

**Step 1:** เช็คของที่มี:

```bash
java -version 2>&1 | head -1        # ต้องเป็น 17+ (AGP 8.x)
echo "$ANDROID_HOME"; ls "$LOCALAPPDATA/Android/Sdk" 2>/dev/null | head
```

**Step 2:** ถ้าไม่ครบ ติดตั้งผ่าน winget (รัน background, อย่ารัน foreground):

```bash
"$LOCALAPPDATA/Microsoft/WindowsApps/winget.exe" install EclipseAdoptium.TemperJDK17 # ถ้าไม่มี JDK
# Android SDK: ถาม user ก่อนว่ามี Android Studio ไหม — ถ้ามีใช้ SDK ของมันเลย (เร็วกว่า)
```

**Step 3:** สร้าง `local.properties`: `sdk.dir=C\:\\Users\\Mynew\\AppData\\Local\\Android\\Sdk`

**Step 4:** Build (background, timeout ยาว — ครั้งแรกโหลด gradle เยอะ):

```bash
cd MobileBot && ./gradlew assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL` + มี `app/build/outputs/apk/debug/app-debug.apk`
ถ้าพังเพราะ Aliyun mirror (network ไทย): แก้ `settings.gradle` ใช้ google()/mavenCentral() พี่งพอ

**Step 5:** Commit ถ้ามีแก้ settings: `git commit -am "fix: use standard maven repos"`

---

### Task 3: PetBrain skeleton + JSON extractor (TDD)

**Objective:** มี "สมอง" เป็น interface สะอาด + parser JSON ที่พิสูจน์แล้วจาก fc_test.py ย้ายมาเป็น Java + unit test ผ่าน

**Files:**
- Create: `app/src/main/java/com/cfks/goosedroid/GooseDesktop/PetBrain.java`
- Create: `app/src/main/java/com/cfks/goosedroid/GooseDesktop/LlmActionJson.java`
- Test: `app/src/test/java/com/cfks/goosedroid/LlmActionJsonTest.java`
- Test: `app/src/test/java/com/cfks/goosedroid/PromptBuilderTest.java`
- Create: `app/src/main/java/com/cfks/goosedroid/GooseDesktop/PromptBuilder.java`

**Step 1: เขียน failing test ก่อน** (`LlmActionJsonTest.java`):

```java
package com.cfks.goosedroid;

import static org.junit.Assert.*;
import org.junit.Test;
import com.cfks.goosedroid.GooseDesktop.LlmActionJson;

public class LlmActionJsonTest {
    @Test public void parsesCleanAction() {
        LlmActionJson a = LlmActionJson.parse("{\"action\":\"tap\",\"x\":975,\"y\":55}");
        assertEquals("tap", a.action); assertEquals(975, a.x); assertEquals(55, a.y);
    }
    @Test public void stripsThinkBlock() {
        LlmActionJson a = LlmActionJson.parse(
            "<think>hm</think>\n{\"action\":\"input_text\",\"text\":\"แม่จะกลับบ้านเก็บ\"}");
        assertEquals("input_text", a.action);
        assertTrue(a.text.contains("แม่จะกลับบ้าน"));
    }
    @Test public void handlesMarkdownFence() {
        LlmActionJson a = LlmActionJson.parse("```json\n{\"action\":\"chat\",\"reply\":\"ฮั่นแน่\"}\n```");
        assertEquals("chat", a.action);
    }
    @Test public void nullWhenNoJson() {
        assertNull(LlmActionJson.parse("ไม่มี json เลยจ้า"));
    }
}
```

**Step 2:** Run: `./gradlew :app:testDebugUnitTest --tests "*LlmActionJson*"` → Expected: FAIL (class ยังไม่มี)

**Step 3:** Implement `LlmActionJson.java` (org.json มีใน Android อยู่แล้ว, JUnit ฝั่ง JVM ใช้ json 20180813 เพิ่มใน `testImplementation`):

```java
package com.cfks.goosedroid.GooseDesktop;

import org.json.JSONException;
import org.json.JSONObject;

/** Parser สำหรับ action JSON ของ pet — ยืดหยุ่นตามผลจริงจาก fc_test.py */
public final class LlmActionJson {
    public String action; public String text; public String reply; public String pkg;
    public int x = -1, y = -1, x1=-1,y1=-1,x2=-1,y2=-1;

    public static LlmActionJson parse(String raw) {
        if (raw == null) return null;
        String t = raw;
        int thinkEnd = t.lastIndexOf("</think>");
        if (thinkEnd >= 0) t = t.substring(thinkEnd + 8);
        if (t.contains("```")) {
            for (String p : t.split("```")) {
                p = p.replaceFirst("^json", "").trim();
                if (p.startsWith("{")) { t = p; break; }
            }
        }
        int s = t.indexOf('{'), e = t.lastIndexOf('}');
        if (s < 0 || e <= s) return null;
        try {
            JSONObject o = new JSONObject(t.substring(s, e + 1));
            LlmActionJson a = new LlmActionJson();
            a.action = o.optString("action", "");
            a.text = o.optString("text", null);
            a.reply = o.optString("reply", null);
            a.pkg = o.optString("package", null);
            a.x = o.optInt("x", -1); a.y = o.optInt("y", -1);
            a.x1=o.optInt("x1",-1); a.y1=o.optInt("y1",-1);
            a.x2=o.optInt("x2",-1); a.y2=o.optInt("y2",-1);
            return a.action.isEmpty() ? null : a;
        } catch (JSONException ex) { return null; }
    }
}
```

(`app/build.gradle` เพิ่ม: `testImplementation 'org.json:json:20180813'`)

**Step 4:** Run test อีกครั้ง → PASS 4 tests

**Step 5:** PromptBuilder (TDD เหมือนกัน — test เช็คว่ามีกฎ 3 ข้อ + chatml marker):

```java
package com.cfks.goosedroid.GooseDesktop;

/** System prompt ที่พิสูจน์แล้ว 5/5 บน Typhoon2.5-Qwen3-4B + Qwen3 chatml formatter */
public final class PromptBuilder {
    public static final String SYSTEM =
        "คุณคือ \"น้องหมี\" ผู้ช่วย AI ที่อาศัยอยู่บนหน้าจอมือถือ Android ของผู้ใช้ น่ารักและเป็นมิตร\n" +
        "หน้าที่ของคุณคือช่วยผู้ใช้ควบคุมมือถือด้วยคำสั่งภาษาไทย\n\n" +
        "actions (ตอบ JSON บรรทัดเดียว ไม่มี markdown):\n" +
        "{\"action\":\"open_app\",\"package\":\"<pkg>\"} เปิดแอป\n" +
        "{\"action\":\"tap\",\"x\":<>,\"y\":<>} แตะจอ\n" +
        "{\"action\":\"input_text\",\"text\":\"..\"} พิมพ์\n" +
        "{\"action\":\"swipe\",\"x1\":..,\"y1\":..,\"x2\":..,\"y2\":..} ปัด\n" +
        "{\"action\":\"back\"} หรือ {\"action\":\"home\"}\n" +
        "{\"action\":\"chat\",\"reply\":\"..\"} ตอบแชททั่วไป\n\n" +
        "กฎสำคัญ: ตอบ action เดียวต่อครั้ง ถ้างานหลายขั้นให้ทำทีละขั้น\n" +
        "ถ้าผู้ใช้สั่งพิมพ์ ตอบ input_text ทันที ไม่ต้อง tap หาช่อง (ระบบ focus ให้)\n" +
        "ถ้ามี UI elements ให้ใช้พิกัดกึ่งกลาง bounds [x1,y1,x2,y2]";

    /** Qwen3 chatml + empty-think (non-thinking mode) */
    public static String chatml(String userText) {
        return "<|im_start|>system\n" + SYSTEM + "<|im_end|>\n"
             + "<|im_start|>user\n" + userText + "<|im_end|>\n"
             + "<|im_start|>assistant\n<think>\n\n</think>\n\n";
    }

    /** event จากระบบ gesture -> ข้อความ user ที่โมเดลเข้าใจ */
    public static String fromEvent(String eventDescription) {
        return "สิ่งที่เกิดขึ้นกับน้องหมี: " + eventDescription
             + "\nตอบด้วย {\"action\":\"chat\",\"reply\":\"...\"} สั้นๆ น่ารักๆ ไม่เกิน 2 ประโยค";
    }
}
```

**Step 6:** `./gradlew :app:testDebugUnitTest` → PASS ทั้งหมด

**Step 7:** `git add -A && git commit -m "feat: PetBrain core - JSON parser + verified Thai system prompt (TDD)"`

---

### Task 4: Backend interface + HttpBackend (dev-loop)

**Objective:** PetBrain ใช้งานได้จริงทันทีผ่าน llama-server บน PC (OpenAI-compatible endpoint เดียวกับ fc_test.py) — de-risk ส่วน integration ก่อนลงมือ JNI

**Files:**
- Create: `app/src/main/java/com/cfks/goosedroid/GooseDesktop/LlmBackend.java` (interface)
- Create: `.../HttpLlmBackend.java`
- Modify: `.../PetBrain.java` (wire backend + executor thread)

**Step 1:** Interface:

```java
package com.cfks.goosedroid.GooseDesktop;

public interface LlmBackend {
    /** blocking generate — caller ต้องเรียกจาก background thread */
    String generate(String formattedPrompt, int maxTokens, float temp, float repeatPenalty);
    void shutdown();
}
```

**Step 2:** HttpLlmBackend — POST `/v1/chat/completions` style เดียวกับ fc_test.py แต่ยิง raw prompt ผ่านช่อง system เปล่า? ไม่ — ใช้ field `"prompt"` ของ llama-server (`/completion` endpoint) เพื่อคุม chatml เอง:

```java
package com.cfks.goosedroid.GooseDesktop;

import java.io.*; import java.net.HttpURLConnection; import java.net.URL;
import org.json.*;

/** DEV ONLY — ชี้ llama-server บน PC (เช่น http://192.168.1.x:8080) ตัดทิ้งก่อน release */
public final class HttpLlmBackend implements LlmBackend {
    private final String baseUrl;
    public HttpLlmBackend(String baseUrl) { this.baseUrl = baseUrl; }

    @Override public String generate(String prompt, int maxTokens, float temp, float repPen) {
        try {
            JSONObject body = new JSONObject()
                .put("prompt", prompt)
                .put("n_predict", maxTokens)
                .put("temperature", temp)
                .put("repeat_penalty", repPen)
                .put("stop", new JSONArray().put("<|im_end|>"));
            HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + "/completion").openConnection();
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setConnectTimeout(4000); c.setReadTimeout(60000);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes("UTF-8"));
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return new JSONObject(sb.toString()).getString("content");
        } catch (Exception e) { return null; }
    }
    @Override public void shutdown() {}
}
```

**Step 3:** PetBrain — public API เลียน `GooseLLM.generateResponse(String, Consumer<String>)` เป๊ะ เพื่อสลับที่เดียวจบ:

```java
package com.cfks.goosedroid.GooseDesktop;

import android.os.Handler; import android.os.Looper; import android.util.Log;
import java.util.concurrent.ExecutorService; import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** สมองจริงของ pet — แทน GooseLLM ปลอมๆ ทำงาน off-main-thread ตลอด */
public final class PetBrain {
    private static final String TAG = "PetBrain";
    public static final float TEMP = 0.2f;
    public static final float REP_PEN = 1.15f;
    public static final int MAX_TOKENS = 160;

    private static volatile LlmBackend backend;
    private static final ExecutorService exec = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile boolean busy = false;

    public static void configure(LlmBackend b) { backend = b; }
    public static boolean isReady() { return backend != null && !busy; }

    /** signature เดิมของ GooseLLM.generateResponse — drop-in replacement */
    public static void generateResponse(String eventOrUserText, Consumer<String> onReply) {
        LlmBackend b = backend;
        if (b == null || busy) { onReply.accept(null); return; }
        busy = true;
        final String prompt = PromptBuilder.chatml(PromptBuilder.fromEvent(eventOrUserText));
        exec.execute(() -> {
            String out = null;
            try {
                String raw = b.generate(prompt, MAX_TOKENS, TEMP, REP_PEN);
                LlmActionJson a = LlmActionJson.parse(raw);
                if (a != null && "chat".equals(a.action)) out = a.reply;
                else if (a != null) out = describeAction(a);   // แสดง bubble ว่ากำลังจะทำอะไร
            } catch (Exception ex) { Log.e(TAG, "generate failed", ex); }
            busy = false;
            final String f = out;
            if (f != null) main.post(() -> onReply.accept(f));
        });
    }

    /** คำสั่งผู้ใช้ (จากช่องพิมพ์ debug) — คืน action JSON เต็ม ไม่ flter เป็นแค่ reply */
    public static void act(String userText, Consumer<LlmActionJson> cb) {
        LlmBackend b = backend; if (b == null || busy) { cb.accept(null); return; }
        busy = true;
        final String prompt = PromptBuilder.chatml(userText);
        exec.execute(() -> {
            LlmActionJson a = null;
            try { a = LlmActionJson.parse(b.generate(prompt, MAX_TOKENS, TEMP, REP_PEN)); }
            finally { busy = false; main.post(() -> cb.accept(a)); }
        });
    }

    private static String describeAction(LlmActionJson a) {
        switch (a.action) {
            case "open_app": return "เดี๋ยวเปิดแอปให้นะ!";
            case "tap": return "(เดินไปแตะจอ)";
            case "input_text": return "พิมพ์ให้อยู่นะ...";
            default: return null;
        }
    }
}
```

**Step 4:** Run unit tests ยัง PASS + `./gradlew assembleDebug`

**Step 5:** Commit: `git commit -m "feat: PetBrain with backend abstraction + dev HTTP backend"`

---

### Task 5: จุดเชื่อม UI — debug chat box + สลับ GooseLLM → PetBrain

**Objective:** ผู้ใช้กดปุ่มใน MainActivity พิมพ์ภาษาไทย → ห่านตอบ bubble จริง + การ stroke ห่าน (PET) ใช้สมองใหม่

**Files:**
- Modify: `app/src/main/java/com/cfks/goosedroid/MainActivity.java` (เพิ่มปุ่ม "🧠 Talk" + dialog พิมพ์ + ปุ่ม "⚙️ LLM host" เก็บ IP ลง SharedPreferences)
- Modify: `app/src/main/java/com/cfks/goosedroid/GooseDesktop/TheGoose.java` (~line 1673 ใน case PET: เปลี่ยน `GooseLLM.generateResponse(...)` → `PetBrain.generateResponse("ถูกลูบหัว", ...)`, ที่เหลือของ GooseLLM เก็บไว้เป็น fallback ตอน backend ยังไม่ configure)

**Step 1:** ใน MainActivity `onCreate` เพิ่ม listener (วางใต้ปุ่ม ShowShadow):

```java
Button talkBtn = findViewById(R.id.btnTalk);   // เพิ่มปุ่มใน activity_main.xml ด้วย
talkBtn.setOnClickListener(v -> {
    if (!PetBrain.isReady()) { Utils.showToast(this, "LLM ยังไม่พร้อม"); return; }
    EditText input = new EditText(this); input.setHint("พิมพ์สั่งน้องหมี (ไทยได้)");
    new AlertDialog.Builder(this).setTitle("คุยกับน้องหมี")
        .setView(input)
        .setPositiveButton("ส่ง", (d, w) ->
            PetBrain.act(input.getText().toString(), action -> {
                String msg = action == null ? "(ไม่ตอบ)" :
                    ("chat".equals(action.action) ? action.reply : action.action + " " + action.pkg);
                Utils.showToast(MainActivity.this, msg);
            }))
        .setNegativeButton("ยกเลิก", null).show();
});
```

**Step 2:** Configure backend ตอนเปิดแอป (อ่าน IP จาก prefs, default `http://<PC-LAN-IP>:8080`):

```java
String host = getSharedPreferences("mb", MODE_PRIVATE).getString("llm_host", "");
if (!host.isEmpty()) PetBrain.configure(new HttpLlmBackend(host));
```

**Step 3:** รัน llama-server บน PC (bind LAN):

```bash
"$LOCALAPPDATA/Microsoft/WinGet/Packages/ggml.llamacpp_Microsoft.Winget.Source_8wekyb3d8bbwe/llama-server.exe" \
  -m models/typhoon2.5-qwen3-4b-q4_k_m.gguf -c 2048 --port 8080 --host 0.0.0.0
```

**Step 4:** Manual verify (emulator/มือถือเดียวกัน WiFi): ป้อน IP → พิมพ์ "เปิด YouTube" → Toast แสดง `open_app com.google.android.youtube`; pet ห่าน → bubble ภาษาไทยจากโมเดล

**Step 5:** `git commit -m "feat: wire PetBrain into goose interaction + debug talk box"`

---

### Task 6: Vendor llama.cpp + JNI wrapper (หัวใจของ "JNI ตรงๆ")

**Objective:** compile llama.cpp เป็น `libllama-mobile.so` ในแอป + `PetLlama.java` (native methods 5 ตัว)

**Files:**
- Create: `app/src/main/cpp/CMakeLists.txt`
- Create: `app/src/main/cpp/llama-mobile.cpp`
- Vendor: `app/src/main/cpp/llama.cpp/` (pinned tag)
- Create: `app/src/main/java/com/cfks/goosedroid/GooseDesktop/PetLlama.java`
- Modify: `app/build.gradle` (externalNativeBuild + abiFilters)
- Modify: `.gitignore` (เพิ่ม `app/src/main/cpp/llama.cpp/bin/`, `.o` ฯลฯ ถ้ามี)

**Step 1:** Pin llama.cpp (resolve tag ณ วันทำจริง):

```bash
cd MobileBot/app/src/main/cpp
git ls-remote --tags --refs https://github.com/ggml-org/llama.cpp | tail -3   # ดู tag ล่าสุด เช่น b6xxx
git clone --depth 1 --branch <TAG> https://github.com/ggml-org/llama.cpp
rm -rf llama.cpp/.git llama.cpp/tests llama.cpp/examples llama.cpp/models llama.cpp/pocs
```

(เก็บ: `src/ ggml/ include/ common/ vendor/ CMakeLists.txt *.cmake`)

**Step 2:** `CMakeLists.txt`:

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(llama-mobile CXX)
set(CMAKE_CXX_STANDARD 17)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)      # NDK ไม่มี OpenMP
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
add_subdirectory(llama.cpp)
add_library(llama-mobile SHARED llama-mobile.cpp)
find_library(log-lib log)
target_link_libraries(llama-mobile PRIVATE llama common ${log-lib})
```

**Step 3:** `llama-mobile.cpp` — JNI 5 functions (port จาก `examples/llama.android/llama/src/main/cpp/llama-android.cpp` ของ tag ที่ pin ไว้ — **copy โครง init/sampling จากตัวนั้นตามตัว** เพราะ llama.cpp API เปลี่ยนเร็ว แล้ว expose signature ตามนี้):

```cpp
// extern "C" functions required:
// JNIEXPORT jboolean JNICALL Java_com_cfks_goosedroid_GooseDesktop_PetLlama_loadModel(JNIEnv*, jclass, jstring path, jint nCtx, jint nThreads)
//   -> llama_model_load_from_file + llama_init_from_model (mmap default) ; store globals g_model,g_ctx,g_sampler
// ...PetLlama_freeModel -> cleanup globals
// ...PetLlama_generate(JNIEnv*, jclass, jstring prompt, jint maxTokens, jfloat temp, jfloat repPen) -> jstring
//     single-shot decode loop: tokenize(prompt) -> sampler chain {rep_pen, temp->dist->sample} -> detokenize
//     หยุดที่ llama_vocab_is_eos หรือครบ maxTokens ; return accumulated std::string
// ...PetLlama_stopCompletion -> atomic_flag abort (เผื่ออนาคต stream)
// ...PetLlama_systemInfo -> llama_print_system_info string (debug UI)
```

**Step 4:** `PetLlama.java`:

```java
package com.cfks.goosedroid.GooseDesktop;
import android.util.Log;

/** Thin JNI wrapper — 1 instance ต่อ process, blocking calls (เรียกจาก PetBrain executor เท่านั้น) */
public final class PetLlama implements LlmBackend {
    private static final String TAG = "PetLlama";
    static { System.loadLibrary("llama-mobile"); }

    public static native boolean loadModel(String path, int nCtx, int nThreads);
    public static native void freeModel();
    public static native String generate(String prompt, int maxTokens, float temp, float repeatPenalty);
    public static native void stopCompletion();
    public static native String systemInfo();

    @Override public String generate(String p, int mt, float t, float rp) {
        return PetLlama.generate(p, mt, t, rp);
    }
    @Override public void shutdown() { freeModel(); }

    public static boolean tryLoad(String path, int ctx) {
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
        boolean ok = loadModel(path, ctx, threads);
        Log.i(TAG, "loadModel ok=" + ok + " threads=" + threads);
        return ok;
    }
}
```

**Step 5:** `app/build.gradle` เพิ่ม:

```groovy
android {
    ndkVersion "27.0.12077973"   // หรือที่ SDK manager มี
    defaultConfig {
        externalNativeBuild { cmake { arguments "-DCMAKE_BUILD_TYPE=Release" } }
        ndk { abiFilters "arm64-v8a", "x86_64" }   // x86_64 = emulator
    }
    externalNativeBuild { cmake { path "src/main/cpp/CMakeLists.txt"; version "3.22.1+" } }
}
```

**Step 6:** Build ครั้งแรก (นาน 5-15 นาที — run background):

```bash
./gradlew assembleDebug   # expected: BUILD SUCCESSFUL, .so 2 abis ใน apk
```

Gotchas ที่เจอบ่อย: `ggml-openmp` error → เช็ค GGML_OPENMP=OFF ถูก FORCE; ไม่เจอ `common` → เช็คว่าไม่ได้ลบ common/ ตอน trim; OOM ตอน compile → ปิดแอปอื่น หรือลด abiFilters ชั่วคราวเป็น arm64 เดียว

**Step 7:** Commit: `git commit -m "feat: vendor llama.cpp JNI (PetLlama) - direct Java binding"`

---

### Task 7: JniBackend สลับเข้าจริง + โหลดโมเดลจาก device storage

**Objective:** แอปโหลด GGUF จาก internal storage แล้วคิดเอง offline 100%

**Files:**
- Modify: `MainActivity.java` (ปุ่ม "📥 Load model": copy/stream จาก SAF picker หรือ adb push path → `PetLlama.tryLoad` → `PetBrain.configure(new PetLlama())`)
- Modify: `assets/config.ini` (default keys เพิ่ม: `llm_mode=http|jni`, `llm_http_host=`, `llm_model_path=`, `llm_ctx=2048`)
- Modify: `ConfigureActivity.java` (อ่าน/เขียน keys ใหม่ — pattern เดิม getIniKey/setIniKey)

**Step 1:** Provision โมเดลลงเครื่อง (dev):

```bash
adb push models/typhoon2.5-qwen3-4b-q4_k_m.gguf /sdcard/Android/data/com.cfks.goosedroid/files/model.gguf
```

**Step 2:** ปุ่ม Load model → รันบน executor (โหลด ~10-20s รอบแรกจาก sdcard) → success toast + ปิดปุ่ม

**Step 3:** Manual verify **offline**: โหมดเครื่องบิน → pet ห่าน → bubble ภาษาไทยยังโผล่

**Step 4:** Metric จริง (เก็บลง README):

```bash
adb shell dumpsys meminfo com.cfks.goosedroid | grep -E "TOTAL|Heap"
# latency: logcat -s PetBrain PetLlama (log เวลา generate แต่ละครั้ง)
```

Target ยอมรับได้: app RAM ≤ 700MB (mmap), 1 action ≤ 8s บนเครื่องทดสอบ

**Step 5:** `git commit -m "feat: on-device inference via JNI - offline mode works"`

---

### Task 8: Edge cases + polish ปิด Phase

**Objective:** กันพังจริง: โมเดลยังไม่โหลด / generate ค้าง / JSON เพี้ยน / memory pressure kill service

- `PetBrain.busy` guard มีแล้ว → เพิ่ม timeout watchdog 12s (future task cancel → คืน fallback GooseLLM template)
- Low-memory callback (`onTrimMemory(TRIM_MEMORY_RUNNING_LOW)`) → ถ้ายังไม่ได้ใช้ LLM 30 วินาที → `freeModel()` ปลด RAM ไว้ก่อน โหลดใหม่ lazy
- Unit test เพิ่ม: prompt ยาว (UI tree ใหญ่ 60 elements) ตัดเหลือ top-N ตามระยะจาก center screen (เตรียม Phase 3) — เขียน test ตอนนี้เลยเพราะเป็น pure logic
- Update README.md สถานะ Phase 1 ✅ + ตาราง benchmark เครื่องจริง
- `git commit -m "feat: robustness pass - timeouts, memory trim, README results"`

---

## Files likely to change (รวม)

```
MobileBot/
├── app/build.gradle                     (cmake, ndk, testImpl org.json)
├── app/src/main/
│   ├── cpp/{CMakeLists.txt, llama-mobile.cpp, llama.cpp/**}
│   ├── assets/config.ini                (llm_* keys)
│   ├── java/com/cfks/goosedroid/
│   │   ├── MainActivity.java            (talk box, load-model btn, prefs)
│   │   └── GooseDesktop/
│   │       ├── PetBrain.java            ★ new
│   │       ├── LlmBackend.java          ★ new
│   │       ├── HttpLlmBackend.java      ★ new (dev only)
│   │       ├── JniBackend→ใช้ PetLlama ตรง ★ new
│   │       ├── PetLlama.java            ★ new
│   │       ├── LlmActionJson.java       ★ new
│   │       ├── PromptBuilder.java       ★ new
│   │       └── TheGoose.java            (case PET + DOUBLE_TAP จุดเดียว)
└── app/src/test/java/.../{LlmActionJsonTest, PromptBuilderTest}.java
```

## Tests / Validation

- **Unit (JVM, เร็ว):** `./gradlew :app:testDebugUnitTest` — parser 4 cases + prompt builder + UI-tree trim
- **Integration dev:** Task 5 manual flow ผ่าน llama-server PC
- **Acceptance Phase 1:** เครื่องบิน + ไม่มี WiFi → pet/ห่าน โดนลูบแล้ว bubble เป็นประโยคไทยสร้างสรรค์จากโมเดลจริง; ปุ่มคุยสั่ง "เปิด YouTube" ได้ JSON ถูก (ยังไม่ dispatch — แค่ toast แสดง action)
- **Perf gate:** RAM ≤ 700MB · 1 response ≤ 8s เครื่องทดสอบ · ไม่ ANR main thread (inference อยู่ executor เท่านั้น)

## Risks / Tradeoffs / Open Questions

| เรื่อง | เสี่ยง | ทางเลี่ยง |
|---|---|---|
| GooseDroid license | ห้ามแจก APK ที่มีโค้ดเขา | Phase 1 = private test only; Phase 2 rewrite |
| llama.cpp API drift ระหว่าง tag | JNI compile fail | pin tag + คัดจาก examples/llama.android ของ tag เดียวกัน |
| เครื่อง dev ยังไม่มี SDK/NDK | Task 2 ใช้เวลา | ถาม user ก่อนว่ามี Android Studio ไหม |
| RAM เครื่องล่าง + mmap 2.5GB | kill ตอน multi-task | ctx 2048, trim-memory lazy unload, เก็บ metric จริง (Task 7) |
| Qwen3 คิดยาว (<think>) | ช้า/parse พัง | force empty think block ใน chatml + parser strip แล้ว (T3) |
| คำถามเปิด: ย้าย overlay → Foreground Service ตอนนี้เลยไหม? | — | ตอนนี้ยังใช้ Activity-toggle แบบ GooseDroid ก่อน (scope Phase 1 = สมอง) — เลื่อนเป็น Phase 1.5 ก่อนทำ Phase 3 UI-interact |

## Execution Handoff

Plan saved: `.hermes/plans/2026-08-24_130000-phase1-goose-llm-jni.md`
พร้อม execute ทีละ task (subagent-driven-development: fresh subagent/task + spec review + quality review) — เริ่ม Task 1 เลยไหม?
