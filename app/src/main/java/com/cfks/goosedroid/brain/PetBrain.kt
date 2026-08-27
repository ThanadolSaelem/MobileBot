package com.cfks.goosedroid.brain

import android.content.Context
import com.cfks.goosedroid.ai.AiManager
import com.cfks.goosedroid.ai.ChatEngine
import com.cfks.goosedroid.ai.EngineLogBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * PetBrain - ตัวขับเคลื่อนความคิดและการตอบสนองของตัวละคร ตาม INTEGRATION_PLAN
 * รองรับการแยก Context และความจำเฉพาะของตัวละครแต่ละตัวตามชื่อเฉพาะ
 */
object PetBrain {
    suspend fun processCommandStream(
        context: Context,
        userText: String,
        petName: String = "Unit",
        conversationId: Long? = null
    ): Flow<LlmActionResult> {
        val aiManager = AiManager(context)
        val unitInfo = CharacterRegistry.getUnitInfo(petName)
        val data = unitInfo?.id?.let { CharacterRegistry.getCharacterData(it) }
        val persona = data?.persona ?: ""
        
        if (conversationId != null) ChatEngine.setStatus(conversationId, "THINKING...")

        return aiManager.getNextActionStream(userText, persona, petName, conversationId)
            .map { directive ->
                LlmActionResult(
                    action = LlmActionJson(
                        action = directive.action,
                        reply = directive.speech ?: "",
                        moveset = directive.movesetName,
                        target_dx = directive.targetDx,
                        target_dy = directive.targetDy
                    ),
                    displayReply = directive.speech ?: "",
                    actionBadge = "AI // ${directive.action.uppercase()}"
                )
            }
    }

