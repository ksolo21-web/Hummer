package com.mystudycompanion.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mystudycompanion.app.BuildConfig
import com.mystudycompanion.app.auth.AuthRepository
import com.mystudycompanion.app.auth.GoogleSignInCoordinator
import com.mystudycompanion.app.design.LocalThemeVisualIdentity
import com.mystudycompanion.app.design.ThemeEmblem
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    googleSignInCoordinator: GoogleSignInCoordinator,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val capabilities = authRepository.capabilities
    val identity = LocalThemeVisualIdentity.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
            shape = RoundedCornerShape(32.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(
                    modifier = Modifier.size(82.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    ThemeEmblem(
                        modifier = Modifier.size(if (identity.isAnimalTheme) 62.dp else 52.dp),
                        mode = identity.mode,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("My Study Companion", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Your Daily Text, meeting preparation, private notes, and Family Worship plans—kept together and protected.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                }

                if (capabilities.firebaseConfigured && capabilities.googleWebClientConfigured) {
                    Button(
                        onClick = {
                            val activity = context.findActivity()
                            if (activity == null) {
                                error = "The sign-in window could not be opened."
                                return@Button
                            }
                            scope.launch {
                                working = true
                                error = null
                                runCatching {
                                    val payload = googleSignInCoordinator.requestGoogleSignIn(
                                        activity = activity,
                                        serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
                                    )
                                    authRepository.signInWithGoogle(payload)
                                }.onFailure { error = it.message ?: "Google sign-in did not complete." }
                                working = false
                            }
                        },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        if (working) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.AccountCircle, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Text(if (working) "Signing in…" else "Continue with Google")
                    }
                } else {
                    AuthSetupNotice()
                }

                if (capabilities.localOwnerModeAllowed) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f))
                        Text("  PRIVATE TESTING  ", style = MaterialTheme.typography.labelSmall)
                        HorizontalDivider(Modifier.weight(1f))
                    }
                    OutlinedButton(
                        onClick = { authRepository.signInAsPrivateOwner() },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Text("Enter owner-only private alpha")
                    }
                    Text(
                        "This local owner session is for your private device test only. Family distribution will require each person to sign in with their own account.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                    )
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Private notes and AI history are personal by default. Household sharing is explicit.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthSetupNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Account service is not provisioned in this source build", fontWeight = FontWeight.SemiBold)
            Text(
                "Google sign-in requires the private Firebase Android configuration and Google OAuth web client ID. Family synchronization additionally requires the deployed HTTPS backend and completed household invitation/join service.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
