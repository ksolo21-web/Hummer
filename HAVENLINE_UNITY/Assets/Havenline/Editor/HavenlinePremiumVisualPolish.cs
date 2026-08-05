using System;
using System.Collections.Generic;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

namespace Havenline.Editor
{
    [InitializeOnLoad]
    internal static class HavenlinePremiumVisualPolish
    {
        private const string DressingName = "PremiumVisualDressing";
        private static bool applying;

        static HavenlinePremiumVisualPolish()
        {
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
            var mainCamera = objects.SelectMany(item => item.GetComponents<Camera>())
                .SingleOrDefault(camera => camera.CompareTag("MainCamera"));
            if (mainCamera == null)
                return;

            ConfigureAtmosphere(mainCamera);
            TuneProductionMaterials();
            var dressing = RebuildDressing(scene);
            BuildLayeredGround(dressing.transform);
            BuildCampDetails(dressing.transform);
            BuildFurnaceSilhouette(dressing.transform);
            BuildShelterSilhouettes(dressing.transform);
            TuneWorldLayout(objects);
            TuneLighting(objects, dressing.transform);
            ConfigureInterface(objects, mainCamera);
            ConfigureRenderers(AllObjects(scene));
        }

        private static void ConfigureAtmosphere(Camera camera)
        {
            camera.clearFlags = CameraClearFlags.SolidColor;
            camera.backgroundColor = new Color(0.018f, 0.045f, 0.075f, 1f);
            camera.orthographic = true;
            camera.orthographicSize = Reference.CameraSize;
            camera.allowHDR = true;
            camera.allowMSAA = true;
            camera.nearClipPlane = 0.12f;
            camera.farClipPlane = 150f;
            camera.useOcclusionCulling = true;

            var additional = camera.GetUniversalAdditionalCameraData();
            additional.renderPostProcessing = true;
            additional.antialiasing = AntialiasingMode.FastApproximateAntialiasing;
            additional.stopNaN = true;
            additional.dithering = true;

            RenderSettings.skybox = null;
            RenderSettings.fog = true;
            RenderSettings.fogMode = FogMode.ExponentialSquared;
            RenderSettings.fogColor = new Color(0.035f, 0.085f, 0.13f, 1f);
            RenderSettings.fogDensity = 0.0145f;
            RenderSettings.ambientMode = AmbientMode.Trilight;
            RenderSettings.ambientSkyColor = new Color(0.23f, 0.36f, 0.50f, 1f);
            RenderSettings.ambientEquatorColor = new Color(0.095f, 0.18f, 0.27f, 1f);
            RenderSettings.ambientGroundColor = new Color(0.025f, 0.055f, 0.075f, 1f);
            RenderSettings.reflectionIntensity = 0.72f;

            QualitySettings.antiAliasing = 4;
            QualitySettings.shadows = UnityEngine.ShadowQuality.All;
            QualitySettings.shadowResolution = UnityEngine.ShadowResolution.High;
            QualitySettings.shadowDistance = 48f;
            QualitySettings.shadowCascades = 2;
            QualitySettings.lodBias = Mathf.Max(QualitySettings.lodBias, 1.45f);
        }

        private static GameObject RebuildDressing(Scene scene)
        {
            var existing = AllObjects(scene).FirstOrDefault(item => item.name == DressingName);
            if (existing != null)
                UnityEngine.Object.DestroyImmediate(existing);
            var dressing = new GameObject(DressingName);
            SceneManager.MoveGameObjectToScene(dressing, scene);
            var worldRoot = scene.GetRootGameObjects()
                .FirstOrDefault(root => root.name.Contains("HAVENLINE", StringComparison.OrdinalIgnoreCase));
            if (worldRoot != null)
                dressing.transform.SetParent(worldRoot.transform, false);
            return dressing;
        }

