from pathlib import Path
import re

ROOT = Path("MyStudyCompanion")

identities = {
    ROOT / "app/build.gradle.kts": (
        "26",
        "0.12.2-private-alpha-complete-jw-links",
    ),
    ROOT / "wear/build.gradle.kts": (
        "360120201",
        "0.12.2-wear-private-alpha-complete-jw-links",
    ),
}

for path, (version_code, version_name) in identities.items():
    text = path.read_text(encoding="utf-8")
    text, code_count = re.subn(
        r"(?m)^(\s*)versionCode\s*=\s*\d+\s*$",
        rf"\g<1>versionCode = {version_code}",
        text,
        count=1,
    )
    text, name_count = re.subn(
        r'(?m)^(\s*)versionName\s*=\s*"[^"]+"\s*$',
        rf'\g<1>versionName = "{version_name}"',
        text,
        count=1,
    )
    if code_count != 1 or name_count != 1:
        raise SystemExit(f"Could not set one final version identity in {path}.")
    path.write_text(text, encoding="utf-8")

app = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
wear = (ROOT / "wear/build.gradle.kts").read_text(encoding="utf-8")
assert "versionCode = 26" in app
assert 'versionName = "0.12.2-private-alpha-complete-jw-links"' in app
assert "versionCode = 360120201" in wear
assert 'versionName = "0.12.2-wear-private-alpha-complete-jw-links"' in wear
print("Pinned final 0.12.2 phone and Wear identities after every overlay.")
