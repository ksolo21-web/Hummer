using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.Animations;
using UnityEditor.Build;
using UnityEngine;
using UnityEngine.Audio;

namespace Havenline.Editor
{
    /// <summary>
    /// Prevents prototype, placeholder, incomplete or visually mixed content from being
    /// exported as a HAVENLINE premium release candidate.
    /// </summary>
    public static class HavenlinePremiumBuildGate
    {
        public const int CurrentSchemaVersion = 2;
        public const string ProductionRoot = "Assets/Havenline/Art/Production";
        public const string ManifestPath = ProductionRoot + "/HAVENLINE_PRODUCTION_ART.json";

        private static readonly string[] BannedNameTokens =
        {
            "placeholder", "prototype", "superhero", "mannequin", "capsule",
            "primitive", "blockout", "block_out", "greybox", "graybox",
            "lowpoly", "low_poly", "temp_", "temporary", "reference"
        };

        private static readonly string[] ModelExtensions = { ".fbx", ".obj", ".gltf", ".glb" };
        private static readonly string[] TextureExtensions = { ".png", ".jpg", ".jpeg", ".tga", ".exr", ".psd", ".tif", ".tiff" };
        private static readonly string[] AudioExtensions = { ".wav", ".ogg", ".mp3", ".aiff", ".aif" };

        [Serializable]
        public sealed class ProductionArtManifest
        {
            public int schemaVersion;
            public string artVersion;
            public bool approved;
            public string approvedBy;
            public string approvalNote;

            public string playerModel;
            public string survivorModel;
            public string wolfModel;
            public string furnaceModel;
            public string furnaceLevel2Model;
            public string furnaceLevel3Model;
            public string furnaceLevel4Model;
            public string campfireModel;
            public string tentModel;
            public string storageModel;
            public string barricadeModel;
            public string forestGateModel;
            public string backpackModel;
            public string logModel;
            public string stoneResourceModel;
            public string metalResourceModel;
            public string fuelResourceModel;
            public string pineModelA;
            public string pineModelB;
            public string rockModelA;
            public string rockModelB;
            public string environmentPrefab;

            public string snowMaterial;
            public string iceMaterial;
            public string warmthMaterial;
            public string hudAtlas;
            public string uiFont;
            public string hudPrefab;
            public string pauseMenuPrefab;

            public string playerController;
            public string survivorController;
            public string wolfController;

            public string fireVfxPrefab;
            public string snowfallVfxPrefab;
            public string gatherVfxPrefab;
            public string buildVfxPrefab;
            public string hitVfxPrefab;

            public string audioMixer;
            public string audioRigPrefab;

            public int minimumEnvironmentModels;
            public int minimumTextureFiles;
            public int minimumAnimationClips;
            public int minimumAudioClips;
            public int minimumMaterials;
        }

        public sealed class ValidationResult
        {
            public ProductionArtManifest Manifest { get; }
            public IReadOnlyList<string> Failures { get; }
            public bool Passed => Failures.Count == 0;

            public ValidationResult(ProductionArtManifest manifest, IReadOnlyList<string> failures)
            {
                Manifest = manifest;
                Failures = failures;
            }
        }

        [MenuItem("HAVENLINE Premium/Validate Production Content")]
        public static void ValidateFromMenu()
        {
            var result = InspectProductionContent();
            if (!result.Passed)
            {
                throw new BuildFailedException(
                    "HAVENLINE premium production gate failed:\n - " +
                    string.Join("\n - ", result.Failures));
            }
            Debug.Log($"HAVENLINE premium production content passed. Art version: {result.Manifest.artVersion}");
        }

