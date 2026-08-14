using System;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.CompilerServices;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

namespace Havenline.Editor
{
    /// <summary>
    /// Final presentation pass for the frozen outpost. This runs after the deterministic
    /// production studio and replaces the rejected circular test-arena composition with a
    /// close, layered, inhabited survival camp while preserving the complete gameplay loop.
    /// </summary>
    [InitializeOnLoad]
    internal static class ZZHavenlineReferenceGradeVisualRebuild
    {
        private const string RootName = "ReferenceGradeVisualRebuild";
        private const string ProductionRoot = "Assets/Havenline/Art/Production";
        private const string EnvironmentRoot = ProductionRoot + "/Environment";
        private const string StructureRoot = ProductionRoot + "/Structures";
        private const string ResourceRoot = ProductionRoot + "/Resources";
        private const string PropRoot = ProductionRoot + "/Props";
        private const string MaterialRoot = ProductionRoot + "/Materials";

        private static bool applying;

        static ZZHavenlineReferenceGradeVisualRebuild()
        {
            // Ensure the existing polish pass subscribes first. This pass intentionally owns
            // the final authored frame and must therefore run last on every shipping-scene save.
            RuntimeHelpers.RunClassConstructor(typeof(HavenlinePremiumVisualPolish).TypeHandle);
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
            HavenlinePremiumVisualAssets.Ensure();
            var objects = AllObjects(scene);
            var camera = objects.SelectMany(item => item.GetComponents<Camera>())
                .SingleOrDefault(candidate => candidate.CompareTag("MainCamera"));
            if (camera == null)
                return;

            var existing = objects.FirstOrDefault(item => item.name == RootName);
            if (existing != null)
                UnityEngine.Object.DestroyImmediate(existing);

            // Never continue through a scene snapshot containing the root we just destroyed.
            // Recursive scene saves from the example-game quality pass re-enter this authoring
            // path, and Unity's destroyed-object sentinels will throw as soon as name/components
            // are touched. Refreshing here keeps the pass idempotent instead of hiding the error.
            objects = AllObjects(scene);

            var root = new GameObject(RootName);
            SceneManager.MoveGameObjectToScene(root, scene);
            var worldRoot = scene.GetRootGameObjects()
                .FirstOrDefault(item => item.name.Contains("HAVENLINE", StringComparison.OrdinalIgnoreCase));
            if (worldRoot != null)
                root.transform.SetParent(worldRoot.transform, false);

            ConfigureCameraAndAtmosphere(camera);
            SuppressRejectedArenaVisuals(objects);
            TuneCoreMaterials();
            BuildOpenWorldDressing(root.transform);
            RecomposeExistingCamp(objects);
            UpgradeShelters(root.transform, objects);
            ReplaceFurnaceSilhouettes(objects);
            BuildLivedInCampDetails(root.transform);
            TuneActors(objects);
            TuneLighting(root.transform, objects);
            RestyleHud(AllObjects(scene));
            ConfigureRenderers(AllObjects(scene));
        }

