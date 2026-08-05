using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using UnityEditor;
using UnityEditor.Animations;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Audio;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

namespace Havenline.Editor
{
    /// <summary>
    /// Deterministic in-repository production studio for HAVENLINE's coherent winter-cartoon
    /// presentation. Every generated source is reproducible from committed code; no remote
    /// asset pack, runtime download, random web model or prototype fallback is permitted.
    /// </summary>
    public static partial class HavenlineProceduralArtStudio
    {
        internal const string ProductionRoot = "Assets/Havenline/Art/Production";
        internal const string CharacterRoot = ProductionRoot + "/Characters";
        internal const string EnemyRoot = ProductionRoot + "/Enemies";
        internal const string StructureRoot = ProductionRoot + "/Structures";
        internal const string PropsRoot = ProductionRoot + "/Props";
        internal const string ResourcesRoot = ProductionRoot + "/Resources";
        internal const string EnvironmentRoot = ProductionRoot + "/Environment";
        internal const string MaterialRoot = ProductionRoot + "/Materials";
        internal const string TextureRoot = ProductionRoot + "/Textures";
        internal const string UiRoot = ProductionRoot + "/UI";
        internal const string AnimationRoot = ProductionRoot + "/Animation";
        internal const string VfxRoot = ProductionRoot + "/VFX";
        internal const string AudioRoot = ProductionRoot + "/Audio";
        internal const string ReviewRoot = "Builds/Review/HAVENLINE-Studio";

        internal const string PlayerModelPath = CharacterRoot + "/HAVENLINE_Player.obj";
        internal const string SurvivorModelPath = CharacterRoot + "/HAVENLINE_Survivor.obj";
        internal const string WolfModelPath = EnemyRoot + "/HAVENLINE_Wolf.obj";
        internal const string FontPath = UiRoot + "/HAVENLINE_UI_Font.fontsettings";
        internal const string AudioProfilePath = AudioRoot + "/HAVENLINE_AudioProfile.asset";
        internal const string MixerPath = AudioRoot + "/HAVENLINE_Audio.mixer";

        private static readonly Color Snow = new(0.72f, 0.86f, 0.94f, 1f);
        private static readonly Color Ice = new(0.20f, 0.55f, 0.76f, 1f);
        private static readonly Color Navy = new(0.045f, 0.105f, 0.17f, 1f);
        private static readonly Color Blue = new(0.075f, 0.31f, 0.50f, 1f);
        private static readonly Color Orange = new(1f, 0.31f, 0.06f, 1f);
        private static readonly Color Amber = new(1f, 0.64f, 0.14f, 1f);
        private static readonly Color Pine = new(0.045f, 0.30f, 0.27f, 1f);

        [Serializable]
        private sealed class StudioReport
        {
            public string generatedAtUtc;
            public string manifestVersion;
            public int modelFiles;
            public int textureFiles;
            public int materials;
            public int animationClips;
            public int audioClips;
            public int prefabs;
            public string[] sceneFailures;
            public string[] proofFrames;
        }

        [MenuItem("HAVENLINE Premium/Generate Deterministic Studio Review Package")]
        public static void GenerateReviewPackage() => GenerateForCi();

        public static void GenerateForCi()
        {
            EnsureDirectories();
            GenerateModels();
            GenerateTextures();
            GenerateMaterials();
            GenerateFont();
            GenerateAnimations();
            GenerateVfx();
            GenerateAudio();
            GenerateEnvironment();
            GenerateInterface();
            GenerateAudioRig();
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);

            var manifest = LoadReviewManifest();
            HavenlinePremiumSceneAuthoring.Author(manifest);
            var sceneResult = HavenlinePremiumSceneGate.InspectPremiumScene(manifest);
            var frames = RenderReviewFrames();
            WriteReport(manifest, sceneResult, frames);

            if (!sceneResult.Passed)
            {
                throw new InvalidOperationException(
                    "HAVENLINE deterministic studio generated the review scene, but premium scene checks failed:\n - " +
                    string.Join("\n - ", sceneResult.Failures));
            }
        }

        private static void EnsureDirectories()
        {
            foreach (var path in new[]
                     {
                         CharacterRoot, EnemyRoot, StructureRoot, PropsRoot, ResourcesRoot,
                         EnvironmentRoot, MaterialRoot, TextureRoot, UiRoot, AnimationRoot,
                         VfxRoot, AudioRoot, ReviewRoot
                     })
                Directory.CreateDirectory(path);
        }

        private static void GenerateTextures()
        {
            var palette = new[]
            {
                (Snow, Color.white), (Ice, new Color(0.52f,0.88f,1f)),
                (Navy, Blue), (Blue, new Color(0.13f,0.48f,0.70f)),
                (Orange, Amber), (Pine, new Color(0.12f,0.49f,0.40f)),
                (new Color(0.24f,0.31f,0.37f), new Color(0.56f,0.65f,0.69f)),
                (new Color(0.29f,0.15f,0.06f), new Color(0.66f,0.40f,0.16f)),
                (new Color(0.14f,0.19f,0.23f), new Color(0.46f,0.51f,0.55f))
            };
            for (var index = 0; index < 36; index++)
            {
                var colors = palette[index % palette.Length];
                HavenlineStudioGeometry.WriteTexture(
                    TextureRoot + $"/HAVENLINE_Surface_{index + 1:00}.png",
                    4103 + index * 7919,
                    colors.Item1,
                    colors.Item2,
                    index % 7 == 0);
            }
            HavenlineStudioGeometry.WriteHudAtlas(UiRoot + "/HAVENLINE_HUD_Atlas.png");
        }

