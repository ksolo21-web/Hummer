using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;
using UnityEngine.SceneManagement;

namespace Havenline.Editor
{
    /// <summary>
    /// Final deterministic presentation pass for the shipping scene. This does not replace
    /// HavenlinePremiumVisualPolish; it raises the finished authored scene to the example-game
    /// quality bar with calibrated post FX, probe coverage, layered perimeter dressing and
    /// intentional warm/cold readability lighting. The pass is idempotent and re-saves the
    /// scene so render proof and Android packaging see the exact same presentation.
    /// </summary>
    [InitializeOnLoad]
    internal static class HavenlineExampleGameQualityPass
    {
        internal const string QualityRootName = "HAVENLINE_ExampleGameQualityBar";
        internal const string PostVolumeName = "HAVENLINE_GlobalPostFX";
        internal const string ReflectionProbeName = "HAVENLINE_CampReflectionProbe";
        internal const string LightProbeName = "HAVENLINE_CampLightProbes";
        internal const string ProfilePath =
            "Assets/Havenline/Generated/Premium/PostFX/HAVENLINE_ExampleGame_PostFX.asset";
        internal const int MinimumPineDressing = 18;
        internal const int MinimumRockDressing = 10;
        internal const int MinimumSnowDrifts = 8;
        internal const int MinimumLightProbes = 18;

        private static bool applying;

        static HavenlineExampleGameQualityPass()
        {
            EditorSceneManager.sceneSaved -= OnSceneSaved;
            EditorSceneManager.sceneSaved += OnSceneSaved;
        }

        private static void OnSceneSaved(Scene scene)
        {
            if (applying || !scene.IsValid() ||
                !string.Equals(scene.path, Reference.ScenePath, StringComparison.Ordinal))
                return;

            applying = true;
            try
            {
                Apply(scene);
                EditorSceneManager.MarkSceneDirty(scene);
                EditorSceneManager.SaveScene(scene);
                AssetDatabase.SaveAssets();
            }
            finally
            {
                applying = false;
            }
        }

        [MenuItem("HAVENLINE Premium/Apply Example-Game Quality Pass")]
        private static void ApplyFromMenu()
        {
            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            applying = true;
            try
            {
                Apply(scene);
                EditorSceneManager.MarkSceneDirty(scene);
                EditorSceneManager.SaveScene(scene);
                AssetDatabase.SaveAssets();
            }
            finally
            {
                applying = false;
            }
            HavenlineExampleGameQualityGate.Require(scene);
            Debug.Log("HAVENLINE example-game visual quality pass applied and validated.");
        }

        internal static void Apply(Scene scene)
        {
            HavenlinePremiumVisualAssets.Ensure();
            var root = RebuildQualityRoot(scene);
            ConfigureGlobalPostFx(root.transform);
            BuildProbeRig(root.transform);
            BuildPerimeterDressing(root.transform);
            BuildSnowDepthDressing(root.transform);
            BuildReadabilityLighting(root.transform);
            ConfigureCameraAndAtmosphere(scene);
        }

        private static GameObject RebuildQualityRoot(Scene scene)
        {
            var existing = AllObjects(scene)
                .Where(item => item.name == QualityRootName)
                .ToArray();
            foreach (var old in existing)
                UnityEngine.Object.DestroyImmediate(old);

            var root = new GameObject(QualityRootName);
            SceneManager.MoveGameObjectToScene(root, scene);
            var worldRoot = scene.GetRootGameObjects()
                .FirstOrDefault(item => item.name.Contains("HAVENLINE", StringComparison.OrdinalIgnoreCase));
            if (worldRoot != null)
                root.transform.SetParent(worldRoot.transform, false);
            return root;
        }

