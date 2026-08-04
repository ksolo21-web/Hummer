package com.kreativstudio.app.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kreativstudio.app.R
import com.kreativstudio.app.model.AppUser
import com.kreativstudio.app.ui.theme.KreativTheme
import com.kreativstudio.app.ui.theme.LocalKreativTokens

@Composable
fun KreativAppStable(viewModel: KreativViewModel, activity: Activity) {
    val settings by viewModel.settings.collectAsState()
    val user by viewModel.user.collectAsState()

    KreativTheme(settings) {
        val signedIn = user
        if (
            signedIn != null &&
            viewModel.screen == StudioScreen.STUDIO &&
            viewModel.currentProject != null
        ) {
            KreativFullscreenStudioHost(viewModel)
        } else {
            StableChrome(
                viewModel = viewModel,
                activity = activity,
                user = signedIn,
            )
        }
    }
}

@Composable
private fun StableChrome(
    viewModel: KreativViewModel,
    activity: Activity,
    user: AppUser?,
) {
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.message) {
        val message = viewModel.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.dismissMessage()
    }

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
                .imePadding(),
        ) {
            if (user == null) {
                StableWelcomeScreen(
                    busy = viewModel.isBusy,
                    googleConfigured = viewModel.isGoogleConfigured,
                    onGoogle = { viewModel.signInWithGoogle(activity) },
                    onOliviaPreview = viewModel::useOliviaPreview,
                    onGuest = viewModel::useGuestStudio,
                )
            } else {
                StableAppShell(viewModel, user, activity)
            }
        }
    }
}

