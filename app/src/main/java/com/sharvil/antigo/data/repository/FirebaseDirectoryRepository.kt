package com.sharvil.antigo.data.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.sharvil.antigo.domain.model.ChatMessage
import com.sharvil.antigo.domain.model.Conversation
import com.sharvil.antigo.domain.model.UserProfile
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class UsernameAlreadyTakenException : IllegalArgumentException("That username is already taken.")
class UsernameAlreadySetException : IllegalArgumentException("Your username is already set and can't be changed.")

/** Uses the existing IUDEX/e-CON Firestore collections and document shape. */
class FirebaseDirectoryRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = db.collection(USERS).document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) close(error)
            else trySend(snapshot?.toUserProfile())
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveUsername(uid: String, requested: String, authDisplayName: String?): UserProfile {
        val username = normalizeUsername(requested)
        require(username.matches(USERNAME_PATTERN)) {
            "Use 3–20 letters, numbers, or underscores. Start with a letter."
        }
        require(username !in RESERVED_USERNAMES) { "That username is reserved." }

        val userRef = db.collection(USERS).document(uid)
        val usernameRef = db.collection(USERNAMES).document(username)
        val userSnapshot = userRef.get().awaitResult()
        val existingUsername = userSnapshot.getString("username")
        if (!existingUsername.isNullOrBlank()) {
            if (existingUsername != username) throw UsernameAlreadySetException()
            syncPublicProfile(userSnapshot, username)
            return userSnapshot.toUserProfile() ?: UserProfile(
                uid, username, authDisplayName?.takeIf(String::isNotBlank) ?: username
            )
        }

        db.runTransaction { transaction ->
            val reservation = transaction.get(usernameRef)
            val currentProfile = transaction.get(userRef)
            val currentUsername = currentProfile.getString("username")
            if (!currentUsername.isNullOrBlank() && currentUsername != username) {
                throw UsernameAlreadySetException()
            }
            if (reservation.exists() && reservation.getString("uid") != uid) {
                throw UsernameAlreadyTakenException()
            }
            if (!reservation.exists()) {
                transaction.set(usernameRef, mapOf(
                    "uid" to uid,
                    "username" to username,
                    "claimedAt" to FieldValue.serverTimestamp()
                ))
            }
            val name = currentProfile.getString("name")?.takeIf(String::isNotBlank)
                ?: authDisplayName?.takeIf(String::isNotBlank)
                ?: username
            val profileData = mutableMapOf<String, Any>(
                "uid" to uid,
                "username" to username,
                "usernameSetAt" to FieldValue.serverTimestamp(),
                "name" to name
            )
            FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()?.takeIf(String::isNotBlank)
                ?.let { profileData["photoURL"] = it }
            if (!currentProfile.exists()) {
                FirebaseAuth.getInstance().currentUser?.email?.let { profileData["email"] = it }
                profileData["bio"] = ""
                profileData["joinedAt"] = FieldValue.serverTimestamp()
            }
            transaction.set(userRef, profileData, SetOptions.merge())
            UserProfile(uid, username, name)
        }.awaitResult()

        val saved = userRef.get().awaitResult()
        syncPublicProfile(saved, username)
        return saved.toUserProfile() ?: UserProfile(
            uid, username, authDisplayName?.takeIf(String::isNotBlank) ?: username
        )
    }

    suspend fun findUser(identifier: String): UserProfile? {
        val query = identifier.trim()
        if (query.isBlank()) return null

        val uid = if (query.startsWith('@') || query.matches(USERNAME_PATTERN)) {
            val username = normalizeUsername(query)
            db.collection(USERNAMES).document(username).get().awaitResult().getString("uid") ?: return null
        } else query

        // e-CON exposes a small public profile projection for discovery.
        val publicProfile = db.collection(PUBLIC_PROFILES).document(uid).get().awaitResult()
        if (publicProfile.exists()) return publicProfile.toUserProfile(uid)
        // Older accounts can predate the public projection; their profile is
        // still readable to signed-in users under the existing IUDEX rules.
        return db.collection(USERS).document(uid).get().awaitResult().toUserProfile()
    }

    fun observeConversations(uid: String): Flow<List<Conversation>> = callbackFlow {
        val registration = db.collection(CONVERSATIONS)
            .whereArrayContains("participants", uid)
            .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error)
                else trySend(snapshot.toConversations(uid))
            }
        awaitClose { registration.remove() }
    }

    suspend fun startConversation(currentUid: String, other: UserProfile): String {
        require(currentUid != other.id) { "You can't start a conversation with yourself." }
        val participants = listOf(currentUid, other.id).sorted()
        val conversationId = participants.joinToString("_")
        val conversationRef = db.collection(CONVERSATIONS).document(conversationId)
        val myProfileRef = db.collection(USERS).document(currentUid)
        val otherProfileRef = db.collection(USERS).document(other.id)

        db.runTransaction { transaction ->
            val existing = transaction.get(conversationRef)
            val myProfile = transaction.get(myProfileRef)
            val recipient = transaction.get(otherProfileRef)
            val myUsername = myProfile.getString("username")
                ?: throw IllegalStateException("Choose a username in Profile before starting a conversation.")
            val recipientIsPrivate = recipient.getBoolean("private") == true
            val participantInfo = mapOf(
                currentUid to publicInfo(myProfile, myUsername),
                other.id to publicInfo(recipient, other.username)
            )
            if (!existing.exists()) {
                val data = mutableMapOf<String, Any?>(
                    "participants" to participants,
                    "participantInfo" to participantInfo,
                    "lastMessage" to null,
                    "status" to if (recipientIsPrivate) "pending" else "active",
                    "requestedBy" to if (recipientIsPrivate) currentUid else null,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                transaction.set(conversationRef, data)
            } else {
                transaction.update(conversationRef, mapOf(
                    "participantInfo.$currentUid" to publicInfo(myProfile, myUsername),
                    "participantInfo.${other.id}" to publicInfo(recipient, other.username)
                ))
            }
            conversationId
        }.awaitResult()
        return conversationId
    }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = callbackFlow {
        val registration = db.collection(CONVERSATIONS).document(conversationId)
            .collection(MESSAGES).orderBy("timestamp")
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error)
                else trySend(snapshot.toMessages(conversationId))
            }
        awaitClose { registration.remove() }
    }

    suspend fun sendMessage(conversationId: String, senderId: String, text: String) {
        val content = text.trim()
        require(content.isNotEmpty()) { "Write a message first." }
        val conversation = db.collection(CONVERSATIONS).document(conversationId)
        val batch = db.batch()
        batch.set(conversation.collection(MESSAGES).document(), mapOf(
            "senderId" to senderId,
            "text" to content,
            "image" to null,
            "replyTo" to null,
            "reactions" to emptyMap<String, String>(),
            "timestamp" to FieldValue.serverTimestamp()
        ))
        batch.update(conversation, mapOf(
            "lastMessage" to mapOf(
                "text" to content,
                "senderId" to senderId,
                "at" to FieldValue.serverTimestamp()
            ),
            "updatedAt" to FieldValue.serverTimestamp()
        ))
        batch.commit().awaitResult()
    }

    suspend fun decideMessageRequest(conversationId: String, accept: Boolean) {
        db.collection(CONVERSATIONS).document(conversationId).update(mapOf(
            "status" to if (accept) "active" else "declined",
            "updatedAt" to FieldValue.serverTimestamp()
        )).awaitResult()
    }

    private suspend fun syncPublicProfile(user: DocumentSnapshot, username: String) {
        val name = user.getString("name")?.takeIf(String::isNotBlank) ?: username
        val photoUrl = user.getString("photoURL").orEmpty()
        db.collection(PUBLIC_PROFILES).document(user.id).set(mapOf(
            "username" to username,
            "name" to name,
            "photoURL" to photoUrl,
            "searchTerms" to usernameSearchTerms(username)
        )).awaitResult()
    }

    private fun publicInfo(profile: DocumentSnapshot, username: String): Map<String, String> = mapOf(
        "username" to username,
        "name" to (profile.getString("name")?.takeIf(String::isNotBlank) ?: username),
        "photoURL" to profile.getString("photoURL").orEmpty()
    )

    private fun normalizeUsername(value: String) = value.trim().removePrefix("@").lowercase()

    private fun usernameSearchTerms(username: String): List<String> = buildSet {
        for (start in username.indices) {
            for (end in start + 1..username.length) add(username.substring(start, end))
        }
    }.toList()

    private fun DocumentSnapshot.toUserProfile(): UserProfile? = toUserProfile(id)

    private fun DocumentSnapshot.toUserProfile(profileId: String): UserProfile? {
        if (!exists()) return null
        val username = getString("username")?.takeIf(String::isNotBlank) ?: return null
        return UserProfile(
            id = profileId,
            username = username,
            displayName = getString("name")?.takeIf(String::isNotBlank)
                ?: getString("displayName")?.takeIf(String::isNotBlank)
                ?: username
        )
    }

    private fun QuerySnapshot?.toConversations(currentUid: String): List<Conversation> =
        this?.documents.orEmpty().mapNotNull { document ->
            val participants = document.get("participants") as? List<*> ?: return@mapNotNull null
            val peerUid = participants.filterIsInstance<String>().firstOrNull { it != currentUid }
                ?: return@mapNotNull null
            val participantInfo = document.get("participantInfo") as? Map<*, *>
            val peerInfo = participantInfo?.get(peerUid) as? Map<*, *>
            val username = peerInfo?.get("username") as? String
            val name = peerInfo?.get("name") as? String
            val title = username?.takeIf(String::isNotBlank)?.let { "@$it" }
                ?: name?.takeIf(String::isNotBlank)
                ?: "AntiGO user"
            val photo = peerInfo?.get("photoURL") as? String
            val updatedAt = document.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
            val lastMessage = document.get("lastMessage") as? Map<*, *>
            val preview = lastMessage?.get("text") as? String ?: ""
            Conversation(
                id = document.id,
                title = title,
                avatarUrl = photo,
                updatedAt = updatedAt,
                preview = preview,
                status = document.getString("status") ?: "active",
                requestedBy = document.getString("requestedBy")
            )
        }.sortedByDescending(Conversation::updatedAt)

    private fun QuerySnapshot?.toMessages(conversationId: String): List<ChatMessage> =
        this?.documents.orEmpty().mapNotNull { document ->
            val text = document.getString("text").orEmpty()
            if (text.isBlank() && document.getString("image").isNullOrBlank()) return@mapNotNull null
            ChatMessage(
                id = document.id,
                conversationId = conversationId,
                text = text.ifBlank { "📷 Photo" },
                createdAt = document.getTimestamp("timestamp")?.toDate()?.time ?: 0L,
                senderId = document.getString("senderId").orEmpty()
            )
        }

    private companion object {
        const val USERS = "users"
        const val PUBLIC_PROFILES = "publicProfiles"
        const val USERNAMES = "usernames"
        const val CONVERSATIONS = "conversations"
        const val MESSAGES = "messages"
        val USERNAME_PATTERN = Regex("[a-z][a-z0-9_]{2,19}")
        val RESERVED_USERNAMES = setOf(
            "econ", "admin", "administrator", "root", "system", "support", "help",
            "about", "settings", "profile", "me", "home", "chat", "chats", "discover",
            "search", "login", "logout", "signin", "signup", "register", "api",
            "official", "staff", "team", "moderator", "mod", "null", "undefined"
        )
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) continuation.resumeIfActive(task.result)
        else continuation.resumeExceptionIfActive(task.exception ?: IllegalStateException("Firebase request failed."))
    }
}

private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
    if (isActive) resume(value)
}

private fun <T> CancellableContinuation<T>.resumeExceptionIfActive(error: Throwable) {
    if (isActive) resumeWithException(error)
}
