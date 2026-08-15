using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Runtime.CompilerServices;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.SceneManagement;

namespace Havenline.Editor
{
    /// <summary>
    /// The checksum-pinned device-test FBXs preserve the proven Humanoid rigs, while their
    /// matching production GLBs preserve the recovered surface textures. CI extracts those exact
    /// embedded images after verifying the GLB and image SHA-256 values. This helper rebuilds the
    /// corresponding URP materials and binds them to the FBX material slots without altering the
    /// rig, mesh, UVs, skin weights or source pixels.
    ///
    /// Device-test proof and generated gameplay prefabs use the same bindings. Verified release
    /// never enters this path; it continues to require human-approved production assets through
    /// the normal character approval gate.
    /// </summary>
    [InitializeOnLoad]
    internal static class HavenlineDeviceTestCharacterMaterialRestorer
    {
        private const string UrpLitShader = "Universal Render Pipeline/Lit";
        private const string BodySlotName = "Material_0";
        private const string Character3FaceSlotName = "Character3_ApprovedFaceMaterial";
        private const string Character4FaceSlotName = "Character4_ApprovedFaceMaterial";

        private static readonly Dictionary<HavenlineCharacterId, Dictionary<string, Material>> MaterialCache = new();
        private static bool preparedForProof;

        static HavenlineDeviceTestCharacterMaterialRestorer()
        {
            // The proof preview must run first on scene-open so its transient C1-C4 instances
            // exist before this handler binds the recovered surfaces.
            RuntimeHelpers.RunClassConstructor(typeof(HavenlineApprovedCrewProofPreview).TypeHandle);
            EditorSceneManager.sceneOpened -= OnSceneOpened;
            EditorSceneManager.sceneOpened += OnSceneOpened;
        }

        internal static void PrepareForProof()
        {
            if (!HavenlineBuildStageContext.IsDeviceTest)
                return;

            MaterialCache.Clear();
            foreach (var plan in HavenlineProductionCharacterAssetBuilder.Plans)
                MaterialCache[plan.Id] = BuildBindings(plan);
            AssetDatabase.SaveAssets();
            preparedForProof = true;
        }

        internal static void ApplyToGameplayPrefabs()
        {
            if (!HavenlineBuildStageContext.IsDeviceTest)
                return;
            if (!preparedForProof)
                PrepareForProof();

            foreach (var plan in HavenlineProductionCharacterAssetBuilder.Plans)
            {
                var prefabRoot = PrefabUtility.LoadPrefabContents(plan.PrefabPath);
                try
                {
                    var visual = prefabRoot.transform.Find("Visual");
                    if (visual == null)
                        throw new InvalidOperationException($"Generated {plan.Id} gameplay prefab has no Visual root.");
                    Apply(plan, visual.gameObject);
                    if (PrefabUtility.SaveAsPrefabAsset(prefabRoot, plan.PrefabPath) == null)
                        throw new InvalidOperationException($"Unity failed to save restored device-test prefab: {plan.PrefabPath}");
                }
                finally
                {
                    PrefabUtility.UnloadPrefabContents(prefabRoot);
                }
            }
            AssetDatabase.SaveAssets();
        }

        private static void OnSceneOpened(Scene scene, OpenSceneMode mode)
        {
            if (!preparedForProof || !HavenlineBuildStageContext.IsDeviceTest || !scene.IsValid() ||
                !string.Equals(scene.path, Reference.ScenePath, StringComparison.Ordinal))
                return;

            var previewRoot = scene.GetRootGameObjects()
                .FirstOrDefault(root => root.name == HavenlineApprovedCrewProofPreview.RootName);
            if (previewRoot == null)
                return;

            foreach (var plan in HavenlineProductionCharacterAssetBuilder.Plans)
            {
                var proofName = plan.Id == HavenlineCharacterId.Character1
                    ? HavenlineApprovedCrewProofPreview.LeadProofName
                    : plan.Id + "_ProofCompanion";
                var proof = previewRoot.GetComponentsInChildren<Transform>(true)
                    .FirstOrDefault(item => string.Equals(item.name, proofName, StringComparison.Ordinal));
                if (proof == null)
                    throw new InvalidOperationException($"Device-test proof preview is missing {proofName} for exact material restoration.");
                Apply(plan, proof.gameObject);
            }
        }

