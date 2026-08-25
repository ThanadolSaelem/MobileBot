package com.cfks.goosedroid.brain

import org.json.JSONException
import org.json.JSONObject

/**
 * Parser สำหรับ action JSON ของ pet — ตามโครงสร้างใน INTEGRATION_PLAN
 */
data class LlmActionJson(
    val action: String,
    val text: String? = null,
    val reply: String? = null,
    val pkg: String? = null,
    val moveset: String? = null,
    val x: Int = -1,
    val y: Int = -1,
    val x1: Int = -1,
    val y1: Int = -1,
    val x2: Int = -1,
    val y2: Int = -1,
    val target_dx: Float? = null,
    val target_dy: Float? = null
) {
    companion object {
        fun parse(raw: String?): LlmActionJson? {
            if (raw.isNullOrBlank()) return null
            var t = raw.trim()

            // Remove <think> ... </think>
            val thinkEnd = t.lastIndexOf("</think>")
            if (thinkEnd >= 0) {
                t = t.substring(thinkEnd + 8).trim()
            }

            // Strip markdown fences
            if (t.contains("```")) {
                val parts = t.split("```")
                for (p in parts) {
                    val clean = p.replaceFirst("^json".toRegex(), "").trim()
                    if (clean.startsWith("{")) {
                        t = clean
                        break
                    }
                }
            }

            val s = t.indexOf('{')
            val e = t.lastIndexOf('}')
            if (s < 0 || e <= s) {
                // If not JSON, interpret as plain chat reply
                return LlmActionJson(action = "chat", reply = raw.trim())
            }

            return try {
                val jsonStr = t.substring(s, e + 1)
                val o = JSONObject(jsonStr)
                val action = o.optString("action", "chat")
                LlmActionJson(
                    action = action,
                    text = if (o.has("text")) o.optString("text") else null,
                    reply = if (o.has("reply")) o.optString("reply") else null,
                    pkg = if (o.has("package")) o.optString("package") else null,
                    moveset = if (o.has("moveset")) o.optString("moveset") else null,
                    x = o.optInt("x", -1),
                    y = o.optInt("y", -1),
                    x1 = o.optInt("x1", -1),
                    y1 = o.optInt("y1", -1),
                    x2 = o.optInt("x2", -1),
                    y2 = o.optInt("y2", -1),
                    target_dx = if (o.has("target_dx")) o.optDouble("target_dx").toFloat() else null,
                    target_dy = if (o.has("target_dy")) o.optDouble("target_dy").toFloat() else null
                )
            } catch (ex: JSONException) {
                LlmActionJson(action = "chat", reply = raw.trim())
            }
        }
    }
}
