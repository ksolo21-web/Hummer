using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using UnityEngine;

namespace Havenline.Editor
{
    /// <summary>
    /// Fail-closed promotion gate for production characters. Machine-valid assets may be
    /// imported into the isolated Unity review scene, but they cannot be accepted by the
    /// production roster or a release-candidate build until the exact FBX, Unity review
    /// report, machine proof status, and approved reference sheet are all checksum pinned
    /// to an explicit human visual approval.
    /// </summary>
    public static class HavenlineCharacterApprovalGate
    {
        public const string ManifestPath =
            "Assets/Havenline/Art/Characters/Production/HAVENLINE_CHARACTER_APPROVALS.json";

        private const string EvidenceRoot =
            "Assets/Havenline/Art/Characters/Production/ApprovalEvidence";

        private static readonly string[] RequiredCharacters =
        {
            "Character1",
            "Character2",
            "Character3",
            "Character4"
        };

        [Serializable]
        internal sealed class ApprovalManifest
        {
            public int schemaVersion;
            public string reviewContractVersion;
            public bool humanVisualApprovalRequired;
            public CharacterApproval[] characters = Array.Empty<CharacterApproval>();
        }

        [Serializable]
        internal sealed class CharacterApproval
        {
            public string character;
            public string productionFbxPath;
            public string productionFbxSha256;
            public string unityReviewReportPath;
            public string unityReviewReportSha256;
            public string machineProofStatusPath;
            public string machineProofStatusSha256;
            public string approvedReferencePath;
            public string approvedReferenceSha256;
            public bool approved;
            public string approvedBy;
            public string approvedUtc;
            public string approvalNote;
        }

        [Serializable]
        private sealed class UnityReviewReport
        {
            public bool allMachineEvidenceComplete;
            public bool humanVisualApprovalRequired;
            public string humanVisualReviewStatus;
            public bool approved;
            public UnityCharacterEvidence[] characters = Array.Empty<UnityCharacterEvidence>();
        }

        [Serializable]
        private sealed class UnityCharacterEvidence
        {
            public string character;
            public string modelAssetPath;
            public string modelAssetSha256;
            public string[] renderSha256 = Array.Empty<string>();
            public bool machineEvidenceComplete;
            public string humanVisualReviewStatus;
            public bool approved;
        }

        [Serializable]
        private sealed class MachineProofStatus
        {
            public string character;
            public string productionGlbSha256;
            public bool machinePassed;
            public bool humanVisualApprovalRequired;
        }

        public static List<string> Validate()
        {
            if (!File.Exists(ManifestPath))
            {
                return new List<string>
                {
                    $"Character approval manifest is missing at {ManifestPath}."
                };
            }

            try
            {
                return ValidateManifest(
                    File.ReadAllText(ManifestPath),
                    File.Exists,
                    File.ReadAllText,
                    Sha256);
            }
            catch (Exception exception)
            {
                return new List<string>
                {
                    $"Character approval validation crashed closed: {exception.Message}"
                };
            }
        }

        internal static List<string> ValidateManifest(
            string json,
            Func<string, bool> fileExists,
            Func<string, string> readAllText,
            Func<string, string> sha256)
        {
            var failures = new List<string>();
            ApprovalManifest manifest;
            try
            {
                manifest = JsonUtility.FromJson<ApprovalManifest>(json);
            }
            catch (Exception exception)
            {
                failures.Add($"Character approval manifest is invalid JSON: {exception.Message}");
                return failures;
            }

            if (manifest == null)
            {
                failures.Add("Character approval manifest is empty.");
                return failures;
            }
            if (manifest.schemaVersion != 1)
                failures.Add($"Character approval schema must be 1; found {manifest.schemaVersion}.");
            if (!string.Equals(manifest.reviewContractVersion, "1.0", StringComparison.Ordinal))
                failures.Add("Character approval reviewContractVersion must be 1.0.");
            if (!manifest.humanVisualApprovalRequired)
                failures.Add("Character approval manifest attempted to bypass human visual review.");

            var entries = manifest.characters ?? Array.Empty<CharacterApproval>();
            var duplicateIds = entries
                .Where(entry => entry != null && !string.IsNullOrWhiteSpace(entry.character))
                .GroupBy(entry => entry.character, StringComparer.Ordinal)
                .Where(group => group.Count() > 1)
                .Select(group => group.Key)
                .ToArray();
            foreach (var duplicate in duplicateIds)
                failures.Add($"Character approval manifest contains duplicate entry {duplicate}.");

            foreach (var character in RequiredCharacters)
            {
                var entry = entries.FirstOrDefault(candidate =>
                    candidate != null &&
                    string.Equals(candidate.character, character, StringComparison.Ordinal));
                if (entry == null)
                {
                    failures.Add($"Character approval manifest has no entry for {character}.");
                    continue;
                }

                ValidateEntry(entry, fileExists, readAllText, sha256, failures);
            }

            var unknown = entries
                .Where(entry => entry != null &&
                    !RequiredCharacters.Contains(entry.character, StringComparer.Ordinal))
                .Select(entry => entry.character ?? "<empty>")
                .Distinct(StringComparer.Ordinal)
                .ToArray();
            foreach (var character in unknown)
                failures.Add($"Character approval manifest contains unsupported entry {character}.");

            return failures.Distinct(StringComparer.Ordinal).ToList();
        }