        private static void Apply(
            HavenlineProductionCharacterAssetBuilder.CharacterPlan plan,
            GameObject instance)
        {
            if (plan == null)
                throw new ArgumentNullException(nameof(plan));
            if (instance == null)
                throw new ArgumentNullException(nameof(instance));
            if (!HavenlineBuildStageContext.IsDeviceTest)
                return;

            if (!MaterialCache.TryGetValue(plan.Id, out var bindings))
            {
                bindings = BuildBindings(plan);
                MaterialCache[plan.Id] = bindings;
            }

            var renderers = instance.GetComponentsInChildren<Renderer>(true);
            if (renderers.Length == 0)
                throw new InvalidOperationException($"{plan.Id} has no renderers for device-test material restoration.");

            var seenSlots = new HashSet<string>(StringComparer.Ordinal);
            foreach (var renderer in renderers)
            {
                var sourceMaterials = renderer.sharedMaterials;
                if (sourceMaterials == null || sourceMaterials.Length == 0)
                    throw new InvalidOperationException($"{plan.Id} renderer '{renderer.name}' has no imported FBX materials.");

                var restored = new Material[sourceMaterials.Length];
                for (var index = 0; index < sourceMaterials.Length; index++)
                {
                    var source = sourceMaterials[index]
                        ?? throw new InvalidOperationException($"{plan.Id} renderer '{renderer.name}' has a null material slot at {index}.");
                    var slot = NormalizeMaterialName(source.name);
                    if (!bindings.TryGetValue(slot, out var material))
                    {
                        throw new InvalidOperationException(
                            $"{plan.Id} contains unexpected FBX material slot '{source.name}' on renderer '{renderer.name}'. " +
                            "Device-test surface restoration is fail-closed; update the pinned GLB/FBX mapping instead of leaving gray fallback material.");
                    }
                    restored[index] = material;
                    seenSlots.Add(slot);
                }
                renderer.sharedMaterials = restored;
                EditorUtility.SetDirty(renderer);
            }

            foreach (var required in bindings.Keys)
            {
                if (!seenSlots.Contains(required))
                    throw new InvalidOperationException($"{plan.Id} did not expose required pinned material slot '{required}'.");
            }

            foreach (var material in bindings.Values)
            {
                if (material == null || material.GetTexture("_BaseMap") == null)
                    throw new InvalidOperationException($"{plan.Id} restored material is missing its exact recovered base-color texture.");
            }
        }

        private static Dictionary<string, Material> BuildBindings(
            HavenlineProductionCharacterAssetBuilder.CharacterPlan plan)
        {
            var shader = Shader.Find(UrpLitShader)
                ?? throw new InvalidOperationException($"Required shader is unavailable: {UrpLitShader}");
            var bindings = new Dictionary<string, Material>(StringComparer.Ordinal);

            switch (plan.Id)
            {
                case HavenlineCharacterId.Character1:
                    bindings.Add(
                        BodySlotName,
                        RequireMaterial(
                            plan,
                            BodySlotName,
                            RequireTexture(plan, 0, "png"),
                            Color.white,
                            0f,
                            0.28f,
                            false,
                            shader));
                    break;

                case HavenlineCharacterId.Character2:
                    bindings.Add(
                        BodySlotName,
                        RequireMaterial(
                            plan,
                            BodySlotName,
                            RequireTexture(plan, 0, "png"),
                            Color.white,
                            0f,
                            0.28f,
                            false,
                            shader));
                    break;

                case HavenlineCharacterId.Character3:
                    bindings.Add(
                        Character3FaceSlotName,
                        RequireMaterial(
                            plan,
                            Character3FaceSlotName,
                            RequireTexture(plan, 0, "jpg"),
                            Color.white,
                            0f,
                            0.12f,
                            true,
                            shader));
                    bindings.Add(
                        BodySlotName,
                        RequireMaterial(
                            plan,
                            BodySlotName,
                            RequireTexture(plan, 1, "png"),
                            new Color(0.4f, 0.4f, 0.4f, 1f),
                            0f,
                            0.096398f,
                            false,
                            shader));
                    break;

                case HavenlineCharacterId.Character4:
                    bindings.Add(
                        Character4FaceSlotName,
                        RequireMaterial(
                            plan,
                            Character4FaceSlotName,
                            RequireTexture(plan, 0, "jpg"),
                            Color.white,
                            0f,
                            0.12f,
                            true,
                            shader));
                    bindings.Add(
                        BodySlotName,
                        RequireMaterial(
                            plan,
                            BodySlotName,
                            RequireTexture(plan, 1, "png"),
                            new Color(0.4f, 0.4f, 0.4f, 1f),
                            0f,
                            0.096398f,
                            false,
                            shader));
                    break;

                default:
                    throw new ArgumentOutOfRangeException(nameof(plan.Id), plan.Id, "Unknown HAVENLINE character id.");
            }

            return bindings;
        }

