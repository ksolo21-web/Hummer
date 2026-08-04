@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mystudycompanion.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mystudycompanion.app.BuildConfig
import com.mystudycompanion.app.ai.AiStudyRepository
import com.mystudycompanion.app.ai.knowledge.KnowledgeSyncStateStore
import com.mystudycompanion.app.ai.knowledge.OfficialKnowledgeSyncEngine
import com.mystudycompanion.app.auth.AccountAgeGroup
import com.mystudycompanion.app.auth.AccountProvider
import com.mystudycompanion.app.auth.AuthRepository
import com.mystudycompanion.app.auth.AuthState
import com.mystudycompanion.app.auth.GoogleSignInCoordinator
import com.mystudycompanion.app.auth.UserAccount
import com.mystudycompanion.app.companion.CompanionHubRepository
import com.mystudycompanion.app.data.repository.StudyRepository
import com.mystudycompanion.app.design.ThemeBackdrop
import com.mystudycompanion.app.design.ThemeStore
import com.mystudycompanion.app.family.FamilyWorshipOrganizerRepository
import com.mystudycompanion.app.network.BackendConfig
import com.mystudycompanion.app.performance.PerformanceDiagnosticsStore
import com.mystudycompanion.app.studyreader.UnifiedStudyReaderRepository
import com.mystudycompanion.app.ui.adaptive.AdaptiveLayoutSpec
import com.mystudycompanion.app.ui.adaptive.toStudyLayoutSpec
import com.mystudycompanion.app.ui.navigation.AppNavigator
import com.mystudycompanion.app.ui.navigation.AppRoute
import com.mystudycompanion.app.ui.navigation.DeepLinkRouter
import com.mystudycompanion.app.ui.navigation.NavEntry
import com.mystudycompanion.app.ui.navigation.rememberAppNavigator
import com.mystudycompanion.app.update.ContentSyncEngine
import com.mystudycompanion.app.update.SyncStateStore
import kotlinx.coroutines.launch

private data class AppDestination(
    val route: AppRoute,
    val label: String,
    val icon: ImageVector,
)

private val wideDestinations = listOf(
    AppDestination(AppRoute.HOME, "Home", Icons.Outlined.Home),
    AppDestination(AppRoute.STUDY, "Study", Icons.Outlined.MenuBook),
    AppDestination(AppRoute.FAMILY, "Family Hub", Icons.Outlined.FamilyRestroom),
    AppDestination(AppRoute.NOTES, "Notes", Icons.Outlined.NoteAlt),
    AppDestination(AppRoute.AI, "AI Study", Icons.Outlined.AutoAwesome),
    AppDestination(AppRoute.COMPANION, "Companion", Icons.Outlined.AutoStories),
    AppDestination(AppRoute.SETTINGS, "Settings", Icons.Outlined.Settings),
)

