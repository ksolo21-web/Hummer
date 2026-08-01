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
            "Cube", "Sphere", "Capsule", "Cylinder", "Plane", "Quad"
        };

        public int callbackOrder => -10000;

        public void OnPreprocessBuild(BuildReport report)
        {
            var output = report.summary.outputPath ?? string.Empty;
            var reviewCandidate = output.Contains("review-candidate", StringComparison.OrdinalIgnoreCase);
            if (reviewCandidate && (report.summary.options & BuildOptions.Development) == 0)
            {
                throw new BuildFailedException("HAVENLINE review candidates must be Development builds.");
            }

            ValidateProductionOrThrow(requireVisualApproval: !reviewCandidate);

            if (report.summary.platform != BuildTarget.Android)
            {
                throw new BuildFailedException("HAVENLINE production output is Android-first.");
            }

            if (PlayerSettings.GetScriptingBackend(NamedBuildTarget.Android) != ScriptingImplementation.IL2CPP)
            {
                throw new BuildFailedException("HAVENLINE Android requires IL2CPP.");
            }

            if ((PlayerSettings.Android.targetArchitectures & AndroidArchitecture.ARM64) == 0)
            {
                throw new BuildFailedException("HAVENLINE Android requires ARM64.");
            }
        }

        [MenuItem("HAVENLINE/Validate Production Fidelity")]
        public static void ValidateFromMenu()
        {
            ValidateProductionOrThrow(false);
            Debug.Log("HAVENLINE Unity production fidelity validation passed.");
        }

        public static void ValidateProductionOrThrow(bool requireVisualApproval)
        {
            var failures = new List<string>();
            ValidateConfig(failures);
            ValidateScene(failures);
            ValidateSettings(failures);
            if (requireVisualApproval)
            {
                ValidateApproval(failures);
            }

            if (failures.Count > 0)
            {
                throw new BuildFailedException("HAVENLINE production validation failed:\n- " + string.Join("\n- ", failures));
            }
        }

        private static void ValidateConfig(ICollection<string> failures)
        {
            var guids = AssetDatabase.FindAssets("t:HavenlineProductionConfig");
            if (guids.Length != 1)
            {
                failures.Add($"Exactly one production config is required; found {guids.Length}.");
                return;
            }

            var config = AssetDatabase.LoadAssetAtPath<HavenlineProductionConfig>(AssetDatabase.GUIDToAssetPath(guids[0]));
            if (config == null)
            {
                failures.Add("The production config could not be loaded.");
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

            ValidatePrefab(config.PlayerPrefab, "player", true, failures);
            ValidatePrefab(config.SurvivorPrefab, "survivor", true, failures);
            ValidatePrefab(config.WolfPrefab, "wolf", true, failures);
            ValidatePrefab(config.FurnacePrefab, "furnace", false, failures);
            ValidatePrefab(config.BarricadePrefab, "barricade", false, failures);
            ValidatePrefab(config.TentPrefab, "tent", false, failures);
            foreach (var prefab in config.ResourcePrefabs ?? Array.Empty<GameObject>())
            {
                ValidatePrefab(prefab, "resource", false, failures);
            }
        }

        private static void ValidatePrefab(GameObject prefab, string role, bool needsAnimator, ICollection<string> failures)
        {
            if (prefab == null)
            {
                return;
            }

            if (prefab.GetComponentsInChildren<Renderer>(true).Length == 0)
            {
                failures.Add($"The {role} prefab '{prefab.name}' has no visible renderer.");
            }

            foreach (var filter in prefab.GetComponentsInChildren<MeshFilter>(true))
            {
                if (filter.sharedMesh != null && PrimitiveMeshNames.Contains(filter.sharedMesh.name))
                {
                    failures.Add($"The {role} prefab '{prefab.name}' contains primitive final art: {filter.sharedMesh.name}.");
                }
            }

            if (!needsAnimator)
            {
                return;
            }

            var animator = prefab.GetComponentInChildren<Animator>(true);
            if (animator == null || animator.runtimeAnimatorController == null)
            {
                failures.Add($"The {role} prefab '{prefab.name}' requires an animation controller.");
            }
        }

        private static void ValidateScene(ICollection<string> failures)
        {
            var scenes = EditorBuildSettings.scenes.Where(scene => scene.enabled).ToArray();
            if (scenes.Length != 1)
            {
                failures.Add($"Exactly one frozen-outpost scene must be enabled; found {scenes.Length}.");
                return;
            }

            var previous = SceneManager.GetActiveScene().path;
            try
            {
                var scene = EditorSceneManager.OpenScene(scenes[0].path, OpenSceneMode.Single);
                RequireExactlyOne<HavenlinePlayerMotor>(scene, "player", failures);
                RequireExactlyOne<HavenlineFurnace>(scene, "furnace", failures);
                RequireExactlyOne<HavenlineWarmthZone>(scene, "warmth zone", failures);

                var cameraRig = FindAll<HavenlineIsometricCamera>(scene);
                if (cameraRig.Length != 1)
                {
                    failures.Add($"Exactly one isometric camera is required; found {cameraRig.Length}.");
                }
                else
                {
                    var camera = cameraRig[0].GetComponent<Camera>();
                    if (camera == null || !camera.orthographic || camera.orthographicSize is < 6.5f or > 10.5f)
                    {
                        failures.Add("The camera must be close orthographic/isometric with a readable 6.5–10.5 size.");
                    }
                }

                if (FindAll<HavenlineResourceNode>(scene).Length < 6)
                    failures.Add("At least six authored resource nodes are required.");
                if (FindAll<HavenlineSurvivorHelper>(scene).Length < 1)
                    failures.Add("A rescueable survivor/helper is required.");
                if (FindAll<HavenlineBarricade>(scene).Length < 2)
                    failures.Add("At least two visible barricades are required.");
                if (FindAll<HavenlineWolf>(scene).Length < 1)
                    failures.Add("Visible wolf pressure is required.");
            }
            finally
            {
                if (!string.IsNullOrWhiteSpace(previous) && File.Exists(previous))
                {
                    EditorSceneManager.OpenScene(previous, OpenSceneMode.Single);
                }
            }
        }

        private static T[] FindAll<T>(Scene scene) where T : Component =>
            scene.GetRootGameObjects().SelectMany(root => root.GetComponentsInChildren<T>(true)).ToArray();

        private static void RequireExactlyOne<T>(Scene scene, string label, ICollection<string> failures) where T : Component
        {
            var count = FindAll<T>(scene).Length;
            if (count != 1)
            {
                failures.Add($"Exactly one {label} is required; found {count}.");
            }
        }

        private static void ValidateSettings(ICollection<string> failures)
        {
            if (QualitySettings.vSyncCount != 0)
                failures.Add("vSyncCount must be 0 because HAVENLINE manages refresh rate explicitly.");
            if (PlayerSettings.defaultInterfaceOrientation != UIOrientation.LandscapeLeft &&
                PlayerSettings.defaultInterfaceOrientation != UIOrientation.AutoRotation)
                failures.Add("Landscape mobile presentation is required.");
        }

        private static void ValidateApproval(ICollection<string> failures)
        {
            if (!File.Exists(ApprovalPath))
            {
                failures.Add("Production visual approval is absent. Only a development review-candidate APK may be exported.");
                return;
            }

            var marker = File.ReadAllText(ApprovalPath);
            if (!marker.Contains("\"approved\": true", StringComparison.Ordinal) ||
                !marker.Contains("\"referenceFidelity\": true", StringComparison.Ordinal))
            {
                failures.Add("VisualApproval.json does not explicitly approve original-reference fidelity.");
            }
        }
    }
}