        private static Texture2D RequireTexture(
            HavenlineProductionCharacterAssetBuilder.CharacterPlan plan,
            int imageIndex,
            string extension)
        {
            var path = $"{plan.Folder}/{plan.Id}_glb_image_{imageIndex}.{extension}";
            var texture = AssetDatabase.LoadAssetAtPath<Texture2D>(path);
            if (texture == null)
            {
                throw new FileNotFoundException(
                    $"Exact recovered GLB texture is missing for {plan.Id}. Device-test character cannot fall back to gray material.",
                    path);
            }

            var importer = AssetImporter.GetAtPath(path) as TextureImporter;
            if (importer != null && (!importer.sRGBTexture || !importer.mipmapEnabled || importer.wrapMode != TextureWrapMode.Repeat))
            {
                importer.sRGBTexture = true;
                importer.mipmapEnabled = true;
                importer.wrapMode = TextureWrapMode.Repeat;
                importer.filterMode = FilterMode.Trilinear;
                importer.anisoLevel = 4;
                importer.SaveAndReimport();
                texture = AssetDatabase.LoadAssetAtPath<Texture2D>(path)
                    ?? throw new InvalidOperationException($"Recovered texture failed to reimport for {plan.Id}: {path}");
            }
            return texture;
        }

        private static Material RequireMaterial(
            HavenlineProductionCharacterAssetBuilder.CharacterPlan plan,
            string slotName,
            Texture2D baseMap,
            Color baseColor,
            float metallic,
            float smoothness,
            bool doubleSided,
            Shader shader)
        {
            var safeSlot = slotName.Replace('/', '_').Replace('\\', '_');
            var path = $"{plan.Folder}/{plan.Id}_{safeSlot}_DeviceTest.mat";
            var material = AssetDatabase.LoadAssetAtPath<Material>(path);
            if (material == null)
            {
                material = new Material(shader) { name = $"{plan.Id}_{safeSlot}_DeviceTest" };
                AssetDatabase.CreateAsset(material, path);
            }
            else
            {
                material.shader = shader;
            }

            material.SetTexture("_BaseMap", baseMap);
            material.SetColor("_BaseColor", baseColor);
            material.SetFloat("_Metallic", metallic);
            material.SetFloat("_Smoothness", smoothness);
            material.SetFloat("_Surface", 0f);
            material.SetFloat("_AlphaClip", 0f);
            material.SetFloat("_Cull", doubleSided ? (float)CullMode.Off : (float)CullMode.Back);
            material.enableInstancing = true;
            material.renderQueue = -1;
            EditorUtility.SetDirty(material);
            return material;
        }

        private static string NormalizeMaterialName(string name)
        {
            if (string.IsNullOrWhiteSpace(name))
                return string.Empty;
            var normalized = name.Trim();
            foreach (var suffix in new[] { " (Instance)", " (Clone)" })
            {
                if (normalized.EndsWith(suffix, StringComparison.Ordinal))
                    normalized = normalized[..^suffix.Length];
            }
            return normalized;
        }
    }
}
