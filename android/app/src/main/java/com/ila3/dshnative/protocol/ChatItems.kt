package com.ila3.dshnative.protocol

import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject

sealed class ChatItem {
    abstract val key: String

    data class ChatAttachment(
        val name: String,
        val mime: String? = null,
        val data: String? = null,
        val receiptId: String? = null,
        val bytes: Long? = null,
        val preview: Bitmap? = null,
    )

    data class User(
        override val key: String,
        val text: String,
        val attachments: List<ChatAttachment> = emptyList(),
    ) : ChatItem()
    data class Assistant(
        override val key: String,
        val text: String,
        val reasoning: String? = null,
        val streaming: Boolean = false,
    ) : ChatItem()

    data class Tool(
        override val key: String,
        val callId: String,
        val name: String,
        val arguments: String,
        val result: String? = null,
        val isError: Boolean = false,
    ) : ChatItem()

    data class ToolResult(
        override val key: String,
        val callId: String,
        val text: String,
        val isError: Boolean,
    ) : ChatItem()

    data class Notice(override val key: String, val text: String) : ChatItem()
}

/**
 * Minimal reader for the durable event surface. Unknown events are ignored: a
 * native client can grow its rendering without refusing a session whose log
 * contains newer event kinds.
 */
object ChatParser {
    fun parseRecord(record: JSONObject): ChatItem? {
        val event = record.optJSONObject("event") ?: return null
        return parseEvent(event)
    }

    fun parseEvent(event: JSONObject): ChatItem? {
        val type = event.optString("type")
        val seq = event.optLong("seq", 0L)
        val data = event.optJSONObject("data") ?: return null
        return when (type) {
            "user/message" -> {
                // user/message stores the UserMessage fields directly on data;
                // assistant/message nests its message under `message`.
                val sourceKind = data.optJSONObject("source")?.optString("kind")
                if (sourceKind != null && sourceKind != "user") null else {
                    val text = messageText(data)
                    val attachments = parseAttachments(data)
                    if (text.isBlank() && attachments.isEmpty()) null else ChatItem.User("e$seq", text, attachments)
                }
            }
            "assistant/message" -> {
                val message = data.optJSONObject("message")
                val text = messageText(message)
                val reasoning = reasoningText(message)
                if (text.isBlank() && reasoning == null) null else ChatItem.Assistant("e$seq", text, reasoning)
            }
            "tool/call" -> {
                val callId = data.optString("callId")
                if (callId.isEmpty()) null else ChatItem.Tool(
                    key = "tool-$callId",
                    callId = callId,
                    name = data.optString("name", "tool"),
                    arguments = data.optString("arguments", ""),
                )
            }
            "tool/result" -> {
                val message = data.optJSONObject("message")
                val callId = message?.optString("toolCallId").orEmpty()
                if (callId.isEmpty()) null else ChatItem.ToolResult(
                    key = "result-$seq",
                    callId = callId,
                    text = messageText(message),
                    isError = message?.optBoolean("isError", false) == true,
                )
            }
            "turn/start", "turn/end", "step/start", "step/end", "request/header",
            "assistant/attempt", "model/selection", "compaction/summary" -> null
            else -> {
                if (seq == 0L) null else null
            }
        }
    }

    /** Attachment blocks of a user message, in the order the user sent them. */
    fun parseAttachments(message: JSONObject?): List<ChatItem.ChatAttachment> {
        message ?: return emptyList()
        val content = message.optJSONArray("content") ?: return emptyList()
        val out = ArrayList<ChatItem.ChatAttachment>()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            val name = block.optString("name").ifEmpty {
                when (block.optString("type")) {
                    "image" -> "图片"
                    "file" -> "文件"
                    else -> "附件"
                }
            }
            when (block.optString("type")) {
                "image" -> out.add(
                    ChatItem.ChatAttachment(
                        name = name,
                        mime = block.optString("mediaType").ifEmpty { null },
                        data = block.optString("data").ifEmpty { null },
                    ),
                )
                "file" -> out.add(
                    ChatItem.ChatAttachment(
                        name = name,
                        mime = block.optString("mediaType").ifEmpty { null },
                        receiptId = block.optString("receiptId").ifEmpty { null },
                        bytes = block.optLong("bytes", 0L).takeIf { it > 0 },
                    ),
                )
                else -> Unit
            }
        }
        return out
    }

    /** Human-authored text plus visible attachment placeholders for a user event. */
    fun userMessageText(message: JSONObject?): String {
        message ?: return ""
        val builder = StringBuilder(messageText(message))
        val content = message.optJSONArray("content") ?: return builder.toString()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            val label = when (block.optString("type")) {
                "image" -> block.optString("name").ifEmpty { "图片" }
                "file" -> block.optString("name").ifEmpty { "文件" }
                else -> continue
            }
            if (builder.isNotEmpty()) builder.append('\n')
            builder.append("📎 ").append(label)
        }
        return builder.toString()
    }

    fun messageText(message: JSONObject?): String {
        message ?: return ""
        val content = message.optJSONArray("content") ?: return ""
        val builder = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "text") {
                if (builder.isNotEmpty()) builder.append('\n')
                builder.append(block.optString("text"))
            }
        }
        return builder.toString()
    }

    /** Reasoning blocks of an assistant message, joined; null when absent. */
    fun reasoningText(message: JSONObject?): String? {
        message ?: return null
        val content = message.optJSONArray("content") ?: return null
        val builder = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "reasoning") {
                val value = block.optString("text")
                if (value.isNotBlank()) {
                    if (builder.isNotEmpty()) builder.append('\n')
                    builder.append(value)
                }
            }
        }
        return builder.toString().ifBlank { null }
    }

    fun displayArguments(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching { JSONObject(raw).toString(2) }.getOrElse { raw }
    }

    fun parseRecords(records: JSONArray?): List<ChatItem> {
        records ?: return emptyList()
        val out = ArrayList<ChatItem>()
        for (i in 0 until records.length()) {
            val record = records.optJSONObject(i) ?: continue
            parseRecord(record)?.let { out.add(it) }
        }
        return out
    }
}
