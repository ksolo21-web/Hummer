from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
repo = root / "MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/family/FamilyWorshipOrganizerRepository.kt"
ui = root / "MyStudyCompanion/app/src/main/java/com/mystudycompanion/app/ui/HouseholdScreen.kt"
app_gradle = root / "MyStudyCompanion/app/build.gradle.kts"
wear_gradle = root / "MyStudyCompanion/wear/build.gradle.kts"
sw = root / "MyStudyCompanionWeb/sw.js"
test = root / "MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/family/FamilyCancellationSafetyTest.kt"

text = repo.read_text()
assert "import kotlinx.coroutines.CoroutineScope" in text
if "import kotlinx.coroutines.CancellationException" not in text:
    text = text.replace(
        "import kotlinx.coroutines.CoroutineScope\n",
        "import kotlinx.coroutines.CancellationException\nimport kotlinx.coroutines.CoroutineScope\n",
        1,
    )

# Kotlin runCatching also catches CancellationException. In this repository that
# converted normal structured-concurrency cancellation into a red user error.
text = text.replace("runCatching {", "runFamilyCatching {")
text = text.replace("return@runCatching", "return@runFamilyCatching")
marker = "data class FamilyOrganizerState("
assert marker in text
helper = '''internal inline fun <T> runFamilyCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Throwable) {
    Result.failure(error)
}

internal fun familyErrorMessageForDisplay(message: String?): String? {
    val clean = message?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return clean.takeUnless {
        it.contains("StandaloneCoroutine", ignoreCase = true) ||
            it.contains("Coroutine was cancelled", ignoreCase = true) ||
            it.contains("Job was cancelled", ignoreCase = true)
    }
}

'''
if "internal inline fun <T> runFamilyCatching" not in text:
    text = text.replace(marker, helper + marker, 1)

# User mutations must outlive a transient Compose recomposition/navigation scope.
anchor = '    suspend fun createHousehold(familyName: String = "My Family") {'
assert anchor in text
launchers = '''    fun requestRefreshCapabilities() {
        scope.launch { refreshCapabilities() }
    }

    fun requestCreateHousehold(familyName: String = "My Family") {
        scope.launch { createHousehold(familyName) }
    }

    fun requestCreateHouseholdInvitation() {
        scope.launch { createHouseholdInvitation() }
    }

    fun requestJoinHousehold(invitationCode: String) {
        scope.launch { joinHousehold(invitationCode) }
    }

'''
if "fun requestCreateHouseholdInvitation()" not in text:
    text = text.replace(anchor, launchers + anchor, 1)
repo.write_text(text)

ui_text = ui.read_text()
ui_text = ui_text.replace("import androidx.compose.runtime.rememberCoroutineScope\n", "")
ui_text = ui_text.replace("import kotlinx.coroutines.launch\n", "")
if "import com.mystudycompanion.app.family.familyErrorMessageForDisplay\n" not in ui_text:
    ui_text = ui_text.replace(
        "import com.mystudycompanion.app.family.FamilyWorshipOrganizerRepository\n",
        "import com.mystudycompanion.app.family.FamilyWorshipOrganizerRepository\n"
        "import com.mystudycompanion.app.family.familyErrorMessageForDisplay\n",
        1,
    )
ui_text = ui_text.replace("    val scope = rememberCoroutineScope()\n", "")
ui_text = ui_text.replace(
    "    LaunchedEffect(account.uid) { organizerRepository.refreshCapabilities() }",
    "    LaunchedEffect(account.uid) { organizerRepository.requestRefreshCapabilities() }",
)
ui_text = ui_text.replace(
    "            organizerState.errorMessage?.let { message ->\n",
    "            familyErrorMessageForDisplay(organizerState.errorMessage)?.let { message ->\n",
    1,
)
ui_text = ui_text.replace(
    "onClick = { scope.launch { organizerRepository.createHouseholdInvitation() } },",
    "onClick = organizerRepository::requestCreateHouseholdInvitation,",
)
ui_text = ui_text.replace(
    "onClick = { scope.launch { organizerRepository.createHousehold() } },",
    "onClick = { organizerRepository.requestCreateHousehold() },",
)
ui_text = ui_text.replace(
    "onClick = { scope.launch { organizerRepository.joinHousehold(invitationInput) } },",
    "onClick = { organizerRepository.requestJoinHousehold(invitationInput) },",
)
assert "rememberCoroutineScope" not in ui_text
assert "scope.launch" not in ui_text
ui.write_text(ui_text)

app_text = app_gradle.read_text()
app_text, count = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 38", app_text, count=1)
assert count == 1
app_text, count = re.subn(
    r'versionName\s*=\s*"[^"]+"',
    'versionName = "0.15.5-private-alpha-household-cancellation-fix"',
    app_text,
    count=1,
)
assert count == 1
app_gradle.write_text(app_text)

wear_text = wear_gradle.read_text()
wear_text, count = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 360155001", wear_text, count=1)
assert count == 1
wear_text, count = re.subn(
    r'versionName\s*=\s*"[^"]+"',
    'versionName = "0.15.5-wear-private-alpha-household-cancellation-fix"',
    wear_text,
    count=1,
)
assert count == 1
wear_gradle.write_text(wear_text)

sw_text = sw.read_text()
sw_text, count = re.subn(
    r'msc-web-v015[0-9][^"\']*',
    "msc-web-v0155-household-cancellation-v1",
    sw_text,
    count=1,
)
assert count == 1
sw.write_text(sw_text)

test.parent.mkdir(parents=True, exist_ok=True)
test.write_text('''package com.mystudycompanion.app.family

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FamilyCancellationSafetyTest {
    @Test
    fun coroutineCancellationIsRethrownInsteadOfDisplayed() {
        assertThrows(CancellationException::class.java) {
            runFamilyCatching<Unit> {
                throw CancellationException("StandaloneCoroutine was cancelled")
            }
        }
    }

    @Test
    fun realFailureRemainsAResultFailure() {
        val result = runFamilyCatching<Unit> { error("Permission denied") }
        assertEquals("Permission denied", result.exceptionOrNull()?.message)
    }

    @Test
    fun internalCancellationTextIsNeverRendered() {
        assertNull(familyErrorMessageForDisplay("StandaloneCoroutine was cancelled"))
        assertNull(familyErrorMessageForDisplay("Job was cancelled"))
        assertEquals("Permission denied", familyErrorMessageForDisplay("Permission denied"))
    }
}
''')

print("Patched My Study Companion 0.15.5 household cancellation safety.")
