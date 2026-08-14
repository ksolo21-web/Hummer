using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Havenline.Editor
{
    /// <summary>
    /// Editor-only render-proof overlay. The shipping scene cannot know the saved C1/C2 lead
    /// while it is being rendered in CI, so proof deterministically uses Character 1 as the
    /// visible lead and shows Character 2, Character 3 and Character 4 in companion formation.
    ///
    /// Verified release proof requires human-approved production FBXs. Device-test proof may
    /// use the separately checksum-pinned review stage. The overlay is never serialized and is
    /// tagged EditorOnly; runtime Android spawns the selected lead from the generated roster.
    /// </summary>
    [InitializeOnLoad]
    internal static class HavenlineApprovedCrewProofPreview
    {
        internal const string RootName = "HAVENLINE_ApprovedCrewProofPreview";
        internal const string LeadProofName = "Player";
        internal const string LeftShelterProofName = "StartingTent";
        internal const string RightShelterProofName = "RescueShelter";
        private const string LeftPremiumShelterName = "LeftPremiumShelter";
        private const string RightPremiumShelterName = "RightPremiumShelter";

        private static bool applying;

        static HavenlineApprovedCrewProofPreview()
        {
            EditorSceneManager.sceneOpened -= OnSceneOpened;
            EditorSceneManager.sceneOpened += OnSceneOpened;
        }

        private static void OnSceneOpened(Scene scene, OpenSceneMode mode)
        {
            if (applying || !scene.IsValid() ||
                !string.Equals(scene.path, Reference.ScenePath, StringComparison.Ordinal))
                return;

            applying = true;
            try
            {
                RemoveTransientPreview(scene);
                if (InspectPrerequisites().Count == 0)
                    BuildTransientPreview(scene);
            }
            finally
            {
                applying = false;
            }
        }

        [MenuItem("HAVENLINE Premium/Characters/Inspect Core Crew Proof Preview")]
        private static void InspectFromMenu()
        {
            var failures = InspectPrerequisites();
            if (failures.Count > 0)
                throw new InvalidOperationException(
                    "HAVENLINE core crew proof preview is blocked:\n - " +
                    string.Join("\n - ", failures));

            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            BuildTransientPreview(scene);
            SceneView.RepaintAll();
            Debug.Log("HAVENLINE editor-only C1-C4 proof preview is active. It will not be serialized or included in Android builds.");
        }

        internal static IReadOnlyList<string> InspectPrerequisites()
        {
            var failures = new List<string>();
            if (HavenlineBuildStageContext.IsDeviceTest)
                failures.AddRange(HavenlineDeviceTestCharacterGate.Validate());
            else
                failures.AddRange(HavenlineCharacterApprovalGate.Validate());

            foreach (var plan in HavenlineProductionCharacterAssetBuilder.Plans)
            {
                if (AssetDatabase.LoadAssetAtPath<GameObject>(plan.ModelPath) == null)
                    failures.Add($"Proof model is missing or not imported for {plan.Id}: {plan.ModelPath}");
            }

            return failures.Distinct(StringComparer.Ordinal).ToArray();
        }

        internal static bool HasTransientPreview(Scene scene)
        {
            if (!scene.IsValid())
                return false;
            return scene.GetRootGameObjects().Any(root => root.name == RootName);
        }

        private static void BuildTransientPreview(Scene scene)
        {
            RemoveTransientPreview(scene);
            var failures = InspectPrerequisites();
            if (failures.Count > 0)
                throw new InvalidOperationException(
                    "Cannot build HAVENLINE core crew proof preview:\n - " +
                    string.Join("\n - ", failures));

            var manifest = HavenlinePremiumBuildGate.RequireProductionContent();
            var proofController = AssetDatabase.LoadAssetAtPath<RuntimeAnimatorController>(manifest.playerController)
                ?? throw new FileNotFoundException(
                    "Production player AnimatorController is missing from the proof path.",
                    manifest.playerController);

            var previewRoot = new GameObject(RootName)
            {
                tag = "EditorOnly",
                hideFlags = HideFlags.DontSaveInEditor | HideFlags.DontSaveInBuild
            };
            SceneManager.MoveGameObjectToScene(previewRoot, scene);
            previewRoot.transform.SetSiblingIndex(0);

            try
            {
                var leadPosition = Reference.PlayerSpawn;
                CreateCharacterPreview(
                    HavenlineCharacterId.Character1,
                    LeadProofName,
                    leadPosition,
                    Quaternion.Euler(0f, 180f, 0f),
                    previewRoot.transform,
                    proofController);

                var companionIds = new[]
                {
                    HavenlineCharacterId.Character2,
                    HavenlineCharacterId.Character3,
                    HavenlineCharacterId.Character4
                };
                for (var index = 0; index < companionIds.Length; index++)
                {
                    var offset = Reference.CompanionFormationOffsets[index];
                    CreateCharacterPreview(
                        companionIds[index],
                        companionIds[index] + "_ProofCompanion",
                        leadPosition + offset,
                        Quaternion.Euler(0f, 180f, 0f),
                        previewRoot.transform,
                        proofController);
                }

                EnsureShelterProofIdentity(
                    scene,
                    LeftPremiumShelterName,
                    LeftShelterProofName,
                    previewRoot.transform);
                EnsureShelterProofIdentity(
                    scene,
                    RightPremiumShelterName,
                    RightShelterProofName,
                    previewRoot.transform);

                HideLegacyProofPlayer(scene, previewRoot);
            }
            catch
            {
                UnityEngine.Object.DestroyImmediate(previewRoot);
                throw;
            }
        }

        private static void CreateCharacterPreview(
            HavenlineCharacterId characterId,
            string proofName,
            Vector3 position,
            Quaternion rotation,
            Transform parent,
            RuntimeAnimatorController proofController)
        {
            var plan = HavenlineProductionCharacterAssetBuilder.Plans.Single(item => item.Id == characterId);
            var asset = AssetDatabase.LoadAssetAtPath<GameObject>(plan.ModelPath)
                ?? throw new FileNotFoundException(
                    $"{characterId} FBX is missing from the proof path.", plan.ModelPath);

            var instance = PrefabUtility.InstantiatePrefab(asset) as GameObject
                ?? throw new InvalidOperationException($"Could not instantiate {characterId} proof model.");
            instance.name = proofName;
            instance.hideFlags = HideFlags.DontSaveInEditor | HideFlags.DontSaveInBuild;
            instance.transform.SetParent(parent, true);
            instance.transform.rotation = rotation;
            GroundAt(instance, position);

            var animators = instance.GetComponentsInChildren<Animator>(true);
            if (animators.Length == 0)
                throw new InvalidOperationException($"{characterId} proof model has no Animator after Humanoid import.");
            foreach (var animator in animators)
            {
                if (animator.avatar == null || !animator.avatar.isValid)
                    throw new InvalidOperationException($"{characterId} proof model has no valid Humanoid avatar.");
                animator.runtimeAnimatorController = proofController;
                animator.applyRootMotion = false;
                animator.cullingMode = AnimatorCullingMode.AlwaysAnimate;
            }

            foreach (var component in instance.GetComponentsInChildren<MonoBehaviour>(true))
                component.enabled = false;
        }

        private static void EnsureShelterProofIdentity(
            Scene scene,
            string productionName,
            string proofName,
            Transform parent)
        {
            var all = AllObjects(scene);
            var canonical = all.FirstOrDefault(item =>
                item.name == proofName &&
                !item.name.StartsWith("Legacy", StringComparison.Ordinal) &&
                item.activeInHierarchy &&
                item.GetComponentsInChildren<Renderer>(true)
                    .Any(renderer => renderer.enabled && renderer.gameObject.activeInHierarchy));
            if (canonical != null)
                return;

            var source = all.FirstOrDefault(item =>
                item.name == productionName &&
                item.activeInHierarchy &&
                item.GetComponentsInChildren<Renderer>(true)
                    .Any(renderer => renderer.enabled && renderer.gameObject.activeInHierarchy));
            if (source == null)
                throw new InvalidOperationException(
                    $"Visible premium shelter '{productionName}'/'{proofName}' is missing; proof cannot fall back to the retired tent.");

            var clone = UnityEngine.Object.Instantiate(source);
            clone.name = proofName;
            clone.hideFlags = HideFlags.DontSaveInEditor | HideFlags.DontSaveInBuild;
            clone.transform.SetParent(parent, true);
            clone.transform.position = source.transform.position;
            clone.transform.rotation = source.transform.rotation;
            clone.transform.localScale = source.transform.lossyScale;
            clone.SetActive(true);
        }

        private static void HideLegacyProofPlayer(Scene scene, GameObject previewRoot)
        {
            var legacy = AllObjects(scene)
                .FirstOrDefault(item =>
                    item != previewRoot &&
                    item.name == LeadProofName &&
                    item.GetComponent<HavenlinePlayerController>() != null);
            if (legacy == null)
                return;

            foreach (var renderer in legacy.GetComponentsInChildren<Renderer>(true))
                renderer.enabled = false;
        }

        private static void GroundAt(GameObject instance, Vector3 targetGroundPosition)
        {
            var renderers = instance.GetComponentsInChildren<Renderer>(true);
            if (renderers.Length == 0)
                throw new InvalidOperationException($"Proof model {instance.name} contains no renderers.");

            var bounds = renderers[0].bounds;
            for (var index = 1; index < renderers.Length; index++)
                bounds.Encapsulate(renderers[index].bounds);

            var delta = new Vector3(
                targetGroundPosition.x - bounds.center.x,
                targetGroundPosition.y - bounds.min.y,
                targetGroundPosition.z - bounds.center.z);
            instance.transform.position += delta;
        }

        private static void RemoveTransientPreview(Scene scene)
        {
            if (!scene.IsValid())
                return;
            foreach (var root in scene.GetRootGameObjects().Where(item => item.name == RootName).ToArray())
                UnityEngine.Object.DestroyImmediate(root);
        }

        private static GameObject[] AllObjects(Scene scene) => scene.GetRootGameObjects()
            .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
            .Select(item => item.gameObject)
            .Distinct()
            .ToArray();
    }
}
