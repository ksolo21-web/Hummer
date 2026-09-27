package com.koenterprises.territorycardstudio

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.koenterprises.territorycardstudio.core.KnowledgeBaseAssignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DraftEditorItem(val kind: DraftLabelKind,val id: String,val label: String) {
    val key: String get()=kind.name+":"+id
}

@Composable
fun DraftEditorScreen(modifier: Modifier, assignment: KnowledgeBaseAssignment, mode: WorkspaceMode,
    store: AndroidEditingDraftStore, sources: SourceMapIntakeStore, onBack: () -> Unit) {
    val id=assignment.displayId
    val scope=rememberCoroutineScope();val activity=LocalContext.current.previewActivity()
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    val known=remember(assignment) { assignment.roads.map { DraftEditorItem(DraftLabelKind.ROAD,it.segmentId,it.name) }+
        assignment.buildings.map { DraftEditorItem(DraftLabelKind.BUILDING,it.buildingId,it.label) } }
    var draft by remember(id,mode) { mutableStateOf<EditingDraft?>(null) }
    var source by remember(id,mode) { mutableStateOf<SourceMapIntakeRecord?>(null) }
    var loaded by remember(id,mode) { mutableStateOf(false) }
    var loadError by remember(id,mode) { mutableStateOf<String?>(null) }
    var sourceError by remember(id,mode) { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable(id,mode) { mutableStateOf<String?>(null) }
    var section by rememberSaveable(id,mode) { mutableStateOf("Items") }
    var query by rememberSaveable(id,mode) { mutableStateOf("") }
    var selectedKey by rememberSaveable(id,mode) { mutableStateOf("") }
    var proposed by rememberSaveable(id,mode) { mutableStateOf("") }
    var rationale by rememberSaveable(id,mode) { mutableStateOf("") }
    var evidence by rememberSaveable(id,mode) { mutableStateOf("") }
    var dirty by rememberSaveable(id,mode) { mutableStateOf(false) }
    var expectedToken by rememberSaveable(id,mode) { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var confirmedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val selected=known.firstOrNull { it.key==selectedKey }
    val current=draft
    val conflict=loaded && loadError==null && expectedToken!=null && current?.latest?.token!=expectedToken
    val available=loaded && loadError==null && active && !busy
    val writable=available && current!=null && !current.stale && source!=null && !conflict
    val evidenceChoices=(listOfNotNull(source?.sha256)+assignment.sourceHashes).distinct()
    fun clearFields() { selectedKey="";proposed="";rationale="";evidence="";dirty=false }
    fun ask(text: String,action: () -> Unit) { confirmation=text;confirmedAction=action }
    fun leave() { if(!busy) { if(dirty) ask("Discard unsaved label and reason, then return to the workspace? Saved draft revisions will remain.",onBack) else onBack() } }
    fun reload() { clearFields();section="Review";expectedToken=null;loaded=false;refresh++ }
    fun changeItem(item: DraftEditorItem) {
        val choose={
            selectedKey=item.key
            val existing=draft?.latest?.edits?.firstOrNull { it.kind==item.kind && it.itemId==item.id }
            proposed=existing?.proposed ?: item.label;rationale=existing?.rationale ?: "";evidence=existing?.evidenceSha256 ?: ""
            dirty=false;expectedToken=draft?.latest?.token;section="Edit"
        }
        if(dirty) ask("Discard unsaved label and reason before selecting ${item.label.ifBlank { item.id }}?",choose) else choose()
    }
    fun perform(success: String,action: () -> EditingDraft?) {
        if(busy)return
        busy=true;focus.clearFocus();keyboard?.hide()
        scope.launch {
            try {
                val result=withContext(Dispatchers.IO) { runCatching(action) }
                result.onSuccess { value -> draft=value;expectedToken=value?.latest?.token;clearFields();section="Review";message=success }
                    .onFailure { message=it.message ?: "Draft operation failed. Your input is preserved." }
                refresh++
            } finally { busy=false }
        }
    }
    DisposableEffect(activity) {
        val window=activity?.window
        val previousSoftInputMode=window?.attributes?.softInputMode
        previousSoftInputMode?.let { old ->
            window.setSoftInputMode((old and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_PAUSE)active=false
            if(event==Lifecycle.Event.ON_RESUME) { active=true;refresh++ }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
            previousSoftInputMode?.let { window.setSoftInputMode(it) }
        }
    }
    LaunchedEffect(id,mode,refresh,active) {
        if(active) {
            loaded=false
            val result=withContext(Dispatchers.IO) { runCatching { store.read(id,mode) } }
            val sourceResult=withContext(Dispatchers.IO) { runCatching { sources.verifiedRecord(id) } }
            draft=result.getOrNull();loadError=result.exceptionOrNull()?.let { it.message ?: "Draft read failed" }
            source=sourceResult.getOrNull();sourceError=sourceResult.exceptionOrNull()?.message
            if(!dirty) {
                expectedToken=draft?.latest?.token
                selected?.let { item ->
                    val saved=draft?.latest?.edits?.firstOrNull { it.kind==item.kind && it.itemId==item.id }
                    proposed=saved?.proposed ?: item.label;rationale=saved?.rationale ?: "";evidence=saved?.evidenceSha256 ?: ""
                }
            }
            loaded=true
        }
    }
    BackHandler { leave() }
    confirmation?.let { text -> AlertDialog(onDismissRequest={ confirmation=null;confirmedAction=null },
        title={Text("Confirm draft change")},text={Text(text)},
        confirmButton={TextButton(modifier=Modifier.testTag("draft-confirm"),onClick={ val action=confirmedAction;confirmation=null;confirmedAction=null;action?.invoke() }) {Text("Continue")}},
        dismissButton={TextButton(modifier=Modifier.testTag("draft-cancel"),onClick={confirmation=null;confirmedAction=null}) {Text("Cancel")}}) }
    Box(modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
        LazyColumn(Modifier.fillMaxHeight().widthIn(max=960.dp).fillMaxWidth().imePadding().testTag("draft-editor"),
            contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {
                TextButton(onClick={leave()},enabled=!busy,modifier=Modifier.testTag("draft-back")) {Text("← Back to workspace")}
                Text("Draft editor",style=MaterialTheme.typography.headlineLarge)
                Text("Territory $id • ${mode.label}")
                Text("Label proposals only. Saving does not change the map, approve a card, or release a field PDF.",style=MaterialTheme.typography.bodyMedium)
            }
            item {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(when { !loaded -> "Checking current draft…";loadError!=null -> "Draft could not be read";current==null -> "No saved draft";current.stale -> "Stale draft • changes blocked";else -> "Unvalidated draft • revision ${current.latest.number}" },modifier=Modifier.testTag("draft-status"),style=MaterialTheme.typography.titleMedium)
                    if(dirty)Text("Unsaved input • not included in the saved review",modifier=Modifier.testTag("draft-dirty"))
                    if(conflict)Text("The saved draft changed or was removed. Your input has been preserved. Reload explicitly before saving.",color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("draft-conflict"))
                    if(current?.stale==true)Text("The source or locked base changed. Review this history, then discard the stale draft to start again.")
                    loadError?.let { Text("$it. Existing draft data has not been replaced.",color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("draft-read-error")) }
                    if(source==null && loaded)Text(sourceError ?: "Import a source map before creating or saving a draft.",modifier=Modifier.testTag("draft-source-missing"))
                } }
            }
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={if(dirty)ask("Discard unsaved input and load the latest saved draft?"){reload()} else reload()},enabled=!busy,modifier=Modifier.weight(1f).testTag("draft-reload")) {Text("Reload")}
                    if(current!=null)OutlinedButton(onClick={val token=expectedToken ?: return@OutlinedButton;ask("Delete the entire saved draft and all unsaved input? This removes its revision history. Approved cards remain unchanged.") {
                        perform("Draft discarded.") {store.discard(id,mode,token);null}
                    }},enabled=available && !conflict,modifier=Modifier.weight(1f).testTag("draft-discard")) {Text("Discard draft")}
                }
            }
            message?.let { item { Text(it,modifier=Modifier.testTag("draft-message")) } }
            if(busy)item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag("draft-busy")) }
            if(loaded && loadError==null && current==null) {
                item { Button(onClick={perform("Draft started. Select a known item to propose a label."){store.create(id,mode)}},
                    enabled=available && !conflict && source!=null && known.isNotEmpty(),modifier=Modifier.fillMaxWidth().testTag("draft-create")) {Text("Start draft")} }
            }
            if(known.isEmpty())item {Text("No editable road or building records are available. Importing a map does not create locked assignment records.",modifier=Modifier.testTag("draft-no-items"))}
            if(current!=null) {
                item { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    (listOf("Items","Review","History")+if(selected!=null)listOf("Edit") else emptyList()).forEach { tab ->
                        FilterChip(selected=section==tab,onClick={section=tab;focus.clearFocus();keyboard?.hide()},label={Text(tab)},enabled=!busy,modifier=Modifier.testTag("draft-tab-$tab"))
                    }
                } }
                if(section=="Items") {
                    item {OutlinedTextField(query,{query=it},label={Text("Find a road or building")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("draft-search"))}
                    val filtered=known.filter { query.isBlank() || it.label.contains(query,true) || it.id.contains(query,true) }
                    if(filtered.isEmpty())item {Text("No matching items",modifier=Modifier.testTag("draft-no-match"))}
                    items(filtered,key={it.key}) { item -> OutlinedButton(onClick={changeItem(item)},enabled=writable,
                        modifier=Modifier.fillMaxWidth().testTag("draft-item-${item.kind.name}-${item.id}")) {
                        Column(Modifier.fillMaxWidth()) { Text(item.label.ifBlank { "Unlabelled item" });Text("${item.kind.name.lowercase()} • ${item.id}",style=MaterialTheme.typography.bodySmall) }
                    } }
                }
                if(section=="Edit" && selected!=null) {
                    item {Text("${selected.kind.name.lowercase()} • ${selected.id}",style=MaterialTheme.typography.titleMedium);Text("Current label: ${selected.label}",modifier=Modifier.testTag("draft-original"))}
                    item {OutlinedTextField(proposed,{if(it.length<=256) {proposed=it;dirty=true} else message="Labels are limited to 256 characters."},label={Text("Proposed label")},supportingText={Text("${proposed.length}/256 characters")},enabled=writable,modifier=Modifier.fillMaxWidth().testTag("draft-proposed"))}
                    item {OutlinedTextField(rationale,{if(it.length<=1024) {rationale=it;dirty=true} else message="Reasons are limited to 1024 characters."},label={Text("Reason for this correction")},supportingText={Text("${rationale.length}/1024 characters")},enabled=writable,modifier=Modifier.fillMaxWidth().testTag("draft-rationale"))}
                    item {Text("Choose supporting evidence",style=MaterialTheme.typography.titleSmall);Text("An evidence reference does not validate the proposed label.")}
                    items(evidenceChoices) { hash ->
                        FilterChip(selected=evidence==hash,onClick={evidence=hash;dirty=true},enabled=writable,
                            label={Column {Text(if(hash==source?.sha256)"Imported source map" else "Locked assignment source");Text(hash,style=MaterialTheme.typography.bodySmall)}},
                            modifier=Modifier.fillMaxWidth().testTag("draft-evidence-$hash"))
                    }
                    item {Button(onClick={
                        val target=selected;val token=expectedToken ?: return@Button
                        val edit=DraftLabelEdit(target.kind,target.id,target.label,proposed,rationale,evidence)
                        val edits=current.latest.edits.filterNot {it.kind==target.kind && it.itemId==target.id}+edit
                        perform("Proposal saved. It remains unvalidated."){store.save(id,mode,token,edits)}
                    },enabled=writable && dirty && proposed.isNotBlank() && proposed!=selected.label && rationale.isNotBlank() && evidence in evidenceChoices,
                        modifier=Modifier.fillMaxWidth().testTag("draft-save")) {Text("Save proposal")}}
                }
                if(section=="Review") {
                    item {Text("Saved proposals (${current.latest.edits.size})",style=MaterialTheme.typography.titleLarge)}
                    if(current.latest.edits.isEmpty())item {Text("No saved label changes",modifier=Modifier.testTag("draft-empty-review"))}
                    items(current.latest.edits,key={it.kind.name+":"+it.itemId}) { edit ->
                        Card(Modifier.fillMaxWidth().testTag("draft-review-${edit.kind.name}-${edit.itemId}")) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text("${edit.kind.name.lowercase()} • ${edit.itemId}",style=MaterialTheme.typography.titleMedium)
                            Text("Current: ${edit.before}");Text("Proposed: ${edit.proposed}");Text(edit.rationale);Text("Evidence: ${edit.evidenceSha256}",style=MaterialTheme.typography.bodySmall)
                            TextButton(onClick={val token=expectedToken ?: return@TextButton;val edits=current.latest.edits.filterNot {it.kind==edit.kind && it.itemId==edit.itemId};ask("Remove this saved proposal and discard any unsaved input? Other saved proposals will remain.") {
                                perform("Proposal removed."){store.save(id,mode,token,edits)}
                            }},enabled=writable,modifier=Modifier.testTag("draft-remove-${edit.kind.name}-${edit.itemId}")) {Text("Remove proposal")}
                        }}
                    }
                }
                if(section=="History") {
                    item {Text("Revision history",style=MaterialTheme.typography.titleLarge);Text("Restoring adds a new revision; earlier evidence remains in the history.")}
                    items(current.revisions.asReversed(),key={it.token}) { revision ->
                        Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text("Revision ${revision.number} • ${revision.action.lowercase()} • ${revision.edits.size} proposals",style=MaterialTheme.typography.titleMedium)
                            Text(revision.atUtc,style=MaterialTheme.typography.bodySmall)
                            if(revision.number<current.latest.number)TextButton(onClick={val token=expectedToken ?: return@TextButton;ask("Replace the current proposal set and any unsaved input with revision ${revision.number}? A new revision will preserve the history.") {
                                perform("Revision ${revision.number} restored as a new revision."){store.restore(id,mode,token,revision.number)}
                            }},enabled=writable,modifier=Modifier.testTag("draft-restore-${revision.number}")) {Text("Restore revision ${revision.number}")}
                        }}
                    }
                }
            }
        }
    }
}