    suspend fun processCommand(
        context: Context,
        userText: String,
        petName: String = "Unit",
        conversationId: Long? = null
    ): LlmActionResult = withContext(Dispatchers.Default) {
        val trimmed = userText.trim()
        val lower = trimmed.lowercase()

        // บันทึก Log การสั่งการลงใน Context เฉพาะตัวของตัวละครนี้
        CharacterRegistry.addInteractionLog(petName, "USER: $trimmed")

        var fallbackReason = "OFFLINE/ERROR"
        
        try {
            val result = processCommandStream(context, trimmed, petName, conversationId).last()
            CharacterRegistry.addInteractionLog(petName, "ASSISTANT: ${result.displayReply}")
            return@withContext result
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("PetBrain", "AI Call Failed, falling back to local mocks", e)
            EngineLogBus.error("PetBrain", "AI CALL FAILED → local fallback: ${e.message?.take(140)}")
            fallbackReason = when {
                e.message?.contains("429") == true -> "RATE LIMITED"
                e.message?.contains("rate-limited", ignoreCase = true) == true -> "RATE LIMITED"
                e.message?.contains("timeout", ignoreCase = true) == true -> "TIMEOUT"
                else -> "OFFLINE/ERROR"
            }
            if (conversationId != null) {
                ChatEngine.setStatus(conversationId, "OFFLINE — FALLBACK MODE ($fallbackReason)")
            }
        }

        // ตรวจสอบ Moveset พิเศษและ Custom Dialogue ที่ผู้ใช้กำหนดไว้ในตัวละครนี้
        val unitInfo = CharacterRegistry.getUnitInfo(petName)
        val matchedMoveset = unitInfo?.moveSets?.firstOrNull { move ->
            val moveNameLower = move.name.trim().lowercase()
            val moveDescLower = move.description.trim().lowercase()
            (moveNameLower.isNotEmpty() && (lower.contains(moveNameLower) || lower.contains(moveNameLower.replace("_", " ")))) ||
            (moveDescLower.isNotEmpty() && lower.split(" ").any { word -> word.length > 2 && moveDescLower.contains(word) })
        }

        // Match common Android / Thai assistant intents or Custom Moveset
        val result = when {
            matchedMoveset != null -> {
                val customSpeech = if (matchedMoveset.dialogue.isNotBlank()) {
                    matchedMoveset.dialogue
                } else if (matchedMoveset.description.isNotBlank()) {
                    "กำลังแสดงท่าทาง ${matchedMoveset.name} (${matchedMoveset.description}) ตามคำสั่งครับ"
                } else {
                    "รับทราบครับ กำลังแสดงท่าทาง ${matchedMoveset.name} ให้ผู้บัญชาการชมครับ"
                }
                LlmActionResult(
                    action = LlmActionJson(action = "custom_action", moveset = matchedMoveset.name, reply = customSpeech),
                    displayReply = customSpeech,
                    actionBadge = "ACTION // ${matchedMoveset.name.uppercase()}"
                )
            }
            lower.contains("กระโดด") || lower.contains("jump") || lower.contains("โดด") -> {
                val reply = "รับทราบครับ กำลังกระโดดตามคำสั่งผู้บัญชาการ!"
                LlmActionResult(
                    action = LlmActionJson(action = "JUMP", moveset = "JUMP", reply = reply, target_dx = 150f, target_dy = -150f),
                    displayReply = reply,
                    actionBadge = "PHYSICS // JUMP"
                )
            }
            lower.contains("วิ่ง") || lower.contains("run") || lower.contains("สปรินต์") -> {
                val reply = "รับทราบครับ กำลังวิ่งลาดตระเวนด้วยความเร็วสูง!"
                LlmActionResult(
                    action = LlmActionJson(action = "RUN", moveset = "RUN", reply = reply, target_dx = -250f, target_dy = 0f),
                    displayReply = reply,
                    actionBadge = "DIRECTIVE // RUN"
                )
            }
            lower.contains("เดิน") || lower.contains("walk") || lower.contains("ไปข้างหน้า") -> {
                val reply = "รับทราบครับ กำลังเดินตามเส้นทางที่สั่งการ"
                LlmActionResult(
                    action = LlmActionJson(action = "WALK", moveset = "WALK", reply = reply, target_dx = 120f, target_dy = -80f),
                    displayReply = reply,
                    actionBadge = "DIRECTIVE // WALK"
                )
            }
            lower.contains("หยุด") || lower.contains("อยู่นิ่ง") || lower.contains("ยืนนิ่ง") || lower.contains("idle") || lower.contains("stop") || lower.contains("stay") -> {
                val reply = "รับทราบครับ ยูนิต $petName หยุดนิ่งและรักษาตำแหน่งแล้ว"
                LlmActionResult(
                    action = LlmActionJson(action = "IDLE", moveset = "IDLE", reply = reply),
                    displayReply = reply,
                    actionBadge = "DIRECTIVE // IDLE"
                )
            }
            lower.contains("เปิด youtube") || lower.contains("open youtube") -> {
                LlmActionResult(
                    action = LlmActionJson(action = "open_app", pkg = "com.google.android.youtube"),
                    displayReply = "กำลังเปิด YouTube ให้นะครับ",
                    actionBadge = "OPEN_APP // com.google.android.youtube"
                )
            }
            lower.contains("เปิด chrome") || lower.contains("เปิดเว็บ") || lower.contains("open chrome") -> {
                LlmActionResult(
                    action = LlmActionJson(action = "open_app", pkg = "com.android.chrome"),
                    displayReply = "เปิด Chrome สำหรับท่องเว็บให้แล้วครับ",
                    actionBadge = "OPEN_APP // com.android.chrome"
                )
            }
            lower.contains("เปิดกล้อง") || lower.contains("ถ่ายรูป") || lower.contains("camera") -> {
                LlmActionResult(
                    action = LlmActionJson(action = "open_app", pkg = "com.android.camera"),
                    displayReply = "เปิดกล้องถ่ายรูปให้แล้วครับ",
                    actionBadge = "OPEN_APP // com.android.camera"
                )
            }
            lower.contains("พิมพ์") || lower.contains("type") || lower.contains("write") -> {
                val toType = trimmed.replace("(?i)^พิมพ์|^type|^write".toRegex(), "").trim().ifEmpty { trimmed }
                LlmActionResult(
                    action = LlmActionJson(action = "input_text", text = toType),
                    displayReply = "พิมพ์ข้อความ \"$toType\" ลงในช่องแล้วครับ",
                    actionBadge = "INPUT_TEXT // \"$toType\""
                )
            }
            lower.contains("แตะ") || lower.contains("คลิก") || lower.contains("tap") -> {
                LlmActionResult(
                    action = LlmActionJson(action = "tap", x = 500, y = 800),
                    displayReply = "เดินไปแตะหน้าจอให้ที่พิกัด [500, 800] เรียบร้อยครับ",
                    actionBadge = "TAP // (500, 800)"
                )
            }
            lower.contains("ปัด") || lower.contains("เลื่อน") || lower.contains("swipe") || lower.contains("scroll") -> {
                LlmActionResult(
                    action = LlmActionJson(action = "swipe", x1 = 500, y1 = 1200, x2 = 500, y2 = 400),
                    displayReply = "ปัดหน้าจอเลื่อนขึ้นให้แล้วครับ",
                    actionBadge = "SWIPE // [500,1200]->[500,400]"
                )
            }
            lower.contains("กลับ") || lower.contains("back") -> {
                LlmActionResult(
                    action = LlmActionJson(action = "back"),
                    displayReply = "กดย้อนกลับให้แล้วครับ",
                    actionBadge = "SYSTEM // BACK"
                )
            }
            lower.contains("หน้าหลัก") || lower.contains("home") -> {
                LlmActionResult(
                    action = LlmActionJson(action = "home"),
                    displayReply = "กลับสู่หน้าโฮมแล้วครับ",
                    actionBadge = "SYSTEM // HOME"
                )
            }
            lower.contains("ลูบหัว") || lower.contains("pet") || lower.contains("น่ารัก") -> {
                LlmActionResult(
                    action = LlmActionJson(action = "chat", reply = "ขอบคุณที่ลูบหัวนะ กำลังใจเต็มเปี่ยมแล้ว"),
                    displayReply = "ขอบคุณที่ลูบหัวนะ กำลังใจเต็มเปี่ยมแล้ว",
                    actionBadge = "INTERACTION // PET_HEAD"
                )
            }
            lower.contains("สวัสดี") || lower.contains("hello") || lower.contains("hi") -> {
                LlmActionResult(
                    action = LlmActionJson(action = "chat", reply = "สวัสดีครับผู้บัญชาการ $petName พร้อมรับคำสั่งแล้วครับ"),
                    displayReply = "สวัสดีครับผู้บัญชาการ $petName พร้อมรับคำสั่งแล้วครับ",
                    actionBadge = "CHAT // GREETING"
                )
            }
            lower.contains("ทำอะไรได้") || lower.contains("help") || lower.contains("ช่วยอะไร") -> {
                LlmActionResult(
                    action = LlmActionJson(action = "chat", reply = "ผมคือยูนิต $petName สามารถเดินบนหน้าจอ ช่วยเปิดแอป แตะปุ่ม พิมพ์ข้อความ หรืออยู่เป็นเพื่อนคุณได้ครับ"),
                    displayReply = "ผมคือยูนิต $petName สามารถเดินบนหน้าจอ ช่วยเปิดแอป แตะปุ่ม พิมพ์ข้อความ หรืออยู่เป็นเพื่อนคุณได้ครับ",
                    actionBadge = "CHAT // CAPABILITIES"
                )
            }
            lower.contains("มีใครบ้าง") || lower.contains("เพื่อน") || lower.contains("ยูนิต") -> {
                val activeList = CharacterRegistry.getActiveNames()
                val listStr = if (activeList.isNotEmpty()) activeList.joinToString(", ") else petName
                LlmActionResult(
                    action = LlmActionJson(action = "chat", reply = "ขณะนี้มียูนิตที่ปฏิบัติหน้าที่บนหน้าจอทั้งหมด ${activeList.size} ยูนิต ได้แก่: $listStr"),
                    displayReply = "ขณะนี้มียูนิตที่ปฏิบัติหน้าที่บนหน้าจอทั้งหมด ${activeList.size} ยูนิต ได้แก่: $listStr",
                    actionBadge = "REGISTRY // UNITS"
                )
            }
            else -> {
                val genericResponses = listOf(
                    "รับทราบครับผู้บัญชาการ $petName ได้รับคำสั่ง \"$trimmed\" แล้ว กำลังประมวลผล",
                    "เข้าใจแล้วครับ ระบบควบคุมเมทริกซ์ของยูนิต $petName พร้อมทำงานตามคำสั่ง",
                    "ยูนิต $petName ตอบสนองต่อคำสั่ง \"$trimmed\" เรียบร้อยครับ",
                    "พิกัดและการเคลื่อนไหวของยูนิต $petName ปกติ พร้อมลุยภารกิจร่วมกับคุณแล้วครับ"
                )
                val reply = genericResponses.random()
                LlmActionResult(
                    action = LlmActionJson(action = "chat", reply = reply),
                    displayReply = reply,
                    actionBadge = "CHAT // RESPONSE"
                )
            }
        }

        // ทำเครื่องหมายว่าเป็นคำตอบจาก fallback พร้อมเหตุผล ให้ user แยกออกจาก
        // คำตอบของ LLM จริงได้ทันที (เช่น FALLBACK // RATE LIMITED · CHAT // RESPONSE)
        val isOffline = conversationId?.let { ChatEngine.statusMap.value[it]?.startsWith("OFFLINE") } == true
        val finalResult = if (fallbackReason != "OFFLINE/ERROR" || isOffline) {
            result.copy(actionBadge = "FALLBACK // $fallbackReason · ${result.actionBadge}")
        } else {
            result
        }

        // บันทึก Response ลงใน Context
        CharacterRegistry.addInteractionLog(petName, "ASSISTANT: ${finalResult.displayReply}")
        finalResult
    }
}

data class LlmActionResult(
    val action: LlmActionJson,
    val displayReply: String,
    val actionBadge: String
)
