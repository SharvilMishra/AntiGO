package com.sharvil.antigo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.ClipboardManager
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.sharvil.antigo.data.repository.AuthSession
import com.sharvil.antigo.domain.model.ChatMessage
import com.sharvil.antigo.domain.model.ThemeChoice
import com.sharvil.antigo.ui.viewmodel.*
import com.sharvil.antigo.domain.model.Conversation
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch

private enum class AuthFormMode { SIGN_IN, CREATE_ACCOUNT, RESET_PASSWORD }
private val BrandYellow = Color(0xFFFFD60A)

@Composable
fun AntiGoBrand(modifier: Modifier = Modifier, darkMark: Boolean = false) {
    val ink = if (darkMark) Color(0xFF09090B) else MaterialTheme.colorScheme.onBackground
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(40.dp).background(Color(0xFF09090B), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Text("A", color = BrandYellow, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(4.dp).background(BrandYellow, CircleShape))
        }
        Text("Anti", color = ink, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
        Text("GO", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun LoginScreen(
    state: AuthUiState,
    onSignIn: (String, String) -> Unit,
    onCreateAccount: (String, String) -> Unit,
    onResetPassword: (String) -> Unit,
    onGoogleToken: (String) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(AuthFormMode.SIGN_IN) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var googleError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unavailable = state.session is AuthSession.Unavailable
    val busy = state.isBusy
    val title = when (mode) {
        AuthFormMode.SIGN_IN -> "Welcome back"
        AuthFormMode.CREATE_ACCOUNT -> "Create your account"
        AuthFormMode.RESET_PASSWORD -> "Reset password"
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(horizontal = 26.dp).verticalScroll(rememberScrollState()).imePadding(), horizontalAlignment = Alignment.Start) {
            AntiGoBrand()
            Spacer(Modifier.height(40.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Sign in to continue to AntiGO.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(26.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") }, singleLine = true, enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = authFieldColors()
            )
            if (mode != AuthFormMode.RESET_PASSWORD) {
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") }, singleLine = true, enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = authFieldColors()
                )
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    when (mode) {
                        AuthFormMode.SIGN_IN -> onSignIn(email.trim(), password)
                        AuthFormMode.CREATE_ACCOUNT -> onCreateAccount(email.trim(), password)
                        AuthFormMode.RESET_PASSWORD -> onResetPassword(email.trim())
                    }
                },
                enabled = !busy && !unavailable && email.isNotBlank() && (mode == AuthFormMode.RESET_PASSWORD || password.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(when (mode) { AuthFormMode.SIGN_IN -> "Sign in"; AuthFormMode.CREATE_ACCOUNT -> "Create account"; AuthFormMode.RESET_PASSWORD -> "Send reset link" })
            }
            if (mode == AuthFormMode.SIGN_IN) {
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = {
                        googleError = null
                        scope.launch {
                            try {
                                val clientId = runCatching { context.getString(com.sharvil.antigo.R.string.default_web_client_id) }
                                    .getOrNull()
                                if (clientId.isNullOrBlank()) error("Google sign-in isn't configured. Add the updated Firebase app configuration.")
                                val googleOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(clientId)
                                    .setAutoSelectEnabled(false)
                                    .build()
                                val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()
                                val result = CredentialManager.create(context).getCredential(context, request)
                                val credential = result.credential
                                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                    onGoogleToken(GoogleIdTokenCredential.createFrom(credential.data).idToken)
                                } else error("Google sign-in didn't return a valid account. Please try again.")
                            } catch (error: GetCredentialException) {
                                googleError = if (error.message?.contains("cancel", true) == true) null else "Google sign-in couldn't be completed. Please try again."
                            } catch (error: Exception) {
                                googleError = error.message ?: "Google sign-in couldn't be completed. Please try again."
                            }
                        }
                    },
                    enabled = !busy && !unavailable,
                    modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)
                ) { Text("Continue with Google", fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = !busy, onClick = { mode = AuthFormMode.CREATE_ACCOUNT }) { Text("Create an account") }
                    TextButton(enabled = !busy, onClick = { mode = AuthFormMode.RESET_PASSWORD }) { Text("Forgot password?") }
                }
            } else {
                TextButton(enabled = !busy, onClick = { mode = AuthFormMode.SIGN_IN }) { Text("Back to sign in") }
            }
            if (unavailable) Text("Firebase is not configured. Add the Firebase configuration for this Android app.", color = MaterialTheme.colorScheme.error)
            googleError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            state.message?.let { Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
fun ChatsScreen(
    state: ChatsUiState,
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit,
    onNewConversation: () -> Unit = {},
    onConversationClick: (String) -> Unit = {}
) {
    Column(modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        AntiGoBrand()
        Spacer(Modifier.height(26.dp))
        Text("CHATS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = state.query, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(),
            label = { Text("Search conversations") }, singleLine = true, colors = authFieldColors(),
            shape = RoundedCornerShape(18.dp)
        )
        if (state.conversations.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No conversations yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Find someone by username or AntiGO ID to start chatting.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = onNewConversation) { Text("Start a conversation") }
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(state.conversations, key = Conversation::id) { conversation ->
                ListItem(
                    headlineContent = { Text(conversation.title, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(when {
                            conversation.status == "pending" && conversation.requestedBy != null -> "Message request"
                            conversation.status == "declined" -> "Request declined"
                            conversation.preview.isNotBlank() -> conversation.preview
                            else -> "Open conversation"
                        })
                    },
                    leadingContent = {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                                Text(conversation.title.removePrefix("@").take(1).uppercase(), fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    modifier = Modifier.clickable { onConversationClick(conversation.id) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun NewConversationDialog(
    state: ChatsUiState,
    onQueryChange: (String) -> Unit,
    onFindPerson: () -> Unit,
    onStartConversation: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a chat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Search by username or full AntiGO ID.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = state.personQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("@username or AntiGO ID") },
                    singleLine = true,
                    enabled = !state.isSearchingPerson && !state.isStartingConversation,
                    colors = authFieldColors()
                )
                OutlinedButton(
                    onClick = onFindPerson,
                    enabled = state.personQuery.isNotBlank() && !state.isSearchingPerson && !state.isStartingConversation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isSearchingPerson) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Find user")
                }
                state.matchedPerson?.let { person ->
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(person.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("@${person.username}", color = MaterialTheme.colorScheme.primary)
                            Text("AntiGO ID · ${person.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                state.dialogMessage?.let {
                    Text(it, color = if (state.dialogError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            if (state.matchedPerson != null) TextButton(
                enabled = !state.isStartingConversation,
                onClick = onStartConversation
            ) {
                if (state.isStartingConversation) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Start chat")
            }
        },
        dismissButton = { TextButton(enabled = !state.isStartingConversation, onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AiScreen(state: AiUiState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        AntiGoBrand()
        Spacer(Modifier.height(26.dp))
        Text("AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (state.messages.isEmpty()) Text("Your AI conversations will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ChatDetailScreen(
    state: ChatDetailUiState,
    title: String,
    currentUserId: String,
    status: String = "active",
    requestedBy: String? = null,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSend: (String, String) -> Unit,
    onMessageRequestDecision: (Boolean) -> Unit = {}
) {
    var draft by rememberSaveable(state.conversationId) { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    Column(modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹  Chats") }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (status == "pending") {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (requestedBy == currentUserId) "Message request sent. Waiting for a response."
                        else "This user wants to start a conversation."
                    )
                    if (requestedBy != currentUserId) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onMessageRequestDecision(false) }) { Text("Decline") }
                            Button(onClick = { onMessageRequestDecision(true) }) { Text("Accept") }
                        }
                    }
                }
            }
        } else if (status == "declined") {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("This message request was declined.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (state.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Say hello to start the conversation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.messages, key = ChatMessage::id) { message ->
                val mine = message.senderId == currentUserId
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) { Text(message.text, Modifier.widthIn(max = 280.dp).padding(horizontal = 14.dp, vertical = 10.dp)) }
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (status == "active") Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                shape = RoundedCornerShape(22.dp),
                enabled = !state.isSending,
                maxLines = 4
            )
            TextButton(
                enabled = draft.isNotBlank() && !state.isSending,
                onClick = { onSend(currentUserId, draft); draft = "" }
            ) { Text(if (state.isSending) "…" else "Send") }
        }
    }
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    vm: ProfileViewModel,
    modifier: Modifier = Modifier,
    userId: String,
    email: String?,
    authDisplayName: String?,
    onSignOut: () -> Unit = {}
) {
    var username by rememberSaveable { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(state.profile?.username) { username = state.profile?.username.orEmpty() }
    Column(modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        AntiGoBrand()
        Spacer(Modifier.height(26.dp))
        Text("Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.profile?.displayName ?: authDisplayName ?: "Your AntiGO account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                email?.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Your unique AntiGO ID", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(userId, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("AntiGO ID", userId))
                        copied = true
                    }) { Text(if (copied) "Copied" else "Copy ID") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Username", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.removePrefix("@") },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Choose a unique username") },
            prefix = { Text("@") },
            singleLine = true,
            enabled = !state.isLoading && !state.isSavingUsername && state.profile?.username.isNullOrBlank(),
            supportingText = {
                Text(if (state.profile?.username.isNullOrBlank()) "3–20 characters · letters, numbers, and underscore" else "Your e-CON username is permanent.")
            },
            colors = authFieldColors()
        )
        Button(
            onClick = { vm.saveUsername(userId, username, authDisplayName) },
            enabled = username.isNotBlank() && !state.isLoading && !state.isSavingUsername && state.profile?.username.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (state.isSavingUsername) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Claim username")
        }
        state.message?.let { Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(24.dp))
        Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        ThemeChoice.entries.forEach { choice ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = vm.theme.value == choice, onClick = { vm.setTheme(choice) })
                Text(choice.name.lowercase().replaceFirstChar(Char::uppercase))
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onSignOut, shape = RoundedCornerShape(16.dp)) { Text("Sign out") }
    }
}
