using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

namespace Havenline.Editor
{
    /// <summary>
    /// Validates what is actually present in the authored Unity scene. Passing the
    /// production-content manifest alone is not enough to export a release candidate.
    /// </summary>
    public static class HavenlinePremiumSceneGate
    {
        private static readonly string[] ProhibitedSceneTokens =
        {
            "placeholder", "prototype", "superhero", "mannequin", "capsule",
            "primitive", "blockout", "greybox", "graybox", "debug", "temp_"
        };

        public sealed class SceneValidationResult
        {
            public IReadOnlyList<string> Failures { get; }
            public bool Passed => Failures.Count == 0;

            public SceneValidationResult(IReadOnlyList<string> failures)
            {
                Failures = failures;
            }
        }

        [MenuItem("HAVENLINE Premium/Validate Authored Shipping Scene")]
        public static void ValidateFromMenu()
        {
            var manifest = HavenlinePremiumBuildGate.RequireProductionContent();
            RequirePremiumScene(manifest);
            Debug.Log("HAVENLINE authored shipping scene passed the premium visual gate.");
        }

        public static SceneValidationResult InspectPremiumScene(
            HavenlinePremiumBuildGate.ProductionArtManifest manifest)
        {
            var failures = new List<string>();
            if (!File.Exists(Reference.ScenePath))
            {
                failures.Add($"Shipping scene is missing: {Reference.ScenePath}");
                return new SceneValidationResult(failures);
            }

            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            var objects = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
                .Select(transform => transform.gameObject)
                .Distinct()
                .ToArray();

            foreach (var sceneObject in objects)
            {
                if (ProhibitedSceneTokens.Any(token =>
                        sceneObject.name.Contains(token, StringComparison.OrdinalIgnoreCase)))
                {
                    failures.Add($"Shipping scene contains prohibited prototype/debug object: {sceneObject.name}");
                }
            }

            ValidateCamera(objects, failures);
            ValidateActors(scene, manifest, failures);
            ValidateRendering(objects, failures);
            ValidateInterface(objects, manifest, failures);
            ValidateAudio(objects, failures);
            ValidateWorldComposition(scene, failures);

            return new SceneValidationResult(failures.Distinct().OrderBy(message => message).ToArray());
        }

        public static SceneValidationResult RequirePremiumScene(
            HavenlinePremiumBuildGate.ProductionArtManifest manifest)
        {
            var result = InspectPremiumScene(manifest);
            if (!result.Passed)
            {
                throw new BuildFailedException(
                    "HAVENLINE premium shipping scene blocked. Visual/runtime gate failures:\n - " +
                    string.Join("\n - ", result.Failures));
            }

            return result;
        }

        private static void ValidateCamera(IEnumerable<GameObject> objects, ICollection<string> failures)
        {
            var cameras = objects.SelectMany(item => item.GetComponents<Camera>()).ToArray();
            var mainCameras = cameras.Where(camera => camera.CompareTag("MainCamera")).ToArray();
            if (mainCameras.Length != 1)
            {
                failures.Add($"Shipping scene requires exactly one MainCamera; found {mainCameras.Length}.");
                return;
            }

            var camera = mainCameras[0];
            if (!camera.orthographic)
                failures.Add("Shipping camera must use the locked close isometric/orthographic presentation.");
            if (camera.orthographicSize > 12.5f)
                failures.Add($"Player framing is too distant for the premium phone view (orthographic size {camera.orthographicSize:0.0}; maximum 12.5).");
            if (!camera.allowHDR)
                failures.Add("Main camera must allow HDR for calibrated furnace, weather, and post-processing highlights.");
            if (!camera.allowMSAA)
                failures.Add("Main camera must allow MSAA for clean mobile presentation.");
        }

        private static void ValidateActors(
            Scene scene,
            HavenlinePremiumBuildGate.ProductionArtManifest manifest,
            ICollection<string> failures)
        {
            var player = FindSingle<HavenlinePlayerController>(scene, "player", failures);
            var helper = FindSingle<HavenlineHelper>(scene, "rescued survivor/helper", failures);
            var enemies = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<HavenlineEnemy>(true))
                .ToArray();

            if (enemies.Length < 1)
                failures.Add("Shipping scene requires at least one authored enemy/prefab instance for visual validation.");

            if (player != null)
                ValidateActorModel("player", player.gameObject, manifest.playerModel, failures);
            if (helper != null)
                ValidateActorModel("survivor/helper", helper.gameObject, manifest.survivorModel, failures);
            foreach (var enemy in enemies)
                ValidateActorModel("wolf enemy", enemy.gameObject, manifest.wolfModel, failures);