        private static void BuildLayeredGround(Transform parent)
        {
            CreateMeshObject(
                parent,
                "LayeredSnowField",
                HavenlinePremiumVisualAssets.SnowFieldPath,
                HavenlinePremiumVisualAssets.PaleSnowMaterialPath,
                new Vector3(0f, -0.085f, 0f),
                Vector3.one,
                Quaternion.identity);

            CreateMeshObject(
                parent,
                "MainPackedSnowPath",
                HavenlinePremiumVisualAssets.PathPatchPath,
                HavenlinePremiumVisualAssets.SnowPathMaterialPath,
                new Vector3(0f, 0.078f, 3.25f),
                new Vector3(2.15f, 1f, 5.7f),
                Quaternion.Euler(0f, 0f, 0f));
            CreateMeshObject(
                parent,
                "LeftShelterSnowPath",
                HavenlinePremiumVisualAssets.PathPatchPath,
                HavenlinePremiumVisualAssets.SnowPathMaterialPath,
                new Vector3(-3.2f, 0.082f, -1.55f),
                new Vector3(1.5f, 1f, 3.8f),
                Quaternion.Euler(0f, -36f, 0f));
            CreateMeshObject(
                parent,
                "RightShelterSnowPath",
                HavenlinePremiumVisualAssets.PathPatchPath,
                HavenlinePremiumVisualAssets.SnowPathMaterialPath,
                new Vector3(3.2f, 0.083f, -1.6f),
                new Vector3(1.5f, 1f, 3.8f),
                Quaternion.Euler(0f, 36f, 0f));
            CreateMeshObject(
                parent,
                "FurnaceWarmSnow",
                HavenlinePremiumVisualAssets.WarmPatchPath,
                HavenlinePremiumVisualAssets.WarmSnowMaterialPath,
                new Vector3(0f, 0.092f, 0.25f),
                new Vector3(3.25f, 1f, 2.55f),
                Quaternion.identity);
        }

        private static void BuildCampDetails(Transform parent)
        {
            var logPath = "Assets/Havenline/Art/Production/Resources/HAVENLINE_Log.obj";
            for (var index = 0; index < 6; index++)
            {
                var log = InstantiateModel(logPath, parent, $"CampLog_{index + 1:00}");
                log.transform.position = new Vector3(-3.9f + index % 3 * 0.48f, 0.18f + index / 3 * 0.23f, 2.05f);
                log.transform.rotation = Quaternion.Euler(0f, 78f + index * 4f, 2f);
                log.transform.localScale = Vector3.one * 0.72f;
            }

            var stonePath = "Assets/Havenline/Art/Production/Resources/HAVENLINE_Stone.obj";
            for (var index = 0; index < 7; index++)
            {
                var angle = index * Mathf.PI * 2f / 7f;
                var stone = InstantiateModel(stonePath, parent, $"CampfireStone_{index + 1:00}");
                stone.transform.position = new Vector3(
                    3.1f + Mathf.Cos(angle) * 0.86f,
                    0.12f,
                    1.72f + Mathf.Sin(angle) * 0.86f);
                stone.transform.rotation = Quaternion.Euler(0f, index * 47f, 0f);
                stone.transform.localScale = Vector3.one * 0.62f;
            }

            var fuel = InstantiateModel(
                "Assets/Havenline/Art/Production/Resources/HAVENLINE_Fuel.obj",
                parent,
                "FurnaceFuelReserve");
            fuel.transform.position = new Vector3(1.45f, 0.18f, 0.55f);
            fuel.transform.rotation = Quaternion.Euler(0f, -18f, 0f);
            fuel.transform.localScale = Vector3.one * 0.82f;
        }

        private static void BuildFurnaceSilhouette(Transform parent)
        {
            CreateMeshObject(parent, "FurnacePremiumBody",
                HavenlinePremiumVisualAssets.FurnaceBodyPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Metal.mat",
                new Vector3(0f, 0.12f, 0.18f), Vector3.one, Quaternion.identity);
            CreateMeshObject(parent, "FurnacePremiumHood",
                HavenlinePremiumVisualAssets.FurnaceHoodPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_MetalLight.mat",
                new Vector3(0f, 1.86f, 0.18f), Vector3.one, Quaternion.identity);
            CreateMeshObject(parent, "FurnacePremiumChimney",
                HavenlinePremiumVisualAssets.FurnaceChimneyPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Navy.mat",
                new Vector3(0f, 2.36f, 0.05f), Vector3.one, Quaternion.identity);
            CreateMeshObject(parent, "FurnaceDoorFrame",
                HavenlinePremiumVisualAssets.FurnaceBodyPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Amber.mat",
                new Vector3(0f, 0.48f, 1.15f), new Vector3(0.50f, 0.54f, 0.12f), Quaternion.identity);
            CreateMeshObject(parent, "FurnaceGlowCore",
                HavenlinePremiumVisualAssets.FurnaceBodyPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Orange.mat",
                new Vector3(0f, 0.58f, 1.30f), new Vector3(0.38f, 0.39f, 0.055f), Quaternion.identity);
        }

