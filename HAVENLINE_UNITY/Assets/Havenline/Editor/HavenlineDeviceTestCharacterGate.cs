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
            public string glbSha256;
            public string portraitSha256;
            public string[] textureSha256 = Array.Empty<string>();
        }

        private sealed class Expected
        {
            public HavenlineCharacterId Id;
            public long ArtifactId;
            public string FbxSha;
            public string GlbSha;
            public string PortraitSha;
            public string[] TextureSha = Array.Empty<string>();
            public string[] TextureExtensions = Array.Empty<string>();
        }

        private static readonly Expected[] ExpectedCharacters =
        {
            new Expected
            {
                Id = HavenlineCharacterId.Character1,
                ArtifactId = 8975286960L,
                FbxSha = "1a94029a0367ead8623296942de8bd516061de39e077ba2dd237bd238bbb5c1b",
                GlbSha = "027a1bd6965f923bf93a67f4a0619b685bc43c2fd0d958afaea9faa404ef6f17",
                PortraitSha = "e5fd5853e1bbdf58173a4de552c898bdd13b6c489dbd9fba3347f5eb1f9ebbc6",
                TextureSha = new[]
                {
                    "89035df1fd679e527d69ecc7e256ee8bf51c42ba4c67df403a14598b35a33b33"
                },
                TextureExtensions = new[] { "png" }
            },
            new Expected
            {
                Id = HavenlineCharacterId.Character2,
                ArtifactId = 8975298326L,
                FbxSha = "803b91c60f94cae7e4c9871f20e5345a5d9d366cb9d28fae9515cdfa8a17b95f",
                GlbSha = "b4acdbeacc663b36aa9ffab89a91c2d8f2735108ac55799974491e5af1090b1c",
                PortraitSha = "0bd0f6eb56b87e057230bce7cdbe242ce2b8e67da488429feb51045a49f88f6f",
                TextureSha = new[]
                {
                    "56652de047b351cd94e714d697c8557cfb0dd13e650838920b53dfb7326eea5c"
                },
                TextureExtensions = new[] { "png" }
            },
            new Expected
            {
                Id = HavenlineCharacterId.Character3,
                ArtifactId = 8975329072L,
                FbxSha = "2a29e90a12cf0ae0905596cd01401443f85705fc115e4cd6d4cbe1f2539000e7",
                GlbSha = "3021cbcc00ab8f260094255c615892e56152334c47222868bdebafedd58880e9",
                PortraitSha = "e3e7fe2ba62e4a199e22b432144c8787463c7b8e52143c0d70ee8aa95cbd179e",
                TextureSha = new[]
                {
                    "e7e6105e3d8f4cde988091bf5b4b2b9f47db87c45c777b86e6fe3e6e40837a4b",
                    "d66b9f4137c8102a3ab26d674a9c4a8c0701286bb39bf9f277fa2dcfd961cbb3"
                },
                TextureExtensions = new[] { "jpg", "png" }
            },
            new Expected
            {
                Id = HavenlineCharacterId.Character4,
                ArtifactId = 8975346289L,
                FbxSha = "22e8574c3f2c2ec92353871d271786c29e90d39d15b6dbac50ed22115a4bdabb",
                GlbSha = "8820b018e77dcc36dc8a73aa400decc8c08a870fccb8a83aeacec175ae2b6e0d",
                PortraitSha = "a9076971ea36d3c8a0f5fb019870e3a5531d85d5b1609b01bb0c73de8dec6222",
                TextureSha = new[]
                {
                    "dbed475a89640fa4652f44c9c33a293940dc1f365e5c6060137f03bca4c065c5",
                    "65865afdbd9235060efbfdd83de3d336a30a2537dd5e7e4dff6397a404cd03f5"
                },
                TextureExtensions = new[] { "jpg", "png" }
            }
        };

        [MenuItem("HAVENLINE Premium/Characters/Validate Device-Test Character Stage")]
        private static void ValidateFromMenu()
        {
            Require();
            Debug.Log("HAVENLINE device-test character stage passed FBX, recovered-GLB-texture, skinned-mesh and provenance validation. It is not release approval.");
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
            if (manifest.schemaVersion != 2)
                failures.Add($"Device-test character stage schema must be 2; found {manifest.schemaVersion}.");
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
                if (!string.Equals(entry.glbSha256, expected.GlbSha, StringComparison.OrdinalIgnoreCase))
                    failures.Add($"{name} manifest production-GLB checksum does not match the pinned review artifact.");
                if (!string.Equals(entry.portraitSha256, expected.PortraitSha, StringComparison.OrdinalIgnoreCase))
                    failures.Add($"{name} manifest portrait checksum does not match the pinned review artifact.");

                var textureEntries = entry.textureSha256 ?? Array.Empty<string>();
                if (textureEntries.Length != expected.TextureSha.Length)
                {
                    failures.Add($"{name} manifest contains {textureEntries.Length} recovered texture hashes; expected {expected.TextureSha.Length}.");
                }
                else
                {
                    for (var index = 0; index < expected.TextureSha.Length; index++)
                    {
                        if (!string.Equals(textureEntries[index], expected.TextureSha[index], StringComparison.OrdinalIgnoreCase))
                            failures.Add($"{name} recovered texture {index} manifest checksum does not match the pinned GLB image.");
                    }
                }

                var plan = HavenlineProductionCharacterAssetBuilder.Plans.Single(item => item.Id == expected.Id);
                ValidateFile(name, "FBX", plan.ModelPath, expected.FbxSha, failures);
                ValidateFile(name, "portrait", plan.PortraitPath, expected.PortraitSha, failures);
                for (var index = 0; index < expected.TextureSha.Length; index++)
                {
                    var texturePath = $"{plan.Folder}/{plan.Id}_glb_image_{index}.{expected.TextureExtensions[index]}";
                    ValidateFile(name, $"recovered GLB texture {index}", texturePath, expected.TextureSha[index], failures);
                }
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
