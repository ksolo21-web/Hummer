#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import pathlib
from typing import Any

CHARACTERS = ("Character1", "Character2", "Character3", "Character4")
VIEWS = ("front", "three-quarter", "side", "back")
SF3D_COMMIT = "ff21fc491b4dc5314bf6734c7c0dabd86b5f5bb2"
CONFIRMATION = "I-REVIEWED-FOUR-VIEWS"
TOOL_ROOT = pathlib.Path(__file__).resolve().parent
CHARACTER1_REJECTION = TOOL_ROOT / "character1-seed9101-visual-rejection.json"


def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_json(path: pathlib.Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(payload, dict):
        raise RuntimeError(f"Expected a JSON object in {path}")
    return payload


def require_single(root: pathlib.Path, name: str, *, min_bytes: int = 1) -> pathlib.Path:
    matches = sorted(
        path for path in root.rglob(name) if path.is_file() and path.stat().st_size >= min_bytes
    )
    if len(matches) != 1:
        raise RuntimeError(
            f"Expected exactly one {name} under {root}; found {len(matches)}: "
            + ", ".join(str(path) for path in matches)
        )
    return matches[0]


def resolve_artifact(metadata: dict[str, Any], character: str) -> dict[str, Any]:
    if "artifacts" in metadata:
        artifact = (metadata.get("artifacts") or {}).get(character)
    else:
        artifact = metadata
    if not isinstance(artifact, dict):
        raise RuntimeError(f"Artifact metadata has no object for {character}")
    if int(artifact.get("id", 0)) <= 0:
        raise RuntimeError(f"Artifact metadata has no positive ID for {character}")
    digest = str(artifact.get("digest", ""))
    if not digest.startswith("sha256:") or len(digest) != 71:
        raise RuntimeError(f"Artifact metadata has no valid digest for {character}")
    workflow_run = artifact.get("workflow_run") or {}
    if int(workflow_run.get("id", 0)) <= 0 or len(str(workflow_run.get("head_sha", ""))) != 40:
        raise RuntimeError(f"Artifact metadata has no valid workflow run binding for {character}")
    if artifact.get("expired") is True:
        raise RuntimeError(f"Artifact for {character} is expired")
    return artifact


def parse_utc(value: str) -> str:
    normalized = value.replace("Z", "+00:00")
    parsed = dt.datetime.fromisoformat(normalized)
    if parsed.tzinfo is None:
        raise ValueError("reviewed UTC must include a timezone")
    return parsed.astimezone(dt.timezone.utc).isoformat().replace("+00:00", "Z")


def generator_name(character: str, generation: dict[str, Any], status: dict[str, Any]) -> str:
    identity = " ".join(
        str(value)
        for value in (
            generation.get("generator"),
            generation.get("sourceGenerator"),
            generation.get("sourceMode"),
            status.get("generator"),
            status.get("sourceGenerator"),
            status.get("sourceMode"),
        )
        if value is not None
    ).lower()
    if character == "Character2":
        if "trellis" not in identity:
            raise RuntimeError("Character2 candidate is not from the verified TRELLIS route")
        return "trellis-community/TRELLIS"
    if "stable-fast-3d" not in identity and "sf3d" not in identity:
        raise RuntimeError(f"{character} candidate is not from Stable Fast 3D")
    commit = generation.get("generatorCommit") or status.get("generatorCommit")
    if commit != SF3D_COMMIT:
        raise RuntimeError(
            f"{character} Stable Fast 3D revision must be {SF3D_COMMIT}; found {commit!r}"
        )
    return "Stability-AI/stable-fast-3d"


def main() -> int:
    parser = argparse.ArgumentParser(
        description=(
            "Record an exact Blender four-view HAVENLINE artifact as a Unity-review candidate. "
            "This never grants final production approval."
        )
    )
    parser.add_argument("--character", choices=CHARACTERS, required=True)
    parser.add_argument("--package-root", required=True)
    parser.add_argument("--artifact-metadata", required=True)
    parser.add_argument("--reviewer", required=True)
    parser.add_argument("--reviewed-utc", required=True)
    parser.add_argument("--review-note", required=True)
    parser.add_argument("--confirmation", required=True)
    parser.add_argument("--output")
    args = parser.parse_args()

    if args.confirmation != CONFIRMATION:
        raise RuntimeError(
            f"Candidate recording requires --confirmation {CONFIRMATION}; machine review alone is insufficient"
        )
    if len(args.reviewer.strip()) < 3:
        raise RuntimeError("Reviewer identity is missing or implausibly short")
    if len(args.review_note.strip()) < 20:
        raise RuntimeError("Review note must describe the four-view visual decision")
    reviewed_utc = parse_utc(args.reviewed_utc)

    character = args.character
    root = pathlib.Path(args.package_root)
    artifact = resolve_artifact(load_json(pathlib.Path(args.artifact_metadata)), character)
    fbx = require_single(root, f"{character}_production.fbx", min_bytes=10_000)
    glb = require_single(root, f"{character}_production.glb", min_bytes=10_000)
    reference = require_single(root, "approved_reference_sheet.jpg", min_bytes=1_000)
    validation_path = require_single(root, "validation-report.json")
    generation_path = require_single(root, "generation-report.json")

    status_matches = []
    for name in (
        "actionless-seed9101-status.json",
        "matrix-status.json",
        "sf3d-pipeline-status.json",
    ):
        status_matches.extend(path for path in root.rglob(name) if path.is_file())
    status_matches = sorted(set(status_matches))
    if len(status_matches) != 1:
        raise RuntimeError(
            f"Expected exactly one machine-status file; found {len(status_matches)}"
        )
    status_path = status_matches[0]

    validation = load_json(validation_path)
    generation = load_json(generation_path)
    status = load_json(status_path)
    if validation.get("passed") is not True or status.get("machinePassed") is not True:
        raise RuntimeError(f"{character} machine evidence did not pass")
    if status.get("humanVisualApprovalRequired") is not True:
        raise RuntimeError(f"{character} machine status bypassed human review")
    if status.get("approved") is not False or status.get("unityIntegrated") is not False:
        raise RuntimeError(f"{character} machine package was prematurely promoted")
    normalized_generator = generator_name(character, generation, status)

    proof_paths = {
        view: require_single(root, f"proof_{view}.png", min_bytes=5_000)
        for view in VIEWS
    }
    proof_hashes = {view: sha256(path) for view, path in proof_paths.items()}
    if len(set(proof_hashes.values())) != 4:
        raise RuntimeError(f"{character} proof views are not four distinct rendered images")

    fbx_hash = sha256(fbx)
    reference_hash = sha256(reference)
    if character == "Character1":
        rejection = load_json(CHARACTER1_REJECTION)
        rejected_artifact = rejection.get("artifact") or {}
        rejected_hashes = rejection.get("hashes") or {}
        if (
            str(artifact.get("id")) == str(rejected_artifact.get("id"))
            or str(artifact.get("digest")) == str(rejected_artifact.get("digest"))
            or fbx_hash == str(rejected_hashes.get("productionFbxSha256"))
        ):
            raise RuntimeError("The rejected Character1 TRELLIS artifact cannot be recorded as a candidate")

    workflow_run = artifact.get("workflow_run") or {}
    candidate = {
        "schemaVersion": 2,
        "character": character,
        "artifact": {
            "id": str(artifact.get("id")),
            "name": artifact.get("name"),
            "digest": artifact.get("digest"),
            "workflowRunId": str(workflow_run.get("id")),
            "sourceHeadSha": workflow_run.get("head_sha"),
            "updatedAt": artifact.get("updated_at"),
        },
        "source": {
            "generator": normalized_generator,
            "sourceMode": generation.get("sourceMode") or status.get("sourceMode"),
            "generatorCommit": generation.get("generatorCommit") or status.get("generatorCommit"),
        },
        "hashes": {
            "productionFbxSha256": fbx_hash,
            "productionGlbSha256": sha256(glb),
            "approvedReferenceSha256": reference_hash,
            "proofFrontSha256": proof_hashes["front"],
            "proofThreeQuarterSha256": proof_hashes["three-quarter"],
            "proofSideSha256": proof_hashes["side"],
            "proofBackSha256": proof_hashes["back"],
            "validationReportSha256": sha256(validation_path),
            "generationReportSha256": sha256(generation_path),
            "machineStatusSha256": sha256(status_path),
        },
        "machineReview": {
            "validationPassed": True,
            "machinePassed": True,
            "proofViewsDistinct": True,
        },
        "blenderVisualReview": {
            "status": "accepted-for-unity-review",
            "reviewedBy": args.reviewer.strip(),
            "reviewedUtc": reviewed_utc,
            "reviewNote": args.review_note.strip(),
            "confirmation": CONFIRMATION,
        },
        "humanVisualApprovalRequired": True,
        "humanVisualReviewStatus": "pending-unity-review",
        "acceptedForUnityReview": True,
        "approved": False,
        "unityIntegrated": False,
    }

    output = pathlib.Path(args.output) if args.output else candidate_policy_path(character)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(candidate, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(candidate, indent=2))
    return 0


def candidate_policy_path(character: str) -> pathlib.Path:
    return TOOL_ROOT / f"{character.lower()}-unity-review-candidate.json"


if __name__ == "__main__":
    raise SystemExit(main())
