from pathlib import Path

ROOT = Path("MyStudyCompanion")

resolver = ROOT / "app/src/main/java/com/mystudycompanion/app/companion/JwLibraryLinkResolver.kt"
text = resolver.read_text(encoding="utf-8")
text = text.replace(
    '        val cleanUrl = url.trim()\n',
    '        val cleanUrl = url.trim().replace("&amp;", "&")\n',
    1,
)
old_scheme = '''        if (uri.scheme.equals("jwlibrary", ignoreCase = true)) {
            val web = "https://www.jw.org${uri.rawPath.orEmpty()}" +
                rawQuery?.let { "?$it" }.orEmpty()
            return Target(cleanUrl, web, label)
        }

        if (uri.host.equals("www.jw.org", ignoreCase = true) && uri.rawPath == "/finder") {
            return Target(
                libraryUri = "jwlibrary:///finder" + rawQuery?.let { "?$it" }.orEmpty(),
                webUrl = cleanUrl,
                label = label,
            )
        }
'''
new_scheme = '''        if (uri.scheme.equals("jwlibrary", ignoreCase = true)) {
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
'''
if text.count(old_scheme) != 1:
    raise SystemExit("JwLibraryLinkResolver exact finder branch changed unexpectedly.")
text = text.replace(old_scheme, new_scheme, 1)
insert_anchor = '''        return Target(libraryUri = null, webUrl = cleanUrl, label = label)
    }


    /**
'''
helper = '''        return Target(libraryUri = null, webUrl = cleanUrl, label = label)
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
        if (docId.matches(Regex("\\d+"))) return true

        val bible = parameters["bible"]?.firstOrNull().orEmpty()
        if (bible.matches(Regex("\\d{7,8}(?:-\\d{7,8})?"))) return true

        val publication = parameters["pub"]?.firstOrNull().orEmpty()
        if (publication.matches(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,31}"))) return true

        val alias = parameters["alias"]?.firstOrNull().orEmpty()
        val date = parameters["date"]?.firstOrNull().orEmpty()
        return alias.equals("daily-text", ignoreCase = true) &&
            date.matches(Regex("\\d{8}"))
    }


    /**
'''
if text.count(insert_anchor) != 1:
    raise SystemExit("JwLibraryLinkResolver helper insertion anchor changed.")
text = text.replace(insert_anchor, helper, 1)
resolver.write_text(text, encoding="utf-8")

family = ROOT / "app/src/main/java/com/mystudycompanion/app/ui/FamilyWorshipScreen.kt"
text = family.read_text(encoding="utf-8")
import_anchor = "import com.mystudycompanion.app.data.FamilyWorshipSection\n"
if import_anchor not in text:
    raise SystemExit("FamilyWorshipScreen import anchor missing.")
text = text.replace(
    import_anchor,
    "import com.mystudycompanion.app.companion.JwLibraryLinkResolver\n" + import_anchor,
    1,
)
overview_anchor = '''private fun FamilyOverviewCard(study: FamilyWorshipStudy, onOpenAi: (String) -> Unit) {
    val context = LocalContext.current
    Card(
'''
overview_new = '''private fun FamilyOverviewCard(study: FamilyWorshipStudy, onOpenAi: (String) -> Unit) {
    val context = LocalContext.current
    val exactOfficialUrl = exactFamilyOverviewTarget(study.officialUrl, study.keyScripture)
    Card(
'''
if text.count(overview_anchor) != 1:
    raise SystemExit("Family overview anchor changed.")
text = text.replace(overview_anchor, overview_new, 1)
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
if text.count(old_overview_button) != 1:
    raise SystemExit("Family overview link button anchor changed.")
text = text.replace(old_overview_button, new_overview_button, 1)
section_anchor = '''private fun FamilySectionCard(
    section: FamilyWorshipSection,
    onCompleted: (FamilyWorshipSection, Boolean) -> Unit,
) {
    val context = LocalContext.current
    Card(
'''
section_new = '''private fun FamilySectionCard(
    section: FamilyWorshipSection,
    onCompleted: (FamilyWorshipSection, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val exactOfficialUrl = section.officialUrl
        .trim()
        .takeIf(JwLibraryLinkResolver::isDirectLibraryTarget)
    Card(
'''
if text.count(section_anchor) != 1:
    raise SystemExit("Family section anchor changed.")
text = text.replace(section_anchor, section_new, 1)
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
if text.count(old_section_button) != 1:
    raise SystemExit("Family section link button anchor changed.")
text = text.replace(old_section_button, new_section_button, 1)
deep_dive_anchor = '''internal fun familyDeepDivePrompt(study: FamilyWorshipStudy): String =
'''
helper_code = '''internal fun exactFamilyOverviewTarget(officialUrl: String, keyScripture: String): String? {
    val exactOfficial = officialUrl.trim().takeIf(JwLibraryLinkResolver::isDirectLibraryTarget)
    if (exactOfficial != null) return exactOfficial
    return JwLibraryLinkResolver.bibleTarget(keyScripture)
        .webUrl
        .takeIf(JwLibraryLinkResolver::isDirectLibraryTarget)
}

internal fun familyDeepDivePrompt(study: FamilyWorshipStudy): String =
'''
if text.count(deep_dive_anchor) != 1:
    raise SystemExit("Family helper insertion anchor changed.")
text = text.replace(deep_dive_anchor, helper_code, 1)
family.write_text(text, encoding="utf-8")

tests = ROOT / "app/src/test/java/com/mystudycompanion/app/companion/JwLibraryLinkResolverTest.kt"
text = tests.read_text(encoding="utf-8")
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

    @Test
    fun researchGuideUsesPublicationFinderTarget() {
'''
if text.count(test_anchor) != 1:
    raise SystemExit("Resolver test insertion anchor changed.")
text = text.replace(test_anchor, extra_tests, 1)
tests.write_text(text, encoding="utf-8")

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

print("Applied strict exact-Finder validation and stale family-link fail-closed gate.")
