package com.koenterprises.territorycardstudio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.koenterprises.territorycardstudio.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only projection. Authorization remains in the frozen artifact boundary. */
data class KnowledgeRecord(val assignment: KnowledgeBaseAssignment, val referenceEligible: Boolean,
    val releaseReason: String, val patches: List<PermanentPatch>, val blockers: List<OverlapCandidate>,
    val warnings: List<OverlapCandidate>)

object KnowledgeRecords {
    fun resolve(kb: TerritoryKnowledgeBase, id: String): KnowledgeRecord? {
        val a = kb.assignments[id] ?: return null
        val decision = PdfArtifactBoundary.authorizeExactApprovedPassthrough(kb, id, a.canonicalFilename, a.referenceSha256)
        return KnowledgeRecord(a, decision.action == PdfArtifactAction.EXACT_APPROVED_PASSTHROUGH && decision.fieldReleaseState == PdfFieldReleaseState.ALLOWED, decision.reason,
            kb.permanentPatches.values.filter { it.territory == id || id in it.territories || (it.territory == null && it.territories.isEmpty()) }.sortedBy { it.id },
            kb.crossTerritoryOverlapAudit.blockingDuplicateWork.filter { id in it.territories },
            kb.crossTerritoryOverlapAudit.reviewCandidates.filter { id in it.territories })
    }
}

/** Modal destination retains the underlying Workspace and its single picker owner. */
@Composable
fun KnowledgeBaseDestination(kb: TerritoryKnowledgeBase, policyRevision: String, endpointRevision: String,
    exportService: AndroidApprovedExportService, selectedId: String?, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            KnowledgeBaseScreen(Modifier.fillMaxSize().safeDrawingPadding(), kb, policyRevision, endpointRevision,
                exportService, selectedId, onClose)
        }
    }
}

