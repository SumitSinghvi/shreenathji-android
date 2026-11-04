package com.shreenathji.sales.ui.screens.party

const val VOICE_MARKER = "[[voice]]"

/** Replace [[voice]]… URLs with a short label for card previews (not the full chat). */
fun formatChatPreviewText(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw
        .lineSequence()
        .map { line ->
            Regex("""\[\[voice\]\]\S+""")
                .replace(line) { "Voice message" }
        }
        .joinToString("\n")
}

data class ChatMessageItem(
    val id: String,
    val sender: String,
    val senderLabel: String,
    val time: String?,
    val text: String,
    val isFromManager: Boolean,
    val orderTime: Int = -1,
    val seq: Int = 0,
    val voiceUrl: String? = null
)

fun parseTimeToMinutes(timeStr: String?): Int {
    if (timeStr.isNullOrBlank()) return -1
    val clean = timeStr.trim().lowercase()
    val match = Regex("""^(\d{1,2}):(\d{2})(?::\d{2})?\s*(am|pm)?""").find(clean) ?: return 0
    var hours = match.groupValues[1].toIntOrNull() ?: 0
    val minutes = match.groupValues[2].toIntOrNull() ?: 0
    val meridiem = match.groupValues.getOrNull(3)?.lowercase()
    if (meridiem == "pm" && hours < 12) hours += 12
    if (meridiem == "am" && hours == 12) hours = 0
    return hours * 60 + minutes
}

fun extractVoiceUrl(text: String): Pair<String, String?> {
    val trimmed = text.trim()
    if (trimmed.startsWith(VOICE_MARKER)) {
        val url = trimmed.removePrefix(VOICE_MARKER).trim()
        return "Voice message" to url.takeIf { it.startsWith("http") }
    }
    return trimmed to null
}

fun parseStoreChatHistory(
    instructions: String?,
    remarks: String?,
    repName: String?,
    visitedAt: Long? = null
): List<ChatMessageItem> {
    val items = mutableListOf<ChatMessageItem>()

    if (!instructions.isNullOrBlank()) {
        val lines = instructions.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        lines.forEachIndexed { idx, line ->
            val timeMatch = Regex("""^\[(.*?)\]\s*(.*?):\s*(.*)$""").find(line)
            if (timeMatch != null) {
                val time = timeMatch.groupValues[1]
                val sender = timeMatch.groupValues[2]
                val (text, voiceUrl) = extractVoiceUrl(timeMatch.groupValues[3])
                items.add(
                    ChatMessageItem(
                        id = "inst_$idx",
                        sender = sender,
                        senderLabel = sender,
                        time = time,
                        text = text,
                        isFromManager = true,
                        orderTime = parseTimeToMinutes(time),
                        seq = 100 + idx,
                        voiceUrl = voiceUrl
                    )
                )
            } else {
                val (text, voiceUrl) = extractVoiceUrl(line)
                items.add(
                    ChatMessageItem(
                        id = "inst_$idx",
                        sender = "Manager",
                        senderLabel = "Manager Instruction",
                        time = null,
                        text = text,
                        isFromManager = true,
                        orderTime = -1,
                        seq = idx,
                        voiceUrl = voiceUrl
                    )
                )
            }
        }
    }

    if (!remarks.isNullOrBlank()) {
        val lines = remarks.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        lines.forEachIndexed { idx, line ->
            val timeMatch = Regex("""^\[(.*?)\]\s*(.*)$""").find(line)
            if (timeMatch != null) {
                val time = timeMatch.groupValues[1]
                val (text, voiceUrl) = extractVoiceUrl(timeMatch.groupValues[2])
                items.add(
                    ChatMessageItem(
                        id = "rem_$idx",
                        sender = repName ?: "Sales Rep",
                        senderLabel = if (idx == 0) "Field Visit Remark" else "Field Update",
                        time = time,
                        text = text,
                        isFromManager = false,
                        orderTime = parseTimeToMinutes(time),
                        seq = 200 + idx,
                        voiceUrl = voiceUrl
                    )
                )
            } else {
                val time = if (idx == 0 && visitedAt != null) {
                    try {
                        java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                            .format(java.util.Date(visitedAt))
                    } catch (_: Exception) {
                        null
                    }
                } else null
                val (text, voiceUrl) = extractVoiceUrl(line)
                items.add(
                    ChatMessageItem(
                        id = "rem_$idx",
                        sender = repName ?: "Sales Rep",
                        senderLabel = if (idx == 0) "Field Visit Remark" else "Field Update",
                        time = time,
                        text = text,
                        isFromManager = false,
                        orderTime = parseTimeToMinutes(time),
                        seq = 200 + idx,
                        voiceUrl = voiceUrl
                    )
                )
            }
        }
    }

    items.sortWith(compareBy<ChatMessageItem> { it.orderTime }.thenBy { it.seq })
    return items
}
