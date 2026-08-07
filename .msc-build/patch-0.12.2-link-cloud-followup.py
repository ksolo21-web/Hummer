from pathlib import Path

ROOT = Path('MyStudyCompanion')

def replace_once(rel, old, new):
    path = ROOT / rel
    text = path.read_text(encoding='utf-8')
    if text.count(old) != 1:
        raise SystemExit(f'{rel}: expected one anchor, found {text.count(old)}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')

# A personal study plan may contain semicolon shorthand or several weekly readings.
# Give each normalized passage its own exact JW Library button.
path = ROOT / 'app/src/main/java/com/mystudycompanion/app/ui/CompanionHubScreen.kt'
text = path.read_text(encoding='utf-8')
old = '''    val context = LocalContext.current
    Card(
'''
new = '''    val context = LocalContext.current
    val exactPlanPassages = JwLibraryLinkResolver.splitBiblePassages(plan.readingReference)
    Card(
'''
start = text.index('private fun StudyPlanCard(')
pos = text.index(old, start)
text = text[:pos] + text[pos:].replace(old, new, 1)
old_actions = '''            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { JwLibraryLinkResolver.openBibleReference(context, plan.scriptureReference, profile.preferJwLibrary) }) {
                    Icon(Icons.Outlined.LibraryBooks, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Open in JW Library")
                }
                OutlinedButton(onClick = { onOpenAi("Help me study ${plan.readingReference}. Use only verified JW sources and focus on: ${plan.focus}") }) {
'''
new_actions = '''            exactPlanPassages.forEachIndexed { index, passage ->
                Button(
                    onClick = { JwLibraryLinkResolver.openBibleReference(context, passage, true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.LibraryBooks, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (exactPlanPassages.size == 1) "Open in JW Library" else "Open passage ${index + 1}: $passage")
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpenAi("Help me study ${plan.readingReference}. Use only verified JW sources and focus on: ${plan.focus}") }) {
'''
if text.count(old_actions) != 1:
    raise SystemExit('Study plan action anchor changed.')
text = text.replace(old_actions, new_actions, 1)
old_idea = '''            if (idea.scripture.isNotBlank()) {
                TextButton(onClick = { JwLibraryLinkResolver.openBibleReference(context, idea.scripture, true) }) { Text(idea.scripture) }
            }
'''
new_idea = '''            JwLibraryLinkResolver.splitBiblePassages(idea.scripture).forEach { passage ->
                TextButton(onClick = { JwLibraryLinkResolver.openBibleReference(context, passage, true) }) { Text(passage) }
            }
'''
if text.count(old_idea) != 1:
    raise SystemExit('Family idea scripture anchor changed.')
text = text.replace(old_idea, new_idea, 1)
text = text.replace(
    '${state.familyBoard.members.size} profile(s) on this device • Google cloud linking activates when the private Firebase project is configured.',
    '${state.familyBoard.members.size} profile(s) on this device • Cross-device family sync requires Firebase, Google OAuth, the private HTTPS backend, and household invitation/join support.',
    1,
)
path.write_text(text, encoding='utf-8')

replace_once(
    'app/src/main/java/com/mystudycompanion/app/ui/AuthScreen.kt',
    'Google sign-in activates when the private Firebase project, google-services.json, and web client ID are added.',
    'Google sign-in requires the private Firebase Android configuration and Google OAuth web client ID. Family synchronization additionally requires the deployed HTTPS backend and completed household invitation/join service.',
)
replace_once(
    'app/src/main/java/com/mystudycompanion/app/ui/AccountScreen.kt',
    'The account code is installed, but this APK cannot contact a Google/Firebase project until its project configuration is supplied.',
    'The account code is installed, but Google sign-in still requires the Firebase Android configuration and OAuth web client ID. Family synchronization also requires the private HTTPS backend and household invitation/join service.',
)
replace_once(
    'app/src/main/java/com/mystudycompanion/app/ui/HouseholdScreen.kt',
    'Text(if (canManage) "  Family invitations unlock after your private alpha approval" else "  Ask the household organizer for an invitation")',
    'Text(if (canManage) "  Invitations require the private backend invitation service" else "  Join requires an organizer code and the private backend")',
)

print('Applied final multi-passage link and truthful cloud-readiness UI follow-up.')
