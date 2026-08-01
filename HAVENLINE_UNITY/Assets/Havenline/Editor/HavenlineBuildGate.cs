using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Havenline;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Havenline.Editor
{
    public sealed class HavenlineBuildGate : IPreprocessBuildWithReport
    {
        private const string ApprovalPath = "Assets/Havenline/Production/VisualApproval.json";
        private static readonly HashSet<string> PrimitiveMeshNames = new(StringComparer.OrdinalIgnoreCase)
        {
            "Cube",
            "Sphere",
            "Capsule",
            "Cylinder",
            "Plane",
            "Quad"
        };

        public int callbackOrder => -10000;

        public void OnPreprocessBuild(BuildReport report)
        {
            ValidateProductionOrThrow(requireVisualApproval: true);

            if (report.summary.platform != BuildTarget.Android)
            {
                throw new BuildFailedException(
                    $"HAVENLINE production output is Android-first. Unexpected build target: {report.summary.platform}.");
            }

            if (PlayerSettings.GetScriptingBackend(BuildTargetGroup.Android) != ScriptingImplementation.IL2CPP)
            {
                throw new BuildFailedException("HAVENLINE Android production requires the IL2CPP scripting backend.");
            }

            if ((PlayerSettings.Android.targetArchitectures & AndroidArchitecture.ARM64) == 0)
            {
                throw new BuildFailedException("HAVENLINE Android production requires ARM64.");
            }
        }

        [MenuItem("HAVENLINE/Validate Production Fidelity")]
        public static void ValidateFromMenu()
        {
            ValidateProductionOrThrow(requireVisualApproval: false);
            Debug.Log("HAVENLINE Unity production fidelity validation passed.");
        }

        public static void ValidateProductionOrThrow(bool requireVisualApproval)
        {
            var failures = new List<string>();
            ValidateConfig(failures);
            ValidateBuildScenes(failures);
            ValidatePlayerSettings(failures);

            if (requireVisualApproval)
            {
                ValidateApprovalMarker(failures);
            }

            if (failures.Count > 0)
            {
                throw new BuildFailedException(
                    "HAVENLINE production validation failed:\n- " + string.Join("\n- ", failures));
            }
        }

        private static void ValidateConfig(ICollection<string> failures)
        {
            var configGuids = AssetDatabase.FindAssets("t:HavenlineProductionConfig");
            if (configGuids.Length != 1)
            {
                failures.Add(
                    $"Exactly one HavenlineProductionConfig asset is required; found {configGuids.Length}.");
                return;
            }

            var configPath = AssetDatabase.GUIDToAssetPath(configGuids[0]);
            var config = AssetDatabase.LoadAssetAtPath<HavenlineProductionConfig>(configPath);
            if (config == null)
            {
                failures.Add($"Could not load production config at {configPath}.");
                return;
            }

            try
            {
                config.ValidateOrThrow();
            }
            catch (Exception exception)
            {
                failures.Add(exception.Message);
            }

            ValidateProductionPrefab(config.PlayerPrefab, "player", failures);
            ValidateProductionPrefab(config.SurvivorPrefab, "survivor", failures);
            ValidateProductionPrefab(config.WolfPrefab, "wolf", failures);
            ValidateProductionPrefab(config.FurnacePrefab, "furnace", failures);
            ValidateProductionPrefab(config.BarricadePrefab, "barricade", failures);
            ValidateProductionPrefab(config.TentPrefab, "tent", failures);

            foreach (var resource in config.ResourcePrefabs ?? Array.Empty<GameObject>())
            {
                ValidateProductionPrefab(resource, "resource", failures);
            }
        }

        private static void ValidateProductionPrefab(
            GameObject prefab,
            string role,
            ICollection<string> failures)
        {
            if (prefab == null)
            {
                return;
            }

            var renderers = prefab.GetComponentsInChildren<Renderer>(true);
            if (renderers.Length == 0)
            {
                failures.Add($"The {role} prefab '{prefab.name}' has no visible renderer.");
                return;
            }

            foreach (var filter in prefab.GetComponentsInChildren<MeshFilter>(true))
            {
                if (filter.sharedMesh != null && PrimitiveMeshNames.Contains(filter.sharedMesh.name))
                {
                    failures.Add(
                        $"The {role} prefab '{prefab.name}' still contains primitive final art: {filter.sharedMesh.name}.");
                }
            }

            if (role is "player" or "survivor" or "wolf")
            {
                var animator = prefab.GetComponentInChildren<Animator>(true);
                if (animator == null || animator.runtimeAnimatorController == null)
                {
                    failures.Add(
                        $"The {role} prefab '{prefab.name}' requires a production Animator Controller.");
                }
            }
        }

        private static void ValidateBuildScenes(ICollection<string> failures)
        {
            var scenes = EditorBuildSettings.scenes.Where(scene => scene.enabled).ToArray();
            if (scenes.Length != 1)
            {
                failures.Add($"The first vertical slice must have exactly one enabled build scene; found {scenes.Length}.");
                return;
            }

            var originalScene = SceneManager.GetActiveScene().path;
            try
            {
                var scene = EditorSceneManager.OpenScene(scenes[0].path, OpenSceneMode.Single);
                var cameras = scene.GetRootGameObjects()
                    .SelectMany(root => root.GetComponentsInChildren<HavenlineIsometricCamera>(true))
                    .ToArray();

                if (cameras.Length != 1)
                {
                    failures.Add($"The vertical slice requires exactly one HavenlineIsometricCamera; found {cameras.Length}.");
                }
                else
                {
                    var camera = cameras[0].GetComponent<Camera>();
                    if (camera == null || !camera.orthographic)
                    {
                        failures.Add("The HAVENLINE production camera must use the close orthographic isometric presentation.");
                    }
                    else if (camera.orthographicSize is < 6.5f or > 10.5f)
                    {
                        failures.Add(
                            $"The production camera size {camera.orthographicSize:0.00} is outside the readable reference range 6.5–10.5.");
                    }
                }

                RequireExactlyOne<HavenlinePlayerMotor>(scene, "player motor", failures);
                RequireExactlyOne<HavenlineFurnace>(scene, "furnace", failures);
                RequireExactlyOne<HavenlineWarmthZone>(scene, "warmth zone", failures);

                var resourceCount = scene.GetRootGameObjects()
                    .SelectMany(root => root.GetComponentsInChildren<HavenlineResourceNode>(true))
                    .Count();
                if (resourceCount < 6)
                {
                    failures.Add($"The frozen outpost requires at least six authored resource nodes; found {resourceCount}.");
                }

                var helperCount = scene.GetRootGameObjects()
                    .SelectMany(root => root.GetComponentsInChildren<HavenlineSurvivorHelper>(true))
                    .Count();
                if (helperCount < 1)
                {
                    failures.Add("The vertical slice must include at least one rescueable survivor/helper.");
                }

                var barricadeCount = scene.GetRootGameObjects()
                    .SelectMany(root => root.GetComponentsInChildren<HavenlineBarricade>(true))
                    .Count();
                if (barricadeCount < 2)
                {
                    failures.Add($"The vertical slice requires at least two visible barricades; found {barricadeCount}.");
                }

                var wolfCount = scene.GetRootGameObjects()
                    .SelectMany(root => root.GetComponentsInChildren<HavenlineWolf>(true))
                    .Count();
                if (wolfCount < 1)
                {
                    failures.Add("The vertical slice must include visible wolf pressure.");
                }
            }
            finally
            {
                if (!string.IsNullOrWhiteSpace(originalScene) && File.Exists(originalScene))
                {
                    EditorSceneManager.OpenScene(originalScene, OpenSceneMode.Single);
                }
            }
        }

        private static void RequireExactlyOne<T>(
            Scene scene,
            string label,
            ICollection<string> failures)
            where T : Component
        {
            var count = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<T>(true))
                .Count();
            if (count != 1)
            {
                failures.Add($"The vertical slice requires exactly one {label}; found {count}.");
            }
        }

        private static void ValidatePlayerSettings(ICollection<string> failures)
        {
            if (PlayerSettings.defaultInterfaceOrientation != UIOrientation.LandscapeLeft &&
                PlayerSettings.defaultInterfaceOrientation != UIOrientation.AutoRotation)
            {
                failures.Add("HAVENLINE must support the intended landscape mobile presentation.");
            }

            if (QualitySettings.vSyncCount != 0)
            {
                failures.Add("HAVENLINE controls refresh rate explicitly; vSyncCount must be 0 in the production quality profile.");
            }
        }

        private static void ValidateApprovalMarker(ICollection<string> failures)
        {
            if (!File.Exists(ApprovalPath))
            {
                failures.Add(
                    "Visual approval is absent. Android export remains locked until exact Unity-rendered evidence passes review.");
                return;
            }

            var marker = File.ReadAllText(ApprovalPath);
            if (!marker.Contains("\"approved\": true", StringComparison.Ordinal) ||
                !marker.Contains("\"referenceFidelity\": true", StringComparison.Ordinal))
            {
                failures.Add("VisualApproval.json does not explicitly approve reference fidelity.");
            }
        }
    }
}
