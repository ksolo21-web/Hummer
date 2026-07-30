#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('MyStudyCompanion')
resolver = ROOT / 'app/src/main/java/com/mystudycompanion/app/companion/JwLibraryLinkResolver.kt'
meeting = ROOT / 'app/src/main/java/com/mystudycompanion/app/data/official/OfficialWeeklyMeetingRepository.kt'

resolver_text = resolver.read_text(encoding='utf-8')
old_resolver_block = '''    fun targetFromOfficialUrl(url: String, label: String = "official material"): Target {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return Target(null, RESEARCH_GUIDE_URL, label)
        val uri = runCatching { Uri.parse(cleanUrl) }.getOrNull()
            ?: return Target(null, cleanUrl, label)

        if (uri.scheme.equals("jwlibrary", ignoreCase = true)) {
            val web = "https://www.jw.org${uri.path.orEmpty()}" +
                uri.encodedQuery?.let { "?$it" }.orEmpty()
            return Target(cleanUrl, web, label)
        }

        if (uri.host.equals("www.jw.org", ignoreCase = true) && uri.path == "/finder") {
            return Target(
                libraryUri = "jwlibrary:///finder" + uri.encodedQuery?.let { "?$it" }.orEmpty(),
                webUrl = cleanUrl,
                label = label,
            )
        }

        val wolDocId = if (uri.host.equals("wol.jw.org", ignoreCase = true)) {
            Regex("/wol/d/[^/]+/[^/]+/(\\d+)").find(uri.path.orEmpty())?.groupValues?.getOrNull(1)
        } else null
        if (!wolDocId.isNullOrBlank()) return documentTarget(wolDocId, cleanUrl, label)

        val finderDocId = uri.getQueryParameter("docid")
        if (!finderDocId.isNullOrBlank()) return documentTarget(finderDocId, cleanUrl, label)

        return Target(libraryUri = null, webUrl = cleanUrl, label = label)
    }
'''
new_resolver_block = '''    fun targetFromOfficialUrl(url: String, label: String = "official material"): Target {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return Target(null, RESEARCH_GUIDE_URL, label)

        // This method is pure link interpretation and is also exercised by local JVM tests.
        // Use java.net.URI here instead of Android's Uri stub; Android Uri remains the correct
        // type for Intent launching in open() and openWeb().
        val uri = runCatching { java.net.URI(cleanUrl) }.getOrNull()
            ?: return Target(null, cleanUrl, label)
        val rawQuery = uri.rawQuery

        if (uri.scheme.equals("jwlibrary", ignoreCase = true)) {
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

        val wolDocId = if (uri.host.equals("wol.jw.org", ignoreCase = true)) {
            Regex("/wol/d/[^/]+/[^/]+/(\\d+)").find(uri.rawPath.orEmpty())?.groupValues?.getOrNull(1)
        } else null
        if (!wolDocId.isNullOrBlank()) return documentTarget(wolDocId, cleanUrl, label)

        val finderDocId = rawQuery.orEmpty()
            .split('&')
            .asSequence()
            .map { parameter -> parameter.substringBefore('=') to parameter.substringAfter('=', "") }
            .firstOrNull { (name, _) -> name.equals("docid", ignoreCase = true) }
            ?.second
        if (!finderDocId.isNullOrBlank()) return documentTarget(finderDocId, cleanUrl, label)

        return Target(libraryUri = null, webUrl = cleanUrl, label = label)
    }
'''
if old_resolver_block not in resolver_text:
    if new_resolver_block not in resolver_text:
        raise SystemExit('Expected targetFromOfficialUrl block was not found.')
else:
    resolver_text = resolver_text.replace(old_resolver_block, new_resolver_block)
resolver.write_text(resolver_text, encoding='utf-8')

meeting_text = meeting.read_text(encoding='utf-8')
meeting_text = meeting_text.replace('import android.text.Html\n', '')
old_decode = '''        private fun decode(value: String): String = Html.fromHtml(
            value.replace(Regex("<[^>]+>"), " "),
            Html.FROM_HTML_MODE_COMPACT,
        ).toString().replace(Regex("\\s+"), " ").trim()
'''
new_decode = '''        private fun decode(value: String): String {
            val withoutTags = value.replace(Regex("<[^>]+>"), " ")
            val decoded = Regex("&(#x[0-9A-Fa-f]+|#\\d+|[A-Za-z][A-Za-z0-9]+);").replace(withoutTags) { match ->
                decodeHtmlEntity(match.groupValues[1]) ?: match.value
            }
            return decoded
                .replace('\\u00A0', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        private fun decodeHtmlEntity(entity: String): String? = when {
            entity.startsWith("#x", ignoreCase = true) ->
                entity.substring(2).toIntOrNull(16)?.toUnicodeString()
            entity.startsWith('#') ->
                entity.substring(1).toIntOrNull()?.toUnicodeString()
            else -> when (entity.lowercase()) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                "ndash" -> "–"
                "mdash" -> "—"
                "hellip" -> "…"
                else -> null
            }
        }

        private fun Int.toUnicodeString(): String? =
            takeIf(Character::isValidCodePoint)?.let { String(Character.toChars(it)) }
'''
if old_decode not in meeting_text:
    if new_decode not in meeting_text:
        raise SystemExit('Expected Android Html decode block was not found.')
else:
    meeting_text = meeting_text.replace(old_decode, new_decode)
meeting.write_text(meeting_text, encoding='utf-8')

assert 'java.net.URI(cleanUrl)' in resolver.read_text(encoding='utf-8')
assert 'android.text.Html' not in meeting.read_text(encoding='utf-8')
assert 'decodeHtmlEntity' in meeting.read_text(encoding='utf-8')
print('Applied JVM-safe JW Library URL and official meeting HTML parsing fixes.')