        private static void ConfigureCameraAndAtmosphere(Camera camera)
        {
            camera.orthographic = true;
            camera.orthographicSize = Reference.CameraSize;
            camera.clearFlags = CameraClearFlags.SolidColor;
            camera.backgroundColor = new Color(0.055f, 0.105f, 0.155f, 1f);
            camera.allowHDR = true;
            camera.allowMSAA = true;
            camera.nearClipPlane = 0.12f;
            camera.farClipPlane = 170f;
            camera.useOcclusionCulling = true;

            if (camera.GetComponent<HavenlineAdaptiveCameraFraming>() == null)
                camera.gameObject.AddComponent<HavenlineAdaptiveCameraFraming>();

            var additional = camera.GetUniversalAdditionalCameraData();
            additional.renderPostProcessing = true;
            additional.antialiasing = AntialiasingMode.FastApproximateAntialiasing;
            additional.stopNaN = true;
            additional.dithering = true;

            RenderSettings.skybox = null;
            RenderSettings.fog = true;
            RenderSettings.fogMode = FogMode.ExponentialSquared;
            RenderSettings.fogColor = new Color(0.16f, 0.25f, 0.34f, 1f);
            RenderSettings.fogDensity = 0.0065f;
            RenderSettings.ambientMode = AmbientMode.Trilight;
            RenderSettings.ambientSkyColor = new Color(0.46f, 0.58f, 0.72f, 1f);
            RenderSettings.ambientEquatorColor = new Color(0.25f, 0.36f, 0.48f, 1f);
            RenderSettings.ambientGroundColor = new Color(0.12f, 0.17f, 0.23f, 1f);
            RenderSettings.reflectionIntensity = 0.86f;

            QualitySettings.antiAliasing = 4;
            QualitySettings.shadowDistance = 44f;
            QualitySettings.shadowCascades = 2;
            QualitySettings.lodBias = Mathf.Max(QualitySettings.lodBias, 1.35f);

            foreach (var volume in UnityEngine.Object.FindObjectsByType<Volume>(
                         FindObjectsInactive.Include, FindObjectsSortMode.None))
            {
                var profile = volume.sharedProfile;
                if (profile == null)
                    continue;
                if (profile.TryGet(out ColorAdjustments color))
                {
                    color.postExposure.Override(0.28f);
                    color.contrast.Override(7f);
                    color.saturation.Override(6f);
                }
                if (profile.TryGet(out Bloom bloom))
                {
                    bloom.intensity.Override(0.28f);
                    bloom.threshold.Override(1.05f);
                }
                if (profile.TryGet(out Vignette vignette))
                    vignette.intensity.Override(0.09f);
            }
        }

        private static void SuppressRejectedArenaVisuals(IEnumerable<GameObject> objects)
        {
            foreach (var item in objects)
            {
                if (item == null)
                    continue;
                var hide = item.name is "IceShelf" or "SnowIsland" or "WarmthBoundary" or
                           "MainPackedSnowPath" or "LeftShelterSnowPath" or "RightShelterSnowPath" ||
                           item.name.StartsWith("HeatedSnow_", StringComparison.Ordinal) ||
                           item.name.StartsWith("SnowBank_", StringComparison.Ordinal) ||
                           item.name.StartsWith("HudAccent_", StringComparison.Ordinal);
                if (!hide)
                    continue;
                foreach (var renderer in item.GetComponentsInChildren<Renderer>(true))
                    renderer.enabled = false;
                foreach (var graphic in item.GetComponentsInChildren<Graphic>(true))
                    graphic.enabled = false;
            }

            var thaw = objects.FirstOrDefault(item => item != null && item.name == "FurnaceWarmSnow");
            if (thaw != null)
            {
                thaw.transform.localPosition = new Vector3(0f, 0.075f, 0.22f);
                thaw.transform.localScale = new Vector3(1.78f, 1f, 1.18f);
            }
        }

