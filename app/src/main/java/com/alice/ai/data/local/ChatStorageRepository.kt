package com.alice.ai.data.local

import java.util.UUID

data class ChatSessionSummary(
    val id: String,
    val title: String,
    val createdAt: Long
)

class ChatStorageRepository(
    private val chatDao: ChatDao
) {
    suspend fun createSession(title: String = "New Chat"): ChatSessionEntity {
        val session = ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = System.currentTimeMillis()
        )
        chatDao.insertSession(session)
        return session
    }

    suspend fun ensureSession(sessionId: String?): ChatSessionEntity {
        if (!sessionId.isNullOrBlank()) {
            val existing = chatDao.getSession(sessionId)
            if (existing != null) {
                return existing
            }
        }

        val latest = chatDao.listSessions().firstOrNull()
        return latest ?: createSession()
    }

    suspend fun listSessions(): List<ChatSessionSummary> {
        return chatDao.listSessions().map { session ->
            ChatSessionSummary(
                id = session.id,
                title = session.title,
                createdAt = session.createdAt
            )
        }
    }

    suspend fun getMessages(sessionId: String): List<MessageEntity> {
        return chatDao.getMessagesBySession(sessionId)
    }

    suspend fun saveMessage(
        sessionId: String,
        messageId: String = UUID.randomUUID().toString(),
        role: String,
        content: String,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        chatDao.insertMessage(
            MessageEntity(
                id = messageId,
                sessionId = sessionId,
                role = role,
                content = content,
                timestamp = timestamp
            )
        )
        return messageId
    }

    suspend fun deleteMessage(messageId: String) {
        chatDao.deleteMessageById(messageId)
    }

    suspend fun updateSessionTitleFromMessage(sessionId: String, content: String) {
        val existingCount = chatDao.messageCount(sessionId)
        if (existingCount > 1) {
            return
        }

        val title = content
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .trim()
            .ifBlank { "New Chat" }
            .take(60)
        chatDao.updateSessionTitle(sessionId, title)
    }

    suspend fun deleteSession(sessionId: String) {
        chatDao.deleteSession(sessionId)
    }
}