private val compactDestinations = listOf(
    AppDestination(AppRoute.HOME, "Home", Icons.Outlined.Home),
    AppDestination(AppRoute.STUDY, "Study", Icons.Outlined.MenuBook),
    AppDestination(AppRoute.FAMILY, "Family", Icons.Outlined.FamilyRestroom),
    AppDestination(AppRoute.NOTES, "Notes", Icons.Outlined.NoteAlt),
    AppDestination(AppRoute.MORE, "More", Icons.Outlined.MoreHoriz),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MyStudyCompanionApp(
    authRepository: AuthRepository,
    googleSignInCoordinator: GoogleSignInCoordinator,
    themeStore: ThemeStore,
    studyRepository: StudyRepository,
    aiStudyRepository: AiStudyRepository,
    companionHubRepository: CompanionHubRepository,
    officialKnowledgeSyncEngine: OfficialKnowledgeSyncEngine,
    knowledgeSyncStateStore: KnowledgeSyncStateStore,
    syncStateStore: SyncStateStore,
    backendConfig: BackendConfig,
    contentSyncEngine: ContentSyncEngine,
    familyWorshipOrganizerRepository: FamilyWorshipOrganizerRepository,
    unifiedStudyReaderRepository: UnifiedStudyReaderRepository,
    performanceDiagnosticsStore: PerformanceDiagnosticsStore,
    installationId: String,
    launchUri: String?,
    onLaunchUriConsumed: () -> Unit,
) {
    val authState by authRepository.state.collectAsStateWithLifecycle()
    val navigator = rememberAppNavigator()
    var pendingDeepLink by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(launchUri) {
        if (launchUri == null) return@LaunchedEffect
        pendingDeepLink = launchUri
        onLaunchUriConsumed()
    }

    LaunchedEffect(authState, pendingDeepLink) {
        if (authState !is AuthState.SignedIn) return@LaunchedEffect
        val entry = DeepLinkRouter.parse(pendingDeepLink) ?: return@LaunchedEffect
        if (entry.route.topLevel) navigator.reset(entry) else navigator.navigate(entry)
        pendingDeepLink = null
    }

    Box(
        modifier = Modifier.fillMaxSize().preferredFrameRate(FrameRateCategory.High),
    ) {
        ThemeBackdrop(Modifier.fillMaxSize())
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Box(Modifier.fillMaxSize()) {
                when (val state = authState) {
                AuthState.Initializing -> AuthLoadingScreen()
                AuthState.SignedOut, is AuthState.Failure -> AuthScreen(
                    authRepository = authRepository,
                    googleSignInCoordinator = googleSignInCoordinator,
                )
                is AuthState.SignedIn -> SignedInApp(
                    account = state.account,
                    authRepository = authRepository,
                    googleSignInCoordinator = googleSignInCoordinator,
                    navigator = navigator,
                    themeStore = themeStore,
                    studyRepository = studyRepository,
                    aiStudyRepository = aiStudyRepository,
                    companionHubRepository = companionHubRepository,
                    officialKnowledgeSyncEngine = officialKnowledgeSyncEngine,
                    knowledgeSyncStateStore = knowledgeSyncStateStore,
                    syncStateStore = syncStateStore,
                    backendConfig = backendConfig,
                    contentSyncEngine = contentSyncEngine,
                    familyWorshipOrganizerRepository = familyWorshipOrganizerRepository,
                    unifiedStudyReaderRepository = unifiedStudyReaderRepository,
                    performanceDiagnosticsStore = performanceDiagnosticsStore,
                    installationId = installationId,
                )
                }
            }
        }
    }
}

@Composable
private fun AuthLoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(42.dp))
    }
}