        private static void ConfigureGlobalPostFx(Transform parent)
        {
            EnsureFolder("Assets/Havenline/Generated", "Premium");
            EnsureFolder("Assets/Havenline/Generated/Premium", "PostFX");

            var profile = AssetDatabase.LoadAssetAtPath<VolumeProfile>(ProfilePath);
            if (profile == null)
            {
                profile = ScriptableObject.CreateInstance<VolumeProfile>();
                profile.name = "HAVENLINE_ExampleGame_PostFX";
                AssetDatabase.CreateAsset(profile, ProfilePath);
            }

            foreach (var component in profile.components.Where(item => item != null).ToArray())
                UnityEngine.Object.DestroyImmediate(component, true);
            profile.components.Clear();

            var tonemapping = AddProfileComponent<Tonemapping>(profile);
            tonemapping.mode.Override(TonemappingMode.ACES);

            var bloom = AddProfileComponent<Bloom>(profile);
            bloom.threshold.Override(1.05f);
            bloom.intensity.Override(0.19f);

            var color = AddProfileComponent<ColorAdjustments>(profile);
            color.postExposure.Override(0.08f);
            color.contrast.Override(9f);
            color.hueShift.Override(0f);
            color.saturation.Override(6f);
            color.colorFilter.Override(new Color(0.97f, 0.99f, 1f, 1f));

            var whiteBalance = AddProfileComponent<WhiteBalance>(profile);
            whiteBalance.temperature.Override(-6f);
            whiteBalance.tint.Override(2f);

            var vignette = AddProfileComponent<Vignette>(profile);
            vignette.color.Override(new Color(0.005f, 0.016f, 0.028f, 1f));
            vignette.center.Override(new Vector2(0.5f, 0.50f));
            vignette.intensity.Override(0.14f);
            vignette.smoothness.Override(0.30f);
            vignette.rounded.Override(false);

            EditorUtility.SetDirty(profile);

            var volumeObject = new GameObject(PostVolumeName);
            volumeObject.transform.SetParent(parent, false);
            var volume = volumeObject.AddComponent<Volume>();
            volume.isGlobal = true;
            volume.priority = 100f;
            volume.weight = 1f;
            volume.sharedProfile = profile;
        }

        private static T AddProfileComponent<T>(VolumeProfile profile) where T : VolumeComponent
        {
            var component = ScriptableObject.CreateInstance<T>();
            component.name = typeof(T).Name;
            component.active = true;
            AssetDatabase.AddObjectToAsset(component, profile);
            profile.components.Add(component);
            return component;
        }

        private static void BuildProbeRig(Transform parent)
        {
            var reflectionObject = new GameObject(ReflectionProbeName);
            reflectionObject.transform.SetParent(parent, false);
            reflectionObject.transform.localPosition = new Vector3(0f, 2.2f, 0.2f);
            var reflection = reflectionObject.AddComponent<ReflectionProbe>();
            reflection.mode = ReflectionProbeMode.Realtime;
            reflection.refreshMode = ReflectionProbeRefreshMode.OnAwake;
            reflection.resolution = 128;
            reflection.size = new Vector3(26f, 8f, 29f);
            reflection.center = Vector3.zero;
            reflection.intensity = 0.72f;
            reflection.boxProjection = true;
            reflection.hdr = true;

            var probeObject = new GameObject(LightProbeName);
            probeObject.transform.SetParent(parent, false);
            var group = probeObject.AddComponent<LightProbeGroup>();
            var positions = new List<Vector3>();
            foreach (var z in new[] { -5.5f, 0.5f, 6.0f })
            {
                foreach (var x in new[] { -6.0f, 0f, 6.0f })
                {
                    positions.Add(new Vector3(x, 0.65f, z));
                    positions.Add(new Vector3(x, 2.15f, z));
                }
            }
            group.probePositions = positions.ToArray();
        }

