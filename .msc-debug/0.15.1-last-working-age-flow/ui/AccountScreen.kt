package com.mystudycompanion.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.LockPerson
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mystudycompanion.app.BuildConfig
import com.mystudycompanion.app.auth.AccountProvider
import com.mystudycompanion.app.auth.AuthRepository
import com.mystudycompanion.app.auth.GoogleSignInCoordinator
import com.mystudycompanion.app.auth.UserAccount
import com.mystudycompanion.app.ui.adaptive.AdaptiveLayoutSpec
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    account: UserAccount,
    authRepository: AuthRepository,
    googleSignInCoordinator: GoogleSignInCoordinator,
    layoutSpec: AdaptiveLayoutSpec,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val capabilities = authRepository.capabilities

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(layoutSpec.outerPaddingDp.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Account", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Card(shape = RoundedCornerShape(28.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(account.initials, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(account.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(account.email ?: "Private owner session", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f))
                        Text(
                            if (account.provider == AccountProvider.PRIVATE_OWNER) "Private test profile" else "Google account connected",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (account.provider == AccountProvider.PRIVATE_OWNER) {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Connect your Google account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (capabilities.firebaseConfigured && capabilities.googleWebClientConfigured) {
                                "Connect this profile so family membership can follow the real user across devices."
                            } else {
                                "The account code is installed, but Google sign-in still requires the Firebase Android configuration and OAuth web client ID. Family synchronization also requires the private HTTPS backend and household invitation/join service."
                            },
                        )
                        if (capabilities.firebaseConfigured && capabilities.googleWebClientConfigured) {
                            Button(
                                onClick = {
                                    val activity = context.findAccountActivity()
                                    if (activity == null) {
                                        error = "The Google account window could not be opened."
                                        return@Button
                                    }
                                    scope.launch {
                                        connecting = true
                                        error = null
                                        runCatching {
                                            val payload = googleSignInCoordinator.requestGoogleSignIn(
                                                activity = activity,
                                                serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
                                            )
                                            authRepository.signInWithGoogle(payload)
                                        }.onFailure { error = it.message ?: "Google account connection did not complete." }
                                        connecting = false
                                    }
                                },
                                enabled = !connecting,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                            ) {
                                if (connecting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Outlined.AccountCircle, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(if (connecting) "Connecting…" else "Connect with Google")
                            }
                        } else {
                            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                                Text("Google project configuration required")
                            }
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }

            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.LockPerson, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Personal by default", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Your notes, Daily Text history, saved questions, and AI conversations stay private unless you deliberately share an item with the household.")
                }
            }
            Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Outlined.Logout, contentDescription = null)
                Text("  Sign out")
            }
        }
    }
}

private tailrec fun Context.findAccountActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findAccountActivity()
    else -> null
}
