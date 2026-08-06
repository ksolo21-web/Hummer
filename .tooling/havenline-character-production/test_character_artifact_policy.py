#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import pathlib
import tempfile
import unittest
from unittest import mock

ROOT = pathlib.Path(__file__).resolve().parent
MODULE_PATH = ROOT / "stage_unity_character_review_sources.py"
SPEC = importlib.util.spec_from_file_location("havenline_stage_sources", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Could not load {MODULE_PATH}")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class CharacterArtifactPolicyTests(unittest.TestCase):
    def test_character1_rejected_artifact_id_is_blocked(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "explicitly human-rejected"):
            MODULE.validate_artifact_policy(
                "Character1",
                {
                    "id": 8974534288,
                    "digest": "sha256:d40ac3c0edbc8c81acfbf2cddb815a15060180d36a1b1ff276fc5924d25ff252",
                    "workflow_run": {
                        "id": 31124270527,
                        "head_sha": "1ce320946cb04c9cfa7947a6283b59c81b7d8229",
                    },
                },
                "44550b285fff331d2b5f15b9b817792e3c8a0c1695696a92da5f07b7d4b774bc",
                "36dd42d6d8f80651140958396742d37d14ae2c2676e167fe162a8d96b51df77f",
            )

    def test_character1_rejected_fbx_hash_is_blocked_even_with_new_artifact_id(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "explicitly human-rejected"):
            MODULE.validate_artifact_policy(
                "Character1",
                {
                    "id": 9999999999,
                    "digest": "sha256:" + "a" * 64,
                    "workflow_run": {"id": 9999999999, "head_sha": "b" * 40},
                },
                "44550b285fff331d2b5f15b9b817792e3c8a0c1695696a92da5f07b7d4b774bc",
                "c" * 64,
            )

    def test_character1_requires_sf3d_generator_after_rejection(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "not a Stable Fast 3D recovery artifact"):
            MODULE.validate_generator(
                "Character1",
                {"generator": "trellis-community/TRELLIS", "sourceMode": "clean-multi-image"},
                {"sourceGenerator": "trellis-community/TRELLIS"},
            )

        normalized = MODULE.validate_generator(
            "Character1",
            {
                "generator": "Stability-AI/stable-fast-3d",
                "generatorCommit": MODULE.SF3D_COMMIT,
            },
            {"sourceMode": "self-hosted-rtx-sf3d-recovery-after-trellis-rejection"},
        )
        self.assertEqual(normalized, "Stability-AI/stable-fast-3d")

    def test_character2_exact_candidate_is_accepted(self) -> None:
        policy = MODULE.validate_artifact_policy(
            "Character2",
            {
                "id": 8973791027,
                "digest": "sha256:051ce24ea949227f3bec154529421e35bf53a1e8e8607ec5fa705a345dc6eb5c",
                "workflow_run": {
                    "id": 31115840677,
                    "head_sha": "2773d269b52212b1de67908663787b2cedc432db",
                },
            },
            "64344f84154888f6bd03fde50789bde81ccb2e6599dfbd3bac5a380413950524",
            "a3b492768c5d0bf9fb8cfc9cc294500590987f51e9b4c0668b8125537e7d72b8",
        )
        self.assertIn("pinned", policy)

    def test_character2_substitution_is_rejected(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "Character2 artifact ID mismatch"):
            MODULE.validate_artifact_policy(
                "Character2",
                {
                    "id": 8972832116,
                    "digest": "sha256:5fd4ae2404a4fd0d7af5b12d54edbaed4cce8f1b7d59c947b013e1b6db76ca87",
                    "workflow_run": {
                        "id": 31113584790,
                        "head_sha": "9ef9e796c92604719c50d7da947899d82f3db7b6",
                    },
                },
                "64344f84154888f6bd03fde50789bde81ccb2e6599dfbd3bac5a380413950524",
                "a3b492768c5d0bf9fb8cfc9cc294500590987f51e9b4c0668b8125537e7d72b8",
            )

    def test_sf3d_artifact_cannot_enter_unity_without_blender_candidate_policy(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            missing = pathlib.Path(directory) / "character3-unity-review-candidate.json"
            with mock.patch.object(MODULE, "candidate_policy_path", return_value=missing):
                with self.assertRaisesRegex(
                    RuntimeError,
                    "no checksum-pinned Blender human-review candidate policy",
                ):
                    MODULE.validate_artifact_policy(
                        "Character3",
                        {
                            "id": 9999999998,
                            "digest": "sha256:" + "d" * 64,
                            "workflow_run": {
                                "id": 9999999998,
                                "head_sha": "e" * 40,
                            },
                        },
                        "f" * 64,
                        "1" * 64,
                    )

    def test_candidate_policy_cannot_claim_final_approval(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            policy_path = pathlib.Path(directory) / "character3-unity-review-candidate.json"
            policy_path.write_text(
                """{
                  "schemaVersion": 2,
                  "character": "Character3",
                  "artifact": {
                    "id": "9999999998",
                    "digest": "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                    "workflowRunId": "9999999998",
                    "sourceHeadSha": "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
                  },
                  "hashes": {
                    "productionFbxSha256": "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                    "approvedReferenceSha256": "1111111111111111111111111111111111111111111111111111111111111111"
                  },
                  "blenderVisualReview": {
                    "status": "accepted-for-unity-review",
                    "reviewedBy": "Test reviewer",
                    "reviewedUtc": "2026-08-06T20:00:00Z",
                    "reviewNote": "All four rendered views were inspected before this fixture attempted final approval.",
                    "confirmation": "I-REVIEWED-FOUR-VIEWS"
                  },
                  "humanVisualApprovalRequired": true,
                  "humanVisualReviewStatus": "pending-unity-review",
                  "acceptedForUnityReview": true,
                  "approved": true,
                  "unityIntegrated": false
                }""",
                encoding="utf-8",
            )
            with mock.patch.object(MODULE, "candidate_policy_path", return_value=policy_path):
                with self.assertRaisesRegex(RuntimeError, "prematurely approved"):
                    MODULE.validate_artifact_policy(
                        "Character3",
                        {
                            "id": 9999999998,
                            "digest": "sha256:" + "d" * 64,
                            "workflow_run": {
                                "id": 9999999998,
                                "head_sha": "e" * 40,
                            },
                        },
                        "f" * 64,
                        "1" * 64,
                    )

    def test_candidate_must_explicitly_accept_unity_review(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            policy_path = pathlib.Path(directory) / "character3-unity-review-candidate.json"
            policy_path.write_text(
                """{
                  "schemaVersion": 2,
                  "character": "Character3",
                  "artifact": {},
                  "hashes": {},
                  "blenderVisualReview": {
                    "status": "accepted-for-unity-review",
                    "reviewedBy": "Test reviewer",
                    "reviewedUtc": "2026-08-06T20:00:00Z",
                    "reviewNote": "All four rendered views were inspected but the acceptance flag was omitted.",
                    "confirmation": "I-REVIEWED-FOUR-VIEWS"
                  },
                  "humanVisualApprovalRequired": true,
                  "humanVisualReviewStatus": "pending-unity-review",
                  "acceptedForUnityReview": false,
                  "approved": false,
                  "unityIntegrated": false
                }""",
                encoding="utf-8",
            )
            with mock.patch.object(MODULE, "candidate_policy_path", return_value=policy_path):
                with self.assertRaisesRegex(RuntimeError, "not explicitly accepted"):
                    MODULE.validate_artifact_policy(
                        "Character3",
                        {
                            "id": 9999999998,
                            "digest": "sha256:" + "d" * 64,
                            "workflow_run": {
                                "id": 9999999998,
                                "head_sha": "e" * 40,
                            },
                        },
                        "f" * 64,
                        "1" * 64,
                    )

    def test_support_characters_require_exact_sf3d_revision(self) -> None:
        for character in ("Character3", "Character4"):
            with self.assertRaisesRegex(RuntimeError, "revision must be"):
                MODULE.validate_generator(
                    character,
                    {
                        "generator": "Stability-AI/stable-fast-3d",
                        "generatorCommit": "0" * 40,
                    },
                    {"sourceMode": "self-hosted-rtx-sf3d"},
                )


if __name__ == "__main__":
    unittest.main(verbosity=2)
