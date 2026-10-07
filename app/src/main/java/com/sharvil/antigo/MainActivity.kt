package com.sharvil.antigo

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sharvil.antigo.data.repository.AuthSession
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
                        email = session.user.email,
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
private fun MainNavigation(email: String?, onSignOut: () -> Unit) {
    var selected by remember { mutableStateOf(AppTab.CHATS) }
    var showNewConversation by remember { mutableStateOf(false) }
    val chats: ChatsViewModel = viewModel()
    val chatState by chats.uiState.collectAsState()
    val ai: AiViewModel = viewModel()
    val aiState by ai.uiState.collectAsState()
    val profile: ProfileViewModel = viewModel()
    val profileState by profile.uiState.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surface) { AppTab.entries.forEach { tab ->
        NavigationBarItem(selected == tab, onClick = { selected = tab }, icon = { Text(tab.mark) }, label = { Text(tab.label) })
    } } }, floatingActionButton = { if (selected == AppTab.CHATS) FloatingActionButton(onClick = { showNewConversation = true }) { Text("+") } }) { padding ->
        when (selected) {
            AppTab.CHATS -> ChatsScreen(chatState, Modifier.padding(padding), onSearch = chats::search, onNewConversation = { showNewConversation = true })
            AppTab.AI -> AiScreen(aiState, Modifier.padding(padding))
            AppTab.PROFILE -> ProfileScreen(profileState, profile, Modifier.padding(padding), email, onSignOut)
        }
    }
    if (showNewConversation) AlertDialog(onDismissRequest = { showNewConversation = false }, title = { Text("New conversation") }, text = { Text("No conversations yet") }, confirmButton = { TextButton(onClick = { showNewConversation = false }) { Text("Close") } })
}

private enum class AppTab(val label: String, val mark: String) { CHATS("Chats", "◉"), AI("AI", "✦"), PROFILE("Profile", "●") }