        private static void BuildOpenWorldDressing(Transform parent)
        {
            var pathPatches = new[]
            {
                (new Vector3(0f,0.092f,4.55f), new Vector3(1.25f,1f,2.65f), 2f),
                (new Vector3(-2.15f,0.094f,2.75f), new Vector3(1.05f,1f,1.80f), -28f),
                (new Vector3(2.20f,0.095f,2.60f), new Vector3(1.05f,1f,1.72f), 31f),
                (new Vector3(-4.15f,0.096f,0.40f), new Vector3(0.95f,1f,1.65f), -54f),
                (new Vector3(4.10f,0.097f,0.20f), new Vector3(0.92f,1f,1.62f), 55f),
                (new Vector3(0f,0.098f,-2.15f), new Vector3(1.12f,1f,1.70f), -3f)
            };
            for (var index = 0; index < pathPatches.Length; index++)
            {
                var patch = pathPatches[index];
                CreateMeshObject(parent, $"PackedSnowTrail_{index + 1:00}",
                    HavenlinePremiumVisualAssets.PathPatchPath,
                    HavenlinePremiumVisualAssets.SnowPathMaterialPath,
                    patch.Item1, patch.Item2, Quaternion.Euler(0f, patch.Item3, 0f));
            }

            var pines = new[]
            {
                (new Vector3(-11.8f,0f,-8.6f), 6.2f, 18f),
                (new Vector3(-8.8f,0f,-10.9f), 5.7f, 74f),
                (new Vector3(-5.3f,0f,-11.9f), 6.5f, 131f),
                (new Vector3(5.1f,0f,-12.0f), 6.3f, 213f),
                (new Vector3(8.9f,0f,-10.7f), 5.8f, 282f),
                (new Vector3(11.8f,0f,-8.2f), 6.4f, 337f),
                (new Vector3(-13.1f,0f,-3.8f), 5.4f, 42f),
                (new Vector3(13.2f,0f,-3.4f), 5.6f, 156f),
                (new Vector3(-12.7f,0f,4.5f), 5.1f, 239f),
                (new Vector3(12.8f,0f,4.2f), 5.2f, 309f)
            };
            for (var index = 0; index < pines.Length; index++)
            {
                var pine = pines[index];
                CloneModel(EnvironmentRoot + (index % 2 == 0 ? "/HAVENLINE_Pine_A.obj" : "/HAVENLINE_Pine_B.obj"),
                    parent, $"HorizonPine_{index + 1:00}", pine.Item1, pine.Item2, pine.Item3);
            }

            var cliffs = new[]
            {
                (new Vector3(-10.0f,-0.08f,-11.9f), 3.2f, 17f),
                (new Vector3(-2.7f,-0.10f,-13.0f), 3.7f, 79f),
                (new Vector3(3.0f,-0.10f,-13.1f), 3.5f, 136f),
                (new Vector3(10.1f,-0.08f,-11.7f), 3.3f, 221f),
                (new Vector3(-13.2f,-0.12f,0.5f), 2.8f, 286f),
                (new Vector3(13.1f,-0.12f,0.2f), 2.9f, 344f)
            };
            for (var index = 0; index < cliffs.Length; index++)
            {
                var cliff = cliffs[index];
                CloneModel(EnvironmentRoot + $"/HAVENLINE_Cliff_{index % 4 + 1:00}.obj",
                    parent, $"HorizonCliff_{index + 1:00}", cliff.Item1, cliff.Item2, cliff.Item3);
            }
        }

        private static void RecomposeExistingCamp(IReadOnlyCollection<GameObject> objects)
        {
            SetPose(objects, "SupplyStorage", new Vector3(-3.55f, 0f, 1.25f), -12f);
            SetPose(objects, "Campfire", new Vector3(3.35f, 0f, 1.45f), 7f);
            SetPose(objects, "FrozenSurvivor", new Vector3(5.55f, 0f, -1.15f), -18f);

            var woodPositions = new[]
            {
                new Vector3(-8.4f,0f,5.0f), new Vector3(-10.3f,0f,1.5f),
                new Vector3(8.4f,0f,4.7f), new Vector3(10.4f,0f,1.1f),
                new Vector3(-8.7f,0f,-5.4f), new Vector3(8.9f,0f,-5.2f)
            };
            for (var index = 0; index < woodPositions.Length; index++)
                SetPose(objects, $"WoodNode_{index}", woodPositions[index], 23f + index * 49f);

            var stonePositions = new[]
            {
                new Vector3(-5.6f,0f,7.0f), new Vector3(5.8f,0f,6.9f),
                new Vector3(-9.9f,0f,-2.8f), new Vector3(10.1f,0f,-2.6f)
            };
            for (var index = 0; index < stonePositions.Length; index++)
                SetPose(objects, $"StoneNode_{index}", stonePositions[index], 31f + index * 71f);
        }

        private static void UpgradeShelters(Transform parent, IReadOnlyCollection<GameObject> objects)
        {
            var left = objects.FirstOrDefault(item => item != null && item.name == "LeftPremiumShelter");
            var right = objects.FirstOrDefault(item => item != null && item.name == "RightPremiumShelter");
            if (left != null)
            {
                left.transform.position = new Vector3(-5.75f, 0f, -1.40f);
                left.transform.rotation = Quaternion.Euler(0f, 18f, 0f);
                AddShelterDetail(left.transform, true);
            }
            if (right != null)
            {
                right.transform.position = new Vector3(5.75f, 0f, -1.30f);
                right.transform.rotation = Quaternion.Euler(0f, -20f, 0f);
                AddShelterDetail(right.transform, false);
            }

            CloneModel(StructureRoot + "/HAVENLINE_Storage.obj", parent, "LeftShelterSupplies",
                new Vector3(-7.10f, 0f, 0.85f), 1.10f, 18f);
            CloneModel(StructureRoot + "/HAVENLINE_Storage.obj", parent, "RightShelterSupplies",
                new Vector3(7.05f, 0f, 0.65f), 1.02f, -24f);
        }

