package com.sharvil.antigo.data.repository

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.android.gms.tasks.Task
import com.sharvil.antigo.domain.model.*
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ChatRepository { fun conversations(): Flow<List<Conversation>>; fun messages(conversationId: String): Flow<List<ChatMessage>> }
interface AiRepository { fun messages(): Flow<List<ChatMessage>> }
interface ProfileRepository { fun user(): Flow<AppUser?> }

data class SignedInUser(val id: String, val email: String?)
sealed interface AuthSession {
    data object SignedOut : AuthSession
    data class SignedIn(val user: SignedInUser) : AuthSession
    data object Unavailable : AuthSession
}

interface AuthRepository {
    val session: Flow<AuthSession>
    suspend fun createAccount(email: String, password: String): Result<Unit>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
}

class FirebaseAuthRepository(context: Context) : AuthRepository {
    private val auth: FirebaseAuth? = runCatching {
        FirebaseApp.initializeApp(context.applicationContext)?.let(FirebaseAuth::getInstance)
    }.getOrNull()

    private val mutableSession = MutableStateFlow(
        auth?.currentUser?.toSession() ?: if (auth == null) AuthSession.Unavailable else AuthSession.SignedOut
    )
    override val session = mutableSession.asStateFlow()

    private val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        mutableSession.value = firebaseAuth.currentUser?.toSession() ?: AuthSession.SignedOut
    }

    init { auth?.addAuthStateListener(listener) }

    override suspend fun createAccount(email: String, password: String): Result<Unit> = authResult {
        val firebaseAuth = requireAuth()
        firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).awaitResult()
        Unit
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = authResult {
        requireAuth().signInWithEmailAndPassword(email.trim(), password).awaitResult()
        Unit
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = authResult {
        requireAuth().sendPasswordResetEmail(email.trim()).awaitResult()
    }

    override suspend fun signOut(): Result<Unit> = authResult {
        requireAuth().signOut()
        Unit
    }

    private fun requireAuth(): FirebaseAuth = auth ?: error("Firebase is not configured for this Android app.")

    private fun FirebaseUser.toSession() = AuthSession.SignedIn(SignedInUser(uid, email))
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) continuation.resumeIfActive(task.result)
        else continuation.resumeExceptionIfActive(task.exception ?: IllegalStateException("Authentication request failed."))
    }
}

private suspend fun <T> authResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}

private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
    if (isActive) resume(value)
}

private fun <T> CancellableContinuation<T>.resumeExceptionIfActive(error: Throwable) {
    if (isActive) resumeWithException(error)
}