        private static void ValidateEntry(
            CharacterApproval entry,
            Func<string, bool> fileExists,
            Func<string, string> readAllText,
            Func<string, string> sha256,
            ICollection<string> failures)
        {
            var character = entry.character;
            if (!entry.approved)
            {
                failures.Add($"{character} is still pending human visual approval.");
                return;
            }

            if (string.IsNullOrWhiteSpace(entry.approvedBy))
                failures.Add($"{character} approval has no reviewer identity.");
            if (string.IsNullOrWhiteSpace(entry.approvalNote))
                failures.Add($"{character} approval has no review note.");
            if (!DateTimeOffset.TryParse(
                    entry.approvedUtc,
                    CultureInfo.InvariantCulture,
                    DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal,
                    out _))
            {
                failures.Add($"{character} approvalUtc is missing or invalid.");
            }

            var expectedFbx = ProductionFbxPath(character);
            var expectedReview = $"{EvidenceRoot}/unity-character-review-report.json";
            var expectedMachineStatus = $"{EvidenceRoot}/{character}/machine-proof-status.json";
            var expectedReference = $"{EvidenceRoot}/{character}/approved_reference_sheet.jpg";

            RequireCanonicalPath(character, "production FBX", entry.productionFbxPath, expectedFbx, failures);
            RequireCanonicalPath(character, "Unity review report", entry.unityReviewReportPath, expectedReview, failures);
            RequireCanonicalPath(character, "machine proof status", entry.machineProofStatusPath, expectedMachineStatus, failures);
            RequireCanonicalPath(character, "approved reference", entry.approvedReferencePath, expectedReference, failures);

            var actualFbxHash = RequirePinnedFile(
                character,
                "production FBX",
                expectedFbx,
                entry.productionFbxSha256,
                fileExists,
                sha256,
                failures);
            RequirePinnedFile(
                character,
                "Unity review report",
                expectedReview,
                entry.unityReviewReportSha256,
                fileExists,
                sha256,
                failures);
            RequirePinnedFile(
                character,
                "machine proof status",
                expectedMachineStatus,
                entry.machineProofStatusSha256,
                fileExists,
                sha256,
                failures);
            RequirePinnedFile(
                character,
                "approved reference",
                expectedReference,
                entry.approvedReferenceSha256,
                fileExists,
                sha256,
                failures);

            if (fileExists(expectedReview))
                ValidateUnityReview(character, expectedFbx, actualFbxHash, expectedReview, readAllText, failures);
            if (fileExists(expectedMachineStatus))
                ValidateMachineStatus(character, expectedMachineStatus, readAllText, failures);
        }

