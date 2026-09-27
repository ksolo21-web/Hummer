package com.koenterprises.territorycardstudio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.koenterprises.territorycardstudio.core.KnowledgeBaseAssignment
import com.koenterprises.territorycardstudio.core.TerritoryClass
import com.koenterprises.territorycardstudio.core.TerritoryKnowledgeBase

enum class ProductionRoute { TERRITORIES, WORKSPACE }

enum class TerritoryUiStatus(val label: String) {
    APPROVED("Approved"),
    REAUDIT_REQUIRED("Re-audit required"),
    NEEDS_NEW_CARD("Needs new card"),
    CONFLICT("Overlap conflict"),
    SCREENING("Unassigned screening")
}

data class TerritoryDashboardItem(
    val assignment: KnowledgeBaseAssignment,
    val status: TerritoryUiStatus,
    val modeLabel: String
)

data class TerritoryDashboardModel(
    val revision: String,
    val items: List<TerritoryDashboardItem>,
    val approvedCount: Int,
    val reAuditCount: Int,
    val needsNewCount: Int,
    val conflictCount: Int,
    val screeningCount: Int
) {
    companion object {
        fun from(knowledgeBase: TerritoryKnowledgeBase): TerritoryDashboardModel {
            val blockingIds = knowledgeBase.crossTerritoryOverlapAudit.blockingDuplicateWork
                .flatMap { it.territories }
                .toSet()
            val reviewIds = knowledgeBase.crossTerritoryOverlapAudit.reviewCandidates
                .flatMap { it.territories }
                .toSet()

            val items = knowledgeBase.assignments.values
                .sortedWith(compareBy<KnowledgeBaseAssignment>(
                    { it.identity.baseNumber },
                    { it.identity.territoryClass.token },
                    { it.identity.suffix ?: '\u0000' }
                ))
                .map { assignment ->
                    val status = when {
                        assignment.displayId in blockingIds -> TerritoryUiStatus.CONFLICT
                        assignment.needsNewCard -> TerritoryUiStatus.NEEDS_NEW_CARD
                        assignment.displayId in reviewIds ||
                            assignment.status.contains("reaudit", ignoreCase = true) -> TerritoryUiStatus.REAUDIT_REQUIRED
                        else -> TerritoryUiStatus.APPROVED
                    }
                    TerritoryDashboardItem(
                        assignment = assignment,
                        status = status,
                        modeLabel = when (assignment.identity.territoryClass) {
                            TerritoryClass.Residential -> "Regular"
                            TerritoryClass.Apartment -> "Apartment"
                            TerritoryClass.Telephone -> "Telephone"
                            TerritoryClass.TelephoneApartment -> "Telephone Apartment"
                        }
                    )
                }

            return TerritoryDashboardModel(
                revision = knowledgeBase.revision,
                items = items,
                approvedCount = items.count { it.status == TerritoryUiStatus.APPROVED },
                reAuditCount = items.count { it.status == TerritoryUiStatus.REAUDIT_REQUIRED },
                needsNewCount = items.count { it.status == TerritoryUiStatus.NEEDS_NEW_CARD },
                conflictCount = items.count { it.status == TerritoryUiStatus.CONFLICT },
                screeningCount = knowledgeBase.populationSummary.coverageScreeningCandidates
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerritoryCardStudioProductionApp(
    knowledgeBase: TerritoryKnowledgeBase,
    appearanceStore: AppearancePreferenceStore
) {
    val appearanceMode by appearanceStore.mode
    TerritoryCardStudioTheme(appearanceMode = appearanceMode) {
        val dashboard = remember(knowledgeBase) { TerritoryDashboardModel.from(knowledgeBase) }
        var routeName by rememberSaveable { mutableStateOf(ProductionRoute.TERRITORIES.name) }
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        var knowledgeOpen by rememberSaveable { mutableStateOf(false) }
        val services = (LocalContext.current.applicationContext as TerritoryCardStudioApplication).services
        val route = ProductionRoute.valueOf(routeName)
        val selected = selectedId?.let { id -> dashboard.items.firstOrNull { it.assignment.displayId == id } }

        if (knowledgeOpen) KnowledgeBaseDestination(knowledgeBase, services.activePolicy.revision,
            services.endpointConfig.revision, services.approvedExport, selectedId) { knowledgeOpen = false }

        Scaffold(
            topBar = {
                ProductionTopBar(
                    route = route,
                    appearanceMode = appearanceMode,
                    onAppearanceMode = appearanceStore::setMode
                )
            },
            bottomBar = {
                BoxWithConstraints {
                    if (maxWidth < 840.dp) {
                        ProductionBottomNavigation(
                            route = route,
                            workspaceEnabled = selected != null,
                            onRoute = { routeName = it.name },
                            onKnowledgeBase = { knowledgeOpen = true }
                        )
                    }
                }
            }
        ) { outerPadding ->
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(outerPadding)
            ) {
                if (maxWidth >= 840.dp) {
                    Row(Modifier.fillMaxSize()) {
                        ProductionNavigationRail(
                            route = route,
                            workspaceEnabled = selected != null,
                            onRoute = { routeName = it.name },
                            onKnowledgeBase = { knowledgeOpen = true }
                        )
                        VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
                        ProductionRouteContent(
                            modifier = Modifier.weight(1f),
                            route = route,
                            dashboard = dashboard,
                            selected = selected,
                            onOpenTerritory = { item ->
                                selectedId = item.assignment.displayId
                                routeName = ProductionRoute.WORKSPACE.name
                            },
                            onBackToDashboard = { routeName = ProductionRoute.TERRITORIES.name }
                        )
                    }
                } else {
                    ProductionRouteContent(
                        modifier = Modifier.fillMaxSize(),
                        route = route,
                        dashboard = dashboard,
                        selected = selected,
                        onOpenTerritory = { item ->
                            selectedId = item.assignment.displayId
                            routeName = ProductionRoute.WORKSPACE.name
                        },
                        onBackToDashboard = { routeName = ProductionRoute.TERRITORIES.name }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductionTopBar(
    route: ProductionRoute,
    appearanceMode: AppearanceMode,
    onAppearanceMode: (AppearanceMode) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                Text("Territory Card Studio", fontWeight = FontWeight.SemiBold)
                Text(
                    if (route == ProductionRoute.TERRITORIES) "Territories" else "Territory Workspace",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            Box {
                TextButton(
                    modifier = Modifier.testTag("appearance-menu-button"),
                    onClick = { menuExpanded = true }
                ) {
                    Text("Appearance: " + appearanceMode.label)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    AppearanceMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(if (mode == appearanceMode) mode.label + "  •" else mode.label)
                            },
                            onClick = {
                                onAppearanceMode(mode)
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun ProductionNavigationRail(
    route: ProductionRoute,
    workspaceEnabled: Boolean,
    onRoute: (ProductionRoute) -> Unit,
    onKnowledgeBase: () -> Unit
) {
    NavigationRail(modifier = Modifier.fillMaxHeight()) {
        Spacer(Modifier.height(16.dp))
        NavigationRailItem(
            selected = route == ProductionRoute.TERRITORIES,
            onClick = { onRoute(ProductionRoute.TERRITORIES) },
            icon = { NavGlyph("T", selected = route == ProductionRoute.TERRITORIES) },
            label = { Text("Territories") }
        )
        NavigationRailItem(
            selected = route == ProductionRoute.WORKSPACE,
            onClick = { if (workspaceEnabled) onRoute(ProductionRoute.WORKSPACE) },
            enabled = workspaceEnabled,
            icon = { NavGlyph("W", selected = route == ProductionRoute.WORKSPACE) },
            label = { Text("Workspace") }
        )
        NavigationRailItem(selected = false, onClick = onKnowledgeBase,
            icon = { NavGlyph("K", false) }, label = { Text("Knowledge Base") },
            modifier = Modifier.testTag("nav-knowledge"))
    }
}

@Composable
private fun ProductionBottomNavigation(
    route: ProductionRoute,
    workspaceEnabled: Boolean,
    onRoute: (ProductionRoute) -> Unit,
    onKnowledgeBase: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = route == ProductionRoute.TERRITORIES,
            onClick = { onRoute(ProductionRoute.TERRITORIES) },
            icon = { NavGlyph("T", selected = route == ProductionRoute.TERRITORIES) },
            label = { Text("Territories") }
        )
        NavigationBarItem(
            selected = route == ProductionRoute.WORKSPACE,
            onClick = { if (workspaceEnabled) onRoute(ProductionRoute.WORKSPACE) },
            enabled = workspaceEnabled,
            icon = { NavGlyph("W", selected = route == ProductionRoute.WORKSPACE) },
            label = { Text("Workspace") }
        )
        NavigationBarItem(selected = false, onClick = onKnowledgeBase,
            icon = { NavGlyph("K", false) }, label = { Text("Knowledge Base") },
            modifier = Modifier.testTag("nav-knowledge"))
    }
}

@Composable
private fun NavGlyph(text: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProductionRouteContent(
    modifier: Modifier,
    route: ProductionRoute,
    dashboard: TerritoryDashboardModel,
    selected: TerritoryDashboardItem?,
    onOpenTerritory: (TerritoryDashboardItem) -> Unit,
    onBackToDashboard: () -> Unit
) {
    when (route) {
        ProductionRoute.TERRITORIES -> TerritoriesDashboard(
            modifier = modifier,
            model = dashboard,
            onOpenTerritory = onOpenTerritory
        )
        ProductionRoute.WORKSPACE -> {
            if (selected == null) {
                EmptyWorkspace(modifier, onBackToDashboard)
            } else {
                ModeAwareTerritoryWorkspace(modifier, selected, dashboard.revision, onBackToDashboard)
            }
        }
    }
}

@Composable
private fun TerritoriesDashboard(
    modifier: Modifier,
    model: TerritoryDashboardModel,
    onOpenTerritory: (TerritoryDashboardItem) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase()
    val filtered = remember(model.items, normalizedQuery) {
        if (normalizedQuery.isEmpty()) model.items else model.items.filter { item ->
            item.assignment.displayId.lowercase().contains(normalizedQuery) ||
                item.assignment.canonicalFilename.lowercase().contains(normalizedQuery) ||
                item.modeLabel.lowercase().contains(normalizedQuery) ||
                item.status.label.lowercase().contains(normalizedQuery)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("territories-dashboard"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Territories", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Production workspace backed by the frozen Phase 1 engine • KB " + model.revision,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { PhaseOneLockedBanner() }
        item { DashboardStats(model) }
        item {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().testTag("territory-search"),
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Search territories") },
                supportingText = { Text(filtered.size.toString() + " of " + model.items.size + " territories") }
            )
        }
        items(filtered, key = { it.assignment.displayId }) { item ->
            TerritoryListCard(item = item, onClick = { onOpenTerritory(item) })
        }
    }
}

@Composable
private fun PhaseOneLockedBanner() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text("Phase 1 engine locked", fontWeight = FontWeight.SemiBold)
                Text(
                    "UI reads the proven territory engine; Compose does not reimplement assignment, color, building, label, packet, or approval rules.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DashboardStats(model: TerritoryDashboardModel) {
    val cards = listOf(
        Triple("Approved", model.approvedCount, TerritoryUiStatus.APPROVED),
        Triple("Re-audit", model.reAuditCount, TerritoryUiStatus.REAUDIT_REQUIRED),
        Triple("Needs new", model.needsNewCount, TerritoryUiStatus.NEEDS_NEW_CARD),
        Triple("Conflicts", model.conflictCount, TerritoryUiStatus.CONFLICT),
        Triple("Screening", model.screeningCount, TerritoryUiStatus.SCREENING)
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 700.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                cards.forEach { (label, value, status) ->
                    StatCard(label, value, status, Modifier.weight(1f))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cards.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, value, status) ->
                            StatCard(label, value, status, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, status: TerritoryUiStatus, modifier: Modifier = Modifier) {
    val pair = statusColors(status)
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = pair.first, contentColor = pair.second) {
        Column(Modifier.padding(14.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TerritoryListCard(item: TerritoryDashboardItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("territory-row-" + item.assignment.displayId),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                    Text(item.assignment.displayId, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Territory " + item.assignment.displayId,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TerritoryStatusBadge(item.status)
                }
                Text(
                    item.modeLabel + " • " + item.assignment.roadCount + " roads • " + item.assignment.buildingCount + " buildings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    item.assignment.canonicalFilename,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("Open", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TerritoryStatusBadge(status: TerritoryUiStatus) {
    val pair = statusColors(status)
    Surface(shape = RoundedCornerShape(100.dp), color = pair.first, contentColor = pair.second) {
        Text(status.label, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun statusColors(status: TerritoryUiStatus): Pair<Color, Color> {
    val colors = LocalTerritoryStatusColors.current
    return when (status) {
        TerritoryUiStatus.APPROVED -> colors.approvedContainer to colors.approvedContent
        TerritoryUiStatus.NEEDS_NEW_CARD -> colors.needsNewContainer to colors.needsNewContent
        TerritoryUiStatus.REAUDIT_REQUIRED -> colors.reAuditContainer to colors.reAuditContent
        TerritoryUiStatus.CONFLICT -> colors.conflictContainer to colors.conflictContent
        TerritoryUiStatus.SCREENING -> colors.screeningContainer to colors.screeningContent
    }
}

@Composable
private fun TerritoryWorkspace(
    modifier: Modifier,
    item: TerritoryDashboardItem,
    knowledgeBaseRevision: String,
    onBack: () -> Unit
) {
    val assignment = item.assignment
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("territory-workspace"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Back to territories") }
            Text(
                "Territory " + assignment.displayId,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TerritoryStatusBadge(item.status)
                Text(item.modeLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Workspace foundation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "The map/card preview, verification surfaces, and mode-specific tabs will attach here through the proven Phase 1 engine in subsequent Phase 2 packages.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    WorkspaceFact("Canonical file", assignment.canonicalFilename)
                    WorkspaceFact("Knowledge Base", knowledgeBaseRevision)
                    WorkspaceFact("Roads", assignment.roadCount.toString())
                    WorkspaceFact("Buildings", assignment.buildingCount.toString())
                    WorkspaceFact("Road authority", assignment.roadAssignmentStatus)
                    WorkspaceFact("Building authority", assignment.buildingAssignmentStatus)
                }
            }
        }
        item {
            SourceTruthCard(item)
        }
    }
}

@Composable
private fun SourceTruthCard(item: TerritoryDashboardItem) {
    val assignment = item.assignment
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Source truth", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "UI state is derived from the Territory Knowledge Base. Unknown assignments, unresolved conflicts, and needs-new-card states remain fail-closed.",
                style = MaterialTheme.typography.bodyMedium
            )
            WorkspaceFact("Assignment status", assignment.status)
            WorkspaceFact("Source class", assignment.sourceClass)
            WorkspaceFact("Field release (exact artifact)", assignment.fieldReleaseAllowedForExactArtifact.toString())
            WorkspaceFact("Reference hash", assignment.referenceSha256.take(16) + "…")
        }
    }
}

@Composable
private fun WorkspaceFact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2)
    }
}

@Composable
private fun EmptyWorkspace(modifier: Modifier, onBack: () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Select a territory first", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("Go to territories") }
        }
    }
}
