package com.koenterprises.territorycardstudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.koenterprises.territorycardstudio.core.KnowledgeBaseAssignment
import com.koenterprises.territorycardstudio.core.TerritoryClass

enum class WorkspaceMode(val label: String) {
    REGULAR("Regular"),
    LETTER_WRITING("Letter Writing"),
    TELEPHONE("Telephone")
}

enum class WorkspaceTab(val label: String) {
    MAP("Map"),
    STREETS("Streets"),
    ADDRESSES("Addresses"),
    PHONE_LIST("Phone List"),
    BUILDINGS("Buildings"),
    DETAILS("Details")
}

object WorkspaceModePolicy {
    fun allowedModes(assignment: KnowledgeBaseAssignment): List<WorkspaceMode> =
        when (assignment.identity.territoryClass) {
            TerritoryClass.Telephone,
            TerritoryClass.TelephoneApartment -> listOf(WorkspaceMode.TELEPHONE)
            TerritoryClass.Residential,
            TerritoryClass.Apartment -> listOf(WorkspaceMode.REGULAR, WorkspaceMode.LETTER_WRITING)
        }

    fun defaultMode(assignment: KnowledgeBaseAssignment): WorkspaceMode =
        allowedModes(assignment).first()

    fun tabs(mode: WorkspaceMode): List<WorkspaceTab> = when (mode) {
        WorkspaceMode.REGULAR -> listOf(
            WorkspaceTab.MAP,
            WorkspaceTab.STREETS,
            WorkspaceTab.BUILDINGS,
            WorkspaceTab.DETAILS
        )
        WorkspaceMode.LETTER_WRITING -> listOf(
            WorkspaceTab.MAP,
            WorkspaceTab.ADDRESSES,
            WorkspaceTab.BUILDINGS,
            WorkspaceTab.DETAILS
        )
        WorkspaceMode.TELEPHONE -> listOf(
            WorkspaceTab.MAP,
            WorkspaceTab.PHONE_LIST,
            WorkspaceTab.BUILDINGS,
            WorkspaceTab.DETAILS
        )
    }
}

data class WorkspaceReadinessModel(
    val verifiedChecks: Int,
    val reviewChecks: Int,
    val blockingConflicts: Int,
    val exactArtifactReleaseLabel: String,
    val candidateReadinessLabel: String,
    val provenanceLabel: String,
    val sourceCount: Int,
    val geometryLabel: String
) {
    companion object {
        fun from(item: TerritoryDashboardItem): WorkspaceReadinessModel {
            val a = item.assignment
            val currentSource = !a.needsNewCard &&
                a.sourceClass != "legacy_reference_not_current_approved_card"
            val roadLocked = a.roadAssignmentStatus == "locked"
            val buildingReady = a.buildingAssignmentStatus in setOf("not_applicable", "locked_300_301")
            val exactReleaseReady = a.fieldReleaseAllowedForExactArtifact && !a.needsNewCard

            val verified = listOf(currentSource, roadLocked, buildingReady, exactReleaseReady).count { it }
            var review = 0
            if (!currentSource) review += 1
            if (!roadLocked) review += 1
            if (!buildingReady) review += 1
            if (item.status == TerritoryUiStatus.REAUDIT_REQUIRED) review += 1
            if (a.needsNewCard) review += 1

            val release = when {
                a.needsNewCard -> "Blocked — new card required"
                a.fieldReleaseAllowedForExactArtifact -> "Exact approved artifact eligible"
                else -> "Blocked"
            }
            val candidate = when {
                a.needsNewCard -> "Candidate required; explicit approval will be required"
                a.status.contains("reaudit", ignoreCase = true) -> "Exact artifact preserved; rebuild requires re-audit"
                else -> "Assignment locked; new candidates still require exact validation"
            }
            val provenance = when {
                a.needsNewCard -> "Legacy reference only — not a current approved card"
                a.sourceClass.contains("approved", ignoreCase = true) -> "Current approved source"
                else -> a.sourceClass.replace('_', ' ')
            }
            return WorkspaceReadinessModel(
                verifiedChecks = verified,
                reviewChecks = review,
                blockingConflicts = if (item.status == TerritoryUiStatus.CONFLICT) 1 else 0,
                exactArtifactReleaseLabel = release,
                candidateReadinessLabel = candidate,
                provenanceLabel = provenance,
                sourceCount = a.sourceHashes.distinct().size,
                geometryLabel = if (a.geometryAvailable) "Available" else "Not available"
            )
        }
    }
}