        private static void BuildShelterSilhouettes(Transform parent)
        {
            BuildShelter(parent, "LeftPremiumShelter", new Vector3(-6.25f, 0.02f, -1.15f), 18f, true);
            BuildShelter(parent, "RightPremiumShelter", new Vector3(6.15f, 0.02f, -1.0f), -22f, false);
        }

        private static void BuildShelter(Transform parent, string name, Vector3 position, float yaw, bool left)
        {
            CreateMeshObject(parent, name + "Shell",
                HavenlinePremiumVisualAssets.ShelterShellPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Blue.mat",
                position, Vector3.one, Quaternion.Euler(0f, yaw, 0f));
            CreateMeshObject(parent, name + "SnowCap",
                HavenlinePremiumVisualAssets.ShelterShellPath,
                HavenlinePremiumVisualAssets.PaleSnowMaterialPath,
                position + new Vector3(0f, 0.42f, 0f),
                new Vector3(1.04f, 0.70f, 1.04f), Quaternion.Euler(0f, yaw, 0f));
            var lanternPosition = position + new Vector3(left ? 1.35f : -1.35f, 0.72f, 1.35f);
            CreateMeshObject(parent, name + "Lantern",
                HavenlinePremiumVisualAssets.FurnaceChimneyPath,
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Amber.mat",
                lanternPosition, new Vector3(0.22f, 0.28f, 0.22f), Quaternion.identity);
            CreatePointLight(parent, name + "LanternLight", lanternPosition + Vector3.up * 0.15f,
                new Color(1f, 0.48f, 0.12f), 1.55f, 7.5f, false);
        }

        private static void TuneWorldLayout(IReadOnlyCollection<GameObject> objects)
        {
            SetPose(objects, "StartingTent", new Vector3(-6.25f, 0f, -1.15f), 18f);
            SetPose(objects, "RescueShelter", new Vector3(6.15f, 0f, -1.0f), -22f);
            SetPose(objects, "SupplyStorage", new Vector3(-3.35f, 0f, 1.65f), -16f);
            SetPose(objects, "Campfire", new Vector3(3.15f, 0f, 1.70f), 0f);
            TuneTreeComposition(objects);

            var warmth = objects.FirstOrDefault(item => item.name == "WarmthBoundary");
            if (warmth != null)
                warmth.transform.localPosition = new Vector3(0f, 0.14f, 0f);

            var leftTent = objects.FirstOrDefault(item => item.name == "StartingTent");
            var rightTent = objects.FirstOrDefault(item => item.name == "RescueShelter");
            if (leftTent != null) leftTent.transform.localScale *= 1.12f;
            if (rightTent != null) rightTent.transform.localScale *= 1.12f;
        }

        private static void TuneTreeComposition(IReadOnlyCollection<GameObject> objects)
        {
            var woodPositions = new[]
            {
                new Vector3(-10.6f,0f,7.4f), new Vector3(-11.7f,0f,1.9f),
                new Vector3(10.4f,0f,7.1f), new Vector3(11.8f,0f,1.4f),
                new Vector3(-10.2f,0f,-7.4f), new Vector3(10.8f,0f,-7.0f)
            };
            for (var index = 0; index < woodPositions.Length; index++)
                SetPose(objects, $"WoodNode_{index}", woodPositions[index], 19f + index * 47f);

            var boundaryPositions = new[]
            {
                new Vector3(-13.0f,0f,10.8f), new Vector3(12.7f,0f,9.9f),
                new Vector3(-13.1f,0f,-9.8f), new Vector3(12.8f,0f,-10.3f),
                new Vector3(-5.1f,0f,-13.6f), new Vector3(6.8f,0f,-13.1f)
            };
            for (var index = 0; index < boundaryPositions.Length; index++)
            {
                var pine = objects.FirstOrDefault(item => item.name == $"BoundaryPine_{index}");
                if (pine == null)
                    continue;
                pine.transform.position = boundaryPositions[index];
                pine.transform.rotation = Quaternion.Euler(0f, 31f + index * 61f, 0f);
                pine.transform.localScale *= 0.86f + index % 3 * 0.10f;
            }
        }

