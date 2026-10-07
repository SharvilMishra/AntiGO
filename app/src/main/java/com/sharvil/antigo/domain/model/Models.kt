package com.sharvil.antigo.domain.model

data class Conversation(
    val id: String,
    val title: String,
    val avatarUrl: String? = null,
    val updatedAt: Long = 0L,
    val preview: String = "",
    val status: String = "active",
    val requestedBy: String? = null
)
data class ChatMessage(val id: String, val conversationId: String, val text: String, val createdAt: Long, val senderId: String = "")
data class AppUser(val id: String, val name: String, val avatarUrl: String? = null)
data class UserProfile(val id: String, val username: String, val displayName: String)
enum class ThemeChoice { SYSTEM, LIGHT, DARK }
