#!/usr/bin/env python3
from pathlib import Path
import sys

root=Path(sys.argv[1]).resolve()
path=root/"app/src/main/java/com/koenterprises/territorycardstudio/WorkspaceModeUi.kt"
s=path.read_text()

def rep(old,new,label):
    global s
    n=s.count(old)
    if n != 1:
        raise SystemExit(label + ": expected 1 match, found " + str(n))
    s=s.replace(old,new,1)

rep(
"package com.koenterprises.territorycardstudio\n\n",
"package com.koenterprises.territorycardstudio\n\n"
"import androidx.activity.compose.rememberLauncherForActivityResult\n"
"import androidx.activity.result.contract.ActivityResultContracts\n",
"activity result imports"
)
rep(
"import androidx.compose.material3.Card\n",
"import androidx.compose.material3.Button\nimport androidx.compose.material3.Card\n",
"button import"
)
rep(
"import androidx.compose.runtime.mutableStateOf\n",
"import androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.remember\n",
"remember import"
)
rep(
"import androidx.compose.ui.platform.testTag\n",
"import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.testTag\n",
"context import"
)

anchor='''    val readiness = WorkspaceReadinessModel.from(item)

    LazyColumn(
'''
insert='''    val readiness = WorkspaceReadinessModel.from(item)
    val context = LocalContext.current
    val application = context.applicationContext as TerritoryCardStudioApplication
    val sourceStore = remember(context.applicationContext) { SourceMapIntakeStore(context) }
    var workflowScreen by rememberSaveable(assignment.displayId) { mutableStateOf("WORKSPACE") }
    var sourceRevision by rememberSaveable(assignment.displayId) { mutableStateOf(0) }
    var importError by rememberSaveable(assignment.displayId) { mutableStateOf<String?>(null) }
    val intake = remember(assignment.displayId, sourceRevision) {
        sourceStore.get(assignment.displayId)
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

    if (workflowScreen == "VERIFY") {
        VerificationWorkflowScreen(
            modifier = modifier,
            item = item,
            mode = mode,
            intake = intake,
            knowledgeBase = application.services.knowledgeBase,
            onlinePolicy = application.services.activePolicy,
            onBackToImport = { workflowScreen = "IMPORT" },
            onBackToWorkspace = { workflowScreen = "WORKSPACE" }
        )
        return
    }

    LazyColumn(
'''
rep(anchor,insert,"workflow state")

anchor2='''        item {
            WorkspaceReadinessSummary(readiness)
        }

        if (allowedModes.size > 1) {
'''
insert2='''        item {
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

        if (allowedModes.size > 1) {
'''
rep(anchor2,insert2,"workspace workflow actions")

path.write_text(s)
print("PHASE2C_WORKSPACE_TRANSFORM=PASS")
