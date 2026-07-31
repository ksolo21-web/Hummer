from pathlib import Path

ROOT = Path("MyStudyCompanion")

resolver = ROOT / "app/src/main/java/com/mystudycompanion/app/companion/JwLibraryLinkResolver.kt"
text = resolver.read_text(encoding="utf-8")
function_start = '    fun targetFromOfficialUrl(url: String, label: String = "official material"): Target {'
next_function = '    fun splitBiblePassages(reference: String): List<String> {'
start = text.find(function_start)
next_pos = text.find(next_function, start)
if start < 0 or next_pos < 0:
    raise SystemExit("Could not locate resolver target and multi-passage function boundaries.")
comment_start = text.rfind("    /**", start, next_pos)
if comment_start < 0:
    raise SystemExit("Could not locate multi-passage documentation boundary.")
strict_target = r'''    fun targetFromOfficialUrl(url: String, label: String = "official material"): Target {
        val cleanUrl = url.trim().replace("&amp;", "&")
        if (cleanUrl.isBlank()) return Target(null, "", label)

        // Pure link interpretation is exercised by local JVM tests. java.net.URI
        // avoids Android Uri stubs here; Android Uri remains the launcher type.
        val uri = runCatching { java.net.URI(cleanUrl) }.getOrNull()
            ?: return Target(null, cleanUrl, label)
        val rawQuery = uri.rawQuery

        if (uri.scheme.equals("jwlibrary", ignoreCase = true)) {
            if (uri.rawPath != "/finder" || !hasExactFinderTarget(rawQuery)) {
                return Target(libraryUri = null, webUrl = "", label = label)
            }
            val web = "https://www.jw.org/finder" + rawQuery?.let { "?$it" }.orEmpty()
            return Target(cleanUrl, web, label)
        }

        if (uri.host.equals("www.jw.org", ignoreCase = true) && uri.rawPath == "/finder") {
            if (!hasExactFinderTarget(rawQuery)) {
                return Target(libraryUri = null, webUrl = cleanUrl, label = label)
            }
            return Target(
                libraryUri = "jwlibrary:///finder" + rawQuery?.let { "?$it" }.orEmpty(),
                webUrl = cleanUrl,
                label = label,
            )
        }

        val wolDocId = if (uri.host.equals("wol.jw.org", ignoreCase = true)) {
            Regex("/wol/d/[^/]+/[^/]+/(\\d+)").find(uri.rawPath.orEmpty())?.groupValues?.getOrNull(1)
        } else null
        if (!wolDocId.isNullOrBlank()) return documentTarget(wolDocId, cleanUrl, label)

        return Target(libraryUri = null, webUrl = cleanUrl, label = label)
    }

    private fun hasExactFinderTarget(rawQuery: String?): Boolean {
        val parameters = rawQuery.orEmpty()
            .split('&')
            .asSequence()
            .mapNotNull { parameter ->
                if (parameter.isBlank()) return@mapNotNull null
                val name = parameter.substringBefore('=').trim().lowercase()
                val encodedValue = parameter.substringAfter('=', "").trim()
                if (name.isBlank()) return@mapNotNull null
                val value = runCatching {
                    java.net.URLDecoder.decode(encodedValue, Charsets.UTF_8.name())
                }.getOrDefault(encodedValue)
                name to value
            }
            .groupBy({ it.first }, { it.second })

        val docId = parameters["docid"]?.firstOrNull().orEmpty()
        if (docId.matches(Regex("""\d+"""))) return true

        val bible = parameters["bible"]?.firstOrNull().orEmpty()
        if (bible.matches(Regex("""\d{7,8}(?:-\d{7,8})?"""))) return true

        val publication = parameters["pub"]?.firstOrNull().orEmpty()
        if (publication.matches(Regex("""[A-Za-z0-9][A-Za-z0-9-]{0,31}"""))) return true

        val alias = parameters["alias"]?.firstOrNull().orEmpty()
        val date = parameters["date"]?.firstOrNull().orEmpty()
        return alias.equals("daily-text", ignoreCase = true) &&
            date.matches(Regex("""\d{8}"""))
    }


'''
text = text[:start] + strict_target + text[comment_start:]
resolver.write_text(text, encoding="utf-8")