        public static ValidationResult InspectProductionContent()
        {
            var failures = new List<string>();
            var manifest = LoadManifest(failures);
            if (manifest == null)
                return new ValidationResult(null, failures);

            if (manifest.schemaVersion != CurrentSchemaVersion)
                failures.Add($"Unsupported production-art manifest schema: {manifest.schemaVersion}; require {CurrentSchemaVersion}.");
            if (!manifest.approved)
                failures.Add("Production art is not approved. Set approved=true only after visual review of the complete shipping set.");
            if (string.IsNullOrWhiteSpace(manifest.approvedBy))
                failures.Add("Production-art approval has no reviewer recorded in approvedBy.");
            if (string.IsNullOrWhiteSpace(manifest.artVersion) || manifest.artVersion.Contains("blocked", StringComparison.OrdinalIgnoreCase))
                failures.Add("Production artVersion is missing or still marked blocked.");

            var requiredAssets = RequiredAssets(manifest);
            foreach (var asset in requiredAssets)
                ValidateRequiredAsset(asset.Key, asset.Value, failures);

            ValidateType<GameObject>("player model", manifest.playerModel, failures);
            ValidateType<GameObject>("survivor model", manifest.survivorModel, failures);
            ValidateType<GameObject>("wolf model", manifest.wolfModel, failures);
            ValidateType<GameObject>("furnace level 1", manifest.furnaceModel, failures);
            ValidateType<GameObject>("furnace level 2", manifest.furnaceLevel2Model, failures);
            ValidateType<GameObject>("furnace level 3", manifest.furnaceLevel3Model, failures);
            ValidateType<GameObject>("furnace level 4", manifest.furnaceLevel4Model, failures);
            ValidateType<GameObject>("environment prefab", manifest.environmentPrefab, failures);
            ValidateType<GameObject>("HUD prefab", manifest.hudPrefab, failures);
            ValidateType<GameObject>("pause/settings prefab", manifest.pauseMenuPrefab, failures);
            ValidateType<GameObject>("audio rig prefab", manifest.audioRigPrefab, failures);
            ValidateType<Material>("snow material", manifest.snowMaterial, failures);
            ValidateType<Material>("ice material", manifest.iceMaterial, failures);
            ValidateType<Material>("warmth material", manifest.warmthMaterial, failures);
            ValidateType<Texture2D>("HUD atlas", manifest.hudAtlas, failures);
            ValidateType<Font>("UI font", manifest.uiFont, failures);
            ValidateType<AnimatorController>("player animator controller", manifest.playerController, failures);
            ValidateType<AnimatorController>("survivor animator controller", manifest.survivorController, failures);
            ValidateType<AnimatorController>("wolf animator controller", manifest.wolfController, failures);
            ValidateType<AudioMixer>("audio mixer", manifest.audioMixer, failures);

            if (!Directory.Exists(ProductionRoot))
            {
                failures.Add($"Production art directory is missing: {ProductionRoot}");
                return new ValidationResult(manifest, failures);
            }

            var files = Directory.EnumerateFiles(ProductionRoot, "*", SearchOption.AllDirectories)
                .Select(path => path.Replace('\\', '/'))
                .Where(path => !path.EndsWith(".meta", StringComparison.OrdinalIgnoreCase))
                .ToArray();

            foreach (var file in files)
            {
                if (ContainsBannedToken(file))
                    failures.Add($"Production directory contains a prohibited prototype/placeholder name: {file}");
            }

            var modelCount = files.Count(path => HasExtension(path, ModelExtensions));
            var textureCount = files.Count(path => HasExtension(path, TextureExtensions));
            var audioCount = files.Count(path => HasExtension(path, AudioExtensions));
            var animationClipCount = CountAnimationClips(files);
            var materialCount = AssetDatabase.FindAssets("t:Material", new[] { ProductionRoot }).Length;

            RequireMinimum("production model files", modelCount, manifest.minimumEnvironmentModels, failures);
            RequireMinimum("production texture files", textureCount, manifest.minimumTextureFiles, failures);
            RequireMinimum("production animation clips", animationClipCount, manifest.minimumAnimationClips, failures);
            RequireMinimum("production audio clips", audioCount, manifest.minimumAudioClips, failures);
            RequireMinimum("authored materials", materialCount, manifest.minimumMaterials, failures);

            ValidateAnimatorController(manifest.playerController, "player", failures);
            ValidateAnimatorController(manifest.survivorController, "survivor", failures);
            ValidateAnimatorController(manifest.wolfController, "wolf", failures);

            return new ValidationResult(manifest, failures.Distinct().OrderBy(message => message).ToArray());
        }

        public static ProductionArtManifest RequireProductionContent()
        {
            var result = InspectProductionContent();
            if (!result.Passed)
            {
                throw new BuildFailedException(
                    "HAVENLINE premium release candidate blocked. The shipping content gate found:\n - " +
                    string.Join("\n - ", result.Failures));
            }
            return result.Manifest;
        }

        private static Dictionary<string, string> RequiredAssets(ProductionArtManifest manifest) => new()
        {
            ["player model"] = manifest.playerModel,
            ["survivor model"] = manifest.survivorModel,
            ["wolf model"] = manifest.wolfModel,
            ["furnace level 1"] = manifest.furnaceModel,
            ["furnace level 2"] = manifest.furnaceLevel2Model,
            ["furnace level 3"] = manifest.furnaceLevel3Model,
            ["furnace level 4"] = manifest.furnaceLevel4Model,
            ["campfire model"] = manifest.campfireModel,
            ["tent model"] = manifest.tentModel,
            ["storage model"] = manifest.storageModel,
            ["barricade model"] = manifest.barricadeModel,
            ["forest gate model"] = manifest.forestGateModel,
            ["backpack model"] = manifest.backpackModel,
            ["log model"] = manifest.logModel,
            ["stone resource model"] = manifest.stoneResourceModel,
            ["metal resource model"] = manifest.metalResourceModel,
            ["fuel resource model"] = manifest.fuelResourceModel,
            ["pine model A"] = manifest.pineModelA,
            ["pine model B"] = manifest.pineModelB,
            ["rock model A"] = manifest.rockModelA,
            ["rock model B"] = manifest.rockModelB,
            ["environment prefab"] = manifest.environmentPrefab,
            ["snow material"] = manifest.snowMaterial,
            ["ice material"] = manifest.iceMaterial,
            ["warmth material"] = manifest.warmthMaterial,
            ["HUD atlas"] = manifest.hudAtlas,
            ["UI font"] = manifest.uiFont,
            ["HUD prefab"] = manifest.hudPrefab,
            ["pause/settings prefab"] = manifest.pauseMenuPrefab,
            ["player animator controller"] = manifest.playerController,
            ["survivor animator controller"] = manifest.survivorController,
            ["wolf animator controller"] = manifest.wolfController,
            ["fire VFX prefab"] = manifest.fireVfxPrefab,
            ["snowfall VFX prefab"] = manifest.snowfallVfxPrefab,
            ["gather VFX prefab"] = manifest.gatherVfxPrefab,
            ["build VFX prefab"] = manifest.buildVfxPrefab,
            ["hit VFX prefab"] = manifest.hitVfxPrefab,
            ["audio mixer"] = manifest.audioMixer,
            ["audio rig prefab"] = manifest.audioRigPrefab
        };

