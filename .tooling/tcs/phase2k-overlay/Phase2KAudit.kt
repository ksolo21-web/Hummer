package com.koenterprises.territorycardstudio

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.koenterprises.territorycardstudio.core.BundleIntegrity
import com.koenterprises.territorycardstudio.core.TerritoryKnowledgeBase
import com.koenterprises.territorycardstudio.core.OnlineSourcePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/** A scoped evidence document. It never authorizes field release or exports PDF bytes. */
data class Phase2KAuditSnapshot(val territoryId: String, val mode: WorkspaceMode, val bytes: ByteArray,
    val sha256: String, val sourceHash: String?, val inputHash: String?, val inventoryHash: String?,
    val candidateHash: String?, val packetHash: String?, val revision: String) {
    // ByteArray has referential equals; compare the authenticated content explicitly.
    fun sameEvidence(other: Phase2KAuditSnapshot): Boolean = territoryId == other.territoryId &&
        mode == other.mode && sha256 == other.sha256 && bytes.contentEquals(other.bytes)
}

class Phase2KAuditService(private val kb: TerritoryKnowledgeBase, private val coordinator: AndroidBuildWorkflowCoordinator,
    private val sources: SourceMapIntakeStore, private val policy: OnlineSourcePolicy,
    private val review: AndroidCandidateReviewService? = null) {
    fun snapshot(item: TerritoryDashboardItem, mode: WorkspaceMode): Phase2KAuditSnapshot {
        val first = snapshotOnce(item, mode)
        require(first.sameEvidence(snapshotOnce(item, mode))) { "Audit evidence changed while reading. Recheck." }
        return first
    }
    private fun snapshotOnce(item: TerritoryDashboardItem, mode: WorkspaceMode): Phase2KAuditSnapshot {
        val a = requireNotNull(kb.assignments[item.assignment.displayId]) { "Unknown territory" }
        require(a == item.assignment && mode in WorkspaceModePolicy.allowedModes(a)) { "Territory or mode changed" }
        val intake = sources.verifiedRecord(a.displayId)
        val prepared = coordinator.workspaceSnapshot(a.displayId, mode)?.takeIf {
            it.sourceHash == intake?.sha256 && it.id == a.displayId && it.mode == mode
        }
        val state = coordinator.state(a.displayId, mode)
        val candidate = state.front?.takeIf { it.displayId == a.displayId }
        val packet = state.packet?.takeIf { it.displayId == a.displayId }
        val approval = review?.state(a.displayId, mode)?.takeIf { it.ticket != null }
        val verification = VerificationWorkflowModel.from(item, mode, intake, kb, policy, prepared)
        return compose(item, mode, intake, prepared, candidate?.sha256, packet?.sha256,
            coordinator.currentCandidateVersion(a.displayId, mode), approval?.decision?.approvalState?.name, verification)
    }
    private fun compose(item: TerritoryDashboardItem, mode: WorkspaceMode, intake: SourceMapIntakeRecord?,
        prepared: WorkspacePreparedSnapshot?, front: String?, packet: String?, version: String?, decision: String?,
        verification: VerificationWorkflowModel): Phase2KAuditSnapshot {
        val a = item.assignment
        val findings = Phase2KFindings.from(item, mode, verification, kb, prepared)
        fun nullable(s: String?): Any = s ?: JSONObject.NULL
        val json = JSONObject().put("schema", "tcs-phase2k-scoped-audit-v1")
            .put("scope", "Current read-only UI evidence; not a field release or final production audit")
            .put("territory", a.displayId).put("mode", mode.name).put("canonicalFilename", a.canonicalFilename)
            .put("knowledgeBaseRevision", kb.revision).put("assignmentStatus", a.status)
            .put("referenceSha256", a.referenceSha256).put("referenceKind", "Knowledge Base expected reference; attachment bytes not certified here")
            .put("sourceSha256", nullable(intake?.sha256)).put("sourceFilename", nullable(intake?.sourceFilename))
            .put("sourceAuthority", if (intake == null) "Unavailable" else "User-supplied evidence only")
            .put("preparedInputSha256", nullable(prepared?.inputHash))
            .put("inventorySha256", nullable(prepared?.inventory?.hash))
            .put("candidateFrontSha256", nullable(front)).put("candidatePacketSha256", nullable(packet))
            .put("candidateVersion", nullable(version)).put("localReviewDecision", nullable(decision))
            .put("fieldRelease", "Not granted by this audit; generated candidates remain blocked")
            .put("verificationPolicyRevision", verification.policyRevision)
            .put("preparedForBuild", prepared?.canBuild == true)
            .put("findingsState", if (prepared == null) "UNVALIDATED_OR_UNPREPARED" else "CURRENT_PREPARED")
            .put("findings", JSONArray().apply { findings.forEach { f -> put(JSONObject()
                .put("category", f.category).put("item", nullable(f.itemId)).put("state", f.state.name)
                .put("reason", f.reason).put("provenance", nullable(f.provenance)).put("workspaceTab", f.tab.name)) } })
        val bytes = (json.toString(2) + "\n").toByteArray(StandardCharsets.UTF_8)
        require(bytes.size in 1..(2 * 1024 * 1024)) { "Scoped audit exceeds the safe output limit" }
        return Phase2KAuditSnapshot(a.displayId, mode, bytes, BundleIntegrity.sha256(bytes.inputStream()),
            intake?.sha256, prepared?.inputHash, prepared?.inventory?.hash, front, packet, kb.revision)
    }
    internal fun exportCreated(ticket: Phase2KAuditSnapshot, destination: CreatedExportDestination): String {
        try {
            require(ticket.bytes.size in 1..(2 * 1024 * 1024) && BundleIntegrity.sha256(ticket.bytes.inputStream()) == ticket.sha256) { "Audit ticket bytes changed" }
            val currentItem = TerritoryDashboardModel.from(kb).items.first { it.assignment.displayId == ticket.territoryId }
            val latest = snapshot(currentItem, ticket.mode)
            require(ticket.sameEvidence(latest)) { "Audit evidence changed. Reopen and recheck before export." }
            destination.openOutput().use { it.write(ticket.bytes) }
            val readback = destination.openInput().use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(out.size() + count <= ticket.bytes.size) { "Saved audit length changed" }
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            require(readback.contentEquals(ticket.bytes) && BundleIntegrity.sha256(readback.inputStream()) == ticket.sha256) {
                "Saved audit did not match current evidence"
            }
            require(ticket.sameEvidence(snapshot(currentItem, ticket.mode))) {
                "Audit evidence changed during export. The saved copy was discarded."
            }
            return ticket.sha256
        } catch (failure: Exception) {
            runCatching { destination.deleteCreated() }
            throw failure
        }
    }
}