family = ROOT / "app/src/main/java/com/mystudycompanion/app/ui/FamilyWorshipScreen.kt"
text = family.read_text(encoding="utf-8")
resolver_import = "import com.mystudycompanion.app.companion.JwLibraryLinkResolver\n"
import_anchor = "import com.mystudycompanion.app.data.FamilyWorshipSection\n"
if resolver_import not in text:
    if text.count(import_anchor) != 1:
        raise SystemExit("FamilyWorshipScreen import anchor changed.")
    text = text.replace(import_anchor, resolver_import + import_anchor, 1)

old_overview_start = '''private fun FamilyOverviewCard(study: FamilyWorshipStudy, onOpenAi: (String) -> Unit) {
    val context = LocalContext.current
    Card(
'''
new_overview_start = '''private fun FamilyOverviewCard(study: FamilyWorshipStudy, onOpenAi: (String) -> Unit) {
    val context = LocalContext.current
    val exactOfficialUrl = exactFamilyOverviewTarget(study.officialUrl, study.keyScripture)
    Card(
'''
if "val exactOfficialUrl = exactFamilyOverviewTarget" not in text:
    if text.count(old_overview_start) != 1:
        raise SystemExit("Family overview start anchor changed.")
    text = text.replace(old_overview_start, new_overview_start, 1)

old_overview_button = '''                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { openOfficialUrl(context, study.officialUrl) },
                ) {
                    Icon(Icons.Outlined.Language, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Open in JW Library")
                }
'''
new_overview_button = '''                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = exactOfficialUrl != null,
                    onClick = { exactOfficialUrl?.let { openOfficialUrl(context, it, study.title) } },
                ) {
                    Icon(Icons.Outlined.Language, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (exactOfficialUrl == null) "Exact material unavailable" else "Open in JW Library")
                }
'''
if old_overview_button in text:
    text = text.replace(old_overview_button, new_overview_button, 1)
elif new_overview_button not in text:
    raise SystemExit("Family overview button anchor changed.")

old_section_start = '''private fun FamilySectionCard(
    section: FamilyWorshipSection,
    onCompleted: (FamilyWorshipSection, Boolean) -> Unit,
) {
    val context = LocalContext.current
    Card(
'''
new_section_start = '''private fun FamilySectionCard(
    section: FamilyWorshipSection,
    onCompleted: (FamilyWorshipSection, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val exactOfficialUrl = section.officialUrl
        .trim()
        .takeIf(JwLibraryLinkResolver::isDirectLibraryTarget)
    Card(
'''
if ".takeIf(JwLibraryLinkResolver::isDirectLibraryTarget)" not in text:
    if text.count(old_section_start) != 1:
        raise SystemExit("Family section start anchor changed.")
    text = text.replace(old_section_start, new_section_start, 1)

old_section_button = '''            OutlinedButton(onClick = { openOfficialUrl(context, section.officialUrl) }) {
                Icon(Icons.Outlined.Language, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Open in JW Library")
            }
'''
new_section_button = '''            OutlinedButton(
                enabled = exactOfficialUrl != null,
                onClick = { exactOfficialUrl?.let { openOfficialUrl(context, it, section.title) } },
            ) {
                Icon(Icons.Outlined.Language, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(if (exactOfficialUrl == null) "Exact material unavailable" else "Open in JW Library")
            }
'''
if old_section_button in text:
    text = text.replace(old_section_button, new_section_button, 1)
elif new_section_button not in text:
    raise SystemExit("Family section button anchor changed.")

helper_anchor = '''internal fun familyDeepDivePrompt(study: FamilyWorshipStudy): String =
'''
helper = '''internal fun exactFamilyOverviewTarget(officialUrl: String, keyScripture: String): String? {
    val exactOfficial = officialUrl.trim().takeIf(JwLibraryLinkResolver::isDirectLibraryTarget)
    if (exactOfficial != null) return exactOfficial
    return JwLibraryLinkResolver.bibleTarget(keyScripture)
        .webUrl
        .takeIf(JwLibraryLinkResolver::isDirectLibraryTarget)
}

internal fun familyDeepDivePrompt(study: FamilyWorshipStudy): String =
'''
if "internal fun exactFamilyOverviewTarget" not in text:
    if text.count(helper_anchor) != 1:
        raise SystemExit("Family helper insertion anchor changed.")
    text = text.replace(helper_anchor, helper, 1)
