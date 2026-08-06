#!/usr/bin/env python3
"""Safely register the permanent KREATIV signer and merge shared Firebase rules."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

RULES_API = "https://firebaserules.googleapis.com/v1"
FIREBASE_API = "https://firebase.googleapis.com/v1beta1"
BEGIN_MARKER = "// BEGIN KREATIV STUDIO MANAGED RULES"
END_MARKER = "// END KREATIV STUDIO MANAGED RULES"


class RepairError(RuntimeError):
    """Raised when a repair precondition or Firebase operation fails."""


@dataclass(frozen=True)
class LiveRules:
    service: str
    release_name: str
    ruleset_name: str
    files: list[dict[str, str]]
    target_index: int
    original_content: str
    merged_content: str


class GoogleApi:
    def __init__(self, access_token: str, timeout: int = 30) -> None:
        token = access_token.strip()
        if not token:
            raise RepairError("FIREBASE_ACCESS_TOKEN is empty.")
        self._token = token
        self._timeout = timeout

    def request(
        self,
        method: str,
        url: str,
        body: dict[str, Any] | None = None,
        expected: Iterable[int] = (200,),
    ) -> dict[str, Any]:
        data = None
        headers = {
            "Authorization": f"Bearer {self._token}",
            "Accept": "application/json",
            "User-Agent": "kreativ-firebase-repair/1.0",
        }
        if body is not None:
            data = json.dumps(body, separators=(",", ":")).encode("utf-8")
            headers["Content-Type"] = "application/json; charset=utf-8"

        request = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=self._timeout) as response:
                payload = response.read()
                if response.status not in set(expected):
                    raise RepairError(f"{method} {url} returned HTTP {response.status}.")
                return json.loads(payload.decode("utf-8")) if payload else {}
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8", errors="replace")
            detail = raw
            try:
                parsed = json.loads(raw)
                detail = parsed.get("error", {}).get("message", raw)
            except json.JSONDecodeError:
                pass
            raise RepairError(
                f"{method} {url} failed with HTTP {exc.code}: {detail}"
            ) from exc
        except urllib.error.URLError as exc:
            raise RepairError(f"{method} {url} failed: {exc.reason}") from exc


def normalize_sha(value: str) -> str:
    normalized = re.sub(r"[^0-9A-Fa-f]", "", value).upper()
    if len(normalized) not in (40, 64):
        raise RepairError(
            f"Invalid SHA certificate length: {len(normalized)} hex characters."
        )
    return normalized


def colon_sha(value: str) -> str:
    normalized = normalize_sha(value)
    return ":".join(
        normalized[index : index + 2] for index in range(0, len(normalized), 2)
    )


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def api_path(resource_name: str) -> str:
    return urllib.parse.quote(resource_name, safe="/.:-_")


def list_all(
    api: GoogleApi, base_url: str, collection_key: str
) -> list[dict[str, Any]]:
    values: list[dict[str, Any]] = []
    page_token = ""
    while True:
        separator = "&" if "?" in base_url else "?"
        url = base_url
        if page_token:
            url += f"{separator}pageToken={urllib.parse.quote(page_token, safe='')}"
        payload = api.request("GET", url)
        values.extend(payload.get(collection_key, []))
        page_token = payload.get("nextPageToken", "")
        if not page_token:
            return values


def list_releases(api: GoogleApi, project_id: str) -> list[dict[str, Any]]:
    return list_all(
        api,
        f"{RULES_API}/projects/{urllib.parse.quote(project_id, safe='')}/releases?pageSize=100",
        "releases",
    )


def release_id(release_name: str) -> str:
    marker = "/releases/"
    if marker not in release_name:
        raise RepairError(f"Unexpected release resource name: {release_name}")
    return release_name.split(marker, 1)[1]


def select_release(
    releases: list[dict[str, Any]],
    service: str,
    storage_bucket: str | None = None,
) -> dict[str, Any]:
    if service == "cloud.firestore":
        candidates = [
            item
            for item in releases
            if release_id(str(item.get("name", ""))) == "cloud.firestore"
        ]
    elif service == "firebase.storage":
        candidates = [
            item
            for item in releases
            if release_id(str(item.get("name", ""))).startswith("firebase.storage/")
        ]
        if storage_bucket:
            exact = [
                item
                for item in candidates
                if release_id(str(item.get("name", "")))
                == f"firebase.storage/{storage_bucket}"
            ]
            if exact:
                candidates = exact
    else:
        raise RepairError(f"Unsupported Firebase Rules service: {service}")

    if len(candidates) != 1:
        names = [str(item.get("name", "")) for item in candidates]
        raise RepairError(
            f"Expected exactly one {service} release, found {len(candidates)}: {names}"
        )
    selected = candidates[0]
    if not selected.get("rulesetName"):
        raise RepairError(f"Release {selected.get('name')} has no ruleset.")
    return selected


def get_ruleset(api: GoogleApi, ruleset_name: str) -> dict[str, Any]:
    return api.request("GET", f"{RULES_API}/{api_path(ruleset_name)}")


def source_files(ruleset: dict[str, Any]) -> list[dict[str, str]]:
    files = ruleset.get("source", {}).get("files", [])
    if not files:
        raise RepairError(f"Ruleset {ruleset.get('name')} has no source files.")
    result: list[dict[str, str]] = []
    for index, item in enumerate(files):
        content = item.get("content")
        if not isinstance(content, str):
            raise RepairError(f"Ruleset source file {index} has no textual content.")
        result.append(
            {
                "name": str(item.get("name") or f"rules-{index}.rules"),
                "content": content,
            }
        )
    return result


def find_matching_brace(text: str, opening_index: int) -> int:
    if opening_index < 0 or opening_index >= len(text) or text[opening_index] != "{":
        raise RepairError("Internal brace matching error: opening brace not found.")

    depth = 0
    index = opening_index
    quote: str | None = None
    escaped = False
    line_comment = False
    block_comment = False

    while index < len(text):
        char = text[index]
        nxt = text[index + 1] if index + 1 < len(text) else ""

        if line_comment:
            if char == "\n":
                line_comment = False
            index += 1
            continue
        if block_comment:
            if char == "*" and nxt == "/":
                block_comment = False
                index += 2
            else:
                index += 1
            continue
        if quote is not None:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            index += 1
            continue

        if char == "/" and nxt == "/":
            line_comment = True
            index += 2
            continue
        if char == "/" and nxt == "*":
            block_comment = True
            index += 2
            continue
        if char in ("'", '"'):
            quote = char
            index += 1
            continue
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return index
            if depth < 0:
                break
        index += 1

    raise RepairError("Could not find the target Firebase match block closing brace.")


def line_indent(text: str, index: int) -> str:
    start = text.rfind("\n", 0, index) + 1
    match = re.match(r"[ \t]*", text[start:index])
    return match.group(0) if match else ""


def indent_fragment(fragment: str, indent: str) -> str:
    lines = fragment.strip().splitlines()
    if not lines:
        raise RepairError("KREATIV Firebase rule fragment is empty.")
    return "\n".join(
        f"{indent}{line.rstrip()}" if line.strip() else "" for line in lines
    )


def managed_block(fragment: str, indent: str) -> str:
    body_indent = indent + "  "
    return (
        f"{body_indent}{BEGIN_MARKER}\n"
        f"{indent_fragment(fragment, body_indent)}\n"
        f"{body_indent}{END_MARKER}"
    )


def merge_managed_rules(content: str, fragment: str, service: str) -> str:
    begin_count = content.count(BEGIN_MARKER)
    end_count = content.count(END_MARKER)
    if begin_count != end_count or begin_count > 1:
        raise RepairError(
            f"{service} rules contain inconsistent KREATIV markers "
            f"(begin={begin_count}, end={end_count})."
        )

    if begin_count == 1:
        begin = content.index(BEGIN_MARKER)
        marker_line_start = content.rfind("\n", 0, begin) + 1
        end = content.index(END_MARKER, begin)
        marker_line_end = content.find("\n", end)
        if marker_line_end == -1:
            marker_line_end = len(content)
        else:
            marker_line_end += 1
        marker_indent = line_indent(content, begin)
        target_indent = (
            marker_indent[:-2] if marker_indent.endswith("  ") else marker_indent
        )
        replacement = managed_block(fragment, target_indent)
        if marker_line_end < len(content):
            replacement += "\n"
        merged = (
            content[:marker_line_start] + replacement + content[marker_line_end:]
        )
    else:
        if service == "cloud.firestore":
            target = re.search(
                r"match\s+/databases/\{[^}]+\}/documents\s*\{", content
            )
        elif service == "firebase.storage":
            target = re.search(r"match\s+/b/\{[^}]+\}/o\s*\{", content)
        else:
            raise RepairError(f"Unsupported service for merge: {service}")
        if not target:
            raise RepairError(f"Could not locate the root match block in {service} rules.")

        opening = content.rfind("{", target.start(), target.end())
        closing = find_matching_brace(content, opening)
        target_indent = line_indent(content, target.start())
        block = managed_block(fragment, target_indent)
        prefix = content[:closing].rstrip()
        suffix = content[closing:]
        merged = f"{prefix}\n\n{block}\n{target_indent}{suffix}"

    if merged.count(BEGIN_MARKER) != 1 or merged.count(END_MARKER) != 1:
        raise RepairError(f"{service} merge did not produce one managed block.")
    required_matches = [
        line.strip()
        for line in fragment.splitlines()
        if line.strip().startswith("match ")
    ]
    for required in required_matches:
        if required not in merged:
            raise RepairError(
                f"{service} merged rules are missing fragment line: {required}"
            )
    return merged


def build_live_rules(
    api: GoogleApi,
    releases: list[dict[str, Any]],
    service: str,
    fragment: str,
    storage_bucket: str | None,
) -> LiveRules:
    release = select_release(releases, service, storage_bucket)
    ruleset = get_ruleset(api, str(release["rulesetName"]))
    files = source_files(ruleset)
    candidates = [
        index
        for index, item in enumerate(files)
        if re.search(rf"\bservice\s+{re.escape(service)}\s*\{{", item["content"])
    ]
    if len(candidates) != 1:
        raise RepairError(
            f"Expected one source file declaring {service}; found {len(candidates)}."
        )
    target_index = candidates[0]
    original = files[target_index]["content"]
    merged = merge_managed_rules(original, fragment, service)
    return LiveRules(
        service=service,
        release_name=str(release["name"]),
        ruleset_name=str(release["rulesetName"]),
        files=files,
        target_index=target_index,
        original_content=original,
        merged_content=merged,
    )


def merged_source(live: LiveRules) -> dict[str, Any]:
    files = [dict(item) for item in live.files]
    files[live.target_index]["content"] = live.merged_content
    return {"files": files}


def validate_source(
    api: GoogleApi, project_id: str, live: LiveRules
) -> list[dict[str, Any]]:
    payload = api.request(
        "POST",
        f"{RULES_API}/projects/{urllib.parse.quote(project_id, safe='')}:test",
        {"source": merged_source(live), "testSuite": {"testCases": []}},
    )
    issues = payload.get("issues", [])
    errors = [item for item in issues if item.get("severity") == "ERROR"]
    if errors:
        raise RepairError(f"{live.service} rules failed validation: {errors}")
    return issues


def create_ruleset(api: GoogleApi, project_id: str, live: LiveRules) -> str:
    created = api.request(
        "POST",
        f"{RULES_API}/projects/{urllib.parse.quote(project_id, safe='')}/rulesets",
        {"source": merged_source(live)},
    )
    name = created.get("name")
    if not name:
        raise RepairError(f"No ruleset name returned for {live.service}.")
    return str(name)


def get_release(api: GoogleApi, release_name: str) -> dict[str, Any]:
    return api.request("GET", f"{RULES_API}/{api_path(release_name)}")


def patch_release(
    api: GoogleApi,
    release_name: str,
    ruleset_name: str,
    expected_current_ruleset: str,
) -> dict[str, Any]:
    current = get_release(api, release_name)
    actual_current = str(current.get("rulesetName", ""))
    if actual_current != expected_current_ruleset:
        raise RepairError(
            f"Release {release_name} changed after preflight. "
            f"Expected {expected_current_ruleset}, found {actual_current}. "
            "No rules were overwritten."
        )
    return api.request(
        "PATCH",
        f"{RULES_API}/{api_path(release_name)}",
        {
            "release": {"name": release_name, "rulesetName": ruleset_name},
            "updateMask": "rulesetName",
        },
    )


def verify_android_app(
    api: GoogleApi, project_id: str, android_app_id: str, package_name: str
) -> dict[str, str]:
    app = urllib.parse.quote(android_app_id, safe=":")
    payload = api.request("GET", f"{FIREBASE_API}/projects/-/androidApps/{app}")
    actual = {
        "project_id": str(payload.get("projectId", "")),
        "android_app_id": str(payload.get("appId", "")),
        "package_name": str(payload.get("packageName", "")),
        "state": str(payload.get("state", "")),
    }
    expected = {
        "project_id": project_id,
        "android_app_id": android_app_id,
        "package_name": package_name,
    }
    mismatches = {
        key: {"expected": value, "actual": actual.get(key, "")}
        for key, value in expected.items()
        if actual.get(key, "") != value
    }
    if mismatches:
        raise RepairError(f"Firebase Android app identity mismatch: {mismatches}")
    if actual["state"] and actual["state"] != "ACTIVE":
        raise RepairError(f"Firebase Android app is not active: {actual['state']}")
    return actual


def list_certificates(api: GoogleApi, android_app_id: str) -> list[dict[str, Any]]:
    app = urllib.parse.quote(android_app_id, safe=":")
    payload = api.request("GET", f"{FIREBASE_API}/projects/-/androidApps/{app}/sha")
    return payload.get("certificates", [])


def certificate_map(
    certificates: list[dict[str, Any]],
) -> dict[tuple[str, str], dict[str, Any]]:
    result: dict[tuple[str, str], dict[str, Any]] = {}
    for certificate in certificates:
        raw_hash = str(certificate.get("shaHash", ""))
        if not raw_hash:
            continue
        try:
            key = (str(certificate.get("certType", "")), normalize_sha(raw_hash))
        except RepairError:
            continue
        result[key] = certificate
    return result


def register_missing_certificates(
    api: GoogleApi, android_app_id: str, sha1: str, sha256: str
) -> list[str]:
    app = urllib.parse.quote(android_app_id, safe=":")
    existing = certificate_map(list_certificates(api, android_app_id))
    created: list[str] = []
    for cert_type, cert_hash in [("SHA_1", sha1), ("SHA_256", sha256)]:
        normalized = normalize_sha(cert_hash)
        if (cert_type, normalized) in existing:
            continue
        api.request(
            "POST",
            f"{FIREBASE_API}/projects/-/androidApps/{app}/sha",
            {"shaHash": colon_sha(normalized), "certType": cert_type},
        )
        created.append(cert_type)
    return created


def verify_certificates(
    api: GoogleApi, android_app_id: str, sha1: str, sha256: str
) -> dict[str, bool]:
    existing = certificate_map(list_certificates(api, android_app_id))
    return {
        "sha1_registered": ("SHA_1", normalize_sha(sha1)) in existing,
        "sha256_registered": ("SHA_256", normalize_sha(sha256)) in existing,
    }


def write_generated(live: LiveRules, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(live.merged_content, encoding="utf-8")


def verify_live_rules(
    api: GoogleApi,
    project_id: str,
    service: str,
    expected_content: str,
    storage_bucket: str | None,
) -> dict[str, str]:
    releases = list_releases(api, project_id)
    release = select_release(releases, service, storage_bucket)
    ruleset = get_ruleset(api, str(release["rulesetName"]))
    files = source_files(ruleset)
    candidates = [
        item["content"]
        for item in files
        if re.search(rf"\bservice\s+{re.escape(service)}\s*\{{", item["content"])
    ]
    if len(candidates) != 1 or candidates[0] != expected_content:
        raise RepairError(
            f"Live {service} source does not match the verified merged source."
        )
    return {
        "release": str(release["name"]),
        "ruleset": str(release["rulesetName"]),
        "sha256": sha256_text(candidates[0]),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-id", required=True)
    parser.add_argument("--android-app-id", required=True)
    parser.add_argument("--package-name", required=True)
    parser.add_argument("--storage-bucket", required=True)
    parser.add_argument("--sha1", required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--firestore-fragment", type=Path, required=True)
    parser.add_argument("--storage-fragment", type=Path, required=True)
    parser.add_argument("--generated-firestore", type=Path, required=True)
    parser.add_argument("--generated-storage", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--require-live-ready", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    api = GoogleApi(os.environ.get("FIREBASE_ACCESS_TOKEN", ""))

    if args.package_name != "com.kreativstudio.app":
        raise RepairError(f"Unexpected package name: {args.package_name}")

    android_app = verify_android_app(
        api, args.project_id, args.android_app_id, args.package_name
    )
    sha1 = colon_sha(args.sha1)
    sha256 = colon_sha(args.sha256)
    firestore_fragment = args.firestore_fragment.read_text(encoding="utf-8")
    storage_fragment = args.storage_fragment.read_text(encoding="utf-8")

    releases = list_releases(api, args.project_id)
    firestore = build_live_rules(
        api, releases, "cloud.firestore", firestore_fragment, args.storage_bucket
    )
    storage = build_live_rules(
        api, releases, "firebase.storage", storage_fragment, args.storage_bucket
    )

    firestore_issues = validate_source(api, args.project_id, firestore)
    storage_issues = validate_source(api, args.project_id, storage)
    write_generated(firestore, args.generated_firestore)
    write_generated(storage, args.generated_storage)

    cert_before = verify_certificates(api, args.android_app_id, sha1, sha256)
    report: dict[str, Any] = {
        "timestamp_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "project_id": args.project_id,
        "android_app_id": args.android_app_id,
        "package_name": args.package_name,
        "storage_bucket": args.storage_bucket,
        "firebase_android_app": android_app,
        "apply_requested": args.apply,
        "live_ready_required": args.require_live_ready,
        "signer": {"sha1": sha1, "sha256": sha256},
        "certificates_before": cert_before,
        "firestore": {
            "release_before": firestore.release_name,
            "ruleset_before": firestore.ruleset_name,
            "original_sha256": sha256_text(firestore.original_content),
            "merged_sha256": sha256_text(firestore.merged_content),
            "validation_issues": firestore_issues,
        },
        "storage": {
            "release_before": storage.release_name,
            "ruleset_before": storage.ruleset_name,
            "original_sha256": sha256_text(storage.original_content),
            "merged_sha256": sha256_text(storage.merged_content),
            "validation_issues": storage_issues,
        },
    }

    if args.require_live_ready:
        readiness_failures: list[str] = []
        if not all(cert_before.values()):
            readiness_failures.append(
                f"signer certificates are not fully registered: {cert_before}"
            )
        if firestore.original_content != firestore.merged_content:
            readiness_failures.append(
                "live Firestore rules do not contain the exact KREATIV block"
            )
        if storage.original_content != storage.merged_content:
            readiness_failures.append(
                "live Storage rules do not contain the exact KREATIV block"
            )
        if readiness_failures:
            raise RepairError(
                "Live backend readiness gate failed: " + "; ".join(readiness_failures)
            )
        report["live_backend_ready"] = True

    if args.apply:
        report["certificates_created"] = register_missing_certificates(
            api, args.android_app_id, sha1, sha256
        )
        new_rulesets = {
            "firestore": create_ruleset(api, args.project_id, firestore),
            "storage": create_ruleset(api, args.project_id, storage),
        }
        report["new_rulesets"] = new_rulesets

        patched: list[tuple[str, str]] = []
        try:
            patch_release(
                api,
                firestore.release_name,
                new_rulesets["firestore"],
                firestore.ruleset_name,
            )
            patched.append((firestore.release_name, firestore.ruleset_name))
            patch_release(
                api,
                storage.release_name,
                new_rulesets["storage"],
                storage.ruleset_name,
            )
            patched.append((storage.release_name, storage.ruleset_name))
        except Exception as deployment_error:
            rollback_errors: list[str] = []
            for release_name, old_ruleset in reversed(patched):
                try:
                    current_ruleset = (
                        new_rulesets["firestore"]
                        if release_name == firestore.release_name
                        else new_rulesets["storage"]
                    )
                    patch_release(
                        api, release_name, old_ruleset, current_ruleset
                    )
                except Exception as rollback_error:
                    rollback_errors.append(str(rollback_error))
            if rollback_errors:
                raise RepairError(
                    f"Rules deployment failed ({deployment_error}); rollback also failed: "
                    f"{rollback_errors}"
                ) from deployment_error
            raise RepairError(
                "Rules deployment failed and completed releases were rolled back: "
                f"{deployment_error}"
            ) from deployment_error

        report["certificates_after"] = verify_certificates(
            api, args.android_app_id, sha1, sha256
        )
        if not all(report["certificates_after"].values()):
            raise RepairError("Permanent signer verification failed after apply.")

        report["firestore"]["live_after"] = verify_live_rules(
            api,
            args.project_id,
            "cloud.firestore",
            firestore.merged_content,
            args.storage_bucket,
        )
        report["storage"]["live_after"] = verify_live_rules(
            api,
            args.project_id,
            "firebase.storage",
            storage.merged_content,
            args.storage_bucket,
        )

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RepairError as exc:
        print(f"KREATIV Firebase repair failed: {exc}", file=sys.stderr)
        raise SystemExit(2)
