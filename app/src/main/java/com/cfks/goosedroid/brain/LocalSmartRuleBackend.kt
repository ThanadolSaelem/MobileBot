package com.cfks.goosedroid.brain

import com.cfks.goosedroid.model.LlmActionJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalSmartRuleBackend : LlmBackend {

    override val backendName: String = "Smart Agent & Action Reasoner"
    override val backendType: LlmBackendType = LlmBackendType.SMART_RULE_ENGINE

    override fun isAvailable(): Boolean = true
    override fun getStatusDetails(): String = "Always ready (Zero latency, full Thai/Eng intent mapping)"

    override suspend fun generate(
        prompt: String,
        maxTokens: Int,
        temp: Float,
        repeatPenalty: Float
    ): String = withContext(Dispatchers.Default) {
        val userQuery = extractUserQuery(prompt)
        val action = processQuery(userQuery)
        
        // Return structured JSON format mimicking SLM output
        """
        {
          "action": "${action.action}",
          ${if (action.pkg != null) "\"package\": \"${action.pkg}\"," else ""}
          ${if (action.text != null) "\"text\": \"${action.text}\"," else ""}
          ${if (action.note != null) "\"note\": \"${action.note}\"," else ""}
          ${if (action.x >= 0) "\"x\": ${action.x}, \"y\": ${action.y}," else ""}
          "reply": "${action.reply?.replace("\"", "\\\"") ?: "รับทราบคำสั่งครับ!"}"
        }
        """.trimIndent()
    }

    private fun extractUserQuery(prompt: String): String {
        return if (prompt.contains("<|im_start|>user")) {
            prompt.substringAfter("<|im_start|>user")
                .substringBefore("<|im_end|>")
                .trim()
        } else {
            prompt.trim()
        }
    }

    fun processQuery(input: String): LlmActionJson {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        return when {
            lower.contains("เปิด") || lower.contains("open") || lower.contains("launch") -> {
                val pkg = when {
                    lower.contains("youtube") || lower.contains("ยูทูป") -> "com.google.android.youtube"
                    lower.contains("chrome") || lower.contains("โครม") || lower.contains("เว็บ") || lower.contains("browser") -> "com.android.chrome"
                    lower.contains("line") || lower.contains("ไลน์") -> "jp.naver.line.android"
                    lower.contains("facebook") || lower.contains("เฟส") -> "com.facebook.katana"
                    lower.contains("tiktok") || lower.contains("ติ๊กต็อก") -> "com.zhiliaoapp.musically"
                    lower.contains("settings") || lower.contains("ตั้งค่า") -> "com.android.settings"
                    lower.contains("camera") || lower.contains("กล้อง") -> "com.android.camera"
                    lower.contains("calculator") || lower.contains("เครื่องคิดเลข") -> "com.android.calculator2"
                    lower.contains("maps") || lower.contains("แผนที่") -> "com.google.android.apps.maps"
                    else -> "com.google.android.youtube"
                }
                val appName = when {
                    pkg.contains("youtube") -> "YouTube"
                    pkg.contains("chrome") -> "Google Chrome"
                    pkg.contains("line") -> "LINE"
                    pkg.contains("settings") -> "Settings"
                    pkg.contains("calculator") -> "เครื่องคิดเลข"
                    pkg.contains("maps") -> "Google Maps"
                    else -> "แอป"
                }
                LlmActionJson(
                    action = "open_app",
                    pkg = pkg,
                    reply = "รับทราบ! กำลังเตรียมเปิดแอป $appName ให้เลยครับ 🚀"
                )
            }
            lower.contains("honk") || lower.contains("ร้อง") || lower.contains("ฮ้อง") || lower.contains("ก๊าบ") || lower.contains("เสียง") -> {
                LlmActionJson(
                    action = "honk",
                    reply = "HONK!! HONK HONK!! 🪿🔊 (เสียงกึกก้องไปทั่วหน้าจอ!)"
                )
            }
            lower.contains("มีม") || lower.contains("meme") || lower.contains("ขโมย") || lower.contains("steal") || lower.contains("loot") -> {
                LlmActionJson(
                    action = "steal_meme",
                    reply = "แอบย่องไปคาบมีมลับมาให้แล้วครับ! ดูในกระเป๋า Stolen Loot ได้เลย 🎒"
                )
            }
            lower.contains("โน้ต") || lower.contains("note") || lower.contains("เขียน") || lower.contains("จด") -> {
                val customNote = if (trimmed.length > 5 && !lower.startsWith("จดโน้ต")) {
                    trimmed.substringAfter("โน้ต").substringAfter("note").trim()
                } else "THE GOOSE WAS HERE. Peace was never an option."
                
                LlmActionJson(
                    action = "write_note",
                    note = customNote.ifEmpty { "THE GOOSE WAS HERE." },
                    reply = "จดโน้ตลับแปะหน้าจอเรียบร้อย: \"$customNote\" 📝"
                )
            }
            lower.contains("พิมพ์") || lower.contains("type") || lower.contains("input") -> {
                val textToType = trimmed.substringAfter("พิมพ์").substringAfter("type").trim()
                    .ifEmpty { "Hello from MobileBot Pet Assistant!" }
                LlmActionJson(
                    action = "input_text",
                    text = textToType,
                    reply = "พิมพ์ข้อความ: \"$textToType\" ลงในช่องเรียบร้อย ⌨️"
                )
            }
            lower.contains("กลับ") || lower.contains("back") -> {
                LlmActionJson(action = "back", reply = "กดปุ่มย้อนกลับให้แล้วครับ ⬅️")
            }
            lower.contains("โฮม") || lower.contains("home") || lower.contains("หน้าแรก") -> {
                LlmActionJson(action = "home", reply = "กลับสู่หน้าโฮมหลักเรียบร้อย 🏠")
            }
            lower.contains("แตะ") || lower.contains("tap") || lower.contains("กด") -> {
                LlmActionJson(action = "tap", x = 540, y = 960, reply = "แตะจุดกึ่งกลางหน้าจอให้แล้วครับ 🎯")
            }
            lower.contains("สวัสดี") || lower.contains("hello") || lower.contains("hi") || lower.contains("หวัดดี") -> {
                val greetings = listOf(
                    "ฮ้องงงง! สวัสดีครับเจ้านาย มีอะไรให้น้องช่วยไหม?",
                    "ก๊าบๆ! วันนี้อยากให้ป่วนหรือช่วยทำงานดีครับ?",
                    "Peace was never an option... แต่คุยกับคุณได้เสมอนะ! ✨",
                    "พร้อมรับคำสั่งแล้วครับ! พิมพ์แชท แตะตัว หรือจะให้ลักพาตัวมีมมาส่ง?"
                )
                LlmActionJson(action = "chat", reply = greetings.random())
            }
            lower.contains("ลูบ") || lower.contains("pet") || lower.contains("รัก") || lower.contains("น่ารัก") -> {
                val petReplies = listOf(
                    "งื้อออ สบายจัง ลูบหัวบ่อยๆ นะ ❤️",
                    "แฮปปี้มากเลย! ค่าความสุขพุ่งทะลุ 100 แล้ว!",
                    "ขนฟูหมดแล้วเนี่ย แต่ชอบนะ ลูบอีกสิ! ✨",
                    "Purrrr... ถึงจะเป็นห่านแต่ก็ครางแบบแมวได้นะ!",
                    "ก๊าบ~ ใจฟูสุดๆ รักเจ้านายที่สุดเลย!"
                )
                LlmActionJson(action = "chat", reply = petReplies.random())
            }
            lower.contains("กิน") || lower.contains("feed") || lower.contains("หิว") || lower.contains("ขนมปัง") -> {
                LlmActionJson(action = "chat", reply = "งั่มๆๆ! ขนมปังกรอบอร่อยมากกก! พลังงานเต็ม 100 พร้อมป่วนต่อ 🍞")
            }
            lower.contains("เล่น") || lower.contains("game") || lower.contains("play") -> {
                LlmActionJson(action = "chat", reply = "ไปที่แท็บ Mini Games เลย! มีเลเซอร์พอยเตอร์, โยนขนมปัง, ดวลจังหวะฮ้อง และหลบมีดสุดมันส์! 🎮")
            }
            lower.contains("ตลก") || lower.contains("joke") || lower.contains("มุก") || lower.contains("ขำ") -> {
                val jokes = listOf(
                    "ทำไมห่านถึงไม่ชอบเปิดโหมดเงียบเสียง? ...เพราะ Peace was never an option ไงล่ะ! 😂",
                    "อะไรเอ่ยเดิน 2 ขา ขาวๆ ส้มๆ พิมพ์ไทยได้ แถมขโมยมีมเก่ง? ...MobileBot ตัวนี้เอง!",
                    "รู้ไหมทำไมห่านถึงบินเป็นรูปตัว V? ...เพราะว่าถ้าบินเป็นรูปตัว W มันจะเหนื่อยเป็น 2 เท่า! 🪿"
                )
                LlmActionJson(action = "chat", reply = jokes.random())
            }
            lower.contains("ชื่ออะไร") || lower.contains("who are you") || lower.contains("คุณคือใคร") -> {
                LlmActionJson(action = "chat", reply = "ผมคือ MobileBot ผู้ช่วย AI & Desktop Pet บนมือถือของคุณครับ! วิ่งเล่นบนจอได้ ช่วยพิมพ์งาน เปิดแอป และคุยแก้เหงาได้ตลอดเวลา ✨")
            }
            else -> {
                val smartResponses = listOf(
                    "รับทราบครับผม! \"$trimmed\" น้องบันทึกเข้าหน่วยความจำ AI เรียบร้อยแล้ว ✨",
                    "เข้าใจแล้วครับ! น้องจะคอยซัพพอร์ตคุณตลอดการใช้งานหน้าจอเลย 🪿",
                    "ได้เลยครับ! สั่งอะไรน้องพร้อมลุยเสมอ หรือจะชวนคุยเล่นก็ได้นะ",
                    "น่าสนใจมาก! \"$trimmed\" อยากให้น้องช่วยทำอะไรต่อบอกได้ทันทีเลยครับ!"
                )
                LlmActionJson(action = "chat", reply = smartResponses.random())
            }
        }
    }
}