        private static void BuildPerimeterDressing(Transform parent)
        {
            var sceneryRoot = new GameObject("HAVENLINE_QualityPerimeterDressing");
            sceneryRoot.transform.SetParent(parent, false);

            var pinePositions = new[]
            {
                new Vector3(-13.1f,0f,12.8f), new Vector3(-10.8f,0f,14.1f),
                new Vector3(-7.6f,0f,14.8f), new Vector3(-3.9f,0f,15.2f),
                new Vector3(3.8f,0f,15.0f), new Vector3(7.4f,0f,14.6f),
                new Vector3(10.7f,0f,13.8f), new Vector3(13.0f,0f,12.2f),
                new Vector3(-13.5f,0f,7.0f), new Vector3(13.4f,0f,6.4f),
                new Vector3(-13.6f,0f,0.5f), new Vector3(13.5f,0f,-0.8f),
                new Vector3(-13.1f,0f,-7.4f), new Vector3(13.0f,0f,-7.8f),
                new Vector3(-11.0f,0f,-12.1f), new Vector3(10.8f,0f,-12.4f),
                new Vector3(-7.0f,0f,-14.6f), new Vector3(7.2f,0f,-14.5f),
                new Vector3(-2.9f,0f,-15.2f), new Vector3(3.1f,0f,-15.1f)
            };
            for (var index = 0; index < pinePositions.Length; index++)
            {
                var path = index % 2 == 0
                    ? "Assets/Havenline/Art/Production/Environment/HAVENLINE_Pine_A.obj"
                    : "Assets/Havenline/Art/Production/Environment/HAVENLINE_Pine_B.obj";
                var pine = InstantiateModel(path, sceneryRoot.transform, $"QualitySceneryPine_{index + 1:00}");
                pine.transform.position = pinePositions[index];
                pine.transform.rotation = Quaternion.Euler(0f, 17f + index * 47f, 0f);
                ScaleToHeight(pine, 4.1f + (index % 5) * 0.34f);
            }

            var rockPositions = new[]
            {
                new Vector3(-11.9f,0f,9.5f), new Vector3(-8.7f,0f,12.1f),
                new Vector3(9.0f,0f,11.6f), new Vector3(11.8f,0f,8.7f),
                new Vector3(-12.0f,0f,4.2f), new Vector3(12.1f,0f,3.7f),
                new Vector3(-11.8f,0f,-4.2f), new Vector3(12.0f,0f,-4.8f),
                new Vector3(-9.1f,0f,-11.4f), new Vector3(9.4f,0f,-11.6f),
                new Vector3(-4.2f,0f,-13.5f), new Vector3(4.7f,0f,-13.4f)
            };
            for (var index = 0; index < rockPositions.Length; index++)
            {
                var path = index % 2 == 0
                    ? "Assets/Havenline/Art/Production/Environment/HAVENLINE_Rock_A.obj"
                    : "Assets/Havenline/Art/Production/Environment/HAVENLINE_Rock_B.obj";
                var rock = InstantiateModel(path, sceneryRoot.transform, $"QualitySceneryRock_{index + 1:00}");
                rock.transform.position = rockPositions[index];
                rock.transform.rotation = Quaternion.Euler(0f, 29f + index * 61f, 0f);
                ScaleToHeight(rock, 0.82f + (index % 4) * 0.15f);
            }
        }

        private static void BuildSnowDepthDressing(Transform parent)
        {
            var root = new GameObject("HAVENLINE_QualitySnowDepth");
            root.transform.SetParent(parent, false);
            var positions = new[]
            {
                new Vector3(-9.6f,0.09f,10.8f), new Vector3(-4.9f,0.09f,12.7f),
                new Vector3(5.2f,0.09f,12.5f), new Vector3(9.8f,0.09f,10.4f),
                new Vector3(-10.5f,0.09f,-8.9f), new Vector3(-5.2f,0.09f,-12.0f),
                new Vector3(5.4f,0.09f,-11.9f), new Vector3(10.3f,0.09f,-9.1f),
                new Vector3(-12.0f,0.09f,1.4f), new Vector3(12.0f,0.09f,1.0f)
            };
            for (var index = 0; index < positions.Length; index++)
            {
                var drift = CreateMeshObject(
                    root.transform,
                    $"QualitySnowDrift_{index + 1:00}",
                    HavenlinePremiumVisualAssets.PathPatchPath,
                    HavenlinePremiumVisualAssets.PaleSnowMaterialPath);
                drift.transform.position = positions[index];
                drift.transform.rotation = Quaternion.Euler(0f, index * 37f, 0f);
                drift.transform.localScale = new Vector3(
                    1.45f + (index % 3) * 0.38f,
                    1f,
                    0.62f + (index % 4) * 0.14f);
            }
        }

        private static void BuildReadabilityLighting(Transform parent)
        {
            CreatePointLight(
                parent,
                "QualityColdRimLight",
                new Vector3(0f, 4.5f, -6.8f),
                new Color(0.27f, 0.59f, 1f),
                1.15f,
                13.5f);
            CreatePointLight(
                parent,
                "QualityWarmCampFill",
                new Vector3(0f, 2.15f, 1.15f),
                new Color(1f, 0.39f, 0.085f),
                1.10f,
                10.5f);
        }

        private static void ConfigureCameraAndAtmosphere(Scene scene)
        {
            var camera = AllObjects(scene)
                .SelectMany(item => item.GetComponents<Camera>())
                .SingleOrDefault(item => item.CompareTag("MainCamera"));
            if (camera != null)
            {
                camera.allowHDR = true;
                camera.allowMSAA = true;
                var data = camera.GetUniversalAdditionalCameraData();
                data.renderPostProcessing = true;
                data.antialiasing = AntialiasingMode.FastApproximateAntialiasing;
                data.stopNaN = true;
                data.dithering = true;
            }

            RenderSettings.fog = true;
            RenderSettings.fogMode = FogMode.ExponentialSquared;
            RenderSettings.fogColor = new Color(0.038f, 0.09f, 0.135f, 1f);
            RenderSettings.fogDensity = 0.0135f;
            RenderSettings.ambientMode = AmbientMode.Trilight;
            RenderSettings.ambientSkyColor = new Color(0.26f, 0.39f, 0.54f, 1f);
            RenderSettings.ambientEquatorColor = new Color(0.105f, 0.19f, 0.28f, 1f);
            RenderSettings.ambientGroundColor = new Color(0.028f, 0.06f, 0.08f, 1f);
            RenderSettings.reflectionIntensity = 0.78f;
        }

