package com.sharvil.antigo

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sharvil.antigo.data.repository.AuthSession
import com.sharvil.antigo.data.repository.SignedInUser
import com.sharvil.antigo.data.remote.AppUpdateChecker
import com.sharvil.antigo.data.remote.AvailableAppUpdate
import com.sharvil.antigo.ui.screens.*
import com.sharvil.antigo.ui.theme.AppTheme
import com.sharvil.antigo.ui.viewmodel.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var availableUpdate by remember { mutableStateOf<AvailableAppUpdate?>(null) }
            LaunchedEffect(Unit) {
                availableUpdate = AppUpdateChecker.check(this@MainActivity)
            }
            val themeViewModel: ProfileViewModel = viewModel()
            val theme by themeViewModel.theme.collectAsState()
            AppTheme(theme = theme) {
                val authViewModel: AuthViewModel = viewModel()
                val authState by authViewModel.uiState.collectAsState()
                when (val session = authState.session) {
                    is AuthSession.SignedIn -> MainNavigation(
                        user = session.user,
                        onSignOut = authViewModel::signOut
                    )
                    AuthSession.SignedOut, AuthSession.Unavailable -> LoginScreen(
                        state = authState,
                        onSignIn = authViewModel::signIn,
                        onGoogleToken = authViewModel::signInWithGoogle,
                        onCreateAccount = authViewModel::createAccount,
                        onResetPassword = authViewModel::sendPasswordReset
                    )
                }
                availableUpdate?.let { update ->
                    AlertDialog(
                        onDismissRequest = { availableUpdate = null },
                        title = { Text("Update available") },
                        text = {
                            Text("AntiGO ${update.version} is ready. Open the download page to install the latest version.")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                availableUpdate = null
                                startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://sharvilmishra.github.io/AntiGO/")
                                    )
                                )
                            }) { Text("Open download page") }
                        },
                        dismissButton = {
                            TextButton(onClick = { availableUpdate = null }) { Text("Later") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainNavigation(user: SignedInUser, onSignOut: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(AppTab.CHATS) }
    var showNewConversation by remember { mutableStateOf(false) }
    var activeConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    val chats: ChatsViewModel = viewModel()
    val chatState by chats.uiState.collectAsState()
    val detail: ChatDetailViewModel = viewModel()
    val detailState by detail.uiState.collectAsState()
    val ai: AiViewModel = viewModel()
    val aiState by ai.uiState.collectAsState()
    val profile: ProfileViewModel = viewModel()
    val profileState by profile.uiState.collectAsState()
    LaunchedEffect(user.id) { profile.setAccount(user.id) }
    LaunchedEffect(user.id, profileState.isLoading, profileState.profile?.username) {
        if (!profileState.isLoading && profileState.profile?.username.isNullOrBlank()) {
            selected = AppTab.PROFILE
        }
    }
    LaunchedEffect(user.id) { chats.setAccount(user.id) }
    LaunchedEffect(chatState.openedConversationId) {
        chatState.openedConversationId?.let {
            activeConversationId = it
            selected = AppTab.CHATS
            chats.clearOpenedConversation()
            showNewConversation = false
        }
    }
    LaunchedEffect(activeConversationId) { activeConversationId?.let(detail::open) }
    BackHandler(enabled = activeConversationId != null || selected != AppTab.CHATS) {
        if (activeConversationId != null) activeConversationId = null else selected = AppTab.CHATS
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surface) { AppTab.entries.forEach { tab ->
        NavigationBarItem(selected == tab, onClick = { selected = tab }, icon = { Text(tab.mark) }, label = { Text(tab.label) })
    } } }, floatingActionButton = { if (selected == AppTab.CHATS && activeConversationId == null) FloatingActionButton(onClick = { showNewConversation = true }) { Text("+") } }) { padding ->
        when (selected) {
            AppTab.CHATS -> if (activeConversationId == null) {
                ChatsScreen(
                    state = chatState,
                    modifier = Modifier.padding(padding),
                    onSearch = chats::search,
                    onNewConversation = { showNewConversation = true },
                    onConversationClick = { activeConversationId = it }
                )
            } else {
                val conversation = chatState.conversations.firstOrNull { it.id == activeConversationId }
                ChatDetailScreen(
                    state = detailState,
                    title = conversation?.title ?: "Conversation",
                    currentUserId = user.id,
                    status = conversation?.status ?: "active",
                    requestedBy = conversation?.requestedBy,
                    modifier = Modifier.padding(padding),
                    onBack = { activeConversationId = null },
                    onSend = detail::send,
                    onMessageRequestDecision = detail::decideMessageRequest
                )
            }
            AppTab.AI -> AiScreen(aiState, Modifier.padding(padding))
            AppTab.PROFILE -> ProfileScreen(
                state = profileState,
                vm = profile,
                modifier = Modifier.padding(padding),
                userId = user.id,
                email = user.email,
                authDisplayName = user.displayName,
                onSignOut = onSignOut
            )
        }
    }
    if (showNewConversation) {
        NewConversationDialog(
            state = chatState,
            onQueryChange = chats::updatePersonQuery,
            onFindPerson = chats::findPerson,
            onStartConversation = chats::startConversation,
            onDismiss = { showNewConversation = false; chats.dismissPersonSearch() }
        )
    }
}

private enum class AppTab(val label: String, val mark: String) { CHATS("Chats", "◉"), AI("AI", "✦"), PROFILE("Profile", "●") }