        private static void ValidateUnityReview(
            string character,
            string expectedFbx,
            string actualFbxHash,
            string reportPath,
            Func<string, string> readAllText,
            ICollection<string> failures)
        {
            UnityReviewReport report;
            try
            {
                report = JsonUtility.FromJson<UnityReviewReport>(readAllText(reportPath));
            }
            catch (Exception exception)
            {
                failures.Add($"{character} Unity review report is invalid: {exception.Message}");
                return;
            }

            if (report == null)
            {
                failures.Add($"{character} Unity review report is empty.");
                return;
            }
            if (!report.humanVisualApprovalRequired)
                failures.Add($"{character} Unity review report bypassed human visual review.");
            if (!report.allMachineEvidenceComplete)
                failures.Add($"{character} Unity review report has incomplete machine evidence.");
            if (!report.approved ||
                !string.Equals(report.humanVisualReviewStatus, "approved", StringComparison.OrdinalIgnoreCase))
            {
                failures.Add($"{character} Unity review report is not human-approved.");
            }

            var evidence = (report.characters ?? Array.Empty<UnityCharacterEvidence>())
                .FirstOrDefault(candidate => candidate != null &&
                    string.Equals(candidate.character, character, StringComparison.Ordinal));
            if (evidence == null)
            {
                failures.Add($"{character} has no evidence entry in the Unity review report.");
                return;
            }
            if (!evidence.machineEvidenceComplete)
                failures.Add($"{character} Unity evidence is incomplete.");
            if (!evidence.approved ||
                !string.Equals(evidence.humanVisualReviewStatus, "approved", StringComparison.OrdinalIgnoreCase))
            {
                failures.Add($"{character} Unity evidence is not human-approved.");
            }
            if (!string.Equals(evidence.modelAssetPath, expectedFbx, StringComparison.Ordinal))
                failures.Add($"{character} Unity review used a different FBX path.");
            if (!string.Equals(evidence.modelAssetSha256, actualFbxHash, StringComparison.OrdinalIgnoreCase))
                failures.Add($"{character} Unity review FBX hash does not match the current production FBX.");

            var renderHashes = evidence.renderSha256 ?? Array.Empty<string>();
            if (renderHashes.Length != 4 || renderHashes.Any(hash => !IsValidSha256(hash)))
                failures.Add($"{character} Unity review must pin four valid render hashes.");
            else if (renderHashes.Distinct(StringComparer.OrdinalIgnoreCase).Count() != 4)
                failures.Add($"{character} Unity review render hashes are not four distinct views.");
        }

        private static void ValidateMachineStatus(
            string character,
            string statusPath,
            Func<string, string> readAllText,
            ICollection<string> failures)
        {
            MachineProofStatus status;
            try
            {
                status = JsonUtility.FromJson<MachineProofStatus>(readAllText(statusPath));
            }
            catch (Exception exception)
            {
                failures.Add($"{character} machine proof status is invalid: {exception.Message}");
                return;
            }

            if (status == null)
            {
                failures.Add($"{character} machine proof status is empty.");
                return;
            }
            if (!string.Equals(status.character, character, StringComparison.Ordinal))
                failures.Add($"{character} machine proof status identifies {status.character ?? "<empty>"}.");
            if (!status.machinePassed)
                failures.Add($"{character} machine proof has not passed.");
            if (!status.humanVisualApprovalRequired)
                failures.Add($"{character} machine proof attempted to bypass human visual approval.");
            if (!IsValidSha256(status.productionGlbSha256))
                failures.Add($"{character} machine proof has no valid production GLB hash.");
        }

        private static void RequireCanonicalPath(
            string character,
            string label,
            string actual,
            string expected,
            ICollection<string> failures)
        {
            if (!string.Equals(actual, expected, StringComparison.Ordinal))
                failures.Add($"{character} {label} path must be {expected}.");
        }

        private static string RequirePinnedFile(
            string character,
            string label,
            string path,
            string expectedHash,
            Func<string, bool> fileExists,
            Func<string, string> sha256,
            ICollection<string> failures)
        {
            if (!fileExists(path))
            {
                failures.Add($"{character} {label} is missing at {path}.");
                return string.Empty;
            }
            if (!IsValidSha256(expectedHash))
            {
                failures.Add($"{character} {label} has no valid pinned SHA-256.");
                return string.Empty;
            }

            var actualHash = sha256(path);
            if (!string.Equals(actualHash, expectedHash, StringComparison.OrdinalIgnoreCase))
                failures.Add($"{character} {label} SHA-256 does not match the approved evidence.");
            return actualHash;
        }

        internal static bool IsValidSha256(string value) =>
            !string.IsNullOrWhiteSpace(value) &&
            value.Length == 64 &&
            value.All(Uri.IsHexDigit);

        internal static string ProductionFbxPath(string character) =>
            $"Assets/Havenline/Art/Characters/Production/{character}/{character}_production.fbx";

        internal static string Sha256(string path)
        {
            using var stream = File.OpenRead(path);
            using var algorithm = SHA256.Create();
            var bytes = algorithm.ComputeHash(stream);
            var builder = new StringBuilder(bytes.Length * 2);
            foreach (var value in bytes)
                builder.Append(value.ToString("x2", CultureInfo.InvariantCulture));
            return builder.ToString();
        }
    }
}