@Composable
fun KnowledgeBaseScreen(modifier: Modifier, kb: TerritoryKnowledgeBase, policyRevision: String,
    endpointRevision: String, exportService: AndroidApprovedExportService, selectedId: String?, onClose: () -> Unit) {
    var section by rememberSaveable { mutableStateOf("Territories") }
    var query by rememberSaveable { mutableStateOf("") }
    var recordId by rememberSaveable { mutableStateOf<String?>(null) }
    val record = recordId?.let { KnowledgeRecords.resolve(kb, it) }
    Column(modifier.testTag("knowledge-screen").padding(horizontal = 16.dp)) {
        TextButton(onClick = onClose, modifier = Modifier.testTag("knowledge-close")) { Text("← Return to workspace / territories") }
        Text("Knowledge Base", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Read-only • ${kb.revision}", style = MaterialTheme.typography.bodyMedium)
        if (recordId != null) {
            TextButton(onClick = { recordId = null }, modifier = Modifier.testTag("knowledge-back")) { Text("← All records") }
            if (record == null) Text("This territory is unavailable. No authority is inferred.", Modifier.testTag("knowledge-missing"))
            else key(recordId, kb) { KnowledgeRecordView(record, exportService) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Territories", "Rules", "Conflicts").forEach { label ->
                    FilterChip(selected = section == label, onClick = { section = label },
                        label = { Text(label) }, modifier = Modifier.testTag("knowledge-tab-$label"))
                }
            }
            when (section) {
                "Territories" -> {
                    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                        label = { Text("Find territory or reference") }, modifier = Modifier.fillMaxWidth().testTag("knowledge-search"))
                    val records = remember(kb, query) { kb.assignments.values.filter {
                        query.isBlank() || it.displayId.contains(query.trim(), true) || it.canonicalFilename.contains(query.trim(), true)
                    }.sortedWith(compareBy({ it.identity.baseNumber }, { it.displayId })) }
                    Text("${records.size} of ${kb.assignments.size} records", Modifier.padding(vertical = 8.dp))
                    if (selectedId != null) TextButton(onClick = { recordId = selectedId }, modifier = Modifier.testTag("knowledge-selected")) { Text("Open selected territory $selectedId") }
                    LazyColumn(Modifier.weight(1f).testTag("knowledge-list"), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        if (records.isEmpty()) item { Text("No matching territory records.", Modifier.testTag("knowledge-empty")) }
                        items(records, key = { it.displayId }) { a ->
                            OutlinedCard(onClick = { recordId = a.displayId }, modifier = Modifier.fillMaxWidth().testTag("knowledge-record-${a.displayId}")) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Territory ${a.displayId}", style = MaterialTheme.typography.titleMedium)
                                    Text(a.canonicalFilename)
                                    Text(if (a.needsNewCard) "Needs new card • field release blocked" else a.status,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                "Rules" -> LazyColumn(Modifier.weight(1f).testTag("knowledge-list"), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { KnowledgePanel("Loaded authority", "knowledge-rules") {
                        KnowledgeFact("Knowledge Base revision", kb.revision)
                        KnowledgeFact("Knowledge Base schema", kb.schemaVersion.toString())
                        KnowledgeFact("Online policy revision", policyRevision)
                        KnowledgeFact("Endpoint policy revision", endpointRevision)
                        KnowledgeFact("Road color partition", kb.policy.roadColorPartition)
                        KnowledgeFact("Unknown assignment behavior", kb.policy.unknownAssignmentBehavior)
                        Text("Reference eligibility does not establish a local PDF's availability. Local candidate approval does not promote reference authority.")
                    } }
                    item { Text("Permanent patches (${kb.permanentPatches.size})", style = MaterialTheme.typography.titleLarge) }
                    if (kb.permanentPatches.isEmpty()) item { Text("No permanent patches are recorded.") }
                    items(kb.permanentPatches.values.sortedBy { it.id }, key = { it.id }) { PatchPanel(it) }
                }
                else -> LazyColumn(Modifier.weight(1f).testTag("knowledge-list"), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { KnowledgePanel("Overlap evidence", "knowledge-conflicts") {
                        Text(kb.crossTerritoryOverlapAudit.policy)
                        Text("Warnings are evidence, not permission to change assignments or release a card.")
                    } }
                    item { Text("Blocking duplicate work (${kb.crossTerritoryOverlapAudit.blockingDuplicateWork.size})", style = MaterialTheme.typography.titleMedium) }
                    if (kb.crossTerritoryOverlapAudit.blockingDuplicateWork.isEmpty()) item { Text("No blocking duplicate-work findings recorded.", Modifier.testTag("knowledge-no-blockers")) }
                    items(kb.crossTerritoryOverlapAudit.blockingDuplicateWork) { FindingPanel("Blocking", it) }
                    item { Text("Review warnings (${kb.crossTerritoryOverlapAudit.reviewCandidates.size})", style = MaterialTheme.typography.titleMedium) }
                    if (kb.crossTerritoryOverlapAudit.reviewCandidates.isEmpty()) item { Text("No overlap review warnings recorded.") }
                    items(kb.crossTerritoryOverlapAudit.reviewCandidates) { FindingPanel("Review", it) }
                    item { Text("Name-only overlap groups (${kb.crossTerritoryOverlapAudit.nameOnlyOverlapGroups.size})", style = MaterialTheme.typography.titleMedium) }
                    items(kb.crossTerritoryOverlapAudit.nameOnlyOverlapGroups) { FindingPanel("Name-only context", it) }
                }
            }
        }
    }
}

@Composable
private fun KnowledgeRecordView(record: KnowledgeRecord, service: AndroidApprovedExportService) {
    val a = record.assignment
    var local by remember { mutableStateOf<ApprovedExportState?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(a.displayId, service) {
        try { local = withContext(Dispatchers.IO) { service.state(a.displayId) } }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { failure = "Local availability could not be checked. Return to Export to recheck." }
    }
    LazyColumn(Modifier.fillMaxWidth().testTag("knowledge-detail-list"), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { KnowledgePanel("Territory ${a.displayId}", "knowledge-identity") {
            KnowledgeFact("Canonical filename", a.canonicalFilename)
            KnowledgeFact("Assignment status", a.status)
            KnowledgeFact("Reference eligibility", if (record.referenceEligible) "Eligible for exact approved passthrough" else "Blocked: ${record.releaseReason}")
            KnowledgeFact("Local approved copy — checked on open", failure ?: local?.let { if (it.ticket != null) "Exact hash verified • ${it.ticket.byteCount} bytes" else it.blocker ?: "Unavailable" } ?: "Checking…")
            Text("Export rechecks the bytes and authority before every save. Local candidate approval does not enable field export.")
        } }
        item { KnowledgePanel("Reference & provenance", "knowledge-provenance") {
            KnowledgeFact(if (a.needsNewCard) "Legacy reference (not current approved card)" else "Current reference", a.referenceFile)
            KnowledgeFact(if (a.needsNewCard) "Legacy reference SHA-256" else "Reference SHA-256", a.referenceSha256)
            KnowledgeFact("Source class", a.sourceClass)
            KnowledgeFact("Extractor", a.extractor)
            a.approvalNote?.let { KnowledgeFact("Authority note", it) }
        } }
        item { Text("Bound source hashes (${a.sourceHashes.size})", style = MaterialTheme.typography.titleMedium) }
        if (a.sourceHashes.isEmpty()) item { Text("No bound source hashes available.", Modifier.testTag("knowledge-no-sources")) }
        items(a.sourceHashes.size) { i -> KnowledgePanel("Source ${i + 1}", "knowledge-source-$i") { Text(a.sourceHashes[i]) } }
        item { Text("Applicable permanent patches (${record.patches.size})", style = MaterialTheme.typography.titleMedium) }
        if (record.patches.isEmpty()) item { Text("No applicable patches recorded.") }
        items(record.patches) { PatchPanel(it) }
        item { Text("Blocking findings (${record.blockers.size}) • Review warnings (${record.warnings.size})", style = MaterialTheme.typography.titleMedium) }
        if (record.blockers.isEmpty() && record.warnings.isEmpty()) item { Text("No blocking or review overlap findings for this territory.") }
        items(record.blockers) { FindingPanel("Blocking", it) }
        items(record.warnings) { FindingPanel("Review", it) }
    }
}

@Composable
private fun PatchPanel(patch: PermanentPatch) = KnowledgePanel(patch.id, "knowledge-patch-${patch.id}") {
    Text(patch.rule)
    KnowledgeFact("Status", patch.status)
    KnowledgeFact("Applies to", (listOfNotNull(patch.territory) + patch.territories).distinct().joinToString().ifBlank { "Global" })
    patch.road?.let { KnowledgeFact("Road", it) }
}
@Composable
private fun FindingPanel(kind: String, finding: OverlapCandidate) = KnowledgePanel("$kind • ${finding.road}", "knowledge-finding") {
    KnowledgeFact("Affected territories", finding.territories.joinToString())
    KnowledgeFact("Confidence", finding.confidence)
    Text(finding.reason)
}
@Composable
private fun KnowledgePanel(title: String, tag: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
@Composable
private fun KnowledgeFact(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value.ifBlank { "Unavailable" }, style = MaterialTheme.typography.bodyMedium)
}
