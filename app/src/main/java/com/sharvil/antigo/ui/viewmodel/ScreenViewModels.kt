package com.sharvil.antigo.ui.viewmodel

import androidx.lifecycle.ViewModel
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sharvil.antigo.data.repository.FirebaseDirectoryRepository
import com.sharvil.antigo.domain.model.ChatMessage
import com.sharvil.antigo.domain.model.Conversation
import com.sharvil.antigo.domain.model.UserProfile
import com.sharvil.antigo.data.repository.*
import com.sharvil.antigo.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ChatsUiState(
    val conversations: List<Conversation> = emptyList(),
    val query: String = "",
    val personQuery: String = "",
    val matchedPerson: UserProfile? = null,
    val isSearchingPerson: Boolean = false,
    val isStartingConversation: Boolean = false,
    val dialogMessage: String? = null,
    val dialogError: Boolean = false,
    val openedConversationId: String? = null
)
data class ChatDetailUiState(
    val conversationId: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val sendSuccessCount: Int = 0,
    val error: String? = null
)
data class AiUiState(val messages: List<ChatMessage> = emptyList())
data class ProfileUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = true,
    val isSavingUsername: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)
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

class ChatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseDirectoryRepository()
    private val query = MutableStateFlow("")
    private val mutableUiState = MutableStateFlow(ChatsUiState())
    val uiState = combine(mutableUiState, query) { state, term ->
        state.copy(conversations = filterConversations(state.conversations, term), query = term)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatsUiState())
    private var accountId: String? = null
    private var conversationsJob: kotlinx.coroutines.Job? = null

    fun setAccount(uid: String) {
        if (accountId == uid) return
        accountId = uid
        conversationsJob?.cancel()
        mutableUiState.value = ChatsUiState()
        conversationsJob = viewModelScope.launch {
            repository.observeConversations(uid).catch { error ->
                mutableUiState.update { it.copy(dialogMessage = error.toFriendlyDirectoryMessage(), dialogError = true) }
            }.collect { rows ->
                mutableUiState.update { it.copy(conversations = rows) }
            }
        }
    }

    fun search(value: String) { query.value = value }

    fun updatePersonQuery(value: String) {
        mutableUiState.update { it.copy(personQuery = value, matchedPerson = null, dialogMessage = null, dialogError = false) }
    }

    fun findPerson() {
        val identifier = mutableUiState.value.personQuery
        if (identifier.isBlank() || mutableUiState.value.isSearchingPerson) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSearchingPerson = true, matchedPerson = null, dialogMessage = null) }
            val result = runCatching { repository.findUser(identifier) }
            mutableUiState.update { state ->
                when {
                    result.isFailure -> state.copy(isSearchingPerson = false, dialogMessage = result.exceptionOrNull()?.toFriendlyDirectoryMessage(), dialogError = true)
                    result.getOrNull() == null -> state.copy(isSearchingPerson = false, dialogMessage = "No AntiGO user matched that username or ID.", dialogError = true)
                    result.getOrNull()?.id == accountId -> state.copy(isSearchingPerson = false, dialogMessage = "That's your own account. Search for another user.", dialogError = true)
                    else -> state.copy(isSearchingPerson = false, matchedPerson = result.getOrNull(), dialogMessage = null, dialogError = false)
                }
            }
        }
    }

    fun startConversation() {
        val person = mutableUiState.value.matchedPerson ?: return
        val uid = accountId ?: return
        if (mutableUiState.value.isStartingConversation) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isStartingConversation = true, dialogMessage = null) }
            val result = runCatching { repository.startConversation(uid, person) }
            mutableUiState.update { state ->
                result.fold(
                    onSuccess = { state.copy(isStartingConversation = false, openedConversationId = it, dialogError = false) },
                    onFailure = { state.copy(isStartingConversation = false, dialogMessage = it.toFriendlyDirectoryMessage(), dialogError = true) }
                )
            }
        }
    }

    fun clearOpenedConversation() { mutableUiState.update { it.copy(openedConversationId = null) } }
    fun dismissPersonSearch() { mutableUiState.update { it.copy(personQuery = "", matchedPerson = null, dialogMessage = null, dialogError = false) } }
}

class ChatDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FirebaseDirectoryRepository()
    private val mutableUiState = MutableStateFlow(ChatDetailUiState())
    val uiState = mutableUiState.asStateFlow()
    private var messagesJob: kotlinx.coroutines.Job? = null

    fun open(conversationId: String) {
        if (mutableUiState.value.conversationId == conversationId) return
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            mutableUiState.value = ChatDetailUiState(conversationId)
            repository.observeMessages(conversationId).catch { error ->
                mutableUiState.update { it.copy(error = error.toFriendlyDirectoryMessage()) }
            }.collect { messages ->
                mutableUiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun send(senderId: String, text: String) {
        val conversationId = mutableUiState.value.conversationId
        if (text.isBlank() || mutableUiState.value.isSending) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSending = true, error = null) }
            val result = runCatching { repository.sendMessage(conversationId, senderId, text) }
            mutableUiState.update {
                it.copy(
                    isSending = false,
                    sendSuccessCount = if (result.isSuccess) it.sendSuccessCount + 1 else it.sendSuccessCount,
                    error = result.exceptionOrNull()?.toFriendlyDirectoryMessage()
                )
            }
        }
    }

    fun decideMessageRequest(accept: Boolean) {
        val conversationId = mutableUiState.value.conversationId
        viewModelScope.launch {
            val result = runCatching { repository.decideMessageRequest(conversationId, accept) }
            mutableUiState.update { it.copy(error = result.exceptionOrNull()?.toFriendlyDirectoryMessage()) }
        }
    }
}
class AiViewModel : ViewModel() { val uiState = EmptyAiRepository().messages().map(::AiUiState).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiUiState()) }
class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val directory = FirebaseDirectoryRepository()
    private val mutableUiState = MutableStateFlow(ProfileUiState())
    val uiState = mutableUiState.asStateFlow()
    private var accountId: String? = null
    private var profileJob: kotlinx.coroutines.Job? = null
    val theme = MutableStateFlow(runCatching { ThemeChoice.valueOf(application.getSharedPreferences("preferences", 0).getString("theme", null) ?: "SYSTEM") }.getOrDefault(ThemeChoice.SYSTEM))
    fun setTheme(value: ThemeChoice) {
        getApplication<Application>().getSharedPreferences("preferences", 0).edit().putString("theme", value.name).apply()
        theme.value = value
    }

    fun setAccount(uid: String) {
        if (accountId == uid) return
        accountId = uid
        profileJob?.cancel()
        mutableUiState.value = ProfileUiState(isLoading = true)
        profileJob = viewModelScope.launch {
            directory.observeProfile(uid).catch { error ->
                mutableUiState.update { it.copy(isLoading = false, message = error.toFriendlyDirectoryMessage(), isError = true) }
            }.collect { profile ->
                mutableUiState.update { it.copy(profile = profile, isLoading = false) }
            }
        }
    }

    fun saveUsername(uid: String, requested: String, authDisplayName: String?) {
        if (mutableUiState.value.isSavingUsername) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSavingUsername = true, message = null, isError = false) }
            val result = runCatching { directory.saveUsername(uid, requested, authDisplayName) }
            mutableUiState.update { state ->
                result.fold(
                    onSuccess = { state.copy(profile = it, isSavingUsername = false, message = "Username saved.", isError = false) },
                    onFailure = { state.copy(isSavingUsername = false, message = it.toFriendlyDirectoryMessage(), isError = true) }
                )
            }
        }
    }
}

private fun Throwable?.toFriendlyDirectoryMessage(): String = when (this) {
    is com.sharvil.antigo.data.repository.UsernameAlreadyTakenException -> message ?: "That username is already taken."
    is com.sharvil.antigo.data.repository.UsernameAlreadySetException -> message ?: "Your username is already set."
    is IllegalArgumentException -> message ?: "Check the value and try again."
    is com.google.firebase.firestore.FirebaseFirestoreException -> when (code) {
        com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Firebase denied this action. The IUDEX Firestore rules may need an update."
        com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "Firestore is missing a required index. Deploy the IUDEX Firestore indexes."
        com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
        com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "Firebase is temporarily unavailable. Check your connection and retry."
        com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED ->
            "Your sign-in expired. Sign out and sign in again."
        else -> "Firebase couldn't complete this request (${code.name.lowercase().replace('_', ' ')}). Please retry."
    }
    else -> "Couldn't reach AntiGO right now. Check your connection and try again."
}