        private static void TuneLighting(IReadOnlyCollection<GameObject> objects, Transform parent)
        {
            var key = objects.SelectMany(item => item.GetComponents<Light>())
                .FirstOrDefault(light => light.type == LightType.Directional);
            if (key != null)
            {
                key.transform.rotation = Quaternion.Euler(42f, -28f, 0f);
                key.color = new Color(0.74f, 0.85f, 1f, 1f);
                key.intensity = 1.12f;
                key.shadows = LightShadows.Soft;
                key.shadowStrength = 0.62f;
                key.shadowBias = 0.035f;
                key.shadowNormalBias = 0.32f;
            }

            var furnace = objects.FirstOrDefault(item => item.name == "FurnaceLight")?.GetComponent<Light>();
            if (furnace != null)
            {
                furnace.intensity = 5.4f;
                furnace.range = 14f;
                furnace.color = new Color(1f, 0.30f, 0.055f, 1f);
            }
            var camp = objects.FirstOrDefault(item => item.name == "CampWarmth")?.GetComponent<Light>();
            if (camp != null)
            {
                camp.intensity = 3.8f;
                camp.range = 11f;
                camp.color = new Color(1f, 0.42f, 0.12f, 1f);
            }

            CreatePointLight(parent, "FurnaceBounceLight", new Vector3(0f, 1.2f, 0.4f),
                new Color(1f, 0.25f, 0.04f), 4.2f, 13f, true);
            CreatePointLight(parent, "PlayerReadabilityLight", new Vector3(0f, 3.4f, 6.0f),
                new Color(0.30f, 0.68f, 1f), 1.7f, 10f, false);
            CreatePointLight(parent, "ShelterFillLight", new Vector3(0f, 3.6f, -3.1f),
                new Color(0.22f, 0.49f, 0.78f), 1.5f, 15f, false);
        }

        private static void ConfigureInterface(IReadOnlyCollection<GameObject> objects, Camera camera)
        {
            foreach (var canvas in objects.SelectMany(item => item.GetComponents<Canvas>()).Distinct())
            {
                canvas.renderMode = RenderMode.ScreenSpaceCamera;
                canvas.worldCamera = camera;
                canvas.planeDistance = canvas.name.Contains("Pause", StringComparison.OrdinalIgnoreCase) ? 0.45f : 0.65f;
                canvas.pixelPerfect = false;
                canvas.overrideSorting = true;
                canvas.sortingOrder = canvas.name.Contains("Pause", StringComparison.OrdinalIgnoreCase) ? 40 : 20;
                EditorUtility.SetDirty(canvas);
            }

            foreach (var scaler in objects.SelectMany(item => item.GetComponents<CanvasScaler>()))
            {
                scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
                scaler.referenceResolution = new Vector2(1920f, 1080f);
                scaler.screenMatchMode = CanvasScaler.ScreenMatchMode.MatchWidthOrHeight;
                scaler.matchWidthOrHeight = 0.5f;
            }

            foreach (var image in objects.SelectMany(item => item.GetComponents<Image>()))
            {
                if (image.name.Contains("Panel", StringComparison.OrdinalIgnoreCase))
                {
                    var color = image.color;
                    color.a = Mathf.Clamp(color.a, 0.68f, 0.84f);
                    image.color = color;
                }
            }

            foreach (var text in objects.SelectMany(item => item.GetComponents<Text>()))
            {
                text.fontStyle = FontStyle.Normal;
                text.resizeTextForBestFit = false;
                text.fontSize = 24;
                text.horizontalOverflow = HorizontalWrapMode.Overflow;
                text.verticalOverflow = VerticalWrapMode.Overflow;
                text.lineSpacing = 0.90f;
                text.raycastTarget = false;
                text.color = new Color(0.94f, 0.98f, 1f, 1f);
            }
        }