@Composable
private fun SignedInApp(
    account: UserAccount,
    authRepository: AuthRepository,
    googleSignInCoordinator: GoogleSignInCoordinator,
    navigator: AppNavigator,
    themeStore: ThemeStore,
    studyRepository: StudyRepository,
    aiStudyRepository: AiStudyRepository,
    companionHubRepository: CompanionHubRepository,
    officialKnowledgeSyncEngine: OfficialKnowledgeSyncEngine,
    knowledgeSyncStateStore: KnowledgeSyncStateStore,
    syncStateStore: SyncStateStore,
    backendConfig: BackendConfig,
    contentSyncEngine: ContentSyncEngine,
    familyWorshipOrganizerRepository: FamilyWorshipOrganizerRepository,
    unifiedStudyReaderRepository: UnifiedStudyReaderRepository,
    performanceDiagnosticsStore: PerformanceDiagnosticsStore,
    installationId: String,
) {
    val adaptiveInfo = currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)
    val layoutSpec = adaptiveInfo.toStudyLayoutSpec()
    val current = navigator.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val companionState by companionHubRepository.state.collectAsStateWithLifecycle()
    var ageLookupAttempted by rememberSaveable(account.uid) { mutableStateOf(false) }
    var ageLookupWorking by rememberSaveable(account.uid) { mutableStateOf(false) }
    var ageLookupError by rememberSaveable(account.uid) { mutableStateOf<String?>(null) }

    suspend fun refreshGoogleAgeFromAccount() {
        if (account.provider != AccountProvider.GOOGLE) return
        val activity = context.findActivityForAgeLookup()
        if (activity == null) {
            ageLookupError = "The Google age-check window could not be opened."
            return
        }
        ageLookupWorking = true
        ageLookupError = null
        runCatching {
            val payload = googleSignInCoordinator.requestGoogleSignIn(
                activity = activity,
                serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
            )
            authRepository.signInWithGoogle(payload)
            if (payload.profileHints.ageGroup == AccountAgeGroup.UNKNOWN) {
                ageLookupError = "Google did not return a birthday or age range for this account. Choose the correct level below; the app will save it and will not guess."
            }
        }.onFailure {
            ageLookupError = it.message ?: "Google age information could not be checked."
        }
        ageLookupWorking = false
    }

    LaunchedEffect(account.uid, account.displayName, account.ageGroup, account.ageSource) {
        companionHubRepository.bindAccount(account)
    }

    if (companionState.profile.uid != account.uid) {
        AuthLoadingScreen()
        return
    }

    LaunchedEffect(account.uid, companionState.profile.needsAgeConfirmation) {
        if (
            companionState.profile.needsAgeConfirmation &&
            account.provider == AccountProvider.GOOGLE &&
            !ageLookupAttempted
        ) {
            ageLookupAttempted = true
            refreshGoogleAgeFromAccount()
        }
    }

    if (companionState.profile.needsAgeConfirmation) {
        ProfileAgeSetupScreen(
            displayName = account.greetingName,
            minorOnly = account.ageGroup == AccountAgeGroup.MINOR_UNKNOWN,
            checkingGoogle = ageLookupWorking,
            googleLookupError = ageLookupError,
            onRetryGoogle = {
                ageLookupAttempted = true
                scope.launch { refreshGoogleAgeFromAccount() }
            },
            onSelected = companionHubRepository::confirmProfileAge,
        )
        return
    }

    BackHandler(enabled = navigator.canGoBack) { navigator.pop() }
    ForegroundLiveSync(contentSyncEngine, backendConfig, officialKnowledgeSyncEngine)
    WearSnapshotSync(
        themeStore = themeStore,
        repository = studyRepository,
        companionRepository = companionHubRepository,
        readerRepository = unifiedStudyReaderRepository,
    )
    ReportPerformanceScreen(current.route.name)

    val content: @Composable (Modifier) -> Unit = { modifier ->
        LiveRevisionSurface(revisionKey = current.encode(), modifier = modifier) {
            DestinationContent(
                entry = current,
                account = account,
                authRepository = authRepository,
                googleSignInCoordinator = googleSignInCoordinator,
                navigator = navigator,
                themeStore = themeStore,
                studyRepository = studyRepository,
                aiStudyRepository = aiStudyRepository,
                companionHubRepository = companionHubRepository,
                officialKnowledgeSyncEngine = officialKnowledgeSyncEngine,
                knowledgeSyncStateStore = knowledgeSyncStateStore,
                syncStateStore = syncStateStore,
                backendConfig = backendConfig,
                familyWorshipOrganizerRepository = familyWorshipOrganizerRepository,
                unifiedStudyReaderRepository = unifiedStudyReaderRepository,
                performanceDiagnosticsStore = performanceDiagnosticsStore,
                installationId = installationId,
                layoutSpec = layoutSpec,
                greetingName = companionState.profile.displayName.substringBefore(' ').ifBlank { account.greetingName },
                modifier = Modifier.fillMaxSize(),
                onSignOut = {
                    scope.launch { googleSignInCoordinator.clearCredentialState() }
                    authRepository.signOut()
                    navigator.reset()
                },
            )
        }
    }

    if (layoutSpec.useBottomNavigation) {
        CompactAppScaffold(
            selectedRoute = current.route,
            canGoBack = navigator.canGoBack,
            onBack = { navigator.pop() },
            onSelected = navigator::selectTopLevel,
            content = content,
        )
    } else {
        Row(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.965f),
            ) {
                wideDestinations.forEach { destination ->
                    NavigationRailItem(
                        selected = current.route == destination.route,
                        onClick = {
                            if (destination.route.topLevel) navigator.selectTopLevel(destination.route)
                            else navigator.navigate(NavEntry(destination.route))
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
                NavigationRailItem(
                    selected = current.route == AppRoute.ACCOUNT,
                    onClick = { navigator.navigate(NavEntry(AppRoute.ACCOUNT)) },
                    icon = { Icon(Icons.Outlined.AccountCircle, contentDescription = "Account") },
                    label = { Text("Account") },
                )
            }
            content(Modifier.weight(1f))
        }
    }
}

