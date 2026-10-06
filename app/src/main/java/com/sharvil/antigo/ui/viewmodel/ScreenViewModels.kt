package com.sharvil.antigo.ui.viewmodel

import androidx.lifecycle.ViewModel
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sharvil.antigo.data.repository.*
import com.sharvil.antigo.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatsUiState(val conversations: List<Conversation> = emptyList(), val query: String = "")
data class ChatDetailUiState(val conversationId: String = "", val messages: List<ChatMessage> = emptyList())
data class AiUiState(val messages: List<ChatMessage> = emptyList())
data class ProfileUiState(val user: AppUser? = null)
data class AuthUiState(
    val session: AuthSession = AuthSession.SignedOut,
    val isBusy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseAuthRepository(application)
    private val mutableUiState = MutableStateFlow(AuthUiState(session = repository.session.value))
    val uiState = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                mutableUiState.update { it.copy(session = session) }
            }
        }
    }

    fun createAccount(email: String, password: String) = runOperation("Account created.") {
        repository.createAccount(email, password)
    }

    fun signIn(email: String, password: String) = runOperation(null) {
        repository.signIn(email, password)
    }

    fun signInWithGoogle(idToken: String) = runOperation(null) {
        repository.signInWithGoogle(idToken)
    }

    fun sendPasswordReset(email: String) = runOperation("If an account exists for this email, a reset link has been sent.") {
        repository.sendPasswordReset(email)
    }

    fun signOut() = runOperation("Signed out.") { repository.signOut() }

    private fun runOperation(successMessage: String?, operation: suspend () -> Result<Unit>) {
        if (mutableUiState.value.isBusy) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isBusy = true, message = null, isError = false) }
            val result = operation()
            mutableUiState.update {
                it.copy(
                    isBusy = false,
                    message = result.fold({ successMessage }, { it.toUserMessage() }),
                    isError = result.isFailure
                )
            }
        }
    }

    private fun Throwable.toUserMessage(): String {
        val code = (this as? com.google.firebase.auth.FirebaseAuthException)?.errorCode?.lowercase()
        return when {
            message?.contains("not configured", ignoreCase = true) == true -> "Firebase is not configured yet. Add the app's Firebase configuration to enable sign-in."
            code?.contains("email_already_in_use") == true -> "An account already exists for this email. Try signing in."
            code?.contains("weak_password") == true -> "Choose a stronger password."
            code?.contains("invalid_email") == true -> "Enter a valid email address."
            code?.contains("invalid_credential") == true || code?.contains("wrong_password") == true || code?.contains("user_not_found") == true -> "Email or password is incorrect."
            code?.contains("network_request_failed") == true -> "Check your internet connection and try again."
            else -> "Authentication couldn't be completed. Please try again."
        }
    }
}

fun filterConversations(conversations: List<Conversation>, query: String): List<Conversation> =
    conversations.filter { it.title.contains(query, ignoreCase = true) }

class ChatsViewModel : ViewModel() {
    private val query = MutableStateFlow("")
    val uiState = combine(EmptyChatRepository().conversations(), query) { rows, term -> ChatsUiState(filterConversations(rows, term), term) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatsUiState())
    fun search(value: String) { query.value = value }
}
class ChatDetailViewModel(conversationId: String = "") : ViewModel() {
    val uiState = EmptyChatRepository().messages(conversationId).map { ChatDetailUiState(conversationId, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatDetailUiState(conversationId))
}
class AiViewModel : ViewModel() { val uiState = EmptyAiRepository().messages().map(::AiUiState).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiUiState()) }
class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    val uiState = EmptyProfileRepository().user().map(::ProfileUiState).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())
    val theme = MutableStateFlow(runCatching { ThemeChoice.valueOf(application.getSharedPreferences("preferences", 0).getString("theme", null) ?: "SYSTEM") }.getOrDefault(ThemeChoice.SYSTEM))
    fun setTheme(value: ThemeChoice) {
        getApplication<Application>().getSharedPreferences("preferences", 0).edit().putString("theme", value.name).apply()
        theme.value = value
    }
}
