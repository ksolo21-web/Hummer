using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Runtime.CompilerServices;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Havenline.Editor
{
    /// <summary>
    /// Final r30 composition pass.
    ///
    /// The r29 proof exposed two concrete presentation defects: the legacy tent identities were
    /// still being measured instead of the premium shelters, and the inhabited camp read too
    /// narrow/sparse at shipping aspect ratios. This pass keeps shipping shelter names intact,
    /// retires the imported tent shells, and adds authored production-prop micro detail. The
    /// editor-only crew proof overlay supplies transient StartingTent/RescueShelter aliases.
    /// </summary>
    [InitializeOnLoad]
    internal static class ZZZHavenlineR30PremiumComposition
    {
        private const string RootName = "HAVENLINE_R30PremiumComposition";
        private const string ProductionRoot = "Assets/Havenline/Art/Production";
        private const string ResourceRoot = ProductionRoot + "/Resources";
        private const string PropRoot = ProductionRoot + "/Props";
        private const string EnvironmentRoot = ProductionRoot + "/Environment";

        private static bool applying;

        static ZZZHavenlineR30PremiumComposition()
        {
            RuntimeHelpers.RunClassConstructor(typeof(ZZHavenlineReferenceGradeVisualRebuild).TypeHandle);
            EditorSceneManager.sceneSaving -= OnSceneSaving;
            EditorSceneManager.sceneSaving += OnSceneSaving;
        }

        private static void OnSceneSaving(Scene scene, string path)
        {
            if (applying || !string.Equals(path, Reference.ScenePath, StringComparison.Ordinal))
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
            RecomposePremiumShelter(
                objects,
                "LeftPremiumShelter",
                "StartingTent",
                new Vector3(-5.95f, 0f, -0.55f),
                20f);
            RecomposePremiumShelter(
                objects,
                "RightPremiumShelter",
                "RescueShelter",
                new Vector3(5.95f, 0f, -0.48f),
                -22f);

            SetPose(objects, "SupplyStorage", new Vector3(-3.75f, 0f, 1.55f), -10f);
            SetPose(objects, "Campfire", new Vector3(3.48f, 0f, 1.55f), 8f);
            SetPose(objects, "FrozenSurvivor", new Vector3(5.10f, 0f, -1.72f), -16f);

            RebuildMicroDetail(scene);
            OpenForeground(objects);
        }

        private static void RecomposePremiumShelter(
            IReadOnlyCollection<GameObject> objects,
            string premiumName,
            string legacyName,
            Vector3 position,
            float yaw)
        {
            var premium = objects.FirstOrDefault(item => item.name == premiumName);
            var legacy = objects.FirstOrDefault(item => item.name == legacyName);

            // Keep the exact legacy name because the scene gate explicitly verifies that the
            // superseded imported tent exists only as an inactive shell. Proof clones are
            // transient EditorOnly objects and therefore never alter the shipping hierarchy.
            if (legacy != null && legacy != premium)
                legacy.SetActive(false);

            if (premium == null)
                return;

            premium.transform.SetPositionAndRotation(position, Quaternion.Euler(0f, yaw, 0f));
            premium.SetActive(true);
            foreach (var renderer in premium.GetComponentsInChildren<Renderer>(true))
                renderer.enabled = true;
        }

        private static void RebuildMicroDetail(Scene scene)
        {
            var existing = AllObjects(scene).FirstOrDefault(item => item.name == RootName);
            if (existing != null)
                UnityEngine.Object.DestroyImmediate(existing);

            var root = new GameObject(RootName);
            SceneManager.MoveGameObjectToScene(root, scene);
            var shippingRoot = scene.GetRootGameObjects()
                .FirstOrDefault(item => item.name == HavenlineCoreCrewScenePostprocessor.ShippingRootName);
            if (shippingRoot != null)
                root.transform.SetParent(shippingRoot.transform, false);

            var logClusters = new[]
            {
                new Vector3(-4.85f, 0.08f, 1.75f), new Vector3(-4.62f, 0.08f, 1.58f),
                new Vector3(-4.39f, 0.08f, 1.78f), new Vector3(4.70f, 0.08f, 1.70f),
                new Vector3(4.48f, 0.08f, 1.50f), new Vector3(4.28f, 0.08f, 1.73f),
                new Vector3(-6.55f, 0.08f, 0.78f), new Vector3(6.48f, 0.08f, 0.72f)
            };
            for (var index = 0; index < logClusters.Length; index++)
            {
                var log = InstantiateProductionModel(
                    ResourceRoot + "/HAVENLINE_Log.obj",
                    root.transform,
                    $"R30SplitLog_{index + 1:00}");
                log.transform.position = logClusters[index];
                log.transform.rotation = Quaternion.Euler(6f + index % 3 * 4f, 18f + index * 41f, 86f + index % 2 * 7f);
                ScaleToHeight(log, 0.20f + (index % 3) * 0.025f);
            }

            var rockClusters = new[]
            {
                new Vector3(-2.05f, 0.04f, 0.45f), new Vector3(-1.72f, 0.04f, 0.20f),
                new Vector3(1.90f, 0.04f, 0.42f), new Vector3(2.18f, 0.04f, 0.15f),
                new Vector3(-3.30f, 0.04f, -0.35f), new Vector3(3.25f, 0.04f, -0.30f)
            };
            for (var index = 0; index < rockClusters.Length; index++)
            {
                var rock = InstantiateProductionModel(
                    EnvironmentRoot + (index % 2 == 0 ? "/HAVENLINE_Rock_A.obj" : "/HAVENLINE_Rock_B.obj"),
                    root.transform,
                    $"R30CampRock_{index + 1:00}");
                rock.transform.position = rockClusters[index];
                rock.transform.rotation = Quaternion.Euler(0f, 27f + index * 53f, 0f);
                ScaleToHeight(rock, 0.28f + (index % 3) * 0.055f);
            }

            var packs = new[]
            {
                (new Vector3(-5.18f, 0.06f, 0.95f), -18f),
                (new Vector3(5.08f, 0.06f, 0.90f), 24f),
                (new Vector3(-3.25f, 0.06f, 1.95f), 11f)
            };
            for (var index = 0; index < packs.Length; index++)
            {
                var pack = InstantiateProductionModel(
                    PropRoot + "/HAVENLINE_Backpack.obj",
                    root.transform,
                    $"R30CampPack_{index + 1:00}");
                pack.transform.position = packs[index].Item1;
                pack.transform.rotation = Quaternion.Euler(0f, packs[index].Item2, 0f);
                ScaleToHeight(pack, 0.48f);
            }

            var supplyPieces = new[]
            {
                (ResourceRoot + "/HAVENLINE_Metal.obj", new Vector3(-3.18f,0.08f,1.40f), 0.30f, 17f),
                (ResourceRoot + "/HAVENLINE_Fuel.obj", new Vector3(-3.08f,0.08f,1.82f), 0.32f, -21f),
                (ResourceRoot + "/HAVENLINE_Stone.obj", new Vector3(3.08f,0.07f,1.18f), 0.25f, 49f),
                (ResourceRoot + "/HAVENLINE_Stone.obj", new Vector3(3.38f,0.07f,1.02f), 0.22f, 91f)
            };
            for (var index = 0; index < supplyPieces.Length; index++)
            {
                var piece = supplyPieces[index];
                var prop = InstantiateProductionModel(piece.Item1, root.transform, $"R30SupplyDetail_{index + 1:00}");
                prop.transform.position = piece.Item2;
                prop.transform.rotation = Quaternion.Euler(0f, piece.Item4, 0f);
                ScaleToHeight(prop, piece.Item3);
            }
        }

        private static void OpenForeground(IEnumerable<GameObject> objects)
        {
            foreach (var item in objects)
            {
                if (!item.name.StartsWith("QualitySceneryPine_", StringComparison.Ordinal) &&
                    !item.name.StartsWith("HorizonPine_", StringComparison.Ordinal))
                    continue;
                if (item.transform.position.z > -10.5f)
                    continue;

                var position = item.transform.position;
                position.z -= 1.6f;
                item.transform.position = position;
                item.transform.localScale *= 0.92f;
            }
        }

        private static GameObject InstantiateProductionModel(string path, Transform parent, string name)
        {
            var asset = AssetDatabase.LoadAssetAtPath<GameObject>(path);
            if (asset == null)
                throw new FileNotFoundException("HAVENLINE r30 production model is missing.", path);
            var instance = PrefabUtility.InstantiatePrefab(asset, parent) as GameObject;
            if (instance == null)
                throw new InvalidOperationException("Could not instantiate HAVENLINE r30 production model: " + path);
            instance.name = name;
            foreach (var collider in instance.GetComponentsInChildren<Collider>(true))
                UnityEngine.Object.DestroyImmediate(collider);
            return instance;
        }

        private static void ScaleToHeight(GameObject root, float targetHeight)
        {
            var renderers = root.GetComponentsInChildren<Renderer>(true);
            if (renderers.Length == 0)
                return;
            var bounds = renderers[0].bounds;
            for (var index = 1; index < renderers.Length; index++)
                bounds.Encapsulate(renderers[index].bounds);
            if (bounds.size.y > 0.0001f)
                root.transform.localScale *= targetHeight / bounds.size.y;
        }

        private static void SetPose(
            IEnumerable<GameObject> objects,
            string name,
            Vector3 position,
            float yaw)
        {
            var item = objects.FirstOrDefault(candidate => candidate.name == name);
            if (item != null)
                item.transform.SetPositionAndRotation(position, Quaternion.Euler(0f, yaw, 0f));
        }

        private static List<GameObject> AllObjects(Scene scene)
        {
            var result = new List<GameObject>();
            foreach (var root in scene.GetRootGameObjects())
                result.AddRange(root.GetComponentsInChildren<Transform>(true).Select(transform => transform.gameObject));
            return result;
        }
    }
}
