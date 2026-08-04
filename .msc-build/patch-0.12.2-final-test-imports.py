from pathlib import Path

path = Path(
    "MyStudyCompanion/app/src/test/java/com/mystudycompanion/app/companion/"
    "JwLibraryLinkResolverTest.kt"
)
text = path.read_text(encoding="utf-8")
required_import = "import org.junit.Assert.assertFalse\n"
if required_import not in text:
    anchor = "import org.junit.Assert.assertEquals\n"
    if text.count(anchor) != 1:
        raise SystemExit("Could not locate the resolver-test JUnit import anchor.")
    text = text.replace(anchor, anchor + required_import, 1)
path.write_text(text, encoding="utf-8")

result = path.read_text(encoding="utf-8")
if result.count(required_import) != 1:
    raise SystemExit("The resolver test must contain exactly one assertFalse import.")
print("Pinned the exact-link resolver test assertion imports after all overlays.")
