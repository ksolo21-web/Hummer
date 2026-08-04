package com.mystudycompanion.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mystudycompanion.app.auth.UserAccount
import com.mystudycompanion.app.companion.CompanionHubRepository
import com.mystudycompanion.app.data.repository.StudyRepository
import com.mystudycompanion.app.family.FamilyWorshipOrganizerRepository
import com.mystudycompanion.app.ui.adaptive.AdaptiveLayoutSpec
import com.mystudycompanion.app.ui.adaptive.AdaptiveWidthClass
import com.mystudycompanion.app.update.SyncStateStore

private enum class FamilyHubSection(val label: String, val key: String) {
    WORSHIP("Worship", "worship"),
    BOARD("Family board", "board"),
    HOUSEHOLD("Profiles & household", "household"),
}

@Composable
fun FamilyHubScreen(
    account: UserAccount,
    repository: StudyRepository,
    companionRepository: CompanionHubRepository,
    syncStateStore: SyncStateStore,
    organizerRepository: FamilyWorshipOrganizerRepository,
    layoutSpec: AdaptiveLayoutSpec,
    initialSection: String? = null,
    onOpenAi: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val companionState by companionRepository.state.collectAsStateWithLifecycle()
    val initialIndex = FamilyHubSection.entries.indexOfFirst { it.key == initialSection }.coerceAtLeast(0)
    var sectionIndex by rememberSaveable(initialSection) { mutableIntStateOf(initialIndex) }
    var showAddProfile by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(account.uid) { companionRepository.bindAccount(account) }
    LaunchedEffect(initialSection) {
        val requested = FamilyHubSection.entries.indexOfFirst { it.key == initialSection }
        if (requested >= 0) sectionIndex = requested
    }

    Column(modifier.fillMaxSize()) {
        val tabs: @Composable () -> Unit = {
            FamilyHubSection.entries.forEachIndexed { index, section ->
                Tab(
                    selected = sectionIndex == index,
                    onClick = { sectionIndex = index },
                    text = { Text(section.label, maxLines = 1) },
                )
            }
        }
        if (layoutSpec.widthClass == AdaptiveWidthClass.COMPACT) {
            ScrollableTabRow(
                selectedTabIndex = sectionIndex,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ) { tabs() }
        } else {
            TabRow(
                selectedTabIndex = sectionIndex,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            ) { tabs() }
        }

        when (FamilyHubSection.entries[sectionIndex]) {
            FamilyHubSection.WORSHIP -> FamilyWorshipScreen(
                repository = repository,
                companionRepository = companionRepository,
                syncStateStore = syncStateStore,
                organizerRepository = organizerRepository,
                layoutSpec = layoutSpec,
                onOpenAi = onOpenAi,
                modifier = Modifier.weight(1f),
            )
            FamilyHubSection.BOARD -> FamilyBoardSection(
                state = companionState,
                repository = companionRepository,
                layoutSpec = layoutSpec,
                modifier = Modifier.weight(1f),
            )
            FamilyHubSection.HOUSEHOLD -> Column(Modifier.weight(1f)) {
                ProfileSwitcher(
                    state = companionState,
                    onSelected = companionRepository::switchProfile,
                    onAdd = { showAddProfile = true },
                    modifier = Modifier.padding(
                        horizontal = layoutSpec.outerPaddingDp.dp,
                        vertical = 12.dp,
                    ),
                )
                HouseholdScreen(
                    account = account,
                    organizerRepository = organizerRepository,
                    layoutSpec = layoutSpec,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showAddProfile) {
        AddProfileDialog(
            onDismiss = { showAddProfile = false },
            onAdd = { name, ageGroup ->
                companionRepository.addLocalProfile(name, ageGroup)
                showAddProfile = false
            },
        )
    }
}