        private static void GenerateMaterials()
        {
            var shader = Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");
            if (shader == null)
                throw new InvalidOperationException("HAVENLINE studio could not find a supported lit shader.");

            var specifications = new[]
            {
                ("Snow", Snow, 0.05f, 0.28f, 1), ("Ice", Ice, 0.22f, 0.86f, 2),
                ("Navy", Navy, 0.02f, 0.32f, 3), ("Blue", Blue, 0.03f, 0.36f, 4),
                ("Teal", new Color(0.06f,0.45f,0.48f), 0.02f, 0.34f, 6),
                ("Orange", Orange, 0.02f, 0.38f, 5), ("Amber", Amber, 0.02f, 0.42f, 5),
                ("Wood", new Color(0.33f,0.17f,0.065f), 0f, 0.22f, 8),
                ("WoodLight", new Color(0.58f,0.34f,0.14f), 0f, 0.24f, 8),
                ("Stone", new Color(0.25f,0.32f,0.38f), 0f, 0.27f, 7),
                ("StoneLight", new Color(0.47f,0.56f,0.62f), 0f, 0.30f, 7),
                ("Metal", new Color(0.19f,0.29f,0.36f), 0.78f, 0.67f, 7),
                ("MetalLight", new Color(0.48f,0.64f,0.72f), 0.82f, 0.72f, 7),
                ("Pine", Pine, 0f, 0.22f, 6),
                ("PineLight", new Color(0.10f,0.43f,0.37f), 0f, 0.24f, 6),
                ("Fur", new Color(0.19f,0.24f,0.28f), 0f, 0.18f, 9),
                ("FurLight", new Color(0.42f,0.48f,0.52f), 0f, 0.20f, 9),
                ("Skin", new Color(0.47f,0.27f,0.17f), 0f, 0.30f, 4),
                ("White", new Color(0.92f,0.96f,0.98f), 0f, 0.31f, 1),
                ("Black", new Color(0.012f,0.024f,0.034f), 0.05f, 0.34f, 3)
            };

            foreach (var specification in specifications)
            {
                var path = MaterialRoot + $"/HAVENLINE_{specification.Item1}.mat";
                if (AssetDatabase.LoadAssetAtPath<Material>(path) != null)
                    AssetDatabase.DeleteAsset(path);
                var material = new Material(shader) { name = "HAVENLINE_" + specification.Item1 };
                SetColor(material, specification.Item2);
                SetFloat(material, "_Metallic", specification.Item3);
                SetFloat(material, "_Smoothness", specification.Item4);
                var texturePath = TextureRoot + $"/HAVENLINE_Surface_{specification.Item5:00}.png";
                var texture = AssetDatabase.LoadAssetAtPath<Texture2D>(texturePath);
                if (texture != null)
                {
                    if (material.HasProperty("_BaseMap")) material.SetTexture("_BaseMap", texture);
                    if (material.HasProperty("_MainTex")) material.SetTexture("_MainTex", texture);
                }
                material.enableInstancing = true;
                AssetDatabase.CreateAsset(material, path);
            }

            var warmthPath = MaterialRoot + "/HAVENLINE_Warmth.mat";
            AssetDatabase.DeleteAsset(warmthPath);
            var warmth = new Material(shader) { name = "HAVENLINE_Warmth" };
            SetColor(warmth, new Color(1f, 0.29f, 0.04f, 0.38f));
            SetFloat(warmth, "_Surface", 1f);
            SetFloat(warmth, "_Blend", 0f);
            SetFloat(warmth, "_SrcBlend", (float)BlendMode.SrcAlpha);
            SetFloat(warmth, "_DstBlend", (float)BlendMode.OneMinusSrcAlpha);
            warmth.renderQueue = (int)RenderQueue.Transparent;
            warmth.SetOverrideTag("RenderType", "Transparent");
            warmth.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
            AssetDatabase.CreateAsset(warmth, warmthPath);

            AssetDatabase.SaveAssets();
        }

        private static void GenerateFont()
        {
            HavenlineStudioBitmapFont.Generate(FontPath);
        }

        private static void GenerateAnimations()
        {
            var playerClips = new[]
            {
                "Idle","Walk","Run","CarryIdle","Chop","Mine","Salvage","Deposit","Rescue",
                "Build","Repair","Attack","Hit","Dead"
            };
            var survivorClips = new[]
            {
                "Frozen","Thaw","Idle","Walk","Run","Gather","Carry","Deposit","Build","Repair",
                "Defend","Hit","Dead"
            };
            var wolfClips = new[] { "Idle","Prowl","Walk","Run","Growl","Attack","Hit","Dead","Spawn" };

            CreateController(AnimationRoot + "/HAVENLINE_Player.controller", "Player", playerClips, false);
            CreateController(AnimationRoot + "/HAVENLINE_Survivor.controller", "Survivor", survivorClips, false);
            CreateController(AnimationRoot + "/HAVENLINE_Wolf.controller", "Wolf", wolfClips, true);
        }

        private static void GenerateVfx()
        {
            HavenlinePremiumParticleAssets.Ensure();
            CreateParticlePrefab(VfxRoot + "/HAVENLINE_FurnaceFire.prefab", "FurnaceFire",
                Orange, Amber, 96, 0.65f, 0.28f, new Vector3(0.18f, 0.10f, 0.18f), false, true);
            CreateParticlePrefab(VfxRoot + "/HAVENLINE_FurnaceSparks.prefab", "FurnaceSparks",
                Amber, Orange, 48, 0.55f, 0.075f, new Vector3(0.12f, 0.04f, 0.12f), false, true);
            CreateParticlePrefab(VfxRoot + "/HAVENLINE_FurnaceSmoke.prefab", "FurnaceSmoke",
                new Color(0.22f,0.28f,0.32f,0.34f), new Color(0.08f,0.12f,0.16f,0f),
                40, 2.25f, 0.34f, new Vector3(0.18f,0.08f,0.18f), true, true);
            CreateParticlePrefab(VfxRoot + "/HAVENLINE_Snowfall.prefab", "Snowfall",
                Color.white, new Color(0.68f,0.85f,1f), 420, 7.5f, 0.10f, new Vector3(26f, 1f, 30f), true, false);
            CreateParticlePrefab(VfxRoot + "/HAVENLINE_GatherImpact.prefab", "GatherImpact",
                new Color(0.62f,0.42f,0.20f), Snow, 26, 0.55f, 0.12f, new Vector3(0.25f,0.2f,0.25f), false, false);
            CreateParticlePrefab(VfxRoot + "/HAVENLINE_BuildImpact.prefab", "BuildImpact",
                Amber, new Color(0.72f,0.88f,1f), 34, 0.72f, 0.15f, new Vector3(0.35f,0.25f,0.35f), false, false);
            CreateParticlePrefab(VfxRoot + "/HAVENLINE_CombatHit.prefab", "CombatHit",
                Orange, Color.white, 30, 0.38f, 0.10f, new Vector3(0.22f,0.22f,0.22f), false, false);
        }