        private static void AddShelterDetail(Transform shelter, bool left)
        {
            var side = left ? 1f : -1f;
            CreateMeshObject(shelter, "ReferenceEntryCanopy",
                HavenlinePremiumVisualAssets.FurnaceHoodPath,
                HavenlinePremiumVisualAssets.PaleSnowMaterialPath,
                new Vector3(0f, 1.45f, 1.78f), new Vector3(0.48f, 0.12f, 0.58f),
                Quaternion.Euler(-10f, 0f, 0f));

            for (var index = -2; index <= 2; index++)
            {
                CreateMeshObject(shelter, $"ReferenceRoofRib_{index + 3}",
                    HavenlinePremiumVisualAssets.FurnaceChimneyPath,
                    MaterialRoot + "/HAVENLINE_WoodLight.mat",
                    new Vector3(index * 0.66f, 1.35f + Mathf.Abs(index) * 0.18f, 0f),
                    new Vector3(0.075f, 1.86f, 0.075f),
                    Quaternion.Euler(90f, 0f, index * 2.5f));
            }

            var lanternPosition = new Vector3(side * 1.22f, 0.92f, 1.58f);
            CreateMeshObject(shelter, "ReferenceLantern",
                HavenlinePremiumVisualAssets.FurnaceChimneyPath,
                MaterialRoot + "/HAVENLINE_Amber.mat",
                lanternPosition, new Vector3(0.16f, 0.22f, 0.16f), Quaternion.identity);
            CreatePointLight(shelter, "ReferenceLanternLight", lanternPosition + Vector3.up * 0.20f,
                new Color(1f, 0.52f, 0.16f), 1.25f, 5.8f, false);
        }

        private static void ReplaceFurnaceSilhouettes(IReadOnlyCollection<GameObject> objects)
        {
            var heights = new[] { 3.05f, 3.65f, 4.25f, 4.90f };
            for (var level = 1; level <= 4; level++)
            {
                var stage = objects.SingleOrDefault(item => item != null && item.name == $"FurnaceLevel{level}");
                if (stage == null)
                    continue;

                foreach (var renderer in stage.GetComponentsInChildren<Renderer>(true))
                    renderer.enabled = false;

                var assembly = CloneModel(StructureRoot + $"/HAVENLINE_Furnace_L{level}.obj",
                    stage.transform, $"ReferenceFurnaceAssemblyL{level}", Vector3.zero,
                    heights[level - 1], 180f, true);
                assembly.transform.localPosition = new Vector3(0f, 0.02f, 0f);

                CreateMeshObject(stage.transform, "ReferenceMachinePlinth",
                    HavenlinePremiumVisualAssets.FurnaceBodyPath,
                    HavenlinePremiumVisualAssets.FurnaceDarkMaterialPath,
                    new Vector3(0f, 0.02f, 0f),
                    new Vector3(0.48f + level * 0.055f, 0.12f, 0.48f + level * 0.055f),
                    Quaternion.identity);
                CreateMeshObject(stage.transform, "ReferenceDoorSurround",
                    HavenlinePremiumVisualAssets.FurnaceBodyPath,
                    MaterialRoot + "/HAVENLINE_Amber.mat",
                    new Vector3(0f, 0.72f + level * 0.055f, 0.78f + level * 0.06f),
                    new Vector3(0.23f + level * 0.025f, 0.25f + level * 0.02f, 0.042f),
                    Quaternion.identity);
                CreateMeshObject(stage.transform, "ReferenceFireboxWindow",
                    HavenlinePremiumVisualAssets.FurnaceBodyPath,
                    MaterialRoot + "/HAVENLINE_Orange.mat",
                    new Vector3(0f, 0.78f + level * 0.055f, 0.88f + level * 0.06f),
                    new Vector3(0.17f + level * 0.022f, 0.17f + level * 0.018f, 0.028f),
                    Quaternion.identity);

                for (var side = -1; side <= 1; side += 2)
                {
                    CreateMeshObject(stage.transform, side < 0 ? "ReferenceLeftPipe" : "ReferenceRightPipe",
                        HavenlinePremiumVisualAssets.FurnaceChimneyPath,
                        HavenlinePremiumVisualAssets.FurnaceSteelMaterialPath,
                        new Vector3(side * (0.82f + level * 0.16f), 0.70f + level * 0.11f, -0.05f),
                        new Vector3(0.19f, 0.72f + level * 0.10f, 0.19f), Quaternion.identity);
                }
            }
        }