        private static void ConfigureRenderers(IReadOnlyCollection<GameObject> objects)
        {
            foreach (var renderer in objects.SelectMany(item => item.GetComponents<Renderer>()).Distinct())
            {
                renderer.shadowCastingMode = ShadowCastingMode.On;
                renderer.receiveShadows = true;
                renderer.lightProbeUsage = LightProbeUsage.BlendProbes;
                renderer.reflectionProbeUsage = ReflectionProbeUsage.BlendProbes;
            }
        }

        private static void TuneProductionMaterials()
        {
            TuneMaterial(
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Snow.mat",
                new Color(0.91f, 0.965f, 1f, 1f),
                0.20f,
                false);
            TuneMaterial(
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Ice.mat",
                new Color(0.055f, 0.20f, 0.32f, 1f),
                0.76f,
                false);
            TuneMaterial(
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Blue.mat",
                new Color(0.055f, 0.25f, 0.43f, 1f),
                0.38f,
                false);
            TuneMaterial(
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Orange.mat",
                new Color(1f, 0.28f, 0.035f, 1f),
                0.43f,
                true);
            TuneMaterial(
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Amber.mat",
                new Color(1f, 0.62f, 0.10f, 1f),
                0.44f,
                true);
            TuneMaterial(
                "Assets/Havenline/Art/Production/Materials/HAVENLINE_Warmth.mat",
                new Color(1f, 0.26f, 0.04f, 0.18f),
                0.2f,
                true);
        }

        private static void TuneMaterial(string path, Color color, float smoothness, bool emissive)
        {
            var material = AssetDatabase.LoadAssetAtPath<Material>(path);
            if (material == null)
                return;
            if (material.HasProperty("_BaseColor")) material.SetColor("_BaseColor", color);
            if (material.HasProperty("_Color")) material.SetColor("_Color", color);
            if (material.HasProperty("_Smoothness")) material.SetFloat("_Smoothness", smoothness);
            if (emissive && material.HasProperty("_EmissionColor"))
            {
                material.SetColor("_EmissionColor", new Color(color.r, color.g, color.b, 1f) * 1.35f);
                material.EnableKeyword("_EMISSION");
            }
            EditorUtility.SetDirty(material);
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
            var gameObject = new GameObject(name, typeof(MeshFilter), typeof(MeshRenderer));
            gameObject.transform.SetParent(parent, false);
            gameObject.transform.localPosition = position;
            gameObject.transform.localRotation = rotation;
            gameObject.transform.localScale = scale;
            gameObject.GetComponent<MeshFilter>().sharedMesh = AssetDatabase.LoadAssetAtPath<Mesh>(meshPath);
            gameObject.GetComponent<MeshRenderer>().sharedMaterial = AssetDatabase.LoadAssetAtPath<Material>(materialPath);
            return gameObject;
        }

        private static GameObject InstantiateModel(string path, Transform parent, string name)
        {
            var asset = AssetDatabase.LoadAssetAtPath<GameObject>(path);
            if (asset == null)
                throw new InvalidOperationException("HAVENLINE premium scene detail model failed to load: " + path);
            var instance = PrefabUtility.InstantiatePrefab(asset, parent) as GameObject;
            if (instance == null)
                throw new InvalidOperationException("HAVENLINE premium scene detail model failed to instantiate: " + path);
            instance.name = name;
            return instance;
        }

        private static void SetPose(
            IEnumerable<GameObject> objects,
            string name,
            Vector3 position,
            float yaw)
        {
            var gameObject = objects.FirstOrDefault(item => item.name == name);
            if (gameObject == null)
                return;
            gameObject.transform.position = position;
            gameObject.transform.rotation = Quaternion.Euler(0f, yaw, 0f);
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
            light.shadowStrength = 0.48f;
        }

        private static GameObject[] AllObjects(Scene scene) => scene.GetRootGameObjects()
            .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
            .Select(transform => transform.gameObject)
            .Distinct()
            .ToArray();
    }
}
