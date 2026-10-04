package com.sharvil.antigo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import com.sharvil.antigo.domain.model.ThemeChoice
import com.sharvil.antigo.data.repository.AuthSession
import com.sharvil.antigo.ui.viewmodel.*

private enum class AuthFormMode { SIGN_IN, CREATE_ACCOUNT, RESET_PASSWORD }

@Composable
fun LoginScreen(
    state: AuthUiState,
    onSignIn: (String, String) -> Unit,
    onCreateAccount: (String, String) -> Unit,
    onResetPassword: (String) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(AuthFormMode.SIGN_IN) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val unavailable = state.session is AuthSession.Unavailable
    val busy = state.isBusy

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (mode) {
                    AuthFormMode.SIGN_IN -> "Sign in"
                    AuthFormMode.CREATE_ACCOUNT -> "Create account"
                    AuthFormMode.RESET_PASSWORD -> "Reset password"
                },
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            if (mode != AuthFormMode.RESET_PASSWORD) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    when (mode) {
                        AuthFormMode.SIGN_IN -> onSignIn(email.trim(), password)
                        AuthFormMode.CREATE_ACCOUNT -> onCreateAccount(email.trim(), password)
                        AuthFormMode.RESET_PASSWORD -> onResetPassword(email.trim())
                    }
                },
                enabled = !busy && !unavailable && email.isNotBlank() &&
                    (mode == AuthFormMode.RESET_PASSWORD || password.isNotBlank()),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(when (mode) {
                    AuthFormMode.SIGN_IN -> "Sign in"
                    AuthFormMode.CREATE_ACCOUNT -> "Create account"
                    AuthFormMode.RESET_PASSWORD -> "Send reset link"
                })
            }
            if (mode == AuthFormMode.SIGN_IN) {
                TextButton(enabled = !busy, onClick = { mode = AuthFormMode.CREATE_ACCOUNT; }) { Text("Create an account") }
                TextButton(enabled = !busy, onClick = { mode = AuthFormMode.RESET_PASSWORD }) { Text("Forgot password?") }
            } else {
                TextButton(enabled = !busy, onClick = { mode = AuthFormMode.SIGN_IN }) { Text("Back to sign in") }
            }
            if (unavailable) {
                Text("Firebase is not configured. Add app/google-services.json for this Firebase Android app.", color = MaterialTheme.colorScheme.error)
            }
            state.message?.let { Text(it, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        }
    }
}
@Composable fun ChatsScreen(state: ChatsUiState, modifier: Modifier = Modifier, onSearch: (String) -> Unit, onNewConversation: () -> Unit = {}) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Chats", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(state.query, onSearch, Modifier.fillMaxWidth(), label = { Text("Search") }, singleLine = true)
        if (state.conversations.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("No conversations yet"); TextButton(onClick = onNewConversation) { Text("New conversation") } } }
        else LazyColumn { items(state.conversations.size) { index -> Text(state.conversations[index].title, Modifier.padding(12.dp)) } }
    }
}
@Composable fun AiScreen(state: AiUiState, modifier: Modifier = Modifier) { Column(modifier.fillMaxSize().padding(20.dp)) { Text("AI", style = MaterialTheme.typography.headlineMedium); if (state.messages.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Your messages will appear here") } } }
@Composable fun ChatDetailScreen(state: ChatDetailUiState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Conversation", style = MaterialTheme.typography.headlineMedium)
        if (state.messages.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No messages yet") }
    }
}
@Composable fun ProfileScreen(
    state: ProfileUiState,
    vm: ProfileViewModel,
    modifier: Modifier = Modifier,
    email: String? = null,
    onSignOut: () -> Unit = {}
) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium)
        state.user?.name?.takeIf(String::isNotBlank)?.let { Text(it) }
        email?.takeIf(String::isNotBlank)?.let { Text(it) }
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        ThemeChoice.entries.forEach { choice -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(vm.theme.value == choice, onClick = { vm.setTheme(choice) }); Text(choice.name.lowercase().replaceFirstChar(Char::uppercase)) } }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onSignOut) { Text("Sign out") }
    }
}