@Composable
fun Phase2KAuditScreen(modifier: Modifier, item: TerritoryDashboardItem, mode: WorkspaceMode,
    service: Phase2KAuditService, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context.previewActivity()
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(activity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false) }
    var revision by remember { mutableIntStateOf(0) }
    var ticket by remember(item.assignment.displayId, mode, revision) { mutableStateOf<Phase2KAuditSnapshot?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Phase2KAuditSnapshot?>(null) }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { active = false; ticket = null }
            if (event == Lifecycle.Event.ON_RESUME) { active = true; revision++ }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(item.assignment.displayId, mode, revision, active) {
        if (active) {
            val result = withContext(Dispatchers.IO) { runCatching { service.snapshot(item, mode) } }
            ticket = result.getOrNull(); result.exceptionOrNull()?.let { message = it.message }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val saved = pending; pending = null
        if (uri == null) message = "Audit save cancelled. No audit was exported."
        else if (saved == null) message = "Expired audit selection ignored. Recheck evidence."
        else {
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching {
                    service.exportCreated(saved, AndroidCreatedExportDestination(context.contentResolver, uri))
                } }
                message = result.fold({ "Audit saved and read back • SHA-256 $it" }, { it.message ?: "Audit export failed" })
                revision++; busy = false
            }
        }
    }
    LazyColumn(modifier.fillMaxSize().testTag("audit-screen"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack, enabled = !busy && pending == null) { Text("← Back") }
            Text("Audit output", style = MaterialTheme.typography.headlineLarge)
            Text("Territory ${item.assignment.displayId} • ${mode.label}")
            Text("Read-only current evidence. This record does not approve a candidate or release a field PDF.") }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Knowledge Base ${ticket?.revision ?: "Checking…"}")
            Text("Reference SHA-256 ${item.assignment.referenceSha256}")
            Text("Imported source SHA-256 ${ticket?.sourceHash ?: "Unavailable"}")
            Text("Prepared input SHA-256 ${ticket?.inputHash ?: "Unavailable"}")
            Text("Inventory SHA-256 ${ticket?.inventoryHash ?: "Unavailable"}")
            Text("Candidate SHA-256 ${ticket?.candidateHash ?: "Unavailable"}")
            Text("Packet SHA-256 ${ticket?.packetHash ?: "Unavailable"}")
            Text(if (ticket?.inputHash == null) "Current prepared verification unavailable" else "Current prepared verification recorded")
        } } }
        item { Button(onClick = { pending = ticket; if (pending != null) picker.launch("Territory - ${item.assignment.displayId} - Audit.json") },
            enabled = ticket != null && active && !busy && pending == null,
            modifier = Modifier.fillMaxWidth().testTag("audit-save")) { Text("Save scoped audit…") } }
        message?.let { item { Text(it, modifier = Modifier.testTag("audit-message")) } }
    }
}
