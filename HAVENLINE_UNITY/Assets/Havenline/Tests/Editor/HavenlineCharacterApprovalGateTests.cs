using System;
using System.Collections.Generic;
using System.Linq;
using NUnit.Framework;
using UnityEngine;
using Havenline.Editor;

namespace Havenline.Tests
{
    public sealed class HavenlineCharacterApprovalGateTests
    {
        private static readonly string[] Characters =
        {
            "Character1",
            "Character2",
            "Character3",
            "Character4"
        };

        [Test]
        public void PendingManifestFailsClosedForAllFourCharacters()
        {
            var manifest = new HavenlineCharacterApprovalGate.ApprovalManifest
            {
                schemaVersion = 1,
                reviewContractVersion = "1.0",
                humanVisualApprovalRequired = true,
                characters = Characters.Select(character =>
                    new HavenlineCharacterApprovalGate.CharacterApproval
                    {
                        character = character,
                        approved = false
                    }).ToArray()
            };

            var failures = HavenlineCharacterApprovalGate.ValidateManifest(
                JsonUtility.ToJson(manifest),
                _ => false,
                _ => string.Empty,
                _ => string.Empty);

            foreach (var character in Characters)
            {
                Assert.That(
                    failures,
                    Does.Contain($"{character} is still pending human visual approval."));
            }
        }

        [Test]
        public void ExactApprovedEvidencePassesThePromotionContract()
        {
            var fixture = CreateApprovedFixture();

            var failures = HavenlineCharacterApprovalGate.ValidateManifest(
                fixture.ManifestJson,
                fixture.Files.Contains,
                path => fixture.Text[path],
                path => fixture.Hashes[path]);

            Assert.That(failures, Is.Empty, string.Join("\n", failures));
        }

        [Test]
        public void ReplacingApprovedFbxFailsHashAndUnityReviewBinding()
        {
            var fixture = CreateApprovedFixture();
            var character = "Character1";
            var path = HavenlineCharacterApprovalGate.ProductionFbxPath(character);
            fixture.Hashes[path] = Hex('f');

            var failures = HavenlineCharacterApprovalGate.ValidateManifest(
                fixture.ManifestJson,
                fixture.Files.Contains,
                candidate => fixture.Text[candidate],
                candidate => fixture.Hashes[candidate]);

            Assert.That(
                failures,
                Does.Contain($"{character} production FBX SHA-256 does not match the approved evidence."));
            Assert.That(
                failures,
                Does.Contain($"{character} Unity review FBX hash does not match the current production FBX."));
        }

        [Test]
        public void ApprovalCannotDisableHumanVisualReview()
        {
            var fixture = CreateApprovedFixture();
            fixture.ManifestJson = fixture.ManifestJson.Replace(
                "\"humanVisualApprovalRequired\": true",
                "\"humanVisualApprovalRequired\": false");

            var failures = HavenlineCharacterApprovalGate.ValidateManifest(
                fixture.ManifestJson,
                fixture.Files.Contains,
                path => fixture.Text[path],
                path => fixture.Hashes[path]);

            Assert.That(
                failures,
                Does.Contain("Character approval manifest attempted to bypass human visual review."));
        }

        private static ApprovalFixture CreateApprovedFixture()
        {
            const string evidenceRoot =
                "Assets/Havenline/Art/Characters/Production/ApprovalEvidence";
            var reviewPath = $"{evidenceRoot}/unity-character-review-report.json";
            var reviewHash = Hex('a');
            var files = new HashSet<string>(StringComparer.Ordinal) { reviewPath };
            var hashes = new Dictionary<string, string>(StringComparer.Ordinal)
            {
                [reviewPath] = reviewHash
            };
            var text = new Dictionary<string, string>(StringComparer.Ordinal);
            var approvals = new List<HavenlineCharacterApprovalGate.CharacterApproval>();
            var evidenceJson = new List<string>();

            for (var index = 0; index < Characters.Length; index++)
            {
                var character = Characters[index];
                var fbxPath = HavenlineCharacterApprovalGate.ProductionFbxPath(character);
                var machinePath = $"{evidenceRoot}/{character}/machine-proof-status.json";
                var referencePath = $"{evidenceRoot}/{character}/approved_reference_sheet.jpg";
                var fbxHash = Hex((char)('1' + index));
                var machineHash = Hex((char)('5' + index));
                var referenceHash = Hex((char)('9' + index));
                var productionGlbHash = Hex((char)('b' + index));
                var renderHashes = new[]
                {
                    Hex((char)('1' + index)),
                    Hex((char)('5' + index)),
                    Hex((char)('9' + index)),
                    Hex((char)('d' + index % 3))
                };

                files.Add(fbxPath);
                files.Add(machinePath);
                files.Add(referencePath);
                hashes[fbxPath] = fbxHash;
                hashes[machinePath] = machineHash;
                hashes[referencePath] = referenceHash;
                text[machinePath] =
                    "{" +
                    $"\"character\":\"{character}\"," +
                    $"\"productionGlbSha256\":\"{productionGlbHash}\"," +
                    "\"machinePassed\":true," +
                    "\"humanVisualApprovalRequired\":true" +
                    "}";

                evidenceJson.Add(
                    "{" +
                    $"\"character\":\"{character}\"," +
                    $"\"modelAssetPath\":\"{fbxPath}\"," +
                    $"\"modelAssetSha256\":\"{fbxHash}\"," +
                    "\"renderSha256\":[" +
                    string.Join(",", renderHashes.Select(hash => $"\"{hash}\"")) +
                    "]," +
                    "\"machineEvidenceComplete\":true," +
                    "\"humanVisualReviewStatus\":\"approved\"," +
                    "\"approved\":true" +
                    "}");

                approvals.Add(new HavenlineCharacterApprovalGate.CharacterApproval
                {
                    character = character,
                    productionFbxPath = fbxPath,
                    productionFbxSha256 = fbxHash,
                    unityReviewReportPath = reviewPath,
                    unityReviewReportSha256 = reviewHash,
                    machineProofStatusPath = machinePath,
                    machineProofStatusSha256 = machineHash,
                    approvedReferencePath = referencePath,
                    approvedReferenceSha256 = referenceHash,
                    approved = true,
                    approvedBy = "HAVENLINE Human Visual Review",
                    approvedUtc = "2026-08-06T18:00:00Z",
                    approvalNote = "Approved after side-by-side four-view Blender and Unity review."
                });
            }

            text[reviewPath] =
                "{" +
                "\"allMachineEvidenceComplete\":true," +
                "\"humanVisualApprovalRequired\":true," +
                "\"humanVisualReviewStatus\":\"approved\"," +
                "\"approved\":true," +
                "\"characters\":[" + string.Join(",", evidenceJson) + "]" +
                "}";

            var manifest = new HavenlineCharacterApprovalGate.ApprovalManifest
            {
                schemaVersion = 1,
                reviewContractVersion = "1.0",
                humanVisualApprovalRequired = true,
                characters = approvals.ToArray()
            };

            return new ApprovalFixture
            {
                ManifestJson = JsonUtility.ToJson(manifest, true),
                Files = files,
                Hashes = hashes,
                Text = text
            };
        }

        private static string Hex(char value) => new string(value, 64);

        private sealed class ApprovalFixture
        {
            public string ManifestJson;
            public HashSet<string> Files;
            public Dictionary<string, string> Hashes;
            public Dictionary<string, string> Text;
        }
    }
}
