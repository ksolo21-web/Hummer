using System;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.CompilerServices;
using UnityEditor;
using UnityEngine;

namespace Havenline.Editor
{
    /// <summary>
    /// Fail-closed render-time guard for the checksum-pinned C1-C4 device-test surfaces.
    ///
    /// The normal material restorer owns material creation and gameplay-prefab restoration. Proof
    /// previously relied only on scene-open event ordering, which allowed a transient FBX preview
    /// to reach a camera with Unity's gray imported fallback. Camera pre-cull is the last reliable
    /// point before every real proof render, so this guard binds the already-created exact recovered
    /// materials there and validates that every required slot has a recovered base-color texture.
    /// </summary>
    [InitializeOnLoad]
    internal static class HavenlineDeviceTestProofSurfaceGuard
    {
        private const string BodySlotName = "Material_0";
        private const string Character3FaceSlotName = "Character3_ApprovedFaceMaterial";
        private const string Character4FaceSlotName = "Character4_ApprovedFaceMaterial";
        private static bool applying;

        static HavenlineDeviceTestProofSurfaceGuard()
        {
            RuntimeHelpers.RunClassConstructor(typeof(HavenlineApprovedCrewProofPreview).TypeHandle);
            RuntimeHelpers.RunClassConstructor(typeof(HavenlineDeviceTestCharacterMaterialRestorer).TypeHandle);
            Camera.onPreCull -= OnCameraPreCull;
            Camera.onPreCull += OnCameraPreCull;
        }

        private static void OnCameraPreCull(Camera camera)
        {
            if (applying || camera == null || !HavenlineBuildStageContext.IsDeviceTest)
                return;
            var scene = camera.gameObject.scene;
            if (!scene.IsValid() || !scene.isLoaded ||
                !string.Equals(scene.path, Reference.ScenePath, StringComparison.Ordinal))
                return;

            var previewRoot = scene.GetRootGameObjects()
                .FirstOrDefault(root => root.name == HavenlineApprovedCrewProofPreview.RootName);
            if (previewRoot == null)
                return;

            applying = true;
            try
            {
                foreach (var plan in HavenlineProductionCharacterAssetBuilder.Plans)
                {
                    var proofName = plan.Id == HavenlineCharacterId.Character1
                        ? HavenlineApprovedCrewProofPreview.LeadProofName
                        : plan.Id + "_ProofCompanion";
                    var instance = previewRoot.GetComponentsInChildren<Transform>(true)
                        .FirstOrDefault(item => string.Equals(item.name, proofName, StringComparison.Ordinal));
                    if (instance == null)
                        throw new InvalidOperationException($"Device-test proof is missing {proofName} before camera render.");
                    BindExactRecoveredMaterials(plan, instance.gameObject);
                }
            }
            finally
            {
                applying = false;
            }
        }

        private static void BindExactRecoveredMaterials(
            HavenlineProductionCharacterAssetBuilder.CharacterPlan plan,
            GameObject instance)
        {
            var bindings = LoadBindings(plan);
            var renderers = instance.GetComponentsInChildren<Renderer>(true);
            if (renderers.Length == 0)
                throw new InvalidOperationException($"{plan.Id} proof instance has no renderers before camera render.");

            var seenSlots = new HashSet<string>(StringComparer.Ordinal);
            foreach (var renderer in renderers)
            {
                var source = renderer.sharedMaterials;
                if (source == null || source.Length == 0)
                    throw new InvalidOperationException($"{plan.Id} proof renderer '{renderer.name}' has no material slots.");

                var restored = new Material[source.Length];
                for (var index = 0; index < source.Length; index++)
                {
                    var current = source[index]
                        ?? throw new InvalidOperationException($"{plan.Id} proof renderer '{renderer.name}' has a null material slot at {index}.");
                    var normalized = NormalizeMaterialName(current.name);
                    var match = ResolveBinding(plan, normalized, bindings, source.Length);
                    restored[index] = match.Material;
                    seenSlots.Add(match.Slot);
                }
                renderer.sharedMaterials = restored;
            }

            foreach (var required in bindings.Keys)
            {
                if (!seenSlots.Contains(required))
                    throw new InvalidOperationException($"{plan.Id} proof never exposed required recovered material slot '{required}'.");
                var material = bindings[required];
                if (material.GetTexture("_BaseMap") == null)
                    throw new InvalidOperationException($"{plan.Id} proof material '{material.name}' has no recovered base-color texture.");
            }
        }

        private static Dictionary<string, Material> LoadBindings(
            HavenlineProductionCharacterAssetBuilder.CharacterPlan plan)
        {
            var slots = plan.Id switch
            {
                HavenlineCharacterId.Character1 => new[] { BodySlotName },
                HavenlineCharacterId.Character2 => new[] { BodySlotName },
                HavenlineCharacterId.Character3 => new[] { Character3FaceSlotName, BodySlotName },
                HavenlineCharacterId.Character4 => new[] { Character4FaceSlotName, BodySlotName },
                _ => throw new ArgumentOutOfRangeException(nameof(plan.Id), plan.Id, "Unknown HAVENLINE character id.")
            };

            var result = new Dictionary<string, Material>(StringComparer.Ordinal);
            foreach (var slot in slots)
            {
                var safeSlot = slot.Replace('/', '_').Replace('\\', '_');
                var path = $"{plan.Folder}/{plan.Id}_{safeSlot}_DeviceTest.mat";
                var material = AssetDatabase.LoadAssetAtPath<Material>(path)
                    ?? throw new InvalidOperationException(
                        $"Exact recovered device-test material was not prepared for {plan.Id}/{slot}: {path}");
                if (material.GetTexture("_BaseMap") == null)
                    throw new InvalidOperationException($"Recovered device-test material has no base map: {path}");
                result.Add(slot, material);
            }
            return result;
        }

        private static (string Slot, Material Material) ResolveBinding(
            HavenlineProductionCharacterAssetBuilder.CharacterPlan plan,
            string currentName,
            IReadOnlyDictionary<string, Material> bindings,
            int rendererSlotCount)
        {
            foreach (var pair in bindings)
            {
                if (string.Equals(currentName, pair.Key, StringComparison.Ordinal) ||
                    string.Equals(currentName, NormalizeMaterialName(pair.Value.name), StringComparison.Ordinal))
                    return (pair.Key, pair.Value);
            }

            // The checksum-pinned C1/C2 FBXs each have a single body surface. Accepting a single
            // imported slot by cardinality is deterministic and still fail-closed on the exact
            // recovered material asset; it avoids depending on an exporter-specific slot label.
            if (bindings.Count == 1 && rendererSlotCount == 1)
            {
                var only = bindings.Single();
                return (only.Key, only.Value);
            }

            throw new InvalidOperationException(
                $"{plan.Id} proof contains unexpected material slot '{currentName}'. " +
                "Recovered-surface proof binding is fail-closed; update the pinned FBX/GLB slot mapping.");
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