        private static GameObject InstantiateModel(string path, Transform parent, string name)
        {
            var asset = AssetDatabase.LoadAssetAtPath<GameObject>(path);
            if (asset == null)
                throw new FileNotFoundException("HAVENLINE quality-bar production model is missing.", path);
            var instance = PrefabUtility.InstantiatePrefab(asset, parent) as GameObject;
            if (instance == null)
                throw new InvalidOperationException("Could not instantiate production dressing asset: " + path);
            instance.name = name;
            return instance;
        }

        private static GameObject CreateMeshObject(
            Transform parent,
            string name,
            string meshPath,
            string materialPath)
        {
            var mesh = AssetDatabase.LoadAssetAtPath<Mesh>(meshPath)
                ?? throw new FileNotFoundException("HAVENLINE quality-bar mesh is missing.", meshPath);
            var material = AssetDatabase.LoadAssetAtPath<Material>(materialPath)
                ?? throw new FileNotFoundException("HAVENLINE quality-bar material is missing.", materialPath);
            var gameObject = new GameObject(name, typeof(MeshFilter), typeof(MeshRenderer));
            gameObject.transform.SetParent(parent, false);
            gameObject.GetComponent<MeshFilter>().sharedMesh = mesh;
            gameObject.GetComponent<MeshRenderer>().sharedMaterial = material;
            return gameObject;
        }

        private static void ScaleToHeight(GameObject root, float targetHeight)
        {
            var renderers = root.GetComponentsInChildren<Renderer>(true);
            if (renderers.Length == 0)
                throw new InvalidOperationException("Quality dressing asset has no renderers: " + root.name);
            var bounds = renderers[0].bounds;
            for (var index = 1; index < renderers.Length; index++)
                bounds.Encapsulate(renderers[index].bounds);
            if (bounds.size.y <= 0.001f)
                throw new InvalidOperationException("Quality dressing asset has invalid bounds: " + root.name);
            root.transform.localScale *= targetHeight / bounds.size.y;
        }

        private static void CreatePointLight(
            Transform parent,
            string name,
            Vector3 position,
            Color color,
            float intensity,
            float range)
        {
            var lightObject = new GameObject(name);
            lightObject.transform.SetParent(parent, false);
            lightObject.transform.localPosition = position;
            var light = lightObject.AddComponent<Light>();
            light.type = LightType.Point;
            light.color = color;
            light.intensity = intensity;
            light.range = range;
            light.shadows = LightShadows.None;
        }

        private static void EnsureFolder(string parent, string name)
        {
            var path = parent + "/" + name;
            if (!AssetDatabase.IsValidFolder(path))
                AssetDatabase.CreateFolder(parent, name);
        }

        private static GameObject[] AllObjects(Scene scene) => scene.GetRootGameObjects()
            .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
            .Select(item => item.gameObject)
            .Distinct()
            .ToArray();
    }

    /// <summary>
    /// Independent release invariant for example-game presentation quality. The normal scene
    /// gate still checks production content and gameplay composition; this gate blocks builds
    /// that lose the visual systems needed for comparable polish/readability.
    /// </summary>
    public sealed class HavenlineExampleGameQualityGate : IProcessSceneWithReport
    {
        public int callbackOrder => 1200;

        public void OnProcessScene(Scene scene, BuildReport report)
        {
            if (!string.Equals(scene.path, Reference.ScenePath, StringComparison.Ordinal))
                return;
            Require(scene);
        }

        [MenuItem("HAVENLINE Premium/Validate Example-Game Quality Bar")]
        private static void ValidateFromMenu()
        {
            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            Require(scene);
            Debug.Log("HAVENLINE example-game presentation quality bar passed structural validation.");
        }

        internal static void Require(Scene scene)
        {
            var failures = Inspect(scene);
            if (failures.Count > 0)
            {
                throw new BuildFailedException(
                    "HAVENLINE example-game visual quality bar blocked the build:\n - " +
                    string.Join("\n - ", failures));
            }
        }

