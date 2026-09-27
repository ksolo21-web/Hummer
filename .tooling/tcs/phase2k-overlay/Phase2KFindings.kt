package com.koenterprises.territorycardstudio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.koenterprises.territorycardstudio.core.TerritoryKnowledgeBase

/** Read-only links to evidence; no validation or authority rules live here. */
data class ScopedFinding(val category: String, val itemId: String?, val state: VerificationUiState,
    val reason: String, val provenance: String?, val tab: WorkspaceTab) {
    val itemLabel: String get() = itemId ?: "Category level; affected item unavailable"
}

object Phase2KFindings {
    fun from(item: TerritoryDashboardItem, mode: WorkspaceMode, verification: VerificationWorkflowModel,
        kb: TerritoryKnowledgeBase, prepared: WorkspacePreparedSnapshot?): List<ScopedFinding> {
        val id = item.assignment.displayId
        require(verification.territoryDisplayId == id && verification.mode == mode)
        val categoryTab: (String) -> WorkspaceTab = { key -> when (key) {
            "inventory" -> if (mode == WorkspaceMode.TELEPHONE) WorkspaceTab.PHONE_LIST else if (mode == WorkspaceMode.LETTER_WRITING) WorkspaceTab.ADDRESSES else WorkspaceTab.DETAILS
            "buildings" -> WorkspaceTab.BUILDINGS
            "labels", "overlap", "geometry", "colors_template" -> WorkspaceTab.STREETS
            else -> WorkspaceTab.DETAILS
        } }
        val actual = buildList {
            item.assignment.buildingReauditFailures.forEach { reason ->
                add(ScopedFinding("buildings", null, VerificationUiState.BLOCKED, reason,
                    "Knowledge Base ${kb.revision}", WorkspaceTab.BUILDINGS))
            }
            item.assignment.colorRoleReview?.failures?.forEach { reason ->
                add(ScopedFinding("colors_template", null, VerificationUiState.BLOCKED, reason,
                    "Knowledge Base ${kb.revision}", WorkspaceTab.STREETS))
            }
            kb.crossTerritoryOverlapAudit.blockingDuplicateWork.filter { id in it.territories }.forEach { overlap ->
                add(ScopedFinding("overlap", overlap.road, VerificationUiState.BLOCKED, overlap.reason,
                    "Territories ${overlap.territories.joinToString()} • ${overlap.confidence}", WorkspaceTab.STREETS))
            }
            kb.crossTerritoryOverlapAudit.reviewCandidates.filter { id in it.territories }.forEach { overlap ->
                add(ScopedFinding("overlap", overlap.road, VerificationUiState.NEEDS_REVIEW, overlap.reason,
                    "Territories ${overlap.territories.joinToString()} • ${overlap.confidence}", WorkspaceTab.STREETS))
            }
            prepared?.inventory?.records?.forEach { record ->
                val status = record.details.firstOrNull { it.first == "Status" || it.first == "Address status" }?.second
                val phone = record.details.firstOrNull { it.first == "Phone status" }?.second
                val neighbors = record.details.firstOrNull { it.first == "Neighbor conflicts" }?.second
                if (status in setOf("CONFLICT", "NEEDS_REVIEW", "UNVERIFIED") || phone in setOf("CONFLICT", "NEEDS_REVIEW") || neighbors != null && neighbors != "None")
                    add(ScopedFinding("inventory", record.id,
                        if (status == "CONFLICT" || phone == "CONFLICT" || neighbors != null && neighbors != "None") VerificationUiState.BLOCKED else VerificationUiState.NEEDS_REVIEW,
                        "${record.address} • address $status • phone ${phone ?: "not applicable"} • neighbor ${neighbors ?: "unknown"}",
                        "Inventory SHA-256 ${prepared.inventory.hash}", categoryTab("inventory")))
            }
            verification.categories.filter { it.state in setOf(VerificationUiState.BLOCKED, VerificationUiState.NEEDS_REVIEW) }.forEach { category ->
                if (none { it.category == category.key }) add(ScopedFinding(category.key, null, category.state,
                    category.summary, category.provenance, categoryTab(category.key)))
            }
        }
        return actual.distinct()
    }
}

@Composable
fun Phase2KFindingsScreen(modifier: Modifier, territoryId: String, findings: List<ScopedFinding>,
    validated: Boolean, selectedCategory: String? = null, onOpen: (ScopedFinding) -> Unit, onBack: () -> Unit) {
    LazyColumn(modifier.fillMaxSize().testTag("findings-screen"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Findings", style = MaterialTheme.typography.headlineLarge)
            Text("Territory $territoryId • read-only evidence")
            selectedCategory?.let { Text("Category: ${it.replace('_', ' ')}") }
            if (!validated) Text("No current prepared verification. Missing evidence cannot be treated as a clean result.",
                modifier = Modifier.testTag("findings-unvalidated"))
        }
        val shown = findings.filter { selectedCategory == null || it.category == selectedCategory }
        if (shown.isEmpty()) item { Text(if (validated) "No actionable findings in the current prepared result." else
            "No recorded item findings. Current preparation is unavailable.", modifier = Modifier.testTag("findings-empty")) }
        items(shown) { finding ->
            Card(Modifier.fillMaxWidth().testTag("finding-${finding.category}-${finding.itemId ?: "category"}")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${finding.category.replace('_',' ')} • ${finding.state.label}", style = MaterialTheme.typography.titleMedium)
                    Text(finding.itemLabel)
                    Text(finding.reason)
                    finding.provenance?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { onOpen(finding) }, modifier = Modifier.testTag("finding-open-${finding.category}")) {
                        Text("Open ${finding.tab.label} • ${finding.itemLabel}")
                    }
                }
            }
        }
    }
}
