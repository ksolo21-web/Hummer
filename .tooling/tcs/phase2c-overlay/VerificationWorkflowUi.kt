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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.koenterprises.territorycardstudio.core.OnlineSourcePolicy
import com.koenterprises.territorycardstudio.core.TerritoryKnowledgeBase

enum class VerificationUiState(val label: String) {
    VERIFIED("Verified"),
    NEEDS_REVIEW("Needs review"),
    BLOCKED("Blocked"),
    PENDING("Pending"),
    NOT_APPLICABLE("Not applicable")
}

data class VerificationCategoryUi(
    val key: String,
    val title: String,
    val state: VerificationUiState,
    val summary: String,
    val provenance: String? = null
)

data class VerificationWorkflowModel(
    val territoryDisplayId: String,
    val mode: WorkspaceMode,
    val categories: List<VerificationCategoryUi>,
    val verifiedCount: Int,
    val reviewCount: Int,
    val blockedCount: Int,
    val pendingCount: Int,
    val buildMayProceed: Boolean,
    val policyRevision: String
) {
    companion object {
        fun from(
            item: TerritoryDashboardItem,
            mode: WorkspaceMode,
            intake: SourceMapIntakeRecord?,
            knowledgeBase: TerritoryKnowledgeBase,
            onlinePolicy: OnlineSourcePolicy
        ): VerificationWorkflowModel {
            val a = item.assignment
            val blockingOverlap = knowledgeBase.crossTerritoryOverlapAudit.blockingDuplicateWork
                .any { a.displayId in it.territories }
            val reviewOverlap = knowledgeBase.crossTerritoryOverlapAudit.reviewCandidates
                .any { a.displayId in it.territories }

            val sourceTruth = when {
                intake == null -> VerificationCategoryUi(
                    key = "source_truth",
                    title = "Source truth",
                    state = VerificationUiState.BLOCKED,
                    summary = "No source map is attached to this territory.",
                    provenance = "Import is required before candidate verification."
                )
                intake.territoryDisplayId != a.displayId ||
                    intake.canonicalFilename != a.canonicalFilename -> VerificationCategoryUi(
                    key = "source_truth",
                    title = "Source truth",
                    state = VerificationUiState.BLOCKED,
                    summary = "Imported source identity does not match the selected territory.",
                    provenance = "Fail closed: source identity mismatch."
                )
                else -> VerificationCategoryUi(
                    key = "source_truth",
                    title = "Source truth",
                    state = VerificationUiState.NEEDS_REVIEW,
                    summary = "Source map is hash-bound to this territory, but still must reconcile to the locked assignment.",
                    provenance = intake.provenanceType + " • " + intake.sha256.take(16) + "…"
                )
            }

            val geometry = VerificationCategoryUi(
                key = "geometry",
                title = "Live geometry",
                state = if (intake == null) VerificationUiState.BLOCKED else VerificationUiState.PENDING,
                summary = if (intake == null) {
                    "Live verification cannot start without an imported source map."
                } else {
                    "Candidate topology has not been built yet. Live verification remains required before rendering."
                },
                provenance = "Policy " + onlinePolicy.revision +
                    " • minimum " + onlinePolicy.releasePolicy.minimumIndependentGeometrySources +
                    " independent geometry sources"
            )

            val overlap = when {
                blockingOverlap -> VerificationCategoryUi(
                    "overlap",
                    "Cross-territory overlap",
                    VerificationUiState.BLOCKED,
                    "The Knowledge Base has a blocking duplicate-work conflict for this territory.",
                    knowledgeBase.crossTerritoryOverlapAudit.policy
                )
                reviewOverlap -> VerificationCategoryUi(
                    "overlap",
                    "Cross-territory overlap",
                    VerificationUiState.NEEDS_REVIEW,
                    "The Knowledge Base has an overlap review candidate for this territory.",
                    knowledgeBase.crossTerritoryOverlapAudit.policy
                )
                else -> VerificationCategoryUi(
                    "overlap",
                    "Cross-territory overlap",
                    VerificationUiState.VERIFIED,
                    "No blocking duplicate-work conflict is recorded for this territory.",
                    knowledgeBase.crossTerritoryOverlapAudit.policy
                )
            }

            val labels = VerificationCategoryUi(
                key = "labels",
                title = "Street labels",
                state = VerificationUiState.PENDING,
                summary = "Candidate labels do not exist yet. The frozen direct → curved → relocation → callout → detail hierarchy will run during Build.",
                provenance = "Frozen Phase 1 label contract"
            )

            val buildings = when (a.buildingAssignmentStatus) {
                "not_applicable" -> VerificationCategoryUi(
                    "buildings",
                    "Buildings / sites",
                    VerificationUiState.NOT_APPLICABLE,
                    "No building/site diagram is required for this assignment.",
                    a.buildingAssignmentStatus
                )
                "locked_300_301" -> VerificationCategoryUi(
                    "buildings",
                    "Buildings / sites",
                    VerificationUiState.VERIFIED,
                    "Locked 300/301 building/site assignment is available from the Knowledge Base.",
                    a.referenceSha256.take(16) + "…"
                )
                else -> VerificationCategoryUi(
                    "buildings",
                    "Buildings / sites",
                    VerificationUiState.NEEDS_REVIEW,
                    "Building/site assignment requires re-audit before a candidate may be released.",
                    a.buildingAssignmentStatus
                )
            }

            val colorsTemplate = VerificationCategoryUi(
                key = "colors_template",
                title = "Colors & template",
                state = if (a.colorRoleReview?.passed == false) VerificationUiState.BLOCKED else VerificationUiState.VERIFIED,
                summary = if (a.colorRoleReview?.passed == false) {
                    "Locked assignment color-role review has unresolved failures."
                } else {
                    "Junction-to-junction color rules and the canonical R48 PDF authority are locked."
                },
                provenance = "Frozen Phase 1 renderer authority"
            )

            val fieldRelease = when {
                a.needsNewCard -> VerificationCategoryUi(
                    "field_release",
                    "Exact field release",
                    VerificationUiState.BLOCKED,
                    "This territory needs a new card. No generated candidate may field-release without explicit approval.",
                    "needs_new_card • candidate-unapproved"
                )
                a.fieldReleaseAllowedForExactArtifact -> VerificationCategoryUi(
                    "field_release",
                    "Exact field release",
                    VerificationUiState.VERIFIED,
                    "The exact current approved artifact remains eligible. Importing a new source does not approve or replace it.",
                    a.referenceSha256.take(16) + "…"
                )
                else -> VerificationCategoryUi(
                    "field_release",
                    "Exact field release",
                    VerificationUiState.BLOCKED,
                    "No exact approved artifact is currently field-release eligible.",
                    a.status
                )
            }

            val inventory = when (mode) {
                WorkspaceMode.REGULAR -> VerificationCategoryUi(
                    "inventory",
                    "Address / phone inventory",
                    VerificationUiState.NOT_APPLICABLE,
                    "Regular territory mode does not require a generated address/phone Page 2.",
                    null
                )
                WorkspaceMode.LETTER_WRITING -> VerificationCategoryUi(
                    "inventory",
                    "Letter Writing addresses",
                    VerificationUiState.BLOCKED,
                    "No territory-specific verified address inventory is attached yet.",
                    "Phase 1D-B contract required; counts are not guessed."
                )
                WorkspaceMode.TELEPHONE -> VerificationCategoryUi(
                    "inventory",
                    "Telephone phone list",
                    VerificationUiState.BLOCKED,
                    "No territory-specific authorized/verified phone inventory is attached yet.",
                    "Phase 1D-C contract required; phone data is never inferred."
                )
            }

            val categories = listOf(
                sourceTruth,
                geometry,
                overlap,
                labels,
                buildings,
                colorsTemplate,
                fieldRelease,
                inventory
            )
            val hardBlocked = categories.count { it.state == VerificationUiState.BLOCKED }
            val review = categories.count { it.state == VerificationUiState.NEEDS_REVIEW }
            val pending = categories.count { it.state == VerificationUiState.PENDING }
            val verified = categories.count {
                it.state == VerificationUiState.VERIFIED ||
                    it.state == VerificationUiState.NOT_APPLICABLE
            }
            return VerificationWorkflowModel(
                territoryDisplayId = a.displayId,
                mode = mode,
                categories = categories,
                verifiedCount = verified,
                reviewCount = review,
                blockedCount = hardBlocked,
                pendingCount = pending,
                buildMayProceed = hardBlocked == 0 && review == 0 && pending == 0,
                policyRevision = onlinePolicy.revision
            )
        }
    }
}

