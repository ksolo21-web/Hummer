using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using UnityEditor;
using UnityEditor.Build;
using UnityEngine;

namespace Havenline.Editor
{
    /// <summary>
    /// Device-test-only character gate. These checksum-pinned review artifacts are permitted to
    /// prove Android gameplay and visual integration, but they are deliberately NOT human-approved
    /// release assets. Verified release continues through HavenlineCharacterApprovalGate.
    /// </summary>
    internal static class HavenlineDeviceTestCharacterGate
    {
        internal const string StageManifestPath =
            "Assets/Havenline/Art/Characters/Production/HAVENLINE_DEVICE_TEST_CHARACTER_STAGE.json";
        private const long RequiredSourceRun = 31127195233L;
        private const int MinimumSkinnedVertices = 15000;
        private const int MinimumReferencedBones = 19;

        [Serializable]
        private sealed class StageManifest
        {
            public int schemaVersion;
            public bool deviceTestOnly;
            public long sourceRunId;
            public StageCharacter[] characters = Array.Empty<StageCharacter>();
        }

        [Serializable]
        private sealed class StageCharacter
        {
            public string character;
            public long artifactId;
            public string fbxSha256;
            public string portraitSha256;
        }

        private sealed class Expected
        {
            public HavenlineCharacterId Id;
            public long ArtifactId;
            public string FbxSha;
            public string PortraitSha;
        }

        private static readonly Expected[] ExpectedCharacters =
        {
            new Expected
            {
                Id = HavenlineCharacterId.Character1,
                ArtifactId = 8975286960L,
                FbxSha = "1a94029a0367ead8623296942de8bd516061de39e077ba2dd237bd238bbb5c1b",
                PortraitSha = "e5fd5853e1bbdf58173a4de552c898bdd13b6c489dbd9fba3347f5eb1f9ebbc6"
            },
            new Expected
            {
                Id = HavenlineCharacterId.Character2,
                ArtifactId = 8975298326L,
                FbxSha = "803b91c60f94cae7e4c9871f20e5345a5d9d366cb9d28fae9515cdfa8a17b95f",
                PortraitSha = "0bd0f6eb56b87e057230bce7cdbe242ce2b8e67da488429feb51045a49f88f6f"
            },
            new Expected
            {
                Id = HavenlineCharacterId.Character3,
                ArtifactId = 8975329072L,
                FbxSha = "2a29e90a12cf0ae0905596cd01401443f85705fc115e4cd6d4cbe1f2539000e7",
                PortraitSha = "e3e7fe2ba62e4a199e22b432144c8787463c7b8e52143c0d70ee8aa95cbd179e"
            },
            new Expected
            {
                Id = HavenlineCharacterId.Character4,
                ArtifactId = 8975346289L,
                FbxSha = "22e8574c3f2c2ec92353871d271786c29e90d39d15b6dbac50ed22115a4bdabb",
                PortraitSha = "a9076971ea36d3c8a0f5fb019870e3a5531d85d5b1609b01bb0c73de8dec6222"
            }
        };

        [MenuItem("HAVENLINE Premium/Characters/Validate Device-Test Character Stage")]
        private static void ValidateFromMenu()
        {
            Require();
            Debug.Log("HAVENLINE device-test character stage passed checksum, skinned-mesh and provenance validation. It is not release approval.");
        }

        internal static void Require()
        {
            var failures = Validate();
            if (failures.Count > 0)
                throw new BuildFailedException(
                    "HAVENLINE device-test character stage is invalid:\n - " +
                    string.Join("\n - ", failures));
        }