@Composable
fun ModeAwareTerritoryWorkspace(
    modifier: Modifier,
    item: TerritoryDashboardItem,
    knowledgeBaseRevision: String,
    onBack: () -> Unit
) {
    val assignment = item.assignment
    val allowedModes = WorkspaceModePolicy.allowedModes(assignment)
    val defaultMode = WorkspaceModePolicy.defaultMode(assignment)
    var modeName by rememberSaveable(assignment.displayId) {
        mutableStateOf(defaultMode.name)
    }
    val mode = WorkspaceMode.entries.firstOrNull { it.name == modeName && it in allowedModes } ?: defaultMode
    val tabs = WorkspaceModePolicy.tabs(mode)
    var tabName by rememberSaveable(assignment.displayId, mode.name) {
        mutableStateOf(tabs.first().name)
    }
    val tab = WorkspaceTab.entries.firstOrNull { it.name == tabName && it in tabs } ?: tabs.first()
    val readiness = WorkspaceReadinessModel.from(item)

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WorkspaceStatusBadge(item.status)
                Text(
                    assignment.identity.territoryClass.token.ifBlank { "Regular" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            WorkspaceReadinessSummary(readiness)
        }

        if (allowedModes.size > 1) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Workspace mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        allowedModes.forEach { candidate ->
                            FilterChip(
                                modifier = Modifier.testTag("workspace-mode-" + candidate.label.replace(" ", "-")),
                                selected = candidate == mode,
                                onClick = { modeName = candidate.name },
                                label = { Text(candidate.label) }
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "Telephone workspace",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        item {
            val selectedIndex = tabs.indexOf(tab).coerceAtLeast(0)
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = 0.dp,
                divider = {}
            ) {
                tabs.forEach { candidate ->
                    Tab(
                        modifier = Modifier.testTag("workspace-tab-" + candidate.label.replace(" ", "-")),
                        selected = candidate == tab,
                        onClick = { tabName = candidate.name },
                        text = { Text(candidate.label) }
                    )
                }
            }
        }

        item {
            when (tab) {
                WorkspaceTab.MAP -> WorkspaceMapSurface(item, knowledgeBaseRevision, readiness)
                WorkspaceTab.STREETS -> WorkspaceStreetsSurface(item)
                WorkspaceTab.ADDRESSES -> WorkspaceAddressInventorySurface(item)
                WorkspaceTab.PHONE_LIST -> WorkspacePhoneInventorySurface(item)
                WorkspaceTab.BUILDINGS -> WorkspaceBuildingsSurface(item)
                WorkspaceTab.DETAILS -> WorkspaceDetailsSurface(item, knowledgeBaseRevision, readiness)
            }
        }
    }
}

@Composable
private fun WorkspaceReadinessSummary(model: WorkspaceReadinessModel) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("workspace-readiness"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Verification & readiness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadinessMetric("Verified", model.verifiedChecks, "checks", Modifier.weight(1f))
                ReadinessMetric("Needs review", model.reviewChecks, "checks", Modifier.weight(1f))
                ReadinessMetric("Conflicts", model.blockingConflicts, "blocking", Modifier.weight(1f))
            }
            HorizontalDivider()
            WorkspaceInfoRow("Exact artifact release", model.exactArtifactReleaseLabel)
            WorkspaceInfoRow("Candidate readiness", model.candidateReadinessLabel)
            WorkspaceInfoRow("Source provenance", model.provenanceLabel)
        }
    }
}