            var animators = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Animator>(true))
                .ToArray();
            if (animators.Length < 3)
                failures.Add($"Shipping scene requires final Mecanim animation on player, helper, and enemy; found {animators.Length} Animator components.");
            foreach (var animator in animators)
            {
                if (animator.runtimeAnimatorController == null)
                    failures.Add($"Animator has no production controller: {GetHierarchyPath(animator.transform)}");
                if (animator.avatar == null || !animator.avatar.isValid)
                    failures.Add($"Animator has no valid production avatar: {GetHierarchyPath(animator.transform)}");
            }
        }

        private static void ValidateActorModel(
            string label,
            GameObject actorRoot,
            string expectedAssetPath,
            ICollection<string> failures)
        {
            var rendererPaths = actorRoot.GetComponentsInChildren<Renderer>(true)
                .Select(renderer => PrefabUtility.GetPrefabAssetPathOfNearestInstanceRoot(renderer.gameObject))
                .Where(path => !string.IsNullOrWhiteSpace(path))
                .Distinct(StringComparer.Ordinal)
                .ToArray();

            if (!rendererPaths.Contains(expectedAssetPath, StringComparer.Ordinal))
            {
                failures.Add(
                    $"The {label} in the shipping scene is not using its approved production model. " +
                    $"Expected {expectedAssetPath}; found [{string.Join(", ", rendererPaths)}].");
            }
        }

        private static void ValidateRendering(IEnumerable<GameObject> objects, ICollection<string> failures)
        {
            var objectArray = objects as GameObject[] ?? objects.ToArray();
            var renderers = objectArray.SelectMany(item => item.GetComponents<Renderer>()).ToArray();
            if (renderers.Length < 50)
                failures.Add($"Frozen outpost is not visually dense enough for release: found {renderers.Length} renderers; require at least 50 authored renderers.");

            var materials = renderers.SelectMany(renderer => renderer.sharedMaterials)
                .Where(material => material != null)
                .Distinct()
                .ToArray();
            if (materials.Length < 12)
                failures.Add($"Frozen outpost needs a complete material language: found {materials.Length} materials; require at least 12.");

            foreach (var renderer in renderers)
            {
                if (renderer.sharedMaterials.Length == 0 || renderer.sharedMaterials.Any(material => material == null))
                    failures.Add($"Renderer has a missing material: {GetHierarchyPath(renderer.transform)}");
                foreach (var material in renderer.sharedMaterials.Where(material => material != null))
                {
                    if (material.name.Contains("Default-Material", StringComparison.OrdinalIgnoreCase) ||
                        material.name.Contains("Default Material", StringComparison.OrdinalIgnoreCase))
                    {
                        failures.Add($"Default Unity material is prohibited in the shipping scene: {material.name}");
                    }
                }
            }

            var lights = objectArray.SelectMany(item => item.GetComponents<Light>()).ToArray();
            if (lights.Length < 4)
                failures.Add($"Premium lighting rig is incomplete: found {lights.Length} lights; require at least 4 authored lights.");
            if (!lights.Any(light => light.type == LightType.Directional && light.shadows != LightShadows.None))
                failures.Add("Shipping scene needs a shadow-casting winter key light.");
            if (!lights.Any(light => light.type == LightType.Point && light.color.r > light.color.b))
                failures.Add("Shipping scene needs a warm furnace/fire light affecting the environment.");

            var particles = objectArray.SelectMany(item => item.GetComponents<ParticleSystem>()).ToArray();
            if (particles.Length < 6)
                failures.Add($"Premium feedback/weather VFX are incomplete: found {particles.Length} particle systems; require at least 6.");
            if (!objectArray.SelectMany(item => item.GetComponents<Volume>()).Any())
                failures.Add("Shipping scene requires an authored URP post-processing Volume.");
            if (!objectArray.SelectMany(item => item.GetComponents<ReflectionProbe>()).Any())
                failures.Add("Shipping scene requires reflection probes for ice, snow, metal, and wet surfaces.");
            if (!objectArray.SelectMany(item => item.GetComponents<LightProbeGroup>()).Any())
                failures.Add("Shipping scene requires light probes for animated survivors and enemies.");
        }

        private static void ValidateInterface(
            IEnumerable<GameObject> objects,
            HavenlinePremiumBuildGate.ProductionArtManifest manifest,
            ICollection<string> failures)
        {
            var objectArray = objects as GameObject[] ?? objects.ToArray();
            var canvases = objectArray.SelectMany(item => item.GetComponents<Canvas>()).ToArray();
            if (canvases.Length < 2)
                failures.Add("Premium build requires complete gameplay HUD plus menu/pause/settings canvas presentation.");

            var textComponents = objectArray.SelectMany(item => item.GetComponents<Text>()).ToArray();
            foreach (var text in textComponents)
            {
                if (text.font == null)
                {
                    failures.Add($"UI text has no final font: {GetHierarchyPath(text.transform)}");
                    continue;
                }

                var fontPath = AssetDatabase.GetAssetPath(text.font);
                if (!string.Equals(fontPath, manifest.uiFont, StringComparison.Ordinal))
                    failures.Add($"UI text is not using the approved HAVENLINE font: {GetHierarchyPath(text.transform)} ({fontPath})");
                if (text.font.name.Contains("LegacyRuntime", StringComparison.OrdinalIgnoreCase) ||
                    text.font.name.Equals("Arial", StringComparison.OrdinalIgnoreCase))
                    failures.Add($"Legacy/default UI font is prohibited: {GetHierarchyPath(text.transform)}");
            }

            var images = objectArray.SelectMany(item => item.GetComponents<Image>()).ToArray();
            if (images.Length < 16)
                failures.Add($"Premium HUD/menu presentation is incomplete: found {images.Length} UI images; require at least 16 final elements.");

            var atlas = AssetDatabase.LoadAssetAtPath<Texture2D>(manifest.hudAtlas);
            if (atlas == null)
                failures.Add("Approved HAVENLINE HUD atlas is missing or failed to import.");
        }

        private static void ValidateAudio(IEnumerable<GameObject> objects, ICollection<string> failures)
        {
            var sources = objects.SelectMany(item => item.GetComponents<AudioSource>()).ToArray();
            if (sources.Length < 8)
                failures.Add($"Shipping audio presentation is incomplete: found {sources.Length} AudioSources; require at least 8 spatial/UI/music/ambience sources.");
            foreach (var source in sources)
            {
                if (source.outputAudioMixerGroup == null)
                    failures.Add($"AudioSource is not routed through the production mixer: {GetHierarchyPath(source.transform)}");
            }
        }

        private static void ValidateWorldComposition(Scene scene, ICollection<string> failures)
        {
            RequireAtLeast<HavenlineFurnace>(scene, 1, "upgradeable furnace", failures);
            RequireAtLeast<HavenlineResourceNode>(scene, 10, "resource nodes", failures);
            RequireAtLeast<HavenlineBarricade>(scene, 2, "barricades/defenses", failures);

            var objects = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
                .Select(transform => transform.gameObject)
                .ToArray();

            var minimumStageRenderers = new[] { 6, 10, 16, 20 };
            for (var index = 0; index < minimumStageRenderers.Length; index++)
            {
                var stageName = $"FurnaceLevel{index + 1}";
                var stage = objects.SingleOrDefault(item => item.name == stageName);
                if (stage == null)
                {
                    failures.Add($"Shipping furnace is missing authored progression stage {index + 1}.");
                    continue;
                }
                var stageRenderers = stage.GetComponentsInChildren<Renderer>(true);
                if (stageRenderers.Length < minimumStageRenderers[index])
                {
                    failures.Add(
                        $"Furnace stage {index + 1} is not visually complete: found {stageRenderers.Length} renderers; " +
                        $"require at least {minimumStageRenderers[index]}.");
                }
                if (stageRenderers.Any(renderer =>
                        !string.IsNullOrWhiteSpace(
                            PrefabUtility.GetPrefabAssetPathOfNearestInstanceRoot(renderer.gameObject))))
                {
                    failures.Add($"Furnace stage {index + 1} still depends on an imported prop prefab instead of authored machine parts.");
                }
            }

            if (objects.Any(item => item.name.StartsWith("FurnacePremium", StringComparison.Ordinal)))
                failures.Add("Decorative furnace overlays are prohibited; progression stages must own the complete furnace silhouette.");

            foreach (var shelterName in new[] { "LeftPremiumShelter", "RightPremiumShelter" })
            {
                var shelter = objects.SingleOrDefault(item => item.name == shelterName);
                if (shelter == null)
                {
                    failures.Add($"Shipping outpost is missing authored shelter: {shelterName}.");
                    continue;
                }
                var shelterRenderers = shelter.GetComponentsInChildren<Renderer>(true).Length;
                if (shelterRenderers < 8)
                    failures.Add($"{shelterName} is not a complete multi-part shelter; found {shelterRenderers} renderers.");
            }

            foreach (var oldTentName in new[] { "StartingTent", "RescueShelter" })
            {
                var oldTent = objects.FirstOrDefault(item => item.name == oldTentName);
                if (oldTent != null && oldTent.activeInHierarchy)
                    failures.Add($"Superseded imported tent visual is still active: {oldTentName}.");
            }

            var renderers = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Renderer>(true))
                .ToArray();
            var bounds = new Bounds();
            var initialized = false;
            foreach (var renderer in renderers)
            {
                if (!initialized)
                {
                    bounds = renderer.bounds;
                    initialized = true;
                }
                else
                {
                    bounds.Encapsulate(renderer.bounds);
                }
            }

            if (!initialized || bounds.size.x < 24f || bounds.size.z < 28f)
                failures.Add("Authored frozen outpost does not fill the required compact but substantial world footprint.");
        }

        private static T FindSingle<T>(Scene scene, string label, ICollection<string> failures) where T : Component
        {
            var components = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<T>(true))
                .ToArray();
            if (components.Length != 1)
            {
                failures.Add($"Shipping scene requires exactly one {label}; found {components.Length}.");
                return null;
            }
            return components[0];
        }

        private static void RequireAtLeast<T>(Scene scene, int minimum, string label, ICollection<string> failures)
            where T : Component
        {
            var count = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<T>(true))
                .Count();
            if (count < minimum)
                failures.Add($"Shipping scene requires at least {minimum} {label}; found {count}.");
        }

        private static string GetHierarchyPath(Transform transform)
        {
            var names = new Stack<string>();
            for (var current = transform; current != null; current = current.parent)
                names.Push(current.name);
            return string.Join("/", names);
        }
    }
}
