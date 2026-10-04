package com.sharvil.antigo.domain.model

data class Conversation(val id: String, val title: String, val avatarUrl: String? = null, val updatedAt: Long = 0L)
data class ChatMessage(val id: String, val conversationId: String, val text: String, val createdAt: Long)
data class AppUser(val id: String, val name: String, val avatarUrl: String? = null)
enum class ThemeChoice { SYSTEM, LIGHT, DARK }