@Composable
private fun StableWelcomeScreen(
    busy: Boolean,
    googleConfigured: Boolean,
    onGoogle: () -> Unit,
    onOliviaPreview: () -> Unit,
    onGuest: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                GradientBrush.radialGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = .24f),
                        MaterialTheme.colorScheme.background,
                        Color.Black,
                    ),
                ),
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight),
            shape = RoundedCornerShape(30.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wideEnough = maxWidth >= 760.dp
                val tallEnough = maxHeight >= 620.dp
                if (wideEnough && tallEnough) {
                    Row(
                        Modifier.fillMaxSize().padding(26.dp),
                        horizontalArrangement = Arrangement.spacedBy(26.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StableWelcomeArtwork(Modifier.weight(1f).fillMaxHeight())
                        StableWelcomeCopy(
                            modifier = Modifier.weight(1f),
                            scrollable = true,
                            busy = busy,
                            googleConfigured = googleConfigured,
                            onGoogle = onGoogle,
                            onOliviaPreview = onOliviaPreview,
                            onGuest = onGuest,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item {
                            StableWelcomeArtwork(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp),
                            )
                        }
                        item {
                            StableWelcomeCopy(
                                modifier = Modifier.fillMaxWidth(),
                                scrollable = false,
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
private fun StableWelcomeArtwork(modifier: Modifier) {
    Image(
        painter = painterResource(R.drawable.kreativ_icon_source),
        contentDescription = "KREATIV Studio owl atelier",
        modifier = modifier.clip(RoundedCornerShape(24.dp)),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun StableWelcomeCopy(
    modifier: Modifier,
    scrollable: Boolean,
    busy: Boolean,
    googleConfigured: Boolean,
    onGoogle: () -> Unit,
    onOliviaPreview: () -> Unit,
    onGuest: () -> Unit,
) {
    val tokens = LocalKreativTokens.current
    val scrollModifier = if (scrollable) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }
    Column(
        modifier = modifier.then(scrollModifier),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("KREATIV Studio", style = MaterialTheme.typography.displayMedium)
        Text("Draw. Paint. Learn. Master.", style = MaterialTheme.typography.titleLarge, color = tokens.gold)
        Text(
            "A private adaptive art studio with precision tools, guided teaching, offline creativity, and an AI mentor that protects the artist's own voice.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        Button(
            onClick = onGoogle,
            enabled = !busy && googleConfigured,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (googleConfigured) "Continue with Google" else "Google sign-in awaits private Firebase keys")
        }
        Button(
            onClick = onOliviaPreview,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = tokens.gold, contentColor = Color(0xFF1C1004)),
        ) {
            Icon(Icons.Default.Lock, null)
            Spacer(Modifier.width(8.dp))
            Text("Open Olivia's private preview")
        }
        TextButton(onClick = onGuest, modifier = Modifier.fillMaxWidth()) {
            Text("Explore as guest artist")
        }
    }
}

private data class StableNavItem(
    val screen: StudioScreen,
    val label: String,
    val icon: ImageVector,
)

private val stableNavItems = listOf(
    StableNavItem(StudioScreen.HOME, "Atelier", Icons.Default.Home),
    StableNavItem(StudioScreen.STUDIO, "Studio", Icons.Default.Brush),
    StableNavItem(StudioScreen.LESSONS, "Learn", Icons.Default.MenuBook),
    StableNavItem(StudioScreen.GALLERY, "Gallery", Icons.Default.Collections),
    StableNavItem(StudioScreen.MENTOR, "Mentor", Icons.Default.AutoAwesome),
    StableNavItem(StudioScreen.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
private fun StableAppShell(
    viewModel: KreativViewModel,
    user: AppUser,
    activity: Activity,
) {
    val settings by viewModel.settings.collectAsState()
    val windowState = rememberKreativWindowState(activity)

    key(windowState.signature, settings.focusMode) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val useRail =
                !settings.focusMode &&
                windowState.isExpanded &&
                !windowState.isTabletop &&
                !windowState.isBookPosture

            if (useRail) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        header = {
                            Image(
                                painter = painterResource(R.drawable.kreativ_icon_source),
                                contentDescription = null,
                                modifier = Modifier.padding(10.dp).size(58.dp).clip(CircleShape),
                            )
                        },
                    ) {
                        stableNavItems.forEach { item ->
                            NavigationRailItem(
                                selected = viewModel.screen == item.screen,
                                onClick = { viewModel.navigate(item.screen) },
                                icon = { Icon(item.icon, null) },
                                label = { Text(item.label) },
                                alwaysShowLabel = true,
                            )
                        }
                    }
                    VerticalDivider(Modifier.fillMaxHeight())
                    StableScreenHost(viewModel, user, Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    StableScreenHost(viewModel, user, Modifier.weight(1f))
                    if (!settings.focusMode) StableBottomNavigation(viewModel)
                }
            }
        }
    }
}

@Composable
private fun StableScreenHost(
    viewModel: KreativViewModel,
    user: AppUser,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize().imePadding()) {
        key(viewModel.screen) {
            when (viewModel.screen) {
                StudioScreen.HOME -> HomeScreen(viewModel, user)
                StudioScreen.STUDIO -> EmptyStudioRecovery(viewModel)
                StudioScreen.LESSONS -> LessonsScreen(viewModel)
                StudioScreen.GALLERY -> GalleryScreen(viewModel)
                StudioScreen.MENTOR -> KreativMentorExperience(viewModel)
                StudioScreen.SETTINGS -> SettingsScreen(viewModel, user)
            }
        }
    }
}

@Composable
private fun EmptyStudioRecovery(viewModel: KreativViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { Spacer(Modifier.height(24.dp)) }
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Default.Brush, null, modifier = Modifier.size(54.dp))
                    Text(
                        "No canvas is open",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Create a new canvas or return to the Atelier. This screen remains scrollable and keeps navigation available on every phone size.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = { viewModel.createProject("New Artwork") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create canvas")
                    }
                    TextButton(
                        onClick = { viewModel.navigate(StudioScreen.HOME) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Back to Atelier")
                    }
                }
            }
        }
    }
}

@Composable
private fun StableBottomNavigation(viewModel: KreativViewModel) {
    Surface(tonalElevation = 5.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 660.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    stableNavItems.forEach { item ->
                        StableNavButton(
                            item = item,
                            selected = viewModel.screen == item.screen,
                            onClick = { viewModel.navigate(item.screen) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(stableNavItems, key = { it.screen }) { item ->
                        StableNavButton(
                            item = item,
                            selected = viewModel.screen == item.screen,
                            onClick = { viewModel.navigate(item.screen) },
                            modifier = Modifier.width(98.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StableNavButton(
    item: StableNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(item.icon, null, modifier = Modifier.size(21.dp))
            Text(
                item.label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
