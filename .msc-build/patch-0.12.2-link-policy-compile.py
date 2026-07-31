from pathlib import Path

ROOT = Path("MyStudyCompanion")
policy = ROOT / "app/src/main/java/com/mystudycompanion/app/companion/ExactJwLinkPolicy.kt"
policy.parent.mkdir(parents=True, exist_ok=True)
policy.write_text(
    r'''package com.mystudycompanion.app.companion

/**
 * Pure, JVM-testable policy for deciding whether a stored official URL points
 * to exact material in JW Library and for expanding semicolon Bible shorthand.
 * Android launching remains in [JwLibraryLinkResolver].
 */
object ExactJwLinkPolicy {
    fun isDirectLibraryTarget(url: String): Boolean =
        JwLibraryLinkResolver.targetFromOfficialUrl(url)
            .libraryUri
            ?.startsWith("jwlibrary:///finder") == true

    fun requireDirectLibraryTarget(url: String, label: String = "official material"): String {
        val clean = url.trim()
        require(isDirectLibraryTarget(clean)) {
            "$label must resolve to exact material inside JW Library."
        }
        return clean
    }

    fun splitBiblePassages(reference: String): List<String> {
        var activeBook: String? = null
        return reference.split(';').mapNotNull { raw ->
            val part = raw.trim()
            if (part.isBlank()) return@mapNotNull null
            val explicitBook = bookAtStart(part)
            if (explicitBook != null) {
                activeBook = explicitBook
                part
            } else {
                activeBook?.let { "$it $part" } ?: part
            }
        }
    }

    private fun bookAtStart(reference: String): String? =
        BOOK_PREFIX.find(reference)?.groupValues?.getOrNull(1)?.trim()

    private val BOOK_PREFIX = Regex(
        """^((?:[1-3]\s+)?[A-Za-z]+(?:\s+(?:of\s+)?[A-Za-z]+)*)\s+(?=\d)""",
    )
}
''',
    encoding="utf-8",
)

replacements = {
    "JwLibraryLinkResolver.splitBiblePassages": "ExactJwLinkPolicy.splitBiblePassages",
    "JwLibraryLinkResolver.requireDirectLibraryTarget": "ExactJwLinkPolicy.requireDirectLibraryTarget",
    "JwLibraryLinkResolver.isDirectLibraryTarget": "ExactJwLinkPolicy.isDirectLibraryTarget",
    "JwLibraryLinkResolver::isDirectLibraryTarget": "ExactJwLinkPolicy::isDirectLibraryTarget",
}

source_roots = [
    ROOT / "app/src/main/java",
    ROOT / "app/src/test/java",
]
modified = []
for source_root in source_roots:
    for path in source_root.rglob("*.kt"):
        if path == policy:
            continue
        text = path.read_text(encoding="utf-8")
        updated = text
        for old, new in replacements.items():
            updated = updated.replace(old, new)
        if updated == text:
            continue
        if path.parent.name != "companion" and "ExactJwLinkPolicy" in updated:
            import_line = "import com.mystudycompanion.app.companion.ExactJwLinkPolicy\n"
            if import_line not in updated:
                package_end = updated.find("\n\n")
                if package_end < 0:
                    raise SystemExit(f"No import insertion point in {path}")
                updated = updated[: package_end + 2] + import_line + updated[package_end + 2 :]
        path.write_text(updated, encoding="utf-8")
        modified.append(str(path.relative_to(ROOT)))

required = {
    "app/src/main/java/com/mystudycompanion/app/network/ContentPayloadDecoder.kt",
    "app/src/main/java/com/mystudycompanion/app/ui/CompanionHubScreen.kt",
    "app/src/main/java/com/mystudycompanion/app/ui/FamilyWorshipScreen.kt",
    "app/src/test/java/com/mystudycompanion/app/companion/JwLibraryLinkResolverTest.kt",
}
missing = sorted(required.difference(modified))
if missing:
    raise SystemExit(f"Expected exact-link policy callers were not rewritten: {missing}")

remaining = []
for source_root in source_roots:
    for path in source_root.rglob("*.kt"):
        if path == policy:
            continue
        text = path.read_text(encoding="utf-8")
        for legacy in replacements:
            if legacy in text:
                remaining.append(f"{path.relative_to(ROOT)}: {legacy}")
if remaining:
    raise SystemExit("Unresolved resolver helper calls remain: " + "; ".join(remaining))

assert "fun splitBiblePassages" in policy.read_text(encoding="utf-8")
assert "fun isDirectLibraryTarget" in policy.read_text(encoding="utf-8")
assert "fun requireDirectLibraryTarget" in policy.read_text(encoding="utf-8")
print("Separated exact JW link policy into an independently compiled pure Kotlin object.")
