#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import shutil
from typing import Any

CHARACTERS = ("Character1", "Character2", "Character3", "Character4")
STATUS_CANDIDATES = (
    "actionless-seed9101-status.json",
    "matrix-status.json",
    "sf3d-pipeline-status.json",
)
SF3D_COMMIT = "ff21fc491b4dc5314bf6734c7c0dabd86b5f5bb2"
TOOL_ROOT = pathlib.Path(__file__).resolve().parent
CHARACTER1_REJECTION = TOOL_ROOT / "character1-seed9101-visual-rejection.json"


def candidate_policy_path(character: str) -> pathlib.Path:
    return TOOL_ROOT / f"{character.lower()}-unity-review-candidate.json"


def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


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


def require_status(root: pathlib.Path) -> pathlib.Path:
    matches: list[pathlib.Path] = []
    for name in STATUS_CANDIDATES:
        matches.extend(path for path in root.rglob(name) if path.is_file())
    unique = sorted(set(matches))
    if len(unique) != 1:
        raise RuntimeError(
            f"Expected exactly one recognized machine-status file under {root}; "
            f"found {len(unique)}: " + ", ".join(str(path) for path in unique)
        )
    return unique[0]


def load_json(path: pathlib.Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(payload, dict):
        raise RuntimeError(f"Expected a JSON object in {path}")
    return payload


def require_false(payload: dict[str, Any], key: str, character: str) -> None:
    if payload.get(key) is not False:
        raise RuntimeError(f"{character} status must keep {key}=false before review")


def generator_identity(generation: dict[str, Any], status: dict[str, Any]) -> str:
    return " ".join(
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


def validate_generator(
    character: str,
    generation: dict[str, Any],
    status: dict[str, Any],
) -> str:
    identity = generator_identity(generation, status)
    if character == "Character2":
        if "trellis" not in identity:
            raise RuntimeError("Character2 is not the verified recovered TRELLIS lead artifact")
        return "trellis-community/TRELLIS"

    if "stable-fast-3d" not in identity and "sf3d" not in identity:
        raise RuntimeError(f"{character} is not a Stable Fast 3D recovery artifact")
    commit = generation.get("generatorCommit") or status.get("generatorCommit")
    if commit != SF3D_COMMIT:
        raise RuntimeError(
            f"{character} Stable Fast 3D revision must be {SF3D_COMMIT}; found {commit!r}"
        )
    return "Stability-AI/stable-fast-3d"


def require_equal(actual: Any, expected: Any, label: str) -> None:
    if actual != expected:
        raise RuntimeError(f"{label} mismatch: expected {expected!r}, found {actual!r}")


def validate_candidate_policy(
    character: str,
    artifact: dict[str, Any],
    production_fbx_sha256: str,
    approved_reference_sha256: str,
) -> str:
    policy_path = candidate_policy_path(character)
    if not policy_path.is_file() or policy_path.stat().st_size == 0:
        raise RuntimeError(
            f"{character} has no checksum-pinned Blender human-review candidate policy at {policy_path}"
        )

    candidate = load_json(policy_path)
    require_equal(candidate.get("character"), character, f"{character} candidate character")
    if candidate.get("humanVisualApprovalRequired") is not True:
        raise RuntimeError(f"{character} candidate bypassed human visual approval")
    if candidate.get("approved") is not False:
        raise RuntimeError(f"{character} candidate was prematurely approved")
    if candidate.get("unityIntegrated") is not False:
        raise RuntimeError(f"{character} candidate was prematurely marked Unity-integrated")

    blender_review = candidate.get("blenderVisualReview") or {}
    require_equal(
        blender_review.get("status"),
        "accepted-for-unity-review",
        f"{character} Blender visual review status",
    )

    artifact_id = str(artifact.get("id", ""))
    digest = str(artifact.get("digest", ""))
    workflow_run = artifact.get("workflow_run") or {}
    locked_artifact = candidate.get("artifact") or {}
    locked_hashes = candidate.get("hashes") or {}

    require_equal(artifact_id, str(locked_artifact.get("id", "")), f"{character} artifact ID")
    require_equal(digest, str(locked_artifact.get("digest", "")), f"{character} artifact digest")
    require_equal(
        str(workflow_run.get("id", "")),
        str(locked_artifact.get("workflowRunId", "")),
        f"{character} workflow run ID",
    )
    require_equal(
        str(workflow_run.get("head_sha", "")),
        str(locked_artifact.get("sourceHeadSha", "")),
        f"{character} source head SHA",
    )
    require_equal(
        production_fbx_sha256,
        str(locked_hashes.get("productionFbxSha256", "")),
        f"{character} production FBX SHA-256",
    )
    require_equal(
        approved_reference_sha256,
        str(locked_hashes.get("approvedReferenceSha256", "")),
        f"{character} approved reference SHA-256",
    )
    return f"exact {character} Blender-human-reviewed artifact pinned for Unity review"


def validate_artifact_policy(
    character: str,
    artifact: dict[str, Any],
    production_fbx_sha256: str,
    approved_reference_sha256: str,
) -> str:
    artifact_id = str(artifact.get("id", ""))
    digest = str(artifact.get("digest", ""))

    if character == "Character1":
        rejection = load_json(CHARACTER1_REJECTION)
        rejected_artifact = rejection.get("artifact") or {}
        rejected_hashes = rejection.get("hashes") or {}
        if (
            artifact_id == str(rejected_artifact.get("id", ""))
            or digest == str(rejected_artifact.get("digest", ""))
            or production_fbx_sha256 == str(rejected_hashes.get("productionFbxSha256", ""))
        ):
            raise RuntimeError(
                "Character1 artifact is explicitly human-rejected and cannot enter Unity review"
            )

    return validate_candidate_policy(
        character,
        artifact,
        production_fbx_sha256,
        approved_reference_sha256,
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify immutable HAVENLINE character artifacts and stage exact FBXs for Unity review."
    )
    parser.add_argument("--packages-root", required=True)
    parser.add_argument("--artifact-metadata", required=True)
    parser.add_argument("--unity-project", required=True)
    args = parser.parse_args()

    required_policies = [CHARACTER1_REJECTION] + [
        candidate_policy_path(character) for character in CHARACTERS
    ]
    missing_policies = [
        str(policy_file)
        for policy_file in required_policies
        if not policy_file.is_file() or policy_file.stat().st_size == 0
    ]
    if missing_policies:
        raise FileNotFoundError(
            "Unity review cannot stage characters before all four Blender candidate policies exist: "
            + ", ".join(missing_policies)
        )

    packages_root = pathlib.Path(args.packages_root)
    metadata_path = pathlib.Path(args.artifact_metadata)
    unity_project = pathlib.Path(args.unity_project)
    metadata = load_json(metadata_path)

    expected_ids = metadata.get("artifacts", {})
    if sorted(expected_ids) != list(CHARACTERS):
        raise RuntimeError("Artifact metadata must contain Character1 through Character4 exactly")

    production_root = unity_project / "Assets/Havenline/Art/Characters/Production"
    evidence_root = unity_project / "Assets/Havenline/Generated/CharacterReviewSource"
    production_root.mkdir(parents=True, exist_ok=True)
    if evidence_root.exists():
        shutil.rmtree(evidence_root)
    evidence_root.mkdir(parents=True, exist_ok=True)

    source_entries: list[dict[str, Any]] = []
    for character in CHARACTERS:
        package = packages_root / character
        if not package.is_dir():
            raise FileNotFoundError(f"Missing extracted package directory: {package}")

        fbx = require_single(package, f"{character}_production.fbx", min_bytes=10_000)
        validation_path = require_single(package, "validation-report.json")
        generation_path = require_single(package, "generation-report.json")
        reference_path = require_single(package, "approved_reference_sheet.jpg", min_bytes=1_000)
        status_path = require_status(package)

        validation = load_json(validation_path)
        generation = load_json(generation_path)
        status = load_json(status_path)
        if validation.get("passed") is not True:
            raise RuntimeError(f"{character} validation-report.json did not pass")
        if status.get("machinePassed") is not True:
            raise RuntimeError(f"{character} machine-status did not pass")
        if status.get("humanVisualApprovalRequired") is not True:
            raise RuntimeError(f"{character} attempted to bypass human visual approval")
        require_false(status, "approved", character)
        require_false(status, "unityIntegrated", character)
        normalized_generator = validate_generator(character, generation, status)

        production_fbx_sha256 = sha256(fbx)
        approved_reference_sha256 = sha256(reference_path)
        artifact = expected_ids[character]
        policy = validate_artifact_policy(
            character,
            artifact,
            production_fbx_sha256,
            approved_reference_sha256,
        )

        destination = production_root / character
        if destination.exists():
            shutil.rmtree(destination)
        destination.mkdir(parents=True, exist_ok=True)
        production_fbx = destination / f"{character}_production.fbx"
        shutil.copy2(fbx, production_fbx)

        character_evidence = evidence_root / character
        character_evidence.mkdir(parents=True, exist_ok=True)
        copied = {
            "validationReport": character_evidence / "validation-report.json",
            "generationReport": character_evidence / "generation-report.json",
            "machineStatus": character_evidence / "machine-proof-status.json",
            "approvedReference": character_evidence / "approved_reference_sheet.jpg",
            "candidatePolicy": character_evidence / "blender-unity-review-candidate.json",
        }
        shutil.copy2(validation_path, copied["validationReport"])
        shutil.copy2(generation_path, copied["generationReport"])
        shutil.copy2(status_path, copied["machineStatus"])
        shutil.copy2(reference_path, copied["approvedReference"])
        shutil.copy2(candidate_policy_path(character), copied["candidatePolicy"])

        workflow_run = artifact.get("workflow_run") or {}
        entry = {
            "character": character,
            "artifactId": str(artifact.get("id", "")),
            "artifactName": artifact.get("name"),
            "artifactDigest": artifact.get("digest"),
            "artifactRunId": str(workflow_run.get("id", "")),
            "artifactHeadSha": workflow_run.get("head_sha"),
            "artifactUpdatedAt": artifact.get("updated_at"),
            "sourceGenerator": normalized_generator,
            "sourceMode": generation.get("sourceMode") or status.get("sourceMode"),
            "artifactPolicy": policy,
            "candidatePolicyPath": str(candidate_policy_path(character).as_posix()),
            "candidatePolicySha256": sha256(copied["candidatePolicy"]),
            "productionFbxPath": str(production_fbx.as_posix()),
            "productionFbxBytes": production_fbx.stat().st_size,
            "productionFbxSha256": sha256(production_fbx),
            "validationReportSha256": sha256(copied["validationReport"]),
            "generationReportSha256": sha256(copied["generationReport"]),
            "machineStatusSha256": sha256(copied["machineStatus"]),
            "approvedReferenceSha256": sha256(copied["approvedReference"]),
            "machinePassed": True,
            "acceptedForUnityReview": True,
            "approved": False,
            "humanVisualApprovalRequired": True,
            "unityIntegrated": False,
        }
        source_entries.append(entry)

    source_set = {
        "schemaVersion": 5,
        "characters": source_entries,
        "character1RejectedArtifactPolicy": str(CHARACTER1_REJECTION.as_posix()),
        "blenderCandidatePolicies": {
            character: str(candidate_policy_path(character).as_posix())
            for character in CHARACTERS
        },
        "humanVisualApprovalRequired": True,
        "approved": False,
        "unityIntegrated": False,
    }
    canonical = json.dumps(source_set, sort_keys=True, separators=(",", ":")).encode("utf-8")
    source_set_sha = hashlib.sha256(canonical).hexdigest()
    source_set["sourceSetSha256"] = source_set_sha
    (evidence_root / "source-artifact-set.json").write_text(
        json.dumps(source_set, indent=2) + "\n", encoding="utf-8"
    )

    artifact_ids = ",".join(entry["artifactId"] for entry in source_entries)
    source_run = {
        "schemaVersion": 1,
        "characterProductionRunId": f"artifact-set:{artifact_ids}",
        "characterProductionCommit": source_set_sha,
        "humanVisualApprovalRequired": True,
    }
    (evidence_root / "source-run.json").write_text(
        json.dumps(source_run, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps({"sourceRun": source_run, "sourceSet": source_set}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
