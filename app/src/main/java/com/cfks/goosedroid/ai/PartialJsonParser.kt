package com.cfks.goosedroid.ai

/**
 * Parses truncated JSON strings from LLM streaming output.
 * Focuses on extracting the 'speech' field as it grows.
 */
object PartialJsonParser {

    /**
     * Attempts to find the value of "speech" in a potentially broken JSON string.
     * Example: {"thought": "...", "speech": "Hello there
     * Should return "Hello there"
     */
    fun extractSpeech(partialJson: String): String {
        // Look for "speech": " (with optional spaces and double/single quotes)
        val speechMarker = "\"speech\""
        val startIndex = partialJson.indexOf(speechMarker)
        if (startIndex == -1) return ""

        // Find the opening quote after the marker
        val quoteIndex = partialJson.indexOf("\"", startIndex + speechMarker.length)
        if (quoteIndex == -1) {
            // Check if there's a colon but no quote yet
            return ""
        }

        val valueStart = quoteIndex + 1
        if (valueStart > partialJson.length) return ""

        // Find the closing quote, making sure it's not escaped
        var closingQuoteIndex = -1
        for (i in valueStart until partialJson.length) {
            if (partialJson[i] == '\"' && partialJson[i - 1] != '\\') {
                closingQuoteIndex = i
                break
            }
        }

        return if (closingQuoteIndex != -1) {
            partialJson.substring(valueStart, closingQuoteIndex)
        } else {
            // Still growing
            partialJson.substring(valueStart)
        }
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
    }
}