        private static void BuildLivedInCampDetails(Transform parent)
        {
            for (var index = 0; index < 8; index++)
            {
                var log = CloneModel(ResourceRoot + "/HAVENLINE_Log.obj", parent,
                    $"SplitLogStack_{index + 1:00}",
                    new Vector3(-3.95f + index % 4 * 0.38f, 0.16f + index / 4 * 0.22f, 2.08f),
                    0.25f, 82f + index * 5f);
                log.transform.rotation *= Quaternion.Euler(0f, 0f, 90f);
            }

            for (var index = 0; index < 4; index++)
            {
                CloneModel(PropRoot + $"/HAVENLINE_SupplyDebris_{index + 1:00}.obj", parent,
                    $"CampSupplyCluster_{index + 1:00}",
                    new Vector3(-2.65f + index * 1.78f, 0f, -4.35f - (index % 2) * 0.42f),
                    0.82f + index * 0.06f, -18f + index * 37f);
            }

            for (var index = 0; index < 5; index++)
            {
                var angle = index * Mathf.PI * 2f / 5f;
                CloneModel(EnvironmentRoot + (index % 2 == 0 ? "/HAVENLINE_Rock_A.obj" : "/HAVENLINE_Rock_B.obj"),
                    parent, $"FurnaceFoundationStone_{index + 1:00}",
                    new Vector3(Mathf.Cos(angle) * 1.55f, 0f, 0.22f + Mathf.Sin(angle) * 1.30f),
                    0.58f, index * 67f);
            }
        }

        private static void TuneActors(IEnumerable<GameObject> objects)
        {
            var player = objects.FirstOrDefault(item => item != null && item.name == "PlayerVisual");
            if (player != null)
                player.transform.localScale *= 1.12f;
            var survivor = objects.FirstOrDefault(item => item != null && item.name == "SurvivorVisual");
            if (survivor != null)
                survivor.transform.localScale *= 1.08f;
        }

        private static void TuneLighting(Transform parent, IEnumerable<GameObject> objects)
        {
            var liveObjects = objects.Where(item => item != null).ToArray();
            var key = liveObjects.SelectMany(item => item.GetComponents<Light>())
                .FirstOrDefault(light => light.type == LightType.Directional);
            if (key != null)
            {
                key.transform.rotation = Quaternion.Euler(48f, -30f, 0f);
                key.color = new Color(0.84f, 0.91f, 1f, 1f);
                key.intensity = 1.18f;
                key.shadows = LightShadows.Soft;
                key.shadowStrength = 0.52f;
                key.shadowBias = 0.04f;
                key.shadowNormalBias = 0.30f;
            }

            var furnace = liveObjects.FirstOrDefault(item => item.name == "FurnaceLight")?.GetComponent<Light>();
            if (furnace != null)
            {
                furnace.intensity = 3.25f;
                furnace.range = 11.5f;
                furnace.color = new Color(1f, 0.42f, 0.11f, 1f);
            }

            CreatePointLight(parent, "ReferenceFurnaceBounce", new Vector3(0f, 1.45f, 0.55f),
                new Color(1f, 0.36f, 0.09f), 2.25f, 10.5f, true);
            CreatePointLight(parent, "ReferenceCampReadability", new Vector3(0f, 5.8f, 3.8f),
                new Color(0.46f, 0.70f, 0.94f), 2.35f, 17f, false);
            CreatePointLight(parent, "ReferenceHorizonFill", new Vector3(0f, 6.5f, -7.5f),
                new Color(0.30f, 0.52f, 0.78f), 2.10f, 20f, false);
        }

