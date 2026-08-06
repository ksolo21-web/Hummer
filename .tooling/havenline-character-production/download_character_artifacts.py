#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import pathlib
import shutil
import tempfile
import urllib.error
import urllib.request
import zipfile
from typing import Any

CHARACTERS = ("Character1", "Character2", "Character3", "Character4")


def github_get_json(url: str, token: str) -> dict[str, Any]:
    request = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "HAVENLINE-character-artifact-verifier",
        },
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        payload = json.loads(response.read().decode("utf-8"))
    if not isinstance(payload, dict):
        raise RuntimeError(f"Unexpected GitHub response from {url}")
    return payload


def github_download(url: str, destination: pathlib.Path, token: str) -> None:
    request = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "HAVENLINE-character-artifact-verifier",
        },
    )
    with urllib.request.urlopen(request, timeout=300) as response:
        destination.write_bytes(response.read())


def safe_extract(archive: pathlib.Path, destination: pathlib.Path) -> None:
    destination_resolved = destination.resolve()
    with zipfile.ZipFile(archive) as source:
        for member in source.infolist():
            member_path = (destination / member.filename).resolve()
            if destination_resolved != member_path and destination_resolved not in member_path.parents:
                raise RuntimeError(f"Artifact ZIP contains unsafe path: {member.filename}")
        source.extractall(destination)


def parse_mapping(values: list[str]) -> dict[str, int]:
    mapping: dict[str, int] = {}
    for value in values:
        if "=" not in value:
            raise ValueError(f"Artifact mapping must be Character=ID: {value}")
        character, raw_id = value.split("=", 1)
        character = character.strip()
        if character not in CHARACTERS:
            raise ValueError(f"Unsupported character in artifact mapping: {character}")
        artifact_id = int(raw_id)
        if artifact_id <= 0:
            raise ValueError(f"Artifact ID must be positive for {character}")
        if character in mapping:
            raise ValueError(f"Duplicate artifact mapping for {character}")
        mapping[character] = artifact_id
    if sorted(mapping) != list(CHARACTERS):
        raise ValueError("Artifact mappings must contain Character1 through Character4 exactly")
    if len(set(mapping.values())) != 4:
        raise ValueError("Each character must use a distinct immutable artifact ID")
    return mapping


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Download immutable GitHub Actions artifacts for HAVENLINE Unity review."
    )
    parser.add_argument("--repo", default="ksolo21-web/Hummer")
    parser.add_argument("--artifact", action="append", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--metadata", required=True)
    args = parser.parse_args()

    token = os.environ.get("GH_TOKEN", "").strip()
    if not token:
        raise RuntimeError("GH_TOKEN is required")
    mapping = parse_mapping(args.artifact)
    output = pathlib.Path(args.output)
    metadata_path = pathlib.Path(args.metadata)
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True, exist_ok=True)
    metadata_path.parent.mkdir(parents=True, exist_ok=True)

    result: dict[str, Any] = {
        "schemaVersion": 1,
        "repository": args.repo,
        "artifacts": {},
    }
    owner, repo = args.repo.split("/", 1)
    for character in CHARACTERS:
        artifact_id = mapping[character]
        metadata_url = f"https://api.github.com/repos/{owner}/{repo}/actions/artifacts/{artifact_id}"
        metadata = github_get_json(metadata_url, token)
        if int(metadata.get("id", 0)) != artifact_id:
            raise RuntimeError(f"GitHub returned the wrong artifact for {character}")
        if metadata.get("expired") is True:
            raise RuntimeError(f"Artifact {artifact_id} for {character} has expired")
        if int(metadata.get("size_in_bytes", 0)) <= 0:
            raise RuntimeError(f"Artifact {artifact_id} for {character} is empty")

        archive_url = metadata.get("archive_download_url")
        if not archive_url:
            raise RuntimeError(f"Artifact {artifact_id} for {character} has no download URL")
        destination = output / character
        destination.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(suffix=".zip", delete=False) as handle:
            archive = pathlib.Path(handle.name)
        try:
            github_download(str(archive_url), archive, token)
            if archive.stat().st_size <= 0:
                raise RuntimeError(f"Downloaded artifact {artifact_id} is empty")
            safe_extract(archive, destination)
        finally:
            archive.unlink(missing_ok=True)

        metadata.pop("archive_download_url", None)
        result["artifacts"][character] = metadata
        print(
            json.dumps(
                {
                    "character": character,
                    "artifactId": artifact_id,
                    "artifactName": metadata.get("name"),
                    "artifactBytes": metadata.get("size_in_bytes"),
                    "workflowRun": metadata.get("workflow_run"),
                },
                indent=2,
            )
        )

    metadata_path.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise SystemExit(f"GitHub artifact request failed ({error.code}): {body}") from error
