using System;
using System.IO;
using System.Linq;
using System.Runtime.CompilerServices;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;
using UnityEngine.SceneManagement;

namespace Havenline.Editor
{
    /// <summary>
    /// R32's source meshes are persistent assets, but the premium build pipeline deliberately
    /// re-authors the shipping scene from an empty scene immediately before validation/build.
    /// This final save hook reapplies the human-review R32 scene dressing after that re-author so
    /// the APK and six-frame render proof cannot silently fall back to the pre-R32 composition.
    ///
    /// The hook is fail-closed: it only arms once the generated R32 shelter-rib asset exists.
    /// Therefore the base deterministic studio/EditMode review remains independent of R32, while
    /// CI production preparation and every later shipping-scene save retain R32.
    /// </summary>
    [InitializeOnLoad]
    internal static class ZZZZZHavenlineR32ShippingSceneFinalizer
    {
        private const string ProductionRoot = "Assets/Havenline/Art/Production";
        private const string StructureRoot = ProductionRoot + "/Structures";
        private const string EnvironmentRoot = ProductionRoot + "/Environment";
        private const string PropsRoot = ProductionRoot + "/Props";
        private const string ResourceRoot = ProductionRoot + "/Resources";
        private const string MaterialRoot = ProductionRoot + "/Materials";
        private const string ShelterRibPath = EnvironmentRoot + "/Premium/HAVENLINE_R32ShelterRib.asset";
        private const string DressingRootName = "HAVENLINE_R32CampDressing";
        private const string RibRootName = "R32StructuralRibs";

        private static bool applying;

        static ZZZZZHavenlineR32ShippingSceneFinalizer()
        {
            // Force all earlier scene-composition save hooks to subscribe first. R32 must be the
            // last world-composition writer so R30 can never overwrite the final camp layout.
            RuntimeHelpers.RunClassConstructor(typeof(ZZZHavenlineR30PremiumComposition).TypeHandle);
            EditorSceneManager.sceneSaving -= OnSceneSaving;
            EditorSceneManager.sceneSaving += OnSceneSaving;
        }

        private static void OnSceneSaving(Scene scene, string path)
        {
            if (applying || !string.Equals(path, Reference.ScenePath, StringComparison.Ordinal))
                return;
            if (AssetDatabase.LoadAssetAtPath<Mesh>(ShelterRibPath) == null)
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
            var oldRoot = objects.FirstOrDefault(item => item != null && item.name == DressingRootName);
            if (oldRoot != null)
                UnityEngine.Object.DestroyImmediate(oldRoot);

            var root = new GameObject(DressingRootName);
            SceneManager.MoveGameObjectToScene(root, scene);
            var shippingRoot = scene.GetRootGameObjects()
                .FirstOrDefault(item => item.name == HavenlineCoreCrewScenePostprocessor.ShippingRootName);
            if (shippingRoot != null)
                root.transform.SetParent(shippingRoot.transform, false);

            AddShelterRibs(scene);
            AddPerimeterDressing(root.transform);
            VaryPineSilhouettes(scene);
            TuneAtmosphere(scene);
        }

        private static void AddShelterRibs(Scene scene)
        {
            var ribMesh = AssetDatabase.LoadAssetAtPath<Mesh>(ShelterRibPath);
            var ribMaterial = AssetDatabase.LoadAssetAtPath<Material>(MaterialRoot + "/HAVENLINE_MetalLight.mat");
            if (ribMesh == null || ribMaterial == null)
                throw new InvalidOperationException("R32 shipping finalizer is armed but its shelter-rib mesh/material is missing.");

            foreach (var shelterName in new[] { "LeftPremiumShelter", "RightPremiumShelter" })
            {
                var shelter = AllObjects(scene).FirstOrDefault(item => item != null && item.name == shelterName);
                if (shelter == null)
                    throw new InvalidOperationException("R32 shipping finalizer cannot find premium shelter: " + shelterName);

                var existing = shelter.transform.Find(RibRootName);
                if (existing != null)
                    UnityEngine.Object.DestroyImmediate(existing.gameObject);

                var ribRoot = new GameObject(RibRootName);
                ribRoot.transform.SetParent(shelter.transform, false);
                var depthPositions = new[] { -1.42f, -0.72f, 0f, 0.72f, 1.42f };
                for (var index = 0; index < depthPositions.Length; index++)
                {
                    var rib = new GameObject($"ShelterRib_{index + 1:00}");
                    rib.transform.SetParent(ribRoot.transform, false);
                    rib.transform.localPosition = new Vector3(0f, 0.015f, depthPositions[index]);
                    var filter = rib.AddComponent<MeshFilter>();
                    filter.sharedMesh = ribMesh;
                    var renderer = rib.AddComponent<MeshRenderer>();
                    renderer.sharedMaterial = ribMaterial;
                    renderer.shadowCastingMode = ShadowCastingMode.On;
                    renderer.receiveShadows = true;
                }
            }
        }

