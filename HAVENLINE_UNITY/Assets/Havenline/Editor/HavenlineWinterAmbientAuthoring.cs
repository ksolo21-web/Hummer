using System;
using System.Collections.Generic;
using System.Linq;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Havenline.Editor
{
    [InitializeOnLoad]
    internal static class HavenlineWinterAmbientAuthoring
    {
        internal const string PerimeterRootName = "HAVENLINE_QualityPerimeterDressing";

        static HavenlineWinterAmbientAuthoring()
        {
            EditorSceneManager.sceneSaving -= OnSceneSaving;
            EditorSceneManager.sceneSaving += OnSceneSaving;
        }

        private static void OnSceneSaving(Scene scene, string path)
        {
            if (!string.Equals(path, Reference.ScenePath, StringComparison.Ordinal))
                return;
            Apply(scene);
        }

        internal static void Apply(Scene scene)
        {
            var root = scene.GetRootGameObjects()
                .SelectMany(item => item.GetComponentsInChildren<Transform>(true))
                .FirstOrDefault(item => item.name == PerimeterRootName)
                ?.gameObject;
            if (root == null)
                return;

            var motion = root.GetComponent<HavenlineWinterAmbientMotion>()
                ?? root.AddComponent<HavenlineWinterAmbientMotion>();
            motion.Recapture();
            EditorUtility.SetDirty(root);
            EditorUtility.SetDirty(motion);
        }
    }

    public sealed class HavenlineWinterAmbientMotionGate : IProcessSceneWithReport
    {
        public int callbackOrder => 1270;

        public void OnProcessScene(Scene scene, BuildReport report)
        {
            if (!string.Equals(scene.path, Reference.ScenePath, StringComparison.Ordinal))
                return;

            var failures = Inspect(scene);
            if (failures.Count > 0)
            {
                throw new BuildFailedException(
                    "HAVENLINE winter ambient-motion gate blocked the build:\n - " +
                    string.Join("\n - ", failures));
            }
        }

        internal static IReadOnlyList<string> Inspect(Scene scene)
        {
            var failures = new List<string>();
            var root = scene.GetRootGameObjects()
                .SelectMany(item => item.GetComponentsInChildren<Transform>(true))
                .FirstOrDefault(item => item.name == HavenlineWinterAmbientAuthoring.PerimeterRootName)
                ?.gameObject;
            if (root == null)
            {
                failures.Add("Premium perimeter dressing root is missing.");
                return failures;
            }

            if (root.GetComponent<HavenlineWinterAmbientMotion>() == null)
                failures.Add("Premium perimeter dressing is missing subtle winter ambient motion.");

            var pineCount = root.GetComponentsInChildren<Transform>(true)
                .Count(item => item.name.StartsWith("QualitySceneryPine_", StringComparison.Ordinal));
            if (pineCount < HavenlineExampleGameQualityPass.MinimumPineDressing)
                failures.Add($"Ambient winter layer has only {pineCount} premium pines to animate.");

            return failures;
        }
    }
}
