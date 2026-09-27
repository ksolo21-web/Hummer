package com.koenterprises.territorycardstudio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.koenterprises.territorycardstudio.core.KnowledgeBaseAssignment
import com.koenterprises.territorycardstudio.core.TerritoryClass
import com.koenterprises.territorycardstudio.core.TerritoryKnowledgeBase

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
    val overlapReviewFindings: Int,
    val exactArtifactReleaseLabel: String,
    val candidateReadinessLabel: String,
    val provenanceLabel: String,
    val sourceCount: Int,
    val geometryLabel: String
) {
    companion object {
        fun from(item: TerritoryDashboardItem, knowledgeBase: TerritoryKnowledgeBase? = null): WorkspaceReadinessModel {
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
            val blockingFindings = knowledgeBase?.crossTerritoryOverlapAudit?.blockingDuplicateWork
                ?.count { a.displayId in it.territories }
                ?: if (item.status == TerritoryUiStatus.CONFLICT) 1 else 0
            val reviewFindings = knowledgeBase?.crossTerritoryOverlapAudit?.reviewCandidates
                ?.count { a.displayId in it.territories }
                ?: if (item.status == TerritoryUiStatus.REAUDIT_REQUIRED) 1 else 0
            return WorkspaceReadinessModel(
                verifiedChecks = verified,
                reviewChecks = review,
                blockingConflicts = blockingFindings,
                overlapReviewFindings = reviewFindings,
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
    onBack: () -> Unit,
    knowledgeBase: TerritoryKnowledgeBase? = null,
    coordinator: AndroidBuildWorkflowCoordinator? = null,
    previewService: AndroidPdfPreviewService? = null
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
    val context = LocalContext.current
    val application = context.applicationContext as TerritoryCardStudioApplication
    val buildCoordinator = coordinator ?: application.services.buildWorkflow
    val preview = previewService ?: application.services.pdfPreview
    var refresh by remember { mutableStateOf(0) }
    val activity = context.previewActivity()
    var active by remember { mutableStateOf(activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false) }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { active = true; refresh++ }
            if (event == Lifecycle.Event.ON_PAUSE) { active = false; refresh++ }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    val sourceStore = remember(context.applicationContext) { SourceMapIntakeStore(context) }
    var workflowScreen by rememberSaveable(assignment.displayId) { mutableStateOf("WORKSPACE") }
    var sourceRevision by rememberSaveable(assignment.displayId) { mutableStateOf(0) }
    var importError by rememberSaveable(assignment.displayId) { mutableStateOf<String?>(null) }
    val intake = remember(assignment.displayId, sourceRevision, refresh, active) {
        if (active) runCatching { sourceStore.verifiedRecord(assignment.displayId) }.getOrNull() else null
    }
    val prepared = remember(assignment.displayId, mode, sourceRevision, workflowScreen, refresh, active) {
        if (active) runCatching { buildCoordinator.workspaceSnapshot(assignment.displayId, mode) }.getOrNull() else null
    }
    val currentVerification = VerificationWorkflowModel.from(item, mode, intake,
        knowledgeBase ?: application.services.knowledgeBase, application.services.activePolicy, prepared)
    val readiness = WorkspaceReadinessModel.from(item, knowledgeBase).let { base ->
        if (prepared == null) base else base.copy(verifiedChecks = currentVerification.verifiedCount,
            reviewChecks = currentVerification.reviewCount + currentVerification.pendingCount,
            candidateReadinessLabel = if (prepared.canBuild) "Current verified inputs ready for Build; candidate remains unapproved" else "Prepared inputs cannot build")
    }
    val previewKind = if (!assignment.needsNewCard) PdfPreviewKind.APPROVED else
        if (mode != WorkspaceMode.REGULAR && buildCoordinator.state(assignment.displayId, mode).packet != null) PdfPreviewKind.PACKET else PdfPreviewKind.FRONT
    if (workflowScreen == "PREVIEW") {
        PdfPreviewScreen(modifier, assignment.displayId, mode, previewKind, preview, "← Back to workspace") { workflowScreen = "WORKSPACE" }
        return
    }
    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importError = runCatching {
                sourceStore.importFromUri(assignment, uri)
            }.exceptionOrNull()?.message
            sourceRevision += 1
        }
    }

    if (workflowScreen == "IMPORT") {
        ImportMapWorkflowScreen(
            modifier = modifier,
            item = item,
            mode = mode,
            intake = intake,
            importError = importError,
            onChooseSource = {
                importError = null
                sourcePicker.launch(arrayOf("application/pdf", "image/jpeg", "image/png"))
            },
            onClearSource = {
                sourceStore.clear(assignment.displayId)
                importError = null
                sourceRevision += 1
            },
            onContinueVerification = { workflowScreen = "VERIFY" },
            onBackToWorkspace = { workflowScreen = "WORKSPACE" }
        )
        return
    }

    if (workflowScreen == "BUILD") {
        BuildWorkflowScreen(modifier, assignment.displayId, mode, application.services.buildWorkflow, application.services.pdfPreview, application.services.candidateReview) {
            workflowScreen = "WORKSPACE"
        }
        return
    }

    if (workflowScreen == "EXPORT") {
        ApprovedExportScreen(modifier, assignment.displayId, application.services.approvedExport) {
            workflowScreen = "WORKSPACE"
        }
        return
    }

    if (workflowScreen == "VERIFY") {
        VerificationWorkflowScreen(
            modifier = modifier,
            item = item,
            mode = mode,
            intake = intake,
            knowledgeBase = knowledgeBase ?: application.services.knowledgeBase,
            onlinePolicy = application.services.activePolicy,
            prepared = prepared,
            onBackToImport = { workflowScreen = "IMPORT" },
            onBackToWorkspace = { workflowScreen = "WORKSPACE" }
        )
        return
    }

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

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f).testTag("workspace-import-map"),
                    onClick = { workflowScreen = "IMPORT" }
                ) {
                    Text("Import Map")
                }
                Button(
                    modifier = Modifier.weight(1f).testTag("workspace-verification"),
                    onClick = { workflowScreen = "VERIFY" }
                ) {
                    Text("Verification")
                }
            }
        }

        item {
            Button(onClick = { workflowScreen = "BUILD" },
                modifier = Modifier.fillMaxWidth().testTag("workspace-build")) {
                Text("Build / Generate Page 2")
            }
        }

        item {
            Button(onClick = { workflowScreen = "EXPORT" },
                modifier = Modifier.fillMaxWidth().testTag("workspace-export")) {
                Text("Export existing approved card")
            }
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
                WorkspaceTab.MAP -> WorkspaceMapSurface(item, knowledgeBaseRevision, readiness, mode, previewKind, preview, refresh, active, { refresh++ }) { workflowScreen = "PREVIEW" }
                WorkspaceTab.STREETS -> WorkspaceStreetsSurface(item)
                WorkspaceTab.ADDRESSES -> WorkspaceAddressInventorySurface(item, prepared?.inventory)
                WorkspaceTab.PHONE_LIST -> WorkspacePhoneInventorySurface(item, prepared?.inventory)
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
            WorkspaceInfoRow("Overlap review findings", model.overlapReviewFindings.toString())
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
    readiness: WorkspaceReadinessModel,
    mode: WorkspaceMode, kind: PdfPreviewKind, preview: AndroidPdfPreviewService,
    revision: Int, active: Boolean, onRefresh: () -> Unit, onOpen: () -> Unit
) {
    val a = item.assignment
    WorkspaceSurfaceCard("Map") {
        Text(
            "Locked assignment context",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        WorkspaceExactPreview(a.displayId, mode, kind, preview, revision, active, onOpen)
        TextButton(onClick = onRefresh, modifier = Modifier.testTag("workspace-recheck")) { Text("Recheck current data") }
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
            a.roads.forEachIndexed { index, road ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(road.name, modifier = Modifier.testTag("workspace-road-" + index), fontWeight = FontWeight.Medium)
                    Text(
                        road.status + " • " + road.role + " • inside " + road.insideSide,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceAddressInventorySurface(item: TerritoryDashboardItem, snapshot: WorkspaceInventorySnapshot?) {
    WorkspaceSurfaceCard("Addresses") {
        if (snapshot != null) WorkspaceInventoryContent(snapshot) else InventoryUnavailableBanner(
            title = "Verified address inventory not attached",
            detail = "No current verified Letter Writing inventory is prepared for this territory. Verified/review counts remain unavailable rather than guessed.",
            tag = "letter-inventory-unavailable"
        )
        HorizontalDivider()
        WorkspaceInfoRow("Inventory contract", "Phase 1D-B verified")
        WorkspaceInfoRow("Territory binding", item.assignment.displayId)
        WorkspaceInfoRow("Page 2 generation", "Available from Build when verified")
        WorkspaceInfoRow("Source requirement", "Verified provenance required")
    }
}

@Composable
private fun WorkspacePhoneInventorySurface(item: TerritoryDashboardItem, snapshot: WorkspaceInventorySnapshot?) {
    WorkspaceSurfaceCard("Phone List") {
        if (snapshot != null) WorkspaceInventoryContent(snapshot) else InventoryUnavailableBanner(
            title = "Authorized phone inventory not attached",
            detail = "No current authorized/verified phone inventory is prepared for this territory. Numbers are never inferred or fabricated.",
            tag = "phone-inventory-unavailable"
        )
        HorizontalDivider()
        WorkspaceInfoRow("Inventory contract", "Phase 1D-C verified")
        WorkspaceInfoRow("Territory binding", item.assignment.displayId)
        WorkspaceInfoRow("Unavailable-number state", "Supported")
        WorkspaceInfoRow("Page 2 generation", "Available from Build when verified")
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
            a.buildings.forEachIndexed { index, building ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(building.label, modifier = Modifier.testTag("workspace-building-" + index), fontWeight = FontWeight.Medium)
                    Text(
                        building.housingType + " • members " + building.sourceMembers.joinToString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
        a.sourceHashes.distinct().forEachIndexed { index, hash ->
            Column(Modifier.fillMaxWidth().testTag("workspace-source-" + index)) {
                WorkspaceInfoRow("Source " + (index + 1), hash)
            }
        }
        a.approvalNote?.let { note ->
            WorkspaceInfoRow("Approval note", note)
        }
    }
}

@Composable
private fun WorkspaceSurfaceCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
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
            maxLines = Int.MAX_VALUE
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