@Composable
private fun CompactAppScaffold(
    selectedRoute: AppRoute,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSelected: (AppRoute) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (!selectedRoute.topLevel) {
                TopAppBar(
                    title = { Text(selectedRoute.displayLabel()) },
                    navigationIcon = {
                        androidx.compose.material3.IconButton(onClick = onBack, enabled = canGoBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.965f),
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.965f),
            ) {
                compactDestinations.forEach { destination ->
                    val selected = if (destination.route == AppRoute.MORE) {
                        selectedRoute in setOf(
                            AppRoute.MORE,
                            AppRoute.AI,
                            AppRoute.SETTINGS,
                            AppRoute.ACCOUNT,
                            AppRoute.HOUSEHOLD,
                            AppRoute.COMPANION,
                            AppRoute.READER,
                        )
                    } else {
                        selectedRoute == destination.route
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onSelected(destination.route) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding -> content(Modifier.padding(padding)) }
}

private fun AppRoute.displayLabel(): String = when (this) {
    AppRoute.HOME -> "Home"
    AppRoute.STUDY -> "Study"
    AppRoute.FAMILY -> "Family Hub"
    AppRoute.NOTES -> "Notes"
    AppRoute.MORE -> "More"
    AppRoute.AI -> "AI Study"
    AppRoute.SETTINGS -> "Settings"
    AppRoute.ACCOUNT -> "Account"
    AppRoute.HOUSEHOLD -> "Family Hub"
    AppRoute.COMPANION -> "Study Companion"
    AppRoute.READER -> "Study Reader"
}

@Composable
private fun DestinationContent(
    entry: NavEntry,
    account: UserAccount,
    authRepository: AuthRepository,
    googleSignInCoordinator: GoogleSignInCoordinator,
    navigator: AppNavigator,
    themeStore: ThemeStore,
    studyRepository: StudyRepository,
    aiStudyRepository: AiStudyRepository,
    companionHubRepository: CompanionHubRepository,
    officialKnowledgeSyncEngine: OfficialKnowledgeSyncEngine,
    knowledgeSyncStateStore: KnowledgeSyncStateStore,
    syncStateStore: SyncStateStore,
    backendConfig: BackendConfig,
    familyWorshipOrganizerRepository: FamilyWorshipOrganizerRepository,
    unifiedStudyReaderRepository: UnifiedStudyReaderRepository,
    performanceDiagnosticsStore: PerformanceDiagnosticsStore,
    installationId: String,
    layoutSpec: AdaptiveLayoutSpec,
    greetingName: String,
    modifier: Modifier,
    onSignOut: () -> Unit,
) {
    when (entry.route) {
        AppRoute.HOME -> HomeScreen(
            repository = studyRepository,
            layoutSpec = layoutSpec,
            syncStateStore = syncStateStore,
            greetingName = greetingName,
            onOpenStudy = { navigator.navigate(NavEntry(AppRoute.STUDY, it)) },
            onOpenFamily = { navigator.navigate(NavEntry(AppRoute.FAMILY)) },
            onOpenAi = { navigator.navigate(NavEntry(AppRoute.AI, it)) },
            modifier = modifier,
        )
        AppRoute.STUDY -> StudyScreen(
            repository = studyRepository,
            aiStudyRepository = aiStudyRepository,
            layoutSpec = layoutSpec,
            selectedPartId = entry.argument,
            onSelectedPart = { navigator.replace(NavEntry(AppRoute.STUDY, it)) },
            onAddNote = { navigator.navigate(NavEntry(AppRoute.NOTES, it)) },
            onOpenReader = { title, url -> navigator.navigate(NavEntry(AppRoute.READER, url, title)) },
            modifier = modifier,
        )
        AppRoute.FAMILY -> FamilyHubScreen(
            account = account,
            repository = studyRepository,
            companionRepository = companionHubRepository,
            syncStateStore = syncStateStore,
            organizerRepository = familyWorshipOrganizerRepository,
            layoutSpec = layoutSpec,
            initialSection = entry.argument,
            onOpenAi = { navigator.navigate(NavEntry(AppRoute.AI, it)) },
            modifier = modifier,
        )
        AppRoute.NOTES -> NotesScreen(
            repository = studyRepository,
            layoutSpec = layoutSpec,
            contextPartId = entry.argument,
            modifier = modifier,
        )
        AppRoute.AI -> AiStudyScreen(
            repository = aiStudyRepository,
            layoutSpec = layoutSpec,
            initialQuestion = entry.argument.orEmpty(),
            onInitialQuestionConsumed = { navigator.replace(NavEntry(AppRoute.AI)) },
            modifier = modifier,
        )
        AppRoute.SETTINGS -> SettingsScreen(
            themeStore = themeStore,
            syncStateStore = syncStateStore,
            knowledgeSyncStateStore = knowledgeSyncStateStore,
            officialKnowledgeSyncEngine = officialKnowledgeSyncEngine,
            backendConfig = backendConfig,
            performanceDiagnosticsStore = performanceDiagnosticsStore,
            installationId = installationId,
            layoutSpec = layoutSpec,
            modifier = modifier,
        )
        AppRoute.ACCOUNT -> AccountScreen(
            account = account,
            authRepository = authRepository,
            googleSignInCoordinator = googleSignInCoordinator,
            layoutSpec = layoutSpec,
            onSignOut = onSignOut,
            modifier = modifier,
        )
        AppRoute.HOUSEHOLD -> FamilyHubScreen(
            account = account,
            repository = studyRepository,
            companionRepository = companionHubRepository,
            syncStateStore = syncStateStore,
            organizerRepository = familyWorshipOrganizerRepository,
            layoutSpec = layoutSpec,
            initialSection = "household",
            onOpenAi = { navigator.navigate(NavEntry(AppRoute.AI, it)) },
            modifier = modifier,
        )
        AppRoute.COMPANION -> CompanionHubScreen(
            account = account,
            repository = companionHubRepository,
            layoutSpec = layoutSpec,
            onOpenAi = { navigator.navigate(NavEntry(AppRoute.AI, it)) },
            onOpenNotes = { navigator.navigate(NavEntry(AppRoute.NOTES, "custom:$it")) },
            modifier = modifier,
        )
        AppRoute.READER -> UnifiedStudyReaderScreen(
            repository = unifiedStudyReaderRepository,
            layoutSpec = layoutSpec,
            initialUrl = entry.argument,
            initialTitle = entry.secondaryArgument,
            onBack = { navigator.pop() },
            modifier = modifier,
        )
        AppRoute.MORE -> MoreScreen(
            layoutSpec = layoutSpec,
            onOpenAi = { navigator.navigate(NavEntry(AppRoute.AI)) },
            onOpenCompanion = { navigator.navigate(NavEntry(AppRoute.COMPANION)) },
            onOpenAccount = { navigator.navigate(NavEntry(AppRoute.ACCOUNT)) },
            onOpenSettings = { navigator.navigate(NavEntry(AppRoute.SETTINGS)) },
            modifier = modifier,
        )
    }
}


private tailrec fun Context.findActivityForAgeLookup(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityForAgeLookup()
    else -> null
}