        internal static IReadOnlyList<string> Validate()
        {
            var failures = new List<string>();
            if (!File.Exists(StageManifestPath))
                return new[] { $"Device-test character stage manifest is missing: {StageManifestPath}" };

            StageManifest manifest;
            try
            {
                manifest = JsonUtility.FromJson<StageManifest>(File.ReadAllText(StageManifestPath));
            }
            catch (Exception exception)
            {
                return new[] { $"Device-test character stage manifest is invalid JSON: {exception.Message}" };
            }

            if (manifest == null)
                return new[] { "Device-test character stage manifest is empty." };
            if (manifest.schemaVersion != 1)
                failures.Add($"Device-test character stage schema must be 1; found {manifest.schemaVersion}.");
            if (!manifest.deviceTestOnly)
                failures.Add("Device-test character stage attempted to present itself as promotable production art.");
            if (manifest.sourceRunId != RequiredSourceRun)
                failures.Add($"Device-test character source run must be {RequiredSourceRun}; found {manifest.sourceRunId}.");

            var entries = manifest.characters ?? Array.Empty<StageCharacter>();
            foreach (var expected in ExpectedCharacters)
            {
                var name = expected.Id.ToString();
                var entry = entries.SingleOrDefault(item =>
                    item != null && string.Equals(item.character, name, StringComparison.Ordinal));
                if (entry == null)
                {
                    failures.Add($"Stage manifest is missing {name}.");
                    continue;
                }
                if (entry.artifactId != expected.ArtifactId)
                    failures.Add($"{name} stage artifact ID changed ({entry.artifactId}); expected {expected.ArtifactId}.");
                if (!string.Equals(entry.fbxSha256, expected.FbxSha, StringComparison.OrdinalIgnoreCase))
                    failures.Add($"{name} manifest FBX checksum does not match the pinned review artifact.");
                if (!string.Equals(entry.portraitSha256, expected.PortraitSha, StringComparison.OrdinalIgnoreCase))
                    failures.Add($"{name} manifest portrait checksum does not match the pinned review artifact.");

                var plan = HavenlineProductionCharacterAssetBuilder.Plans.Single(item => item.Id == expected.Id);
                ValidateFile(name, "FBX", plan.ModelPath, expected.FbxSha, failures);
                ValidateFile(name, "portrait", plan.PortraitPath, expected.PortraitSha, failures);
                ValidateImportedModel(plan, failures);
            }

            if (entries.Length != ExpectedCharacters.Length)
                failures.Add($"Device-test stage must contain exactly four character records; found {entries.Length}.");

            return failures.Distinct(StringComparer.Ordinal).OrderBy(item => item, StringComparer.Ordinal).ToArray();
        }

        private static void ValidateImportedModel(
            HavenlineProductionCharacterAssetBuilder.CharacterPlan plan,
            ICollection<string> failures)
        {
            var model = AssetDatabase.LoadAssetAtPath<GameObject>(plan.ModelPath);
            if (model == null)
            {
                failures.Add($"{plan.Id} staged FBX did not import as a GameObject.");
                return;
            }

            var skinned = model.GetComponentsInChildren<SkinnedMeshRenderer>(true);
            if (skinned.Length == 0)
            {
                failures.Add($"{plan.Id} staged FBX has no SkinnedMeshRenderer.");
                return;
            }

            var vertices = skinned.Sum(renderer => renderer.sharedMesh != null ? renderer.sharedMesh.vertexCount : 0);
            var bones = skinned.SelectMany(renderer => renderer.bones ?? Array.Empty<Transform>())
                .Where(bone => bone != null)
                .Distinct()
                .Count();
            var missingMaterials = skinned.Sum(renderer => renderer.sharedMaterials.Count(material => material == null));

            if (vertices < MinimumSkinnedVertices)
                failures.Add($"{plan.Id} staged FBX has only {vertices} skinned vertices; require at least {MinimumSkinnedVertices}.");
            if (bones < MinimumReferencedBones)
                failures.Add($"{plan.Id} staged FBX references only {bones} bones; require at least {MinimumReferencedBones} for device-test motion.");
            if (missingMaterials > 0)
                failures.Add($"{plan.Id} staged FBX has {missingMaterials} missing material slot(s).");
        }

        private static void ValidateFile(
            string character,
            string label,
            string path,
            string expectedSha,
            ICollection<string> failures)
        {
            if (!File.Exists(path))
            {
                failures.Add($"{character} staged {label} is missing: {path}");
                return;
            }

            var actual = Sha256(path);
            if (!string.Equals(actual, expectedSha, StringComparison.OrdinalIgnoreCase))
                failures.Add($"{character} staged {label} checksum changed. Expected {expectedSha}, got {actual}.");
        }

        private static string Sha256(string path)
        {
            using var stream = File.OpenRead(path);
            using var hash = SHA256.Create();
            var bytes = hash.ComputeHash(stream);
            var builder = new StringBuilder(bytes.Length * 2);
            foreach (var value in bytes)
                builder.Append(value.ToString("x2"));
            return builder.ToString();
        }
    }
}
