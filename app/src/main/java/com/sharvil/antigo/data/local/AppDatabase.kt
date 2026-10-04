package com.sharvil.antigo.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(@PrimaryKey val id: String, val title: String, val avatarUrl: String?, val updatedAt: Long)

@Entity(tableName = "messages", primaryKeys = ["id", "conversationId"])
data class MessageEntity(val id: String, val conversationId: String, val text: String, val createdAt: Long)

@Dao interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC") fun observeAll(): Flow<List<ConversationEntity>>
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt") fun observeMessages(conversationId: String): Flow<List<MessageEntity>>
}

@Database(entities = [ConversationEntity::class, MessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() { abstract fun conversationDao(): ConversationDao }
