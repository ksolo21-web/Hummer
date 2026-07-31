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

# Replace the method-reference form before dotted calls so neither replacement
# can partially shadow the other.
replacements = (
    ("JwLibraryLinkResolver::isDirectLibraryTarget", "ExactJwLinkPolicy::isDirectLibraryTarget"),
    ("JwLibraryLinkResolver.splitBiblePassages", "ExactJwLinkPolicy.splitBiblePassages"),
    ("JwLibraryLinkResolver.requireDirectLibraryTarget", "ExactJwLinkPolicy.requireDirectLibraryTarget"),
    ("JwLibraryLinkResolver.isDirectLibraryTarget", "ExactJwLinkPolicy.isDirectLibraryTarget"),
)
legacy_markers = tuple(old for old, _ in replacements)
policy_import = "import com.mystudycompanion.app.companion.ExactJwLinkPolicy\n"

source_roots = [
    ROOT / "app/src/main/java",
    ROOT / "app/src/test/java",
]
for source_root in source_roots:
    for path in source_root.rglob("*.kt"):
        if path == policy:
            continue
        text = path.read_text(encoding="utf-8")
        updated = text
        for old, new in replacements:
            updated = updated.replace(old, new)
        if updated == text:
            continue
        if path.parent.name != "companion" and "ExactJwLinkPolicy" in updated:
            if policy_import not in updated:
                package_end = updated.find("\n\n")
                if package_end < 0:
                    raise SystemExit(f"No import insertion point in {path}")
                updated = updated[: package_end + 2] + policy_import + updated[package_end + 2 :]
        path.write_text(updated, encoding="utf-8")

# Some reconstructed source variants contain only broad spiritual-domain checks
# in the signed-content decoder. Add the exact JW Library requirement after each
# corresponding allowed-domain check, without duplicating a previously inserted
# exact-target check.
decoder = ROOT / "app/src/main/java/com/mystudycompanion/app/network/ContentPayloadDecoder.kt"
decoder_text = decoder.read_text(encoding="utf-8")
if policy_import not in decoder_text:
    package_end = decoder_text.find("\n\n")
    if package_end < 0:
        raise SystemExit("No import insertion point in ContentPayloadDecoder.kt")
    decoder_text = decoder_text[: package_end + 2] + policy_import + decoder_text[package_end + 2 :]

rules = {
    "SpiritualSourcePolicy.requireAllowed(study.officialUrl)":
        'ExactJwLinkPolicy.requireDirectLibraryTarget(study.officialUrl, "signed spiritual source")',
    "SpiritualSourcePolicy.requireAllowed(part.officialUrl)":
        'ExactJwLinkPolicy.requireDirectLibraryTarget(part.officialUrl, "meeting-part source")',
    "SpiritualSourcePolicy.requireAllowed(section.officialUrl)":
        'ExactJwLinkPolicy.requireDirectLibraryTarget(section.officialUrl, "family worship section source")',
}
lines = decoder_text.splitlines()
rebuilt = []
for index, line in enumerate(lines):
    rebuilt.append(line)
    target = rules.get(line.strip())
    if target is None:
        continue
    nearby = "\n".join(candidate.strip() for candidate in lines[index + 1 : index + 5])
    if target in nearby:
        continue
    indent = line[: len(line) - len(line.lstrip())]
    rebuilt.append(indent + target)
decoder_text = "\n".join(rebuilt) + ("\n" if decoder_text.endswith("\n") else "")
decoder.write_text(decoder_text, encoding="utf-8")

final_decoder = decoder.read_text(encoding="utf-8")
expected_decoder_calls = {
    'ExactJwLinkPolicy.requireDirectLibraryTarget(study.officialUrl, "signed spiritual source")': 3,
    'ExactJwLinkPolicy.requireDirectLibraryTarget(part.officialUrl, "meeting-part source")': 1,
    'ExactJwLinkPolicy.requireDirectLibraryTarget(section.officialUrl, "family worship section source")': 1,
}
for call, expected_count in expected_decoder_calls.items():
    actual_count = final_decoder.count(call)
    if actual_count != expected_count:
        raise SystemExit(
            f"Expected {expected_count} signed-content exact-target call(s), found {actual_count}: {call}"
        )

remaining = []
for source_root in source_roots:
    for path in source_root.rglob("*.kt"):
        if path == policy:
            continue
        text = path.read_text(encoding="utf-8")
        for legacy in legacy_markers:
            if legacy in text:
                remaining.append(f"{path.relative_to(ROOT)}: {legacy}")
if remaining:
    raise SystemExit("Unresolved resolver helper calls remain: " + "; ".join(remaining))

# Kotlin call sites may use either a normal dotted invocation or a callable
# reference. Both are valid evidence that the exact-link policy is wired in.
final_invariants = {
    decoder: ("ExactJwLinkPolicy.requireDirectLibraryTarget",),
    ROOT / "app/src/main/java/com/mystudycompanion/app/ui/CompanionHubScreen.kt":
        ("ExactJwLinkPolicy.splitBiblePassages",),
    ROOT / "app/src/main/java/com/mystudycompanion/app/ui/FamilyWorshipScreen.kt":
        ("ExactJwLinkPolicy.isDirectLibraryTarget", "ExactJwLinkPolicy::isDirectLibraryTarget"),
    ROOT / "app/src/test/java/com/mystudycompanion/app/companion/JwLibraryLinkResolverTest.kt":
        ("ExactJwLinkPolicy.isDirectLibraryTarget", "ExactJwLinkPolicy::isDirectLibraryTarget"),
}
for path, accepted_markers in final_invariants.items():
    text = path.read_text(encoding="utf-8")
    if not any(marker in text for marker in accepted_markers):
        raise SystemExit(
            f"Final exact-link policy invariant missing from {path}: one of {accepted_markers}"
        )
    if path.parent.name != "companion" and policy_import.strip() not in text:
        raise SystemExit(f"ExactJwLinkPolicy import missing from {path}")

policy_text = policy.read_text(encoding="utf-8")
assert "fun splitBiblePassages" in policy_text
assert "fun isDirectLibraryTarget" in policy_text
assert "fun requireDirectLibraryTarget" in policy_text
print("Separated exact JW link policy and enforced it across every signed spiritual content surface.")