        private static void RestyleHud(IEnumerable<GameObject> objects)
        {
            var objectArray = objects.Where(item => item != null).ToArray();
            ConfigurePanel(objectArray, "ResourcesPanel", new Vector2(0f, 1f),
                new Vector2(26f, -24f), new Vector2(430f, 72f));
            ConfigurePanel(objectArray, "ObjectivePanel", new Vector2(0.5f, 1f),
                new Vector2(0f, -24f), new Vector2(500f, 68f));
            ConfigurePanel(objectArray, "FurnacePanel", new Vector2(1f, 1f),
                new Vector2(-26f, -24f), new Vector2(255f, 72f));
            ConfigurePanel(objectArray, "ContextPanel", new Vector2(0.5f, 0f),
                new Vector2(0f, 24f), new Vector2(450f, 70f));

            var joystick = objectArray.FirstOrDefault(item => item.name == "JoystickBase")?.GetComponent<Image>();
            if (joystick != null)
            {
                joystick.color = new Color(0.10f, 0.30f, 0.43f, 0.54f);
                SetRect(joystick.rectTransform, new Vector2(0f, 0f), new Vector2(112f, 104f), new Vector2(154f, 154f));
            }
            var knob = objectArray.FirstOrDefault(item => item.name == "JoystickKnob")?.GetComponent<Image>();
            if (knob != null)
            {
                knob.color = new Color(0.72f, 0.89f, 1f, 0.78f);
                SetRect(knob.rectTransform, new Vector2(0.5f, 0.5f), Vector2.zero, new Vector2(62f, 62f));
            }
            var warmth = objectArray.FirstOrDefault(item => item.name == "WarmthIndicator")?.GetComponent<Image>();
            if (warmth != null)
            {
                warmth.color = new Color(1f, 0.31f, 0.055f, 0.88f);
                SetRect(warmth.rectTransform, new Vector2(1f, 0f), new Vector2(-108f, 104f), new Vector2(116f, 116f));
            }

            foreach (var accent in objectArray.Where(item => item.name.StartsWith("HudAccent_", StringComparison.Ordinal)))
                accent.SetActive(false);

            foreach (var text in objectArray.SelectMany(item => item.GetComponents<Text>()))
            {
                text.fontStyle = FontStyle.Normal;
                text.fontSize = text.name == "WarmthText" ? 18 : 21;
                text.resizeTextForBestFit = true;
                text.resizeTextMinSize = 15;
                text.resizeTextMaxSize = text.name == "ObjectiveText" ? 23 : 22;
                text.horizontalOverflow = HorizontalWrapMode.Wrap;
                text.verticalOverflow = VerticalWrapMode.Truncate;
                text.lineSpacing = 0.94f;
                text.color = new Color(0.95f, 0.985f, 1f, 1f);
                text.raycastTarget = false;

                var shadow = text.GetComponent<Shadow>() ?? text.gameObject.AddComponent<Shadow>();
                shadow.effectColor = new Color(0f, 0.04f, 0.08f, 0.72f);
                shadow.effectDistance = new Vector2(1.5f, -1.5f);
                shadow.useGraphicAlpha = true;
            }
        }

        private static void ConfigurePanel(
            IEnumerable<GameObject> objects,
            string name,
            Vector2 anchor,
            Vector2 position,
            Vector2 size)
        {
            var image = objects.FirstOrDefault(item => item.name == name)?.GetComponent<Image>();
            if (image == null)
                return;
            image.sprite = HavenlineStudioUiAssets.Resolve(name);
            image.type = Image.Type.Sliced;
            var alpha = name is "ResourcesPanel" or "ObjectivePanel" or "FurnacePanel" ? 1f : 0.86f;
            image.color = new Color(0.025f, 0.085f, 0.135f, alpha);
            SetRect(image.rectTransform, anchor, position, size);
        }

