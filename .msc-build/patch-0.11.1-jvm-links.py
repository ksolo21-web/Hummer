#!/usr/bin/env python3
from pathlib import Path

ROOT = Path('MyStudyCompanion')
resolver = ROOT / 'app/src/main/java/com/mystudycompanion/app/companion/JwLibraryLinkResolver.kt'
meeting = ROOT / 'app/src/main/java/com/mystudycompanion/app/data/official/OfficialWeeklyMeetingRepository.kt'

resolver_text = resolver.read_text(encoding='utf-8')
new_resolver_block = r'''    fun targetFromOfficialUrl(url: String, label: String = "official material"): Target {
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
resolver_start_marker = '    fun targetFromOfficialUrl(url: String, label: String = "official material"): Target {'
resolver_end_marker = '\n\n    /** Web form retained for official-source storage and citations. */'
resolver_start = resolver_text.find(resolver_start_marker)
resolver_end = resolver_text.find(resolver_end_marker, resolver_start)
if resolver_start < 0 or resolver_end < 0:
    raise SystemExit('Could not locate targetFromOfficialUrl structural boundaries.')
resolver_text = resolver_text[:resolver_start] + new_resolver_block + resolver_text[resolver_end:]
resolver.write_text(resolver_text, encoding='utf-8')

meeting_text = meeting.read_text(encoding='utf-8').replace('import android.text.Html\n', '')
new_decode = r'''        private fun decode(value: String): String {
            val withoutTags = value.replace(Regex("<[^>]+>"), " ")
            val decoded = Regex("""&(#x[0-9A-Fa-f]+|#\d+|[A-Za-z][A-Za-z0-9]+);""").replace(withoutTags) { match ->
                decodeHtmlEntity(match.groupValues[1]) ?: match.value
            }
            return decoded
                .replace('\u00A0', ' ')
                .replace(Regex("""\s+"""), " ")
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
                "quot" -> 34.toChar().toString()
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
decode_start_marker = '        private fun decode(value: String): String'
decode_end_marker = '\n\n        private fun mondayFor(date: LocalDate): LocalDate'
decode_start = meeting_text.find(decode_start_marker)
decode_end = meeting_text.find(decode_end_marker, decode_start)
if decode_start < 0 or decode_end < 0:
    raise SystemExit('Could not locate meeting decode structural boundaries.')
meeting_text = meeting_text[:decode_start] + new_decode + meeting_text[decode_end:]
meeting.write_text(meeting_text, encoding='utf-8')

resolver_result = resolver.read_text(encoding='utf-8')
meeting_result = meeting.read_text(encoding='utf-8')
assert 'java.net.URI(cleanUrl)' in resolver_result
assert 'android.text.Html' not in meeting_result
assert 'Regex("""&(#x[0-9A-Fa-f]+|#\\d+' in meeting_result
assert '"quot" -> 34.toChar().toString()' in meeting_result
print('Applied JVM-safe JW Library URL and official meeting HTML parsing fixes.')