@Composable
fun ImportMapWorkflowScreen(
    modifier: Modifier,
    item: TerritoryDashboardItem,
    mode: WorkspaceMode,
    intake: SourceMapIntakeRecord?,
    importError: String?,
    onChooseSource: () -> Unit,
    onClearSource: () -> Unit,
    onContinueVerification: () -> Unit,
    onBackToWorkspace: () -> Unit
) {
    val a = item.assignment
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("import-map-screen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TextButton(onClick = onBackToWorkspace) { Text("← Back to workspace") }
            Text("Import Map", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "Territory " + a.displayId + " • " + mode.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Assignment authority stays locked", fontWeight = FontWeight.SemiBold)
                    Text(
                        "A selected map is source evidence only. It cannot redefine work boundaries, road colors, buildings, or another territory’s geography.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Selected territory", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    WorkflowInfoRow("Display ID", a.displayId)
                    WorkflowInfoRow("Canonical file", a.canonicalFilename)
                    WorkflowInfoRow("Current reference", a.referenceFile)
                    WorkflowInfoRow("Reference SHA-256", a.referenceSha256)
                    WorkflowInfoRow("Accepted source types", "PDF, JPG, PNG")
                    WorkflowInfoRow("Maximum source size", "50 MB")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("import-source-card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Source map", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (intake == null) {
                        Text(
                            "No source map imported for this territory.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        WorkflowInfoRow("File", intake.sourceFilename)
                        WorkflowInfoRow("Type", intake.mimeType)
                        WorkflowInfoRow("Bytes", intake.byteCount.toString())
                        WorkflowInfoRow("SHA-256", intake.sha256)
                        WorkflowInfoRow("Imported", intake.importedAtUtc)
                        WorkflowInfoRow("Provenance", "User-provided source map")
                        WorkflowInfoRow("Assignment authority", "No")
                    }
                    importError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.testTag("choose-source-map"),
                            onClick = onChooseSource
                        ) {
                            Text(if (intake == null) "Choose source map" else "Replace source map")
                        }
                        if (intake != null) {
                            OutlinedButton(onClick = onClearSource) { Text("Remove") }
                        }
                    }
                }
            }
        }

        item {
            Button(
                modifier = Modifier.fillMaxWidth().testTag("continue-verification"),
                enabled = intake != null,
                onClick = onContinueVerification
            ) {
                Text("Continue to Verification")
            }
        }
    }
}