        private static void TuneCoreMaterials()
        {
            TuneMaterial(MaterialRoot + "/HAVENLINE_Snow.mat",
                new Color(0.82f, 0.90f, 0.97f, 1f), 0.20f, 0f);
            TuneMaterial(HavenlinePremiumVisualAssets.PaleSnowMaterialPath,
                new Color(0.90f, 0.95f, 0.99f, 1f), 0.18f, 0f);
            TuneMaterial(HavenlinePremiumVisualAssets.SnowPathMaterialPath,
                new Color(0.70f, 0.82f, 0.90f, 1f), 0.24f, 0f);
            TuneMaterial(HavenlinePremiumVisualAssets.ThawedSnowMaterialPath,
                new Color(0.68f, 0.76f, 0.78f, 1f), 0.20f, 0f);
            TuneMaterial(MaterialRoot + "/HAVENLINE_Pine.mat",
                new Color(0.055f, 0.27f, 0.25f, 1f), 0.18f, 0f);
            TuneMaterial(MaterialRoot + "/HAVENLINE_PineLight.mat",
                new Color(0.10f, 0.39f, 0.34f, 1f), 0.19f, 0f);
            TuneMaterial(MaterialRoot + "/HAVENLINE_Orange.mat",
                new Color(1f, 0.27f, 0.035f, 1f), 0.40f, 1.45f);
            TuneMaterial(MaterialRoot + "/HAVENLINE_Amber.mat",
                new Color(1f, 0.58f, 0.10f, 1f), 0.42f, 1.20f);
        }

        private static void TuneMaterial(string path, Color color, float smoothness, float emission)
        {
            var material = AssetDatabase.LoadAssetAtPath<Material>(path);
            if (material == null)
                return;
            if (material.HasProperty("_BaseColor")) material.SetColor("_BaseColor", color);
            if (material.HasProperty("_Color")) material.SetColor("_Color", color);
            if (material.HasProperty("_Smoothness")) material.SetFloat("_Smoothness", smoothness);
            if (emission > 0f && material.HasProperty("_EmissionColor"))
            {
                material.SetColor("_EmissionColor", color * emission);
                material.EnableKeyword("_EMISSION");
            }
            EditorUtility.SetDirty(material);
        }

        private static GameObject CloneModel(
            string path,
            Transform parent,
            string name,
            Vector3 position,
            float targetHeight,
            float yaw,
            bool localPosition = false)
        {
            var asset = AssetDatabase.LoadAssetAtPath<GameObject>(path);
            if (asset == null)
                throw new InvalidOperationException("HAVENLINE reference-grade model failed to load: " + path);

            var root = new GameObject(name);
            root.transform.SetParent(parent, false);
            if (localPosition)
                root.transform.localPosition = position;
            else
                root.transform.position = position;
            root.transform.localRotation = Quaternion.Euler(0f, yaw, 0f);
            CopyModelNode(asset.transform, root.transform, true);

            var renderers = root.GetComponentsInChildren<Renderer>(true);
            if (renderers.Length > 0)
            {
                var bounds = renderers[0].bounds;
                for (var index = 1; index < renderers.Length; index++)
                    bounds.Encapsulate(renderers[index].bounds);
                if (bounds.size.y > 0.001f)
                    root.transform.localScale *= targetHeight / bounds.size.y;
            }
            return root;
        }

        private static void CopyModelNode(Transform source, Transform destination, bool copyRootComponents)
        {
            if (copyRootComponents)
                CopyRenderComponents(source.gameObject, destination.gameObject);

            foreach (Transform child in source)
            {
                var clone = new GameObject(child.name);
                clone.transform.SetParent(destination, false);
                clone.transform.localPosition = child.localPosition;
                clone.transform.localRotation = child.localRotation;
                clone.transform.localScale = child.localScale;
                CopyRenderComponents(child.gameObject, clone);
                CopyModelNode(child, clone.transform, false);
            }
        }

