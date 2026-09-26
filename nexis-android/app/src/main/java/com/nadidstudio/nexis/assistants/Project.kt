package com.nadidstudio.nexis.assistants

import androidx.compose.runtime.mutableStateListOf

/** LOCAL is the fully offline, on-device assistant — kept separate from
 *  CODING/CHAT on purpose so it never mixes with API-key-based models. */
enum class AssistantRole {
    CODING,
    CHAT,
    LOCAL
}

data class ChatMessage(
    val role: String, // "user" or "assistant"
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * A conversation is isolated from every other conversation, even within
 * the same project — same principle as Claude Projects.
 */
data class Conversation(
    val id: String,
    val projectId: String,
    val messages: MutableList<ChatMessage> = mutableListOf()
)

/**
 * A project groups conversations that share the same uploaded files.
 * Files uploaded here stay available to any new conversation opened
 * inside this project, without re-uploading.
 */
data class Project(
    val id: String,
    val name: String,
    val assistantRole: AssistantRole,
    val uploadedFilePaths: MutableList<String> = mutableStateListOf(),
    val conversations: MutableList<Conversation> = mutableListOf()
)