@Composable
fun VerificationWorkflowScreen(
    modifier: Modifier,
    item: TerritoryDashboardItem,
    mode: WorkspaceMode,
    intake: SourceMapIntakeRecord?,
    knowledgeBase: TerritoryKnowledgeBase,
    onlinePolicy: OnlineSourcePolicy,
    onBackToImport: () -> Unit,
    onBackToWorkspace: () -> Unit
) {
    val model = VerificationWorkflowModel.from(item, mode, intake, knowledgeBase, onlinePolicy)
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("verification-screen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBackToImport) { Text("← Import") }
                TextButton(onClick = onBackToWorkspace) { Text("Workspace") }
            }
            Text("Verification", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "Territory " + model.territoryDisplayId + " • " + model.mode.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            VerificationCountSummary(model)
        }

        item {
            Surface(
                color = if (model.buildMayProceed) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (model.buildMayProceed) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (model.buildMayProceed) "Verification complete" else "Build remains locked",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (model.buildMayProceed) {
                            "All required verification categories are resolved."
                        } else {
                            "Blocked, review, or pending categories must be resolved by the proven engine before Build."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        items(model.categories.size) { index ->
            VerificationCategoryCard(model.categories[index])
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Verification provenance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    WorkflowInfoRow("Online policy", model.policyRevision)
                    WorkflowInfoRow("Knowledge Base", knowledgeBase.revision)
                    WorkflowInfoRow("Reference SHA-256", item.assignment.referenceSha256)
                    WorkflowInfoRow("Imported source SHA-256", intake?.sha256 ?: "None")
                    WorkflowInfoRow("Imported source authority", if (intake == null) "None" else "Evidence only — never assignment authority")
                }
            }
        }
    }
}

@Composable
private fun VerificationCountSummary(model: VerificationWorkflowModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VerificationMetric("Verified", model.verifiedCount, Modifier.weight(1f))
        VerificationMetric("Review", model.reviewCount, Modifier.weight(1f))
        VerificationMetric("Blocked", model.blockedCount, Modifier.weight(1f))
        VerificationMetric("Pending", model.pendingCount, Modifier.weight(1f))
    }
}

@Composable
private fun VerificationMetric(label: String, count: Int, modifier: Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun VerificationCategoryCard(category: VerificationCategoryUi) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("verification-" + category.key),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(category.title, fontWeight = FontWeight.SemiBold)
                VerificationStatePill(category.state)
            }
            Text(category.summary, style = MaterialTheme.typography.bodyMedium)
            category.provenance?.let {
                HorizontalDivider()
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VerificationStatePill(state: VerificationUiState) {
    val container = when (state) {
        VerificationUiState.VERIFIED -> MaterialTheme.colorScheme.primaryContainer
        VerificationUiState.NOT_APPLICABLE -> MaterialTheme.colorScheme.surfaceVariant
        VerificationUiState.NEEDS_REVIEW -> MaterialTheme.colorScheme.tertiaryContainer
        VerificationUiState.BLOCKED -> MaterialTheme.colorScheme.errorContainer
        VerificationUiState.PENDING -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when (state) {
        VerificationUiState.VERIFIED -> MaterialTheme.colorScheme.onPrimaryContainer
        VerificationUiState.NOT_APPLICABLE -> MaterialTheme.colorScheme.onSurfaceVariant
        VerificationUiState.NEEDS_REVIEW -> MaterialTheme.colorScheme.onTertiaryContainer
        VerificationUiState.BLOCKED -> MaterialTheme.colorScheme.onErrorContainer
        VerificationUiState.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(100.dp)) {
        Text(
            state.label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun WorkflowInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.width(18.dp))
        Text(
            value,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