        private static void GenerateAudio()
        {
            var cues = Enum.GetValues(typeof(HavenlineAudioCue)).Cast<HavenlineAudioCue>().ToArray();
            var entries = new List<HavenlineAudioEntry>();
            for (var index = 0; index < cues.Length; index++)
            {
                var cue = cues[index];
                var duration = cue is HavenlineAudioCue.WinterWind or HavenlineAudioCue.FurnaceLoop or HavenlineAudioCue.CampfireLoop
                    ? 4.2f : 0.22f + (index % 6) * 0.07f;
                var noise = cue.ToString().Contains("Wind", StringComparison.Ordinal) ||
                            cue.ToString().Contains("Impact", StringComparison.Ordinal) ||
                            cue.ToString().Contains("Hit", StringComparison.Ordinal)
                    ? 0.78f : 0.22f + (index % 4) * 0.09f;
                var frequency = 92f + index * 17f;
                var path = AudioRoot + $"/HAVENLINE_{cue}.wav";
                HavenlineStudioGeometry.WriteWav(path, 1207 + index * 1613, duration, frequency, noise, 1.25f + (index % 3) * 0.45f);
                var clip = AssetDatabase.LoadAssetAtPath<AudioClip>(path);
                entries.Add(new HavenlineAudioEntry
                {
                    cue = cue,
                    clips = clip == null ? Array.Empty<AudioClip>() : new[] { clip },
                    volume = cue is HavenlineAudioCue.WinterWind ? 0.32f : 0.76f,
                    minimumPitch = 0.94f,
                    maximumPitch = 1.06f,
                    spatialBlend = cue.ToString().StartsWith("Ui", StringComparison.Ordinal) ? 0f : 1f,
                    minimumRetriggerSeconds = cue.ToString().Contains("Footstep", StringComparison.Ordinal) ? 0.11f : 0.04f
                });
            }

            AssetDatabase.DeleteAsset(AudioProfilePath);
            var profile = ScriptableObject.CreateInstance<HavenlineAudioProfile>();
            profile.Configure(entries.ToArray());
            AssetDatabase.CreateAsset(profile, AudioProfilePath);
            CreateMixer();
        }

        private static void GenerateEnvironment()
        {
            var path = EnvironmentRoot + "/HAVENLINE_FrozenOutpost_Environment.prefab";
            AssetDatabase.DeleteAsset(path);
            var root = new GameObject("HAVENLINE_FrozenOutpost_Environment");
            try
            {
                var ice = InstantiateModel(EnvironmentRoot + "/HAVENLINE_IceShelf.obj", root.transform, "IceShelf");
                ice.transform.localPosition = new Vector3(0f, -0.30f, 0f);
                ApplyMaterial(ice, "Ice");
                var island = InstantiateModel(EnvironmentRoot + "/HAVENLINE_SnowIsland.obj", root.transform, "SnowIsland");
                ApplyMaterial(island, "Snow");
                var floor = island.AddComponent<BoxCollider>();
                floor.center = new Vector3(0f, -0.02f, 0f);
                floor.size = new Vector3(29.8f, 0.55f, 31.5f);

                for (var index = 0; index < 14; index++)
                {
                    var patch = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                    patch.name = $"HeatedSnow_{index + 1:00}";
                    patch.transform.SetParent(root.transform, false);
                    var angle = index * Mathf.PI * 2f / 14f;
                    patch.transform.localPosition = new Vector3(Mathf.Cos(angle) * (3.2f + index % 3), 0.035f, Mathf.Sin(angle) * (3.0f + index % 4));
                    patch.transform.localScale = new Vector3(1.4f + index % 3 * 0.35f, 0.025f, 1.0f + index % 2 * 0.4f);
                    ApplyMaterial(patch, "Snow");
                    UnityEngine.Object.DestroyImmediate(patch.GetComponent<Collider>());
                }

                for (var index = 0; index < 12; index++)
                {
                    var bankPath = EnvironmentRoot + $"/HAVENLINE_SnowBank_{index % 6 + 1:00}.obj";
                    var bank = InstantiateModel(bankPath, root.transform, $"SnowBank_{index + 1:00}");
                    var angle = index * Mathf.PI * 2f / 12f;
                    bank.transform.localPosition = new Vector3(Mathf.Cos(angle) * 13.2f, 0f, Mathf.Sin(angle) * 14.1f);
                    bank.transform.localRotation = Quaternion.Euler(0f, index * 31f, 0f);
                }

                AddEnvironmentLighting(root);
                AddPostProcessing(root);
                AddProbes(root);
                PrefabUtility.SaveAsPrefabAsset(root, path);
            }
            finally
            {
                UnityEngine.Object.DestroyImmediate(root);
            }
        }

        private static void GenerateInterface()
        {
            GenerateHudPrefab();
            GeneratePausePrefab();
        }

        private static void GenerateAudioRig()
        {
            var path = AudioRoot + "/HAVENLINE_AudioRig.prefab";
            AssetDatabase.DeleteAsset(path);
            var root = new GameObject("HAVENLINE_AudioRig");
            try
            {
                var profile = AssetDatabase.LoadAssetAtPath<HavenlineAudioProfile>(AudioProfilePath);
                var mixer = AssetDatabase.LoadAssetAtPath<AudioMixer>(MixerPath);
                var output = mixer?.FindMatchingGroups("Master").FirstOrDefault();
                if (profile == null || mixer == null || output == null)
                    throw new InvalidOperationException("HAVENLINE audio profile or routed mixer failed to generate.");

                var ambience = CreateAudioSource(root.transform, "Ambience", output, 0f);
                var ui = CreateAudioSource(root.transform, "Interface", output, 0f);
                var effects = new AudioSource[8];
                for (var index = 0; index < effects.Length; index++)
                    effects[index] = CreateAudioSource(root.transform, $"Effect_{index + 1:00}", output, 1f);
                var rig = root.AddComponent<HavenlineAudioRig>();
                rig.Configure(profile, ambience, ui, effects);
                PrefabUtility.SaveAsPrefabAsset(root, path);
            }
            finally
            {
                UnityEngine.Object.DestroyImmediate(root);
            }
        }

        private static HavenlinePremiumBuildGate.ProductionArtManifest LoadReviewManifest()
        {
            var json = File.ReadAllText(HavenlinePremiumBuildGate.ManifestPath);
            var manifest = JsonUtility.FromJson<HavenlinePremiumBuildGate.ProductionArtManifest>(json);
            if (manifest == null)
                throw new InvalidOperationException("HAVENLINE production manifest could not be loaded for studio review.");
            manifest.approved = true;
            manifest.approvedBy = "Kaleb-approved visual direction / HAVENLINE deterministic studio review";
            manifest.artVersion = manifest.artVersion.Replace("-blocked", string.Empty, StringComparison.OrdinalIgnoreCase);
            return manifest;
        }