family.write_text(text, encoding="utf-8")

resolver_tests = ROOT / "app/src/test/java/com/mystudycompanion/app/companion/JwLibraryLinkResolverTest.kt"
text = resolver_tests.read_text(encoding="utf-8")
test_anchor = '''    @Test
    fun researchGuideUsesPublicationFinderTarget() {
'''
extra_tests = '''    @Test
    fun incompleteFinderLinksFailClosed() {
        assertFalse(JwLibraryLinkResolver.isDirectLibraryTarget("https://www.jw.org/finder?wtlocale=E"))
        assertFalse(JwLibraryLinkResolver.isDirectLibraryTarget("https://www.jw.org/finder?alias=daily-text&wtlocale=E"))
        assertFalse(JwLibraryLinkResolver.isDirectLibraryTarget("https://www.jw.org/finder?docid=not-a-document"))
        assertFalse(JwLibraryLinkResolver.isDirectLibraryTarget("jwlibrary:///finder?wtlocale=E"))
    }

    @Test
    fun htmlEscapedExactFinderLinkStillResolves() {
        val target = JwLibraryLinkResolver.targetFromOfficialUrl(
            "https://www.jw.org/finder?wtlocale=E&amp;docid=202026244&amp;srcid=share",
            "meeting week",
        )
        assertEquals(
            "jwlibrary:///finder?wtlocale=E&docid=202026244&srcid=share",
            target.libraryUri,
        )
    }

    @Test
    fun broadOfficialPagesRemainExplicitWebFallbacks() {
        assertFalse(JwLibraryLinkResolver.isDirectLibraryTarget("https://www.jw.org/en/library/jw-meeting-workbook/"))
        assertFalse(JwLibraryLinkResolver.isDirectLibraryTarget("https://www.jw.org/en/bible-teachings/family/"))
        assertFalse(JwLibraryLinkResolver.isDirectLibraryTarget("https://wol.jw.org/en/wol/h/r1/lp-e"))
    }

'''
if "fun incompleteFinderLinksFailClosed" not in text:
    if text.count(test_anchor) != 1:
        raise SystemExit("Resolver test insertion anchor changed.")
    text = text.replace(test_anchor, extra_tests + test_anchor, 1)
resolver_tests.write_text(text, encoding="utf-8")

family_tests = ROOT / "app/src/test/java/com/mystudycompanion/app/ui/FamilyWorshipScreenTest.kt"
family_tests.parent.mkdir(parents=True, exist_ok=True)
family_tests.write_text(
    '''package com.mystudycompanion.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FamilyWorshipScreenTest {
    @Test
    fun exactFamilyOverviewUsesItsBoundMaterial() {
        val exact = "https://www.jw.org/finder?wtlocale=E&docid=202026244&srcid=share"
        assertEquals(exact, exactFamilyOverviewTarget(exact, "Philippians 4:6-7"))
    }

    @Test
    fun staleFamilyOverviewFallsBackOnlyToItsExactKeyScripture() {
        assertEquals(
            "https://www.jw.org/finder?wtlocale=E&pub=nwtsty&srctype=wol&bible=50004006-50004007&srcid=share",
            exactFamilyOverviewTarget(
                "https://www.jw.org/en/bible-teachings/family/",
                "Philippians 4:6-7",
            ),
        )
    }

    @Test
    fun staleFamilyOverviewWithInvalidScriptureFailsClosed() {
        assertNull(
            exactFamilyOverviewTarget(
                "https://www.jw.org/en/bible-teachings/family/",
                "not a scripture",
            ),
        )
    }
}
''',
    encoding="utf-8",
)

print("Applied structural, compile-safe exact JW Finder and stale-family link gate.")