        private static void AddPerimeterDressing(Transform parent)
        {
            var barricades = new[]
            {
                (new Vector3(-7.65f,0.02f,3.10f), 72f, 0.78f),
                (new Vector3(-5.25f,0.02f,5.15f), 35f, 0.82f),
                (new Vector3(-2.15f,0.02f,6.15f), 10f, 0.86f),
                (new Vector3(2.15f,0.02f,6.10f), -10f, 0.86f),
                (new Vector3(5.25f,0.02f,5.10f), -35f, 0.82f),
                (new Vector3(7.60f,0.02f,3.05f), -72f, 0.78f)
            };
            for (var index = 0; index < barricades.Length; index++)
            {
                var item = InstantiateProductionModel(
                    StructureRoot + "/HAVENLINE_Barricade.obj",
                    parent,
                    $"R32PerimeterBarricade_{index + 1:00}");
                item.transform.SetPositionAndRotation(
                    barricades[index].Item1,
                    Quaternion.Euler(0f, barricades[index].Item2, 0f));
                item.transform.localScale *= barricades[index].Item3;
            }

            var debris = new[]
            {
                (new Vector3(-4.95f,0.04f,2.22f), -24f),
                (new Vector3(-3.45f,0.04f,2.42f), 18f),
                (new Vector3(3.55f,0.04f,2.35f), -12f),
                (new Vector3(4.90f,0.04f,2.18f), 29f)
            };
            for (var index = 0; index < debris.Length; index++)
            {
                var item = InstantiateProductionModel(
                    PropsRoot + $"/HAVENLINE_SupplyDebris_{index + 1:00}.obj",
                    parent,
                    $"R32SupplyDebris_{index + 1:00}");
                item.transform.SetPositionAndRotation(
                    debris[index].Item1,
                    Quaternion.Euler(0f, debris[index].Item2, 0f));
                item.transform.localScale *= 0.82f + index * 0.04f;
            }

            for (var index = 0; index < 6; index++)
            {
                var path = index % 2 == 0
                    ? ResourceRoot + "/HAVENLINE_Log.obj"
                    : EnvironmentRoot + "/HAVENLINE_Rock_B.obj";
                var angle = index * Mathf.PI * 2f / 6f + 0.38f;
                var radius = 4.4f + (index % 3) * 0.35f;
                var item = InstantiateProductionModel(path, parent, $"R32CampEdgeDetail_{index + 1:00}");
                item.transform.position = new Vector3(
                    Mathf.Cos(angle) * radius,
                    0.04f,
                    2.25f + Mathf.Sin(angle) * 1.55f);
                item.transform.rotation = Quaternion.Euler(
                    index % 2 == 0 ? 82f : 0f,
                    21f + index * 47f,
                    0f);
                item.transform.localScale *= index % 2 == 0 ? 0.78f : 0.62f;
            }
        }

        private static void VaryPineSilhouettes(Scene scene)
        {
            var pines = AllObjects(scene)
                .Where(item => item != null &&
                               item.name.Contains("Pine", StringComparison.OrdinalIgnoreCase) &&
                               item.GetComponentsInChildren<Renderer>(true).Length > 0)
                .OrderBy(item => item.name, StringComparer.Ordinal)
                .ToArray();

            for (var index = 0; index < pines.Length; index++)
            {
                var pine = pines[index];
                // Apply from authored world scale once per save. The marker component-free naming
                // check below prevents multiplicative growth during recursive save callbacks.
                if (pine.name.EndsWith("_R32Varied", StringComparison.Ordinal))
                    continue;

                var variation = ((index * 13 + 7) % 11) / 10f;
                var scale = pine.transform.localScale;
                scale.x *= Mathf.Lerp(0.91f, 1.09f, variation);
                scale.z *= Mathf.Lerp(1.07f, 0.93f, variation);
                scale.y *= Mathf.Lerp(0.94f, 1.10f, ((index * 7 + 3) % 9) / 8f);
                pine.transform.localScale = scale;
                pine.transform.rotation *= Quaternion.Euler(
                    (index % 3 - 1) * 1.6f,
                    17f + (index * 41) % 137,
                    (index % 5 - 2) * 0.7f);
                pine.name += "_R32Varied";
            }
        }

        private static void TuneAtmosphere(Scene scene)
        {
            RenderSettings.ambientIntensity = Mathf.Min(RenderSettings.ambientIntensity, 0.82f);
            RenderSettings.ambientLight = new Color(0.31f, 0.38f, 0.47f);
            if (RenderSettings.fog)
                RenderSettings.fogColor = new Color(0.34f, 0.43f, 0.51f);

            foreach (var volume in AllObjects(scene).SelectMany(item => item.GetComponents<Volume>()))
            {
                var profile = volume.sharedProfile;
                if (profile == null)
                    continue;
                if (profile.TryGet<ColorAdjustments>(out var color))
                {
                    color.postExposure.Override(-0.16f);
                    color.contrast.Override(16f);
                    color.saturation.Override(5f);
                }
                if (profile.TryGet<Bloom>(out var bloom))
                {
                    bloom.intensity.Override(0.28f);
                    bloom.threshold.Override(1.03f);
                    bloom.scatter.Override(0.52f);
                }
            }
        }

        private static GameObject InstantiateProductionModel(string path, Transform parent, string name)
        {
            var asset = AssetDatabase.LoadAssetAtPath<GameObject>(path);
            if (asset == null)
                throw new FileNotFoundException("HAVENLINE R32 shipping model is missing.", path);
            var instance = PrefabUtility.InstantiatePrefab(asset, parent) as GameObject;
            if (instance == null)
                throw new InvalidOperationException("Could not instantiate HAVENLINE R32 shipping model: " + path);
            instance.name = name;
            foreach (var collider in instance.GetComponentsInChildren<Collider>(true))
                UnityEngine.Object.DestroyImmediate(collider);
            return instance;
        }

        private static GameObject[] AllObjects(Scene scene) => scene.GetRootGameObjects()
            .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
            .Select(item => item.gameObject)
            .Distinct()
            .ToArray();
    }
}