        private static ProductionArtManifest LoadManifest(ICollection<string> failures)
        {
            if (!File.Exists(ManifestPath))
            {
                failures.Add($"Production-art manifest is missing: {ManifestPath}");
                return null;
            }
            try
            {
                var manifest = JsonUtility.FromJson<ProductionArtManifest>(File.ReadAllText(ManifestPath));
                if (manifest == null)
                    failures.Add("Production-art manifest could not be parsed.");
                return manifest;
            }
            catch (Exception exception)
            {
                failures.Add($"Production-art manifest is invalid: {exception.Message}");
                return null;
            }
        }

        private static void ValidateRequiredAsset(string label, string path, ICollection<string> failures)
        {
            if (string.IsNullOrWhiteSpace(path))
            {
                failures.Add($"Required {label} path is empty.");
                return;
            }
            var normalized = path.Replace('\\', '/');
            if (!normalized.StartsWith(ProductionRoot + "/", StringComparison.Ordinal))
                failures.Add($"Required {label} must be committed under {ProductionRoot}: {normalized}");
            if (ContainsBannedToken(normalized))
                failures.Add($"Required {label} uses a prohibited prototype/placeholder name: {normalized}");
            if (!File.Exists(normalized))
                failures.Add($"Required {label} is missing: {normalized}");
        }

        private static void ValidateType<T>(string label, string path, ICollection<string> failures) where T : UnityEngine.Object
        {
            if (string.IsNullOrWhiteSpace(path) || !File.Exists(path))
                return;
            if (AssetDatabase.LoadAssetAtPath<T>(path) == null)
                failures.Add($"Required {label} did not import as {typeof(T).Name}: {path}");
        }

        private static void ValidateAnimatorController(string path, string label, ICollection<string> failures)
        {
            if (string.IsNullOrWhiteSpace(path) || !File.Exists(path))
                return;
            var controller = AssetDatabase.LoadAssetAtPath<AnimatorController>(path);
            if (controller == null)
                return;
            var parameterNames = controller.parameters.Select(parameter => parameter.name).ToHashSet(StringComparer.Ordinal);
            foreach (var required in new[] { "Speed", "CarryAmount", "ActionType", "Action", "ActionEnd", "Hit", "Dead" })
            {
                if (!parameterNames.Contains(required))
                    failures.Add($"{label} animator controller is missing required parameter '{required}'.");
            }
        }

        private static int CountAnimationClips(IEnumerable<string> files)
        {
            var clips = new HashSet<string>(StringComparer.Ordinal);
            foreach (var path in files.Where(path => HasExtension(path, ModelExtensions) || path.EndsWith(".anim", StringComparison.OrdinalIgnoreCase)))
            {
                foreach (var clip in AssetDatabase.LoadAllAssetsAtPath(path).OfType<AnimationClip>())
                {
                    if (!clip.name.StartsWith("__preview__", StringComparison.OrdinalIgnoreCase))
                        clips.Add(path + "::" + clip.name);
                }
            }
            return clips.Count;
        }

        private static void RequireMinimum(string label, int actual, int required, ICollection<string> failures)
        {
            if (required <= 0)
            {
                failures.Add($"Manifest minimum for {label} must be greater than zero.");
                return;
            }
            if (actual < required)
                failures.Add($"Not enough {label}: found {actual}, require at least {required}.");
        }

        private static bool ContainsBannedToken(string value) =>
            BannedNameTokens.Any(token => value.Contains(token, StringComparison.OrdinalIgnoreCase));

        private static bool HasExtension(string path, IEnumerable<string> extensions) =>
            extensions.Any(extension => path.EndsWith(extension, StringComparison.OrdinalIgnoreCase));
    }
}
