#!/usr/bin/env python3
"""Inventory every final-source link launcher and account/sync integration point.

This runs only after all archived source stages and overlays are reconstructed.
The report is intentionally diagnostic: it makes the complete final source
visible for the next hardening pass without pretending that a string search is
itself end-to-end link verification.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else "MyStudyCompanion")
OUT = Path(sys.argv[2] if len(sys.argv) > 2 else "final-link-sync-surface.json")
TEXT_OUT = OUT.with_suffix(".txt")

TEXT_SUFFIXES = {
    ".kt", ".kts", ".java", ".xml", ".json", ".toml", ".properties",
    ".gradle", ".md", ".txt", ".pro", ".yaml", ".yml",
}
URL_RE = re.compile(r"(?:jwlibrary|https?)://[^\s\"'<>)]*", re.I)

LINK_TOKENS = (
    "Intent.ACTION_VIEW", "ACTION_VIEW", "Uri.parse", "java.net.URI",
    "JwLibraryLinkResolver", "openOfficial", "openWeb", "officialUrl",
    "citationUrl", "libraryUri", "webUrl", "finder?", "jw.org", "wol.jw.org",
    "jwlibrary://", "actionStartActivity", "ClickableText", "clickable",
)
SYNC_TOKENS = (
    "Firebase", "firebase", "GoogleSignIn", "GoogleIdToken", "CredentialManager",
    "google-services", "web_client_id", "GoogleAuthProvider", "Firestore",
    "family", "Family", "sync", "Sync", "invite", "Invite", "account", "Account",
    "DataClient", "MessageClient", "Wearable", "AppCheck",
)

records: list[dict[str, object]] = []
for path in sorted(ROOT.rglob("*")):
    if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    rel = path.relative_to(ROOT).as_posix()
    for number, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        urls = URL_RE.findall(raw)
        link_hits = sorted({token for token in LINK_TOKENS if token in raw})
        sync_hits = sorted({token for token in SYNC_TOKENS if token in raw})
        if not (urls or link_hits or sync_hits):
            continue
        records.append({
            "file": rel,
            "line": number,
            "text": line[:1200],
            "urls": urls,
            "link_tokens": link_hits,
            "sync_tokens": sync_hits,
        })

link_records = [r for r in records if r["urls"] or r["link_tokens"]]
sync_records = [r for r in records if r["sync_tokens"]]
direct_view = [r for r in link_records if "Intent.ACTION_VIEW" in r["text"] or "ACTION_VIEW" in r["text"]]
resolver_calls = [r for r in link_records if "JwLibraryLinkResolver" in r["text"] or "openOfficial" in r["text"]]
url_literals = sorted({url for r in link_records for url in r["urls"]})

summary = {
    "root": ROOT.as_posix(),
    "scanned_text_files": sum(1 for p in ROOT.rglob("*") if p.is_file() and p.suffix.lower() in TEXT_SUFFIXES),
    "link_records": len(link_records),
    "sync_records": len(sync_records),
    "direct_action_view_records": len(direct_view),
    "resolver_call_records": len(resolver_calls),
    "unique_url_literals": len(url_literals),
}
payload = {
    "summary": summary,
    "unique_url_literals": url_literals,
    "direct_action_view_records": direct_view,
    "resolver_call_records": resolver_calls,
    "link_records": link_records,
    "sync_records": sync_records,
}
OUT.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

lines = [
    "FINAL RECONSTRUCTED SOURCE — LINK AND SYNC SURFACE INVENTORY",
    json.dumps(summary, sort_keys=True),
    "",
    "UNIQUE URL LITERALS",
    *[f"- {url}" for url in url_literals],
    "",
    "DIRECT ACTION_VIEW / BROWSER-CAPABLE CALL SITES",
]
for record in direct_view:
    lines.append(f"- {record['file']}:{record['line']}: {record['text']}")
lines.extend(["", "JW LIBRARY RESOLVER CALL SITES"])
for record in resolver_calls:
    lines.append(f"- {record['file']}:{record['line']}: {record['text']}")
lines.extend(["", "ALL LINK-RELATED SOURCE LINES"])
for record in link_records:
    lines.append(f"- {record['file']}:{record['line']}: {record['text']}")
lines.extend(["", "AUTH / FAMILY / SYNC SOURCE LINES"])
for record in sync_records:
    lines.append(f"- {record['file']}:{record['line']}: {record['text']}")
TEXT_OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")

print(json.dumps(summary, sort_keys=True))