@Composable
private fun ReadinessMetric(label: String, value: Int, suffix: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(suffix, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorkspaceMapSurface(
    item: TerritoryDashboardItem,
    knowledgeBaseRevision: String,
    readiness: WorkspaceReadinessModel
) {
    val a = item.assignment
    WorkspaceSurfaceCard("Map") {
        Text(
            "Locked assignment context",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "This workspace reads the authoritative assignment state. The actual vector-PDF preview is a later production-UI package.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider()
        WorkspaceInfoRow("Knowledge Base", knowledgeBaseRevision)
        WorkspaceInfoRow("Geometry", readiness.geometryLabel)
        WorkspaceInfoRow("Roads", a.roadCount.toString())
        WorkspaceInfoRow("Buildings", a.buildingCount.toString())
        WorkspaceInfoRow("Housing", a.housingType)
        WorkspaceInfoRow("Canonical file", a.canonicalFilename)
    }
}

@Composable
private fun WorkspaceStreetsSurface(item: TerritoryDashboardItem) {
    val a = item.assignment
    WorkspaceSurfaceCard("Streets") {
        WorkspaceInfoRow("Road assignment", a.roadAssignmentStatus)
        WorkspaceInfoRow("Named roads", a.namedRoadCount.toString())
        WorkspaceInfoRow("Rendered road segments", a.roadCount.toString())
        HorizontalDivider()
        if (a.roads.isEmpty()) {
            Text(
                "No locked road geometry is available. The workspace remains fail-closed rather than inventing streets.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            a.roads.take(20).forEach { road ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(road.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        road.status + " • " + road.role + " • inside " + road.insideSide,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (a.roads.size > 20) {
                Text(
                    "+" + (a.roads.size - 20) + " more road segments",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun WorkspaceAddressInventorySurface(item: TerritoryDashboardItem) {
    WorkspaceSurfaceCard("Addresses") {
        InventoryUnavailableBanner(
            title = "Verified address inventory not attached",
            detail = "No territory-specific Letter Writing inventory is present in the current Knowledge Base. Verified/review counts remain unavailable rather than guessed.",
            tag = "letter-inventory-unavailable"
        )
        HorizontalDivider()
        WorkspaceInfoRow("Inventory contract", "Phase 1D-B verified")
        WorkspaceInfoRow("Territory binding", item.assignment.displayId)
        WorkspaceInfoRow("Page 2 generation", "Not in Phase 2B")
        WorkspaceInfoRow("Source requirement", "Verified provenance required")
    }
}

@Composable
private fun WorkspacePhoneInventorySurface(item: TerritoryDashboardItem) {
    WorkspaceSurfaceCard("Phone List") {
        InventoryUnavailableBanner(
            title = "Authorized phone inventory not attached",
            detail = "No territory-specific authorized/verified phone inventory is present in the current Knowledge Base. Numbers are never inferred or fabricated.",
            tag = "phone-inventory-unavailable"
        )
        HorizontalDivider()
        WorkspaceInfoRow("Inventory contract", "Phase 1D-C verified")
        WorkspaceInfoRow("Territory binding", item.assignment.displayId)
        WorkspaceInfoRow("Unavailable-number state", "Supported")
        WorkspaceInfoRow("Page 2 generation", "Not in Phase 2B")
    }
}

@Composable
private fun InventoryUnavailableBanner(title: String, detail: String, tag: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WorkspaceBuildingsSurface(item: TerritoryDashboardItem) {
    val a = item.assignment
    WorkspaceSurfaceCard("Buildings") {
        WorkspaceInfoRow("Building assignment", a.buildingAssignmentStatus)
        WorkspaceInfoRow("Building count", a.buildingCount.toString())
        if (a.buildingReauditFailures.isNotEmpty()) {
            WorkspaceInfoRow("Re-audit findings", a.buildingReauditFailures.size.toString())
        }
        HorizontalDivider()
        if (a.buildings.isEmpty()) {
            Text(
                if (a.buildingAssignmentStatus == "not_applicable") {
                    "No building diagram is required for this assignment."
                } else {
                    "No verified building geometry is available in the locked assignment."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            a.buildings.take(12).forEach { building ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(building.label, fontWeight = FontWeight.Medium)
                    Text(
                        building.housingType + " • members " + building.sourceMembers.joinToString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (a.buildings.size > 12) {
                Text(
                    "+" + (a.buildings.size - 12) + " more buildings",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun WorkspaceDetailsSurface(
    item: TerritoryDashboardItem,
    knowledgeBaseRevision: String,
    readiness: WorkspaceReadinessModel
) {
    val a = item.assignment
    WorkspaceSurfaceCard("Details & provenance") {
        WorkspaceInfoRow("Display ID", a.displayId)
        WorkspaceInfoRow("Canonical file", a.canonicalFilename)
        WorkspaceInfoRow("Assignment status", a.status)
        WorkspaceInfoRow("Knowledge Base", knowledgeBaseRevision)
        WorkspaceInfoRow("Source class", a.sourceClass)
        WorkspaceInfoRow("Reference file", a.referenceFile)
        WorkspaceInfoRow("Reference SHA-256", a.referenceSha256)
        WorkspaceInfoRow("Bound source hashes", readiness.sourceCount.toString())
        a.sourceHashes.distinct().take(4).forEachIndexed { index, hash ->
            WorkspaceInfoRow("Source " + (index + 1), hash)
        }
        if (a.approvalNote != null) {
            WorkspaceInfoRow("Approval note", a.approvalNote)
        }
    }
}

@Composable
private fun WorkspaceSurfaceCard(
    title: String,
    content: @Composable Column.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("workspace-surface-" + title.replace(" ", "-")),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                content()
            }
        )
    }
}

@Composable
private fun WorkspaceInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(18.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WorkspaceStatusBadge(status: TerritoryUiStatus) {
    val colors = LocalTerritoryStatusColors.current
    val pair = when (status) {
        TerritoryUiStatus.APPROVED -> colors.approvedContainer to colors.approvedContent
        TerritoryUiStatus.NEEDS_NEW_CARD -> colors.needsNewContainer to colors.needsNewContent
        TerritoryUiStatus.REAUDIT_REQUIRED -> colors.reAuditContainer to colors.reAuditContent
        TerritoryUiStatus.CONFLICT -> colors.conflictContainer to colors.conflictContent
        TerritoryUiStatus.SCREENING -> colors.screeningContainer to colors.screeningContent
    }
    Surface(shape = RoundedCornerShape(100.dp), color = pair.first, contentColor = pair.second) {
        Text(
            status.label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
