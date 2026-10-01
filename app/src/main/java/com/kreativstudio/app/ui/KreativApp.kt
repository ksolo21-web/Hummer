package com.kreativstudio.app.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush as GradientBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kreativstudio.app.R
import com.kreativstudio.app.model.AppUser
import com.kreativstudio.app.ui.theme.KreativTheme
import com.kreativstudio.app.ui.theme.LocalKreativTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KreativApp(viewModel: KreativViewModel, activity: Activity) {
    val settings by viewModel.settings.collectAsState()
    val user by viewModel.user.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.message) {
        val value = viewModel.message ?: return@LaunchedEffect
        snackbar.showSnackbar(value)
        viewModel.dismissMessage()
    }

    KreativTheme(settings) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                if (user == null) {
                    WelcomeScreen(
                        busy = viewModel.isBusy,
                        googleConfigured = viewModel.isGoogleConfigured,
                        onGoogle = { viewModel.signInWithGoogle(activity) },
                        onOliviaPreview = viewModel::useOliviaPreview,
                        onGuest = viewModel::useGuestStudio,
                    )
                } else {
                    AppShell(viewModel = viewModel, user = requireNotNull(user))
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(
    busy: Boolean,
    googleConfigured: Boolean,
    onGoogle: () -> Unit,
    onOliviaPreview: () -> Unit,
    onGuest: () -> Unit,
) {
    val tokens = LocalKreativTokens.current
    Box(
        Modifier
            .fillMaxSize()
            .background(
                GradientBrush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = .22f),
                        MaterialTheme.colorScheme.background,
                        Color.Black,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth(.94f)
                    .fillMaxHeight(.94f),
                shape = RoundedCornerShape(30.dp),
            ) {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 760.dp
                    if (wide) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WelcomeArtwork(Modifier.weight(1f))
                            WelcomeCopy(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                busy = busy,
                                googleConfigured = googleConfigured,
                                onGoogle = onGoogle,
                                onOliviaPreview = onOliviaPreview,
                                onGuest = onGuest,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            WelcomeArtwork(Modifier.fillMaxWidth())
                            Spacer(Modifier.height(22.dp))
                            WelcomeCopy(
                                modifier = Modifier.fillMaxWidth(),
                                busy = busy,
                                googleConfigured = googleConfigured,
                                onGoogle = onGoogle,
                                onOliviaPreview = onOliviaPreview,
                                onGuest = onGuest,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeArtwork(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.kreativ_icon_source),
        contentDescription = "KREATIV Studio owl atelier",
        modifier = modifier
            .clip(RoundedCornerShape(24.dp)),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun WelcomeCopy(
    modifier: Modifier,
    busy: Boolean,
    googleConfigured: Boolean,
    onGoogle: () -> Unit,
    onOliviaPreview: () -> Unit,
    onGuest: () -> Unit,
) {
    val tokens = LocalKreativTokens.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("KREATIV Studio", style = MaterialTheme.typography.displayMedium)
        Text(
            "Draw. Paint. Learn. Master.",
            style = MaterialTheme.typography.titleLarge,
            color = tokens.gold,
        )
        Text(
            "A private, adaptive art studio with precision tools, guided teaching, offline creativity, and an AI mentor that protects the artist's own voice.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Button(
            onClick = onGoogle,
            enabled = !busy && googleConfigured,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(if (googleConfigured) "Continue with Google" else "Google sign-in awaits private Firebase keys")
        }
        Button(
            onClick = onOliviaPreview,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.gold,
                contentColor = Color(0xFF1C1004),
            ),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Open Olivia's private preview")
        }
        TextButton(onClick = onGuest, modifier = Modifier.fillMaxWidth()) {
            Text("Explore as guest artist")
        }
        Text(
            "Olivia's permanent personalization is bound to her verified Firebase UID in private builds—not to a display name.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppShell(viewModel: KreativViewModel, user: AppUser) {
    val settings by viewModel.settings.collectAsState()
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        if (wide && !settings.focusMode) {
            Row(Modifier.fillMaxSize()) {
                StudioNavigationRail(viewModel)
                VerticalDivider(Modifier.fillMaxHeight())
                ScreenHost(viewModel, user, Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ScreenHost(viewModel, user, Modifier.weight(1f))
                if (!settings.focusMode) CompactNavigation(viewModel)
            }
        }
    }
}

@Composable
private fun ScreenHost(viewModel: KreativViewModel, user: AppUser, modifier: Modifier) {
    Box(modifier = modifier) {
        key(viewModel.screen) {
            when (viewModel.screen) {
                StudioScreen.HOME -> HomeScreen(viewModel, user)
                StudioScreen.STUDIO -> StudioCanvasScreen(viewModel)
                StudioScreen.LESSONS -> LessonsScreen(viewModel)
                StudioScreen.GALLERY -> GalleryScreen(viewModel)
                StudioScreen.MENTOR -> MentorScreen(viewModel)
                StudioScreen.SETTINGS -> SettingsScreen(viewModel, user)
            }
        }
    }
}

private data class NavItem(val screen: StudioScreen, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem(StudioScreen.HOME, "Atelier", Icons.Default.Home),
    NavItem(StudioScreen.STUDIO, "Studio", Icons.Default.Brush),
    NavItem(StudioScreen.LESSONS, "Learn", Icons.Default.MenuBook),
    NavItem(StudioScreen.GALLERY, "Gallery", Icons.Default.Collections),
    NavItem(StudioScreen.MENTOR, "Mentor", Icons.Default.AutoAwesome),
    NavItem(StudioScreen.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
private fun StudioNavigationRail(viewModel: KreativViewModel) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        header = {
            Image(
                painterResource(R.drawable.kreativ_icon_source),
                contentDescription = null,
                modifier = Modifier
                    .padding(10.dp)
                    .size(58.dp)
                    .clip(CircleShape),
            )
        },
    ) {
        navItems.forEach { item ->
            NavigationRailItem(
                selected = viewModel.screen == item.screen,
                onClick = { viewModel.navigate(item.screen) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun CompactNavigation(viewModel: KreativViewModel) {
    Surface(tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            navItems.forEach { item ->
                val selected = viewModel.screen == item.screen
                TextButton(
                    onClick = { viewModel.navigate(item.screen) },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.width(104.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(item.icon, contentDescription = null, modifier = Modifier.size(21.dp))
                        Text(item.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
fun CloudStateBadge(synced: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (synced) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
            Text(if (synced) "Cloud safe" else "Local safe", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun Modifier.verticalSafeScroll(): Modifier = this.then(
    Modifier.verticalScroll(rememberScrollState())
)