        internal static IReadOnlyList<string> Inspect(Scene scene)
        {
            var failures = new List<string>();
            var objects = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
                .Select(item => item.gameObject)
                .Distinct()
                .ToArray();

            if (objects.Count(item => item.name == HavenlineExampleGameQualityPass.QualityRootName) != 1)
                failures.Add("Shipping scene must contain exactly one example-game quality root.");

            var volume = objects
                .Where(item => item.name == HavenlineExampleGameQualityPass.PostVolumeName)
                .Select(item => item.GetComponent<Volume>())
                .SingleOrDefault(item => item != null);
            if (volume == null || !volume.isGlobal || volume.weight < 0.99f || volume.sharedProfile == null)
            {
                failures.Add("Shipping scene requires the calibrated global HAVENLINE post-processing Volume.");
            }
            else
            {
                if (!volume.sharedProfile.TryGet<Tonemapping>(out var tonemapping) ||
                    !tonemapping.active || tonemapping.mode.value != TonemappingMode.ACES)
                    failures.Add("Post-processing must use active ACES tonemapping.");
                if (!volume.sharedProfile.TryGet<Bloom>(out var bloom) ||
                    !bloom.active || bloom.intensity.value < 0.10f)
                    failures.Add("Post-processing must include controlled emissive bloom.");
                if (!volume.sharedProfile.TryGet<ColorAdjustments>(out var color) ||
                    !color.active || color.contrast.value < 5f)
                    failures.Add("Post-processing must include calibrated contrast/color separation.");
            }

            var reflection = objects
                .Where(item => item.name == HavenlineExampleGameQualityPass.ReflectionProbeName)
                .Select(item => item.GetComponent<ReflectionProbe>())
                .SingleOrDefault(item => item != null);
            if (reflection == null || reflection.resolution < 128 ||
                reflection.size.x < 22f || reflection.size.z < 24f)
                failures.Add("Camp reflection probe coverage is missing or too small.");

            var probes = objects
                .Where(item => item.name == HavenlineExampleGameQualityPass.LightProbeName)
                .Select(item => item.GetComponent<LightProbeGroup>())
                .SingleOrDefault(item => item != null);
            if (probes == null || probes.probePositions == null ||
                probes.probePositions.Length < HavenlineExampleGameQualityPass.MinimumLightProbes)
                failures.Add("Animated characters require dense authored light-probe coverage through the camp.");

            var pineCount = objects.Count(item => item.name.StartsWith("QualitySceneryPine_", StringComparison.Ordinal));
            if (pineCount < HavenlineExampleGameQualityPass.MinimumPineDressing)
                failures.Add($"Perimeter forest dressing is too sparse ({pineCount} pines).");
            var rockCount = objects.Count(item => item.name.StartsWith("QualitySceneryRock_", StringComparison.Ordinal));
            if (rockCount < HavenlineExampleGameQualityPass.MinimumRockDressing)
                failures.Add($"Perimeter rock dressing is too sparse ({rockCount} rocks).");
            var driftCount = objects.Count(item => item.name.StartsWith("QualitySnowDrift_", StringComparison.Ordinal));
            if (driftCount < HavenlineExampleGameQualityPass.MinimumSnowDrifts)
                failures.Add($"Snow surface layering is too sparse ({driftCount} authored drifts).");

            var coldRim = objects.FirstOrDefault(item => item.name == "QualityColdRimLight")?.GetComponent<Light>();
            if (coldRim == null || coldRim.color.b <= coldRim.color.r)
                failures.Add("Shipping lighting requires a cool character/environment rim light.");
            var warmFill = objects.FirstOrDefault(item => item.name == "QualityWarmCampFill")?.GetComponent<Light>();
            if (warmFill == null || warmFill.color.r <= warmFill.color.b)
                failures.Add("Shipping lighting requires a warm camp/furnace fill light.");

            var camera = objects.SelectMany(item => item.GetComponents<Camera>())
                .SingleOrDefault(item => item.CompareTag("MainCamera"));
            if (camera == null)
            {
                failures.Add("Example-game quality gate could not find MainCamera.");
            }
            else
            {
                var data = camera.GetUniversalAdditionalCameraData();
                if (!camera.allowHDR || !camera.allowMSAA || !data.renderPostProcessing || !data.dithering)
                    failures.Add("MainCamera is not configured for the calibrated mobile presentation pipeline.");
            }

            if (!RenderSettings.fog || RenderSettings.fogMode != FogMode.ExponentialSquared)
                failures.Add("Frozen outpost requires authored exponential-squared atmospheric depth.");

            return failures.Distinct().OrderBy(message => message, StringComparer.Ordinal).ToArray();
        }
    }
}
