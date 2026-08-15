using System;
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
    /// Final human-review correction layered after the original R32 shipping finalizer. The pass
    /// adds persistent production-authored camp infrastructure and replaces the broad empty center
    /// with readable traversal/service detail. It is part of the shipping scene, not proof-only
    /// decoration, and it intentionally subscribes after the earlier R32 finalizer.
    /// </summary>
    [InitializeOnLoad]
    internal static class ZZZZZZHavenlineR32VisualRecoveryFinalizer
    {
        private const string RootName = "HAVENLINE_R32VisualRecovery";
        private static bool applying;

        static ZZZZZZHavenlineR32VisualRecoveryFinalizer()
        {
            RuntimeHelpers.RunClassConstructor(typeof(ZZZZZHavenlineR32ShippingSceneFinalizer).TypeHandle);
            EditorSceneManager.sceneSaving -= OnSceneSaving;
            EditorSceneManager.sceneSaving += OnSceneSaving;
        }

        private static void OnSceneSaving(Scene scene, string path)
        {
            if (applying || !string.Equals(path, Reference.ScenePath, StringComparison.Ordinal))
                return;
            if (AssetDatabase.LoadAssetAtPath<GameObject>(HavenlineR32VisualRecoveryPass.DuckboardPath) == null)
                return;

            applying = true;
            try
            {
                Apply(scene);
            }
            finally
            {
                applying = false;
            }
        }

        internal static void Apply(Scene scene)
        {
            if (!scene.IsValid() || !scene.isLoaded)
                return;

            var objects = AllObjects(scene);
            var previous = objects.FirstOrDefault(item => item != null && item.name == RootName);
            if (previous != null)
                UnityEngine.Object.DestroyImmediate(previous);

            var shippingRoot = scene.GetRootGameObjects()
                .FirstOrDefault(item => item.name == HavenlineCoreCrewScenePostprocessor.ShippingRootName)
                ?? throw new InvalidOperationException("R32 visual recovery requires the shipping scene root.");
            var furnace = objects.FirstOrDefault(item => item != null && item.name == "Furnace")
                ?? throw new InvalidOperationException("R32 visual recovery requires the Furnace anchor.");
            var campfire = objects.FirstOrDefault(item => item != null && item.name == "Campfire")
                ?? throw new InvalidOperationException("R32 visual recovery requires the Campfire anchor.");
            var storage = objects.FirstOrDefault(item => item != null && item.name == "SupplyStorage")
                ?? throw new InvalidOperationException("R32 visual recovery requires the SupplyStorage anchor.");
            var leftShelter = objects.FirstOrDefault(item => item != null && item.name == "LeftPremiumShelter")
                ?? throw new InvalidOperationException("R32 visual recovery requires LeftPremiumShelter.");
            var rightShelter = objects.FirstOrDefault(item => item != null && item.name == "RightPremiumShelter")
                ?? throw new InvalidOperationException("R32 visual recovery requires RightPremiumShelter.");

            var root = new GameObject(RootName);
            root.transform.SetParent(shippingRoot.transform, false);

            AddCampWalkway(root.transform, furnace.transform.position, campfire.transform.position);
            AddUtilityInfrastructure(root.transform, storage.transform.position, furnace.transform.position);
            AddShelterServiceDetail(root.transform, leftShelter.transform, true);
            AddShelterServiceDetail(root.transform, rightShelter.transform, false);
            TightenKeyLight(scene);
        }

        private static void AddCampWalkway(Transform parent, Vector3 furnace, Vector3 campfire)
        {
            var axis = campfire - furnace;
            axis.y = 0f;
            if (axis.sqrMagnitude < 0.01f)
                axis = Vector3.back;
            axis.Normalize();
            var yaw = Mathf.Atan2(axis.x, axis.z) * Mathf.Rad2Deg;

            // Two connected authored duckboard modules give the close camera real slat, fastener,
            // snow-load and shadow detail while also making the camp read as occupied and traversed.
            for (var index = 0; index < 2; index++)
            {
                var item = InstantiateProductionModel(
                    HavenlineR32VisualRecoveryPass.DuckboardPath,
                    parent,
                    $"R32CampDuckboard_{index + 1:00}");
                var midpoint = Vector3.Lerp(furnace, campfire, 0.34f + index * 0.32f);
                item.transform.SetPositionAndRotation(
                    midpoint + new Vector3(0f, 0.075f, 0f),
                    Quaternion.Euler(0f, yaw, 0f));
                item.transform.localScale *= index == 0 ? 0.88f : 0.82f;
            }
        }

        private static void AddUtilityInfrastructure(Transform parent, Vector3 storage, Vector3 furnace)
        {
            var rack = InstantiateProductionModel(
                HavenlineR32VisualRecoveryPass.UtilityRackPath,
                parent,
                "R32SupplyUtilityRack");
            rack.transform.SetPositionAndRotation(
                storage + new Vector3(1.18f, 0.045f, 0.56f),
                Quaternion.Euler(0f, -18f, 0f));
            rack.transform.localScale *= 0.86f;

            var furnaceRack = InstantiateProductionModel(
                HavenlineR32VisualRecoveryPass.UtilityRackPath,
                parent,
                "R32FurnaceUtilityRack");
            furnaceRack.transform.SetPositionAndRotation(
                furnace + new Vector3(1.72f, 0.045f, 0.68f),
                Quaternion.Euler(0f, 31f, 0f));
            furnaceRack.transform.localScale *= 0.66f;
        }

        private static void AddShelterServiceDetail(Transform parent, Transform shelter, bool left)
        {
            var module = InstantiateProductionModel(
                HavenlineR32VisualRecoveryPass.ShelterServicePath,
                parent,
                left ? "R32LeftShelterServiceModule" : "R32RightShelterServiceModule");
            var lateral = shelter.right * (left ? 1.82f : -1.82f);
            var forward = shelter.forward * -0.78f;
            module.transform.SetPositionAndRotation(
                shelter.position + lateral + forward + new Vector3(0f, 0.04f, 0f),
                shelter.rotation * Quaternion.Euler(0f, left ? 78f : -78f, 0f));
            module.transform.localScale *= 0.78f;
        }

        private static void TightenKeyLight(Scene scene)
        {
            var directional = AllObjects(scene)
                .SelectMany(item => item.GetComponents<Light>())
                .FirstOrDefault(light => light.type == LightType.Directional);
            if (directional == null)
                return;

            directional.shadowStrength = Mathf.Max(directional.shadowStrength, 0.82f);
            directional.shadowBias = Mathf.Min(directional.shadowBias, 0.035f);
            directional.shadowNormalBias = Mathf.Min(directional.shadowNormalBias, 0.28f);
        }

        private static GameObject InstantiateProductionModel(string path, Transform parent, string name)
        {
            var asset = AssetDatabase.LoadAssetAtPath<GameObject>(path);
            if (asset == null)
                throw new FileNotFoundException("HAVENLINE R32 recovery model is missing.", path);
            var instance = PrefabUtility.InstantiatePrefab(asset, parent) as GameObject;
            if (instance == null)
                throw new InvalidOperationException("Could not instantiate HAVENLINE R32 recovery model: " + path);
            instance.name = name;
            foreach (var collider in instance.GetComponentsInChildren<Collider>(true))
                UnityEngine.Object.DestroyImmediate(collider);
            foreach (var renderer in instance.GetComponentsInChildren<Renderer>(true))
            {
                renderer.shadowCastingMode = ShadowCastingMode.On;
                renderer.receiveShadows = true;
            }
            return instance;
        }

        private static GameObject[] AllObjects(Scene scene) => scene.GetRootGameObjects()
            .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
            .Select(item => item.gameObject)
            .Distinct()
            .ToArray();
    }
}
