package com.cfks.goosedroid.brain

import android.util.Log
import com.cfks.goosedroid.model.LlmActionJson
import com.cfks.goosedroid.model.PetAppearance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class BrainTelemetry(
    val activeBackendName: String = "Smart Agent Brain",
    val activeBackendType: LlmBackendType = LlmBackendType.SMART_RULE_ENGINE,
    val lastLatencyMs: Long = 0,
    val totalInferences: Int = 0,
    val statusMessage: String = "Ready"
)

object PetBrain {

    private const val TAG = "PetBrain"

    private val smartRuleBackend = LocalSmartRuleBackend()
    private val httpBackend = HttpLlmBackend()
    private val petLlamaBackend = PetLlamaBackend()

    private var preferredBackendType = LlmBackendType.SMART_RULE_ENGINE

    private val _telemetry = MutableStateFlow(BrainTelemetry())
    val telemetry: StateFlow<BrainTelemetry> = _telemetry.asStateFlow()

    private val THAI_GREETINGS = listOf(
        "ฮ้องงงง! มีอะไรให้น้องช่วยไหมครับ?",
        "ก๊าบๆ! วันนี้อยากให้ป่วนหรือช่วยอะไรดี?",
        "Peace was never an option... แต่คุยกับคุณได้เสมอนะ!",
        "พร้อมรับคำสั่งแล้ว! แตะจอ พิมพ์แชท หรือจะให้ลักพาตัวมีมมาส่ง?",
        "น้องพร้อมลุย! สั่งเปิดแอป พิมพ์งาน หรือส่งเสียงฮ้องได้เลยจ้า"
    )

    private val PETTED_REPLIES = listOf(
        "งื้อออ สบายจัง ลูบหัวบ่อยๆ นะ ❤️",
        "แฮปปี้มากเลย! ความสุขพุ่งทะลุ 100 แล้ว!",
        "ขนฟูหมดแล้วเนี่ย แต่ชอบนะ ลูบอีกสิ!",
        "Purrrr... ถึงจะเป็นห่านแต่ก็ครางแบบแมวได้นะ!",
        "ก๊าบ~ ใจฟูสุดๆ รักเจ้านายที่สุดเลย!"
    )

    private val FED_REPLIES = listOf(
        "งั่มๆๆ! ขนมปังกรอบอร่อยมากกก!",
        "อิ่มแป้เลย! พลังงานเต็ม 100 พร้อมป่วนต่อ!",
        "ขอบคุณสำหรับของว่างครับเจ้านาย!",
        "ของโปรดเลย! ครั้งหน้าขอแอปเปิ้ลด้วยนะ!"
    )

    private val MISCHIEF_QUOTES = listOf(
        "แอบเอาโน้ต 'peace was never an option' ไปซ่อนแป๊บ...",
        "เห็นอะไรวิบวับบนหน้าจอไหม? เดี๋ยวแอบคาบมาให้นะ!",
        "HONK HONK HONK!! เสียงดังฟังชัดไหม?",
        "งานของคุณเสร็จหรือยัง? ถ้าง่วงมาเล่นกับน้องก่อนสิ!",
        "ภารกิจวันนี้: ขโมยมีม 3 ชิ้น และวิ่งรอบหน้าจอ 10 รอบ!"
    )

    fun setPreferredBackend(type: LlmBackendType) {
        preferredBackendType = type
        updateTelemetryStatus()
    }

    fun setModelPath(path: String) {
        petLlamaBackend.setModelPath(path)
        updateTelemetryStatus()
    }

    fun setHttpEndpoint(url: String) {
        httpBackend.endpointUrl = url
        updateTelemetryStatus()
    }

    private fun updateTelemetryStatus() {
        val backend = getActiveBackend()
        _telemetry.value = _telemetry.value.copy(
            activeBackendName = backend.backendName,
            activeBackendType = backend.backendType,
            statusMessage = backend.getStatusDetails()
        )
    }

    fun getActiveBackend(): LlmBackend {
        return when (preferredBackendType) {
            LlmBackendType.ON_DEVICE_GGUF -> {
                if (petLlamaBackend.isAvailable()) petLlamaBackend else smartRuleBackend
            }
            LlmBackendType.LOCAL_SERVER -> {
                if (httpBackend.isAvailable()) httpBackend else smartRuleBackend
            }
            LlmBackendType.SMART_RULE_ENGINE -> smartRuleBackend
        }
    }

    suspend fun processUserCommand(
        input: String,
        appearance: PetAppearance? = null
    ): LlmActionJson = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val backend = getActiveBackend()
        val formattedPrompt = PromptBuilder.formatChatml(input, appearance)

        var rawResult: String? = null
        try {
            rawResult = backend.generate(formattedPrompt, maxTokens = 128, temp = 0.7f, repeatPenalty = 1.1f)
        } catch (e: Exception) {
            Log.w(TAG, "Backend ${backend.backendName} failed, falling back to smart rule engine", e)
        }

        if (rawResult.isNullOrBlank() && backend != smartRuleBackend) {
            rawResult = smartRuleBackend.generate(formattedPrompt)
        }

        val parsedAction = LlmActionParser.parse(rawResult)
            ?: smartRuleBackend.processQuery(input)

        val latency = System.currentTimeMillis() - startTime

        _telemetry.value = _telemetry.value.copy(
            activeBackendName = backend.backendName,
            activeBackendType = backend.backendType,
            lastLatencyMs = latency,
            totalInferences = _telemetry.value.totalInferences + 1,
            statusMessage = backend.getStatusDetails()
        )

        parsedAction
    }

    fun getRandomThought(): String = MISCHIEF_QUOTES.random()
    fun getRandomPetReply(): String = PETTED_REPLIES.random()
    fun getRandomFeedReply(): String = FED_REPLIES.random()
}