        private static void CopyRenderComponents(GameObject source, GameObject destination)
        {
            var sourceFilter = source.GetComponent<MeshFilter>();
            var sourceRenderer = source.GetComponent<MeshRenderer>();
            if (sourceFilter == null || sourceRenderer == null)
                return;

            var filter = destination.AddComponent<MeshFilter>();
            filter.sharedMesh = sourceFilter.sharedMesh;
            var renderer = destination.AddComponent<MeshRenderer>();
            renderer.sharedMaterials = sourceRenderer.sharedMaterials;
            renderer.shadowCastingMode = ShadowCastingMode.On;
            renderer.receiveShadows = true;
            renderer.lightProbeUsage = LightProbeUsage.BlendProbes;
            renderer.reflectionProbeUsage = ReflectionProbeUsage.BlendProbes;
        }

        private static GameObject CreateMeshObject(
            Transform parent,
            string name,
            string meshPath,
            string materialPath,
            Vector3 position,
            Vector3 scale,
            Quaternion rotation)
        {
            var mesh = AssetDatabase.LoadAssetAtPath<Mesh>(meshPath);
            var material = AssetDatabase.LoadAssetAtPath<Material>(materialPath);
            if (mesh == null)
                throw new InvalidOperationException("HAVENLINE reference-grade mesh failed to load: " + meshPath);
            if (material == null)
                throw new InvalidOperationException("HAVENLINE reference-grade material failed to load: " + materialPath);

            var item = new GameObject(name, typeof(MeshFilter), typeof(MeshRenderer));
            item.transform.SetParent(parent, false);
            item.transform.localPosition = position;
            item.transform.localScale = scale;
            item.transform.localRotation = rotation;
            item.GetComponent<MeshFilter>().sharedMesh = mesh;
            var renderer = item.GetComponent<MeshRenderer>();
            renderer.sharedMaterial = material;
            renderer.shadowCastingMode = ShadowCastingMode.On;
            renderer.receiveShadows = true;
            renderer.lightProbeUsage = LightProbeUsage.BlendProbes;
            renderer.reflectionProbeUsage = ReflectionProbeUsage.BlendProbes;
            return item;
        }

        private static void ConfigureRenderers(IEnumerable<GameObject> objects)
        {
            foreach (var renderer in objects.Where(item => item != null)
                         .SelectMany(item => item.GetComponents<Renderer>()).Distinct())
            {
                var flame = renderer.GetComponentInParent<HavenlineFlamePulse>() != null;
                renderer.shadowCastingMode = flame ? ShadowCastingMode.Off : ShadowCastingMode.On;
                renderer.receiveShadows = !flame;
                renderer.lightProbeUsage = LightProbeUsage.BlendProbes;
                renderer.reflectionProbeUsage = flame
                    ? ReflectionProbeUsage.Off
                    : ReflectionProbeUsage.BlendProbes;
            }
        }

        private static void SetPose(IEnumerable<GameObject> objects, string name, Vector3 position, float yaw)
        {
            var item = objects.FirstOrDefault(candidate => candidate != null && candidate.name == name);
            if (item == null)
                return;
            item.transform.position = position;
            item.transform.rotation = Quaternion.Euler(0f, yaw, 0f);
        }

        private static void CreatePointLight(
            Transform parent,
            string name,
            Vector3 position,
            Color color,
            float intensity,
            float range,
            bool shadows)
        {
            var lightObject = new GameObject(name);
            lightObject.transform.SetParent(parent, false);
            lightObject.transform.localPosition = position;
            var light = lightObject.AddComponent<Light>();
            light.type = LightType.Point;
            light.color = color;
            light.intensity = intensity;
            light.range = range;
            light.shadows = shadows ? LightShadows.Soft : LightShadows.None;
            light.shadowStrength = 0.42f;
        }

        private static void SetRect(RectTransform rect, Vector2 anchor, Vector2 position, Vector2 size)
        {
            rect.anchorMin = rect.anchorMax = anchor;
            rect.pivot = anchor;
            rect.anchoredPosition = position;
            rect.sizeDelta = size;
        }

        private static GameObject[] AllObjects(Scene scene) => scene.GetRootGameObjects()
            .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
            .Select(transform => transform.gameObject)
            .Distinct()
            .ToArray();
    }
}