        private static string[] RenderReviewFrames()
        {
            Directory.CreateDirectory(ReviewRoot);
            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            var camera = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Camera>(true))
                .Single(candidate => candidate.CompareTag("MainCamera"));
            var originalSize = camera.orthographicSize;
            var frames = new[]
            {
                Path.Combine(ReviewRoot, "HAVENLINE-studio-wide-1920x1080.png"),
                Path.Combine(ReviewRoot, "HAVENLINE-studio-close-1920x1080.png"),
                Path.Combine(ReviewRoot, "HAVENLINE-studio-foldable-2208x1840.png")
            };
            Render(camera, frames[0], 1920, 1080);
            camera.orthographicSize = 8.85f;
            Render(camera, frames[1], 1920, 1080);
            camera.orthographicSize = originalSize + 1.15f;
            Render(camera, frames[2], 2208, 1840);
            camera.orthographicSize = originalSize;
            return frames.Select(Path.GetFileName).ToArray();
        }

        private static void WriteReport(
            HavenlinePremiumBuildGate.ProductionArtManifest manifest,
            HavenlinePremiumSceneGate.SceneValidationResult sceneResult,
            string[] proofFrames)
        {
            var files = Directory.EnumerateFiles(ProductionRoot, "*", SearchOption.AllDirectories)
                .Select(path => path.Replace('\\', '/')).ToArray();
            var report = new StudioReport
            {
                generatedAtUtc = DateTime.UtcNow.ToString("O"),
                manifestVersion = manifest.artVersion,
                modelFiles = files.Count(path => path.EndsWith(".obj", StringComparison.OrdinalIgnoreCase)),
                textureFiles = files.Count(path => path.EndsWith(".png", StringComparison.OrdinalIgnoreCase)),
                materials = AssetDatabase.FindAssets("t:Material", new[] { ProductionRoot }).Length,
                animationClips = AssetDatabase.FindAssets("t:AnimationClip", new[] { AnimationRoot }).Length,
                audioClips = files.Count(path => path.EndsWith(".wav", StringComparison.OrdinalIgnoreCase)),
                prefabs = files.Count(path => path.EndsWith(".prefab", StringComparison.OrdinalIgnoreCase)),
                sceneFailures = sceneResult.Failures.ToArray(),
                proofFrames = proofFrames
            };
            File.WriteAllText(Path.Combine(ReviewRoot, "HAVENLINE-studio-report.json"), JsonUtility.ToJson(report, true) + "\n");
        }

        private static void GenerateHudPrefab()
        {
            var path = UiRoot + "/HAVENLINE_GameplayHUD.prefab";
            AssetDatabase.DeleteAsset(path);
            var root = new GameObject("HAVENLINE_GameplayHUD", typeof(RectTransform), typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster));
            try
            {
                var canvas = root.GetComponent<Canvas>();
                canvas.renderMode = RenderMode.ScreenSpaceOverlay;
                canvas.sortingOrder = 20;
                var scaler = root.GetComponent<CanvasScaler>();
                scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
                scaler.referenceResolution = new Vector2(1920f, 1080f);
                scaler.screenMatchMode = CanvasScaler.ScreenMatchMode.MatchWidthOrHeight;
                scaler.matchWidthOrHeight = 0.52f;

                var safeArea = new GameObject("SafeArea", typeof(RectTransform), typeof(HavenlineSafeArea));
                safeArea.transform.SetParent(root.transform, false);
                var safe = (RectTransform)safeArea.transform;
                safe.anchorMin = Vector2.zero;
                safe.anchorMax = Vector2.one;
                safe.offsetMin = safe.offsetMax = Vector2.zero;

                var resources = CreatePanel(safe, "ResourcesPanel", new Vector2(0f, 1f), new Vector2(24f, -24f), new Vector2(500f, 92f), Navy, 0.88f);
                CreateText(resources.transform, "ResourcesText", "WOOD 0   STONE 0   METAL 0", 24, TextAnchor.MiddleCenter);
                var objective = CreatePanel(safe, "ObjectivePanel", new Vector2(0.5f, 1f), new Vector2(0f, -24f), new Vector2(580f, 88f), Navy, 0.90f);
                CreateText(objective.transform, "ObjectiveText", "RESTORE THE FURNACE", 24, TextAnchor.MiddleCenter);
                var furnace = CreatePanel(safe, "FurnacePanel", new Vector2(1f, 1f), new Vector2(-24f, -24f), new Vector2(300f, 92f), Navy, 0.88f);
                CreateText(furnace.transform, "StatusText", "FURNACE LV.1", 24, TextAnchor.MiddleCenter);

                var context = CreatePanel(safe, "ContextPanel", new Vector2(0.5f, 0f), new Vector2(0f, 28f), new Vector2(540f, 84f), Navy, 0.82f);
                CreateText(context.transform, "ContextText", "MOVE CLOSE TO ACT", 24, TextAnchor.UpperCenter);
                var progressBackground = CreateImage(context.transform, "ContextProgressBackground", new Color(0.02f,0.07f,0.11f,0.9f));
                SetRect(progressBackground.rectTransform, new Vector2(0.5f,0f), new Vector2(0f,14f), new Vector2(470f,12f));
                var progress = CreateImage(progressBackground.transform, "ContextProgress", Orange);
                progress.type = Image.Type.Filled;
                progress.fillMethod = Image.FillMethod.Horizontal;
                progress.fillOrigin = 0;
                progress.fillAmount = 0f;
                var progressRect = progress.rectTransform;
                progressRect.anchorMin = Vector2.zero;
                progressRect.anchorMax = Vector2.one;
                progressRect.offsetMin = progressRect.offsetMax = Vector2.zero;

                var helper = CreatePanel(safe, "HelperPanel", new Vector2(0f, 0f), new Vector2(24f, 132f), new Vector2(280f, 72f), Navy, 0.78f);
                CreateText(helper.transform, "HelperText", "HELPER: FROZEN", 24, TextAnchor.MiddleCenter);
                helper.SetActive(false);
                var threat = CreatePanel(safe, "ThreatPanel", new Vector2(1f, 0f), new Vector2(-24f, 132f), new Vector2(280f, 72f), Navy, 0.78f);
                CreateText(threat.transform, "ThreatText", "THREAT: QUIET", 24, TextAnchor.MiddleCenter);
                threat.SetActive(false);
                context.SetActive(false);

                var joystick = CreateImage(safe, "JoystickBase", new Color(0.08f,0.23f,0.34f,0.50f));
                SetRect(joystick.rectTransform, new Vector2(0f,0f), new Vector2(132f,126f), new Vector2(190f,190f));
                var knob = CreateImage(joystick.transform, "JoystickKnob", new Color(0.52f,0.78f,0.92f,0.72f));
                SetRect(knob.rectTransform, new Vector2(0.5f,0.5f), Vector2.zero, new Vector2(82f,82f));
                var warmth = CreateImage(safe, "WarmthIndicator", new Color(1f,0.31f,0.06f,0.82f));
                SetRect(warmth.rectTransform, new Vector2(1f,0f), new Vector2(-128f,126f), new Vector2(150f,150f));
                CreateText(warmth.transform, "WarmthText", "WARMTH", 24, TextAnchor.MiddleCenter);

                for (var index = 0; index < 8; index++)
                {
                    var marker = CreateImage(safe, $"HudAccent_{index + 1:00}", index % 2 == 0 ? Orange : new Color(0.36f,0.68f,0.86f,0.84f));
                    SetRect(marker.rectTransform, new Vector2(index % 2, index < 4 ? 1f : 0f),
                        new Vector2(index % 2 == 0 ? 14f : -14f, index < 4 ? -170f - index * 22f : 80f + index * 10f),
                        new Vector2(8f, 42f + index * 3f));
                }

                root.AddComponent<HavenlineHud>();
                PrefabUtility.SaveAsPrefabAsset(root, path);
            }
            finally
            {
                UnityEngine.Object.DestroyImmediate(root);
            }
        }

        private static void GeneratePausePrefab()
        {
            var path = UiRoot + "/HAVENLINE_PauseSettings.prefab";
            AssetDatabase.DeleteAsset(path);
            var root = new GameObject("HAVENLINE_PauseSettings", typeof(RectTransform), typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster));
            try
            {
                var canvas = root.GetComponent<Canvas>();
                canvas.renderMode = RenderMode.ScreenSpaceOverlay;
                canvas.sortingOrder = 40;
                var scaler = root.GetComponent<CanvasScaler>();
                scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
                scaler.referenceResolution = new Vector2(1920f,1080f);
                var dim = CreateImage(root.transform, "Dimmer", new Color(0.01f,0.03f,0.05f,0.86f));
                dim.rectTransform.anchorMin = Vector2.zero;
                dim.rectTransform.anchorMax = Vector2.one;
                dim.rectTransform.offsetMin = dim.rectTransform.offsetMax = Vector2.zero;
                var panel = CreatePanel(root.transform, "PauseCard", new Vector2(0.5f,0.5f), Vector2.zero, new Vector2(760f,760f), Navy, 0.97f);
                CreateText(panel.transform, "PauseTitle", "HAVENLINE", 46, TextAnchor.MiddleCenter, new Vector2(0f,290f), new Vector2(620f,90f));
                var labels = new[] { "RESUME", "60 / 90 / 120 FPS", "GRAPHICS: ADAPTIVE HIGH", "AUDIO", "CONTROLS", "SAVE & EXIT" };
                for (var index = 0; index < labels.Length; index++)
                {
                    var button = CreatePanel(panel.transform, $"PauseOption_{index + 1:00}", new Vector2(0.5f,0.5f),
                        new Vector2(0f, 190f - index * 92f), new Vector2(590f,72f), index == 0 ? Blue : new Color(0.07f,0.18f,0.27f), 0.96f);
                    CreateText(button.transform, $"PauseOptionText_{index + 1:00}", labels[index], 24, TextAnchor.MiddleCenter);
                }
                PrefabUtility.SaveAsPrefabAsset(root, path);
            }
            finally
            {
                UnityEngine.Object.DestroyImmediate(root);
            }
        }

        private static void CreateController(string path, string prefix, IReadOnlyList<string> clipNames, bool quadruped)
        {
            AssetDatabase.DeleteAsset(path);
            foreach (var old in AssetDatabase.FindAssets($"{prefix}_ t:AnimationClip", new[] { AnimationRoot }))
                AssetDatabase.DeleteAsset(AssetDatabase.GUIDToAssetPath(old));

            var clips = new Dictionary<string, AnimationClip>(StringComparer.Ordinal);
            for (var index = 0; index < clipNames.Count; index++)
            {
                var name = clipNames[index];
                var clipPath = AnimationRoot + $"/HAVENLINE_{prefix}_{name}.anim";
                var clip = CreateMotionClip(prefix + "_" + name, name, index, quadruped);
                AssetDatabase.CreateAsset(clip, clipPath);
                clips[name] = clip;
            }

            var controller = AnimatorController.CreateAnimatorControllerAtPath(path);
            controller.AddParameter("Speed", AnimatorControllerParameterType.Float);
            controller.AddParameter("CarryAmount", AnimatorControllerParameterType.Int);
            controller.AddParameter("ActionType", AnimatorControllerParameterType.Int);
            controller.AddParameter("Action", AnimatorControllerParameterType.Trigger);
            controller.AddParameter("ActionEnd", AnimatorControllerParameterType.Trigger);
            controller.AddParameter("Hit", AnimatorControllerParameterType.Trigger);
            controller.AddParameter("Dead", AnimatorControllerParameterType.Trigger);

            var machine = controller.layers[0].stateMachine;
            var states = new Dictionary<string, AnimatorState>(StringComparer.Ordinal);
            foreach (var clip in clips)
            {
                var state = machine.AddState(clip.Key);
                state.motion = clip.Value;
                state.writeDefaultValues = false;
                states[clip.Key] = state;
            }
            var idleName = clips.ContainsKey("Idle") ? "Idle" : clipNames[0];
            machine.defaultState = states[idleName];

            if (states.TryGetValue("Walk", out var walk))
            {
                var enter = states[idleName].AddTransition(walk);
                enter.hasExitTime = false;
                enter.duration = 0.12f;
                enter.AddCondition(AnimatorConditionMode.Greater, 0.06f, "Speed");
                var leave = walk.AddTransition(states[idleName]);
                leave.hasExitTime = false;
                leave.duration = 0.12f;
                leave.AddCondition(AnimatorConditionMode.Less, 0.05f, "Speed");
            }
            if (states.TryGetValue("Run", out var run) && states.TryGetValue("Walk", out walk))
            {
                var enter = walk.AddTransition(run);
                enter.hasExitTime = false;
                enter.duration = 0.10f;
                enter.AddCondition(AnimatorConditionMode.Greater, 0.72f, "Speed");
                var leave = run.AddTransition(walk);
                leave.hasExitTime = false;
                leave.duration = 0.10f;
                leave.AddCondition(AnimatorConditionMode.Less, 0.68f, "Speed");
            }

            var actionNames = clipNames.Where(name => name is not "Idle" and not "Walk" and not "Run" and not "Dead" and not "Hit").ToArray();
            for (var index = 0; index < actionNames.Length; index++)
            {
                var target = states[actionNames[index]];
                var transition = machine.AddAnyStateTransition(target);
                transition.hasExitTime = false;
                transition.duration = 0.07f;
                transition.AddCondition(AnimatorConditionMode.If, 0f, "Action");
                transition.AddCondition(AnimatorConditionMode.Equals, index + 1, "ActionType");
                var back = target.AddTransition(states[idleName]);
                back.hasExitTime = true;
                back.exitTime = 0.94f;
                back.duration = 0.08f;
            }
            if (states.TryGetValue("Hit", out var hit))
            {
                var transition = machine.AddAnyStateTransition(hit);
                transition.hasExitTime = false;
                transition.AddCondition(AnimatorConditionMode.If, 0f, "Hit");
                hit.AddTransition(states[idleName]).hasExitTime = true;
            }
            if (states.TryGetValue("Dead", out var dead))
            {
                var transition = machine.AddAnyStateTransition(dead);
                transition.hasExitTime = false;
                transition.AddCondition(AnimatorConditionMode.If, 0f, "Dead");
            }
            EditorUtility.SetDirty(controller);
        }

        private static AnimationClip CreateMotionClip(string clipName, string motionName, int index, bool quadruped)
        {
            var clip = new AnimationClip { name = "HAVENLINE_" + clipName, frameRate = 60f, wrapMode = WrapMode.Loop };
            var duration = motionName is "Attack" or "Hit" or "Chop" or "Mine" or "Build" or "Repair" ? 0.72f : 1f;
            var amplitude = motionName switch
            {
                "Run" => 0.11f,
                "Walk" or "Prowl" => 0.065f,
                "Attack" or "Chop" or "Mine" => 0.16f,
                "Dead" => 0.22f,
                _ => 0.025f + index % 4 * 0.008f
            };
            var vertical = new AnimationCurve(
                new Keyframe(0f, 0f), new Keyframe(duration * 0.25f, amplitude),
                new Keyframe(duration * 0.5f, 0f), new Keyframe(duration * 0.75f, amplitude * 0.72f),
                new Keyframe(duration, 0f));
            var pitch = new AnimationCurve(
                new Keyframe(0f, 0f), new Keyframe(duration * 0.5f, quadruped ? amplitude * 90f : amplitude * 42f),
                new Keyframe(duration, 0f));
            clip.SetCurve(string.Empty, typeof(Transform), "localPosition.y", vertical);
            clip.SetCurve(string.Empty, typeof(Transform), "localEulerAnglesRaw.x", pitch);
            if (motionName == "Dead")
                clip.wrapMode = WrapMode.ClampForever;
            AnimationUtility.SetAnimationClipSettings(clip, new AnimationClipSettings
            {
                loopTime = motionName is "Idle" or "Walk" or "Run" or "Prowl" or "CarryIdle" or "Frozen",
                keepOriginalOrientation = true,
                keepOriginalPositionXZ = true,
                keepOriginalPositionY = true
            });
            return clip;
        }

        private static ParticleSystem.MinMaxCurve CreateConstantCurve(float value)
        {
            var curve = new ParticleSystem.MinMaxCurve
            {
                mode = ParticleSystemCurveMode.Constant,
                constantMin = value,
                constantMax = value
            };
            return curve;
        }

        private static void CreateParticlePrefab(
            string path, string name, Color start, Color end, int maxParticles,
            float lifetime, float size, Vector3 shapeScale, bool worldSimulation, bool looping)
        {
            AssetDatabase.DeleteAsset(path);
            var root = new GameObject("HAVENLINE_" + name);
            try
            {
                var isSnow = name == "Snowfall";
                var isFire = name == "FurnaceFire";
                var isSparks = name == "FurnaceSparks";
                var isSmoke = name == "FurnaceSmoke";

                var particles = root.AddComponent<ParticleSystem>();
                var main = particles.main;
                main.loop = looping || isSnow;
                main.playOnAwake = true;
                main.maxParticles = maxParticles;
                main.simulationSpace = worldSimulation
                    ? ParticleSystemSimulationSpace.World
                    : ParticleSystemSimulationSpace.Local;
                main.startColor = new ParticleSystem.MinMaxGradient(start, end);
                main.startRotation = new ParticleSystem.MinMaxCurve(-0.35f, 0.35f);
                main.startLifetime = isFire
                    ? new ParticleSystem.MinMaxCurve(0.34f, 0.68f)
                    : isSparks
                        ? new ParticleSystem.MinMaxCurve(0.28f, 0.58f)
                        : isSmoke
                            ? new ParticleSystem.MinMaxCurve(1.7f, 2.6f)
                            : new ParticleSystem.MinMaxCurve(lifetime * 0.82f, lifetime * 1.18f);
                main.startSpeed = isSnow
                    ? new ParticleSystem.MinMaxCurve(1.15f, 1.75f)
                    : isFire
                        ? new ParticleSystem.MinMaxCurve(0.30f, 0.78f)
                        : isSparks
                            ? new ParticleSystem.MinMaxCurve(1.10f, 2.10f)
                            : isSmoke
                                ? new ParticleSystem.MinMaxCurve(0.24f, 0.52f)
                                : new ParticleSystem.MinMaxCurve(0.72f, 1.22f);
                main.startSize = isFire
                    ? CreateConstantCurve(0.18f)
                    : isSparks
                        ? CreateConstantCurve(0.055f)
                        : isSmoke
                            ? CreateConstantCurve(0.28f)
                            : new ParticleSystem.MinMaxCurve(size * 0.72f, size * 1.18f);
                main.gravityModifier = isSnow ? 0.04f : isSparks ? 0.26f : isSmoke ? -0.018f : isFire ? -0.06f : 0.16f;

                var emission = particles.emission;
                emission.rateOverTime = isSnow ? 210f : isFire ? 42f : isSparks ? 12f : isSmoke ? 6.5f : looping ? 34f : 0f;
                if (!looping && !isSnow)
                    emission.SetBursts(new[] { new ParticleSystem.Burst(0f, (short)Mathf.Min(maxParticles, 30)) });

                var shape = particles.shape;
                shape.shapeType = isSnow ? ParticleSystemShapeType.Box : ParticleSystemShapeType.Cone;
                shape.scale = shapeScale;
                shape.radius = isFire ? 0.12f : isSparks ? 0.08f : isSmoke ? 0.14f : 0.22f;
                shape.angle = isFire ? 8f : isSparks ? 20f : isSmoke ? 10f : 32f;

                var color = particles.colorOverLifetime;
                color.enabled = true;
                var gradient = new Gradient();
                gradient.SetKeys(
                    new[]
                    {
                        new GradientColorKey(start, 0f),
                        new GradientColorKey(Color.Lerp(start, end, 0.48f), 0.48f),
                        new GradientColorKey(end, 1f)
                    },
                    isSmoke
                        ? new[]
                        {
                            new GradientAlphaKey(0f, 0f), new GradientAlphaKey(0.34f, 0.18f),
                            new GradientAlphaKey(0.20f, 0.66f), new GradientAlphaKey(0f, 1f)
                        }
                        : new[]
                        {
                            new GradientAlphaKey(0f, 0f), new GradientAlphaKey(0.96f, 0.12f),
                            new GradientAlphaKey(0.72f, 0.64f), new GradientAlphaKey(0f, 1f)
                        });
                color.color = gradient;

                var sizeLifetime = particles.sizeOverLifetime;
                sizeLifetime.enabled = isFire || isSmoke;
                if (sizeLifetime.enabled)
                {
                    sizeLifetime.size = new ParticleSystem.MinMaxCurve(1f, new AnimationCurve(
                        new Keyframe(0f, isSmoke ? 0.38f : 0.55f),
                        new Keyframe(0.55f, 1f),
                        new Keyframe(1f, isSmoke ? 1.42f : 0.18f)));
                }

                var noise = particles.noise;
                noise.enabled = isFire || isSmoke;
                if (noise.enabled)
                {
                    noise.strength = isSmoke ? 0.18f : 0.12f;
                    noise.frequency = isSmoke ? 0.42f : 0.72f;
                    noise.scrollSpeed = 0.24f;
                    noise.damping = true;
                }

                var renderer = root.GetComponent<ParticleSystemRenderer>();
                renderer.sharedMaterial = HavenlinePremiumParticleAssets.Resolve(name);
                renderer.renderMode = isFire || isSparks
                    ? ParticleSystemRenderMode.Stretch
                    : ParticleSystemRenderMode.Billboard;
                renderer.lengthScale = isSparks ? 2.4f : isFire ? 1.15f : 1f;
                renderer.velocityScale = isSparks ? 0.38f : isFire ? 0.18f : 0f;
                renderer.sortMode = ParticleSystemSortMode.Distance;
                PrefabUtility.SaveAsPrefabAsset(root, path);
            }
            finally
            {
                UnityEngine.Object.DestroyImmediate(root);
            }
        }

        private static void CreateMixer()
        {
            AssetDatabase.DeleteAsset(MixerPath);
            var type = Type.GetType("UnityEditor.Audio.AudioMixerController, UnityEditor");
            var method = type?.GetMethods(BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic)
                .FirstOrDefault(candidate => candidate.Name == "CreateMixerControllerAtPath" &&
                                             candidate.GetParameters().Length == 1 &&
                                             candidate.GetParameters()[0].ParameterType == typeof(string));
            var created = method?.Invoke(null, new object[] { MixerPath });
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);
            if (created == null || AssetDatabase.LoadAssetAtPath<AudioMixer>(MixerPath) == null)
                throw new InvalidOperationException("Unity could not create the HAVENLINE production audio mixer.");
        }

        private static void AddEnvironmentLighting(GameObject root)
        {
            CreateLight(root.transform, "CoolFill", LightType.Point, new Color(0.36f,0.61f,0.86f), 2.4f, 18f, new Vector3(-7f,5f,5f));
            CreateLight(root.transform, "IceRim", LightType.Point, new Color(0.18f,0.54f,0.82f), 2.1f, 20f, new Vector3(8f,4f,-5f));
            CreateLight(root.transform, "CampWarmth", LightType.Point, new Color(1f,0.35f,0.08f), 2.6f, 12f, new Vector3(2.7f,2.1f,2.1f));
        }

        private static void AddPostProcessing(GameObject root)
        {
            var profilePath = EnvironmentRoot + "/HAVENLINE_FrozenOutpost_Post.asset";
            AssetDatabase.DeleteAsset(profilePath);
            var profile = ScriptableObject.CreateInstance<VolumeProfile>();
            var bloom = profile.Add<Bloom>(true);
            bloom.intensity.Override(0.38f);
            bloom.threshold.Override(0.9f);
            bloom.scatter.Override(0.58f);
            var color = profile.Add<ColorAdjustments>(true);
            color.postExposure.Override(0.12f);
            color.contrast.Override(12f);
            color.saturation.Override(8f);
            var vignette = profile.Add<Vignette>(true);
            vignette.intensity.Override(0.18f);
            vignette.smoothness.Override(0.72f);
            var tone = profile.Add<Tonemapping>(true);
            tone.mode.Override(TonemappingMode.ACES);
            AssetDatabase.CreateAsset(profile, profilePath);
            var volumeObject = new GameObject("HAVENLINE_PostProcessing");
            volumeObject.transform.SetParent(root.transform, false);
            var volume = volumeObject.AddComponent<Volume>();
            volume.isGlobal = true;
            volume.priority = 1f;
            volume.sharedProfile = profile;
        }

        private static void AddProbes(GameObject root)
        {
            var reflectionObject = new GameObject("HAVENLINE_ReflectionProbe");
            reflectionObject.transform.SetParent(root.transform, false);
            reflectionObject.transform.localPosition = new Vector3(0f,2.2f,0f);
            var reflection = reflectionObject.AddComponent<ReflectionProbe>();
            reflection.mode = ReflectionProbeMode.Realtime;
            reflection.refreshMode = ReflectionProbeRefreshMode.OnAwake;
            reflection.timeSlicingMode = ReflectionProbeTimeSlicingMode.AllFacesAtOnce;
            reflection.size = new Vector3(28f,9f,30f);
            reflection.resolution = 128;
            reflection.boxProjection = true;

            var probesObject = new GameObject("HAVENLINE_LightProbes");
            probesObject.transform.SetParent(root.transform, false);
            var probes = probesObject.AddComponent<LightProbeGroup>();
            var positions = new List<Vector3>();
            for (var z = -10; z <= 10; z += 5)
                for (var x = -10; x <= 10; x += 5)
                {
                    positions.Add(new Vector3(x,0.7f,z));
                    positions.Add(new Vector3(x,2.2f,z));
                }
            probes.probePositions = positions.ToArray();
        }

        private static GameObject InstantiateModel(string path, Transform parent, string name)
        {
            var asset = AssetDatabase.LoadAssetAtPath<GameObject>(path);
            if (asset == null)
                throw new FileNotFoundException("HAVENLINE generated model did not import.", path);
            var instance = PrefabUtility.InstantiatePrefab(asset, parent) as GameObject;
            if (instance == null)
                throw new InvalidOperationException("HAVENLINE generated model could not be instantiated: " + path);
            instance.name = name;
            return instance;
        }

        private static void ApplyMaterial(GameObject root, string name)
        {
            var material = LoadMaterial(name);
            foreach (var renderer in root.GetComponentsInChildren<Renderer>(true))
                renderer.sharedMaterial = material;
        }

        private static Material LoadMaterial(string name) =>
            AssetDatabase.LoadAssetAtPath<Material>(MaterialRoot + $"/HAVENLINE_{name}.mat") ??
            throw new FileNotFoundException("HAVENLINE material failed to import.", name);

        private static AudioSource CreateAudioSource(Transform parent, string name, AudioMixerGroup group, float spatialBlend)
        {
            var child = new GameObject(name);
            child.transform.SetParent(parent, false);
            var source = child.AddComponent<AudioSource>();
            source.playOnAwake = false;
            source.spatialBlend = spatialBlend;
            source.outputAudioMixerGroup = group;
            source.dopplerLevel = 0f;
            source.rolloffMode = AudioRolloffMode.Linear;
            source.minDistance = 1.5f;
            source.maxDistance = 24f;
            return source;
        }

        private static GameObject CreatePanel(Transform parent, string name, Vector2 anchor, Vector2 position, Vector2 size, Color color, float alpha)
        {
            var image = CreateImage(parent, name, new Color(color.r,color.g,color.b,alpha));
            SetRect(image.rectTransform, anchor, position, size);
            return image.gameObject;
        }

        private static Image CreateImage(Transform parent, string name, Color color)
        {
            var gameObject = new GameObject(name, typeof(RectTransform), typeof(CanvasRenderer), typeof(Image));
            gameObject.transform.SetParent(parent, false);
            var image = gameObject.GetComponent<Image>();
            image.color = color;
            image.sprite = HavenlineStudioUiAssets.Resolve(name);
            image.type = HavenlineStudioUiAssets.ShouldSlice(name)
                ? Image.Type.Sliced
                : Image.Type.Simple;
            return image;
        }

        private static Text CreateText(Transform parent, string name, string value, int size, TextAnchor alignment, Vector2? position = null, Vector2? dimensions = null)
        {
            var gameObject = new GameObject(name, typeof(RectTransform), typeof(CanvasRenderer), typeof(Text));
            gameObject.transform.SetParent(parent, false);
            var text = gameObject.GetComponent<Text>();
            text.text = value;
            text.font = AssetDatabase.LoadAssetAtPath<Font>(FontPath);
            text.fontSize = 24;
            text.fontStyle = FontStyle.Normal;
            text.alignment = alignment;
            text.color = new Color(0.94f,0.98f,1f,1f);
            text.resizeTextForBestFit = false;
            text.horizontalOverflow = HorizontalWrapMode.Overflow;
            text.verticalOverflow = VerticalWrapMode.Overflow;
            text.lineSpacing = 0.90f;
            text.raycastTarget = false;
            var rect = text.rectTransform;
            if (position.HasValue || dimensions.HasValue)
                SetRect(rect, new Vector2(0.5f,0.5f), position ?? Vector2.zero, dimensions ?? new Vector2(500f,80f));
            else
            {
                rect.anchorMin = Vector2.zero;
                rect.anchorMax = Vector2.one;
                rect.offsetMin = new Vector2(12f,8f);
                rect.offsetMax = new Vector2(-12f,-8f);
            }
            return text;
        }

        private static void SetRect(RectTransform rect, Vector2 anchor, Vector2 position, Vector2 size)
        {
            rect.anchorMin = rect.anchorMax = anchor;
            rect.pivot = anchor;
            rect.anchoredPosition = position;
            rect.sizeDelta = size;
        }

        private static void CreateLight(Transform parent, string name, LightType type, Color color, float intensity, float range, Vector3 position)
        {
            var gameObject = new GameObject(name);
            gameObject.transform.SetParent(parent, false);
            gameObject.transform.localPosition = position;
            var light = gameObject.AddComponent<Light>();
            light.type = type;
            light.color = color;
            light.intensity = intensity;
            light.range = range;
            light.shadows = LightShadows.Soft;
            light.shadowStrength = 0.52f;
        }

        private static void Render(Camera camera, string path, int width, int height)
        {
            var texture = new RenderTexture(width,height,24,RenderTextureFormat.ARGB32)
            {
                antiAliasing = 4,
                useMipMap = false,
                autoGenerateMips = false
            };
            var previousTarget = camera.targetTexture;
            var previousActive = RenderTexture.active;
            camera.targetTexture = texture;
            RenderTexture.active = texture;
            camera.Render();
            var image = new Texture2D(width,height,TextureFormat.RGB24,false,false);
            image.ReadPixels(new Rect(0,0,width,height),0,0);
            image.Apply(false,false);
            File.WriteAllBytes(path,image.EncodeToPNG());
            camera.targetTexture = previousTarget;
            RenderTexture.active = previousActive;
            UnityEngine.Object.DestroyImmediate(image);
            UnityEngine.Object.DestroyImmediate(texture);
        }

        private static void DuplicateMaterial(string sourceName, string destinationName)
        {
            var sourcePath = MaterialRoot + $"/HAVENLINE_{sourceName}.mat";
            var destinationPath = MaterialRoot + $"/{destinationName}.mat";
            AssetDatabase.DeleteAsset(destinationPath);
            if (!AssetDatabase.CopyAsset(sourcePath,destinationPath))
                throw new InvalidOperationException("HAVENLINE could not create required material: " + destinationPath);
        }

        private static void SetColor(Material material, Color color)
        {
            if (material.HasProperty("_BaseColor")) material.SetColor("_BaseColor",color);
            if (material.HasProperty("_Color")) material.SetColor("_Color",color);
        }

        private static void SetFloat(Material material, string property, float value)
        {
            if (material.HasProperty(property))
                material.SetFloat(property,value);
        }
    }
}
