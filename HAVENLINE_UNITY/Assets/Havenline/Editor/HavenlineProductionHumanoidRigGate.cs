using System;
using System.Collections.Generic;
using System.Linq;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEngine;

namespace Havenline.Editor
{
    /// <summary>
    /// Canonical C1-C4 production FBXs are rigged characters, not generic scene meshes. This
    /// postprocessor guarantees Unity builds a Humanoid Avatar directly from each reviewed FBX
    /// and keeps bone transforms available for runtime deformation/polish validation.
    /// </summary>
    public sealed class HavenlineProductionHumanoidImporter : AssetPostprocessor
    {
        private void OnPreprocessModel()
        {
            if (!IsCanonicalCharacterModel(assetPath))
                return;

            var importer = assetImporter as ModelImporter;
            if (importer == null)
                return;

            importer.animationType = ModelImporterAnimationType.Human;
            importer.avatarSetup = ModelImporterAvatarSetup.CreateFromThisModel;
            importer.optimizeGameObjects = false;
        }

        internal static bool IsCanonicalCharacterModel(string path) =>
            HavenlineProductionCharacterAssetBuilder.Plans.Any(plan =>
                string.Equals(plan.ModelPath, path, StringComparison.Ordinal));
    }

    public sealed class HavenlineProductionHumanoidRigGate : IPreprocessBuildWithReport
    {
        public int callbackOrder => -9000;

        public void OnPreprocessBuild(BuildReport report) => Require();

        [MenuItem("HAVENLINE Premium/Characters/Validate Humanoid Production Rigs")]
        private static void ValidateFromMenu()
        {
            Require();
            Debug.Log("HAVENLINE C1-C4 production FBXs all import as valid Unity Humanoid Avatars.");
        }

        internal static void Require()
        {
            var failures = Inspect();
            if (failures.Count > 0)
            {
                throw new BuildFailedException(
                    "HAVENLINE humanoid production-rig gate blocked the build:\n - " +
                    string.Join("\n - ", failures));
            }
        }

        internal static IReadOnlyList<string> Inspect()
        {
            var failures = new List<string>();
            foreach (var plan in HavenlineProductionCharacterAssetBuilder.Plans)
            {
                var importer = AssetImporter.GetAtPath(plan.ModelPath) as ModelImporter;
                if (importer == null)
                {
                    failures.Add($"{plan.Id} production FBX has no ModelImporter: {plan.ModelPath}");
                    continue;
                }

                if (importer.animationType != ModelImporterAnimationType.Human)
                    failures.Add($"{plan.Id} production FBX is not configured as Humanoid.");
                if (importer.avatarSetup != ModelImporterAvatarSetup.CreateFromThisModel)
                    failures.Add($"{plan.Id} must create its Humanoid Avatar from the approved FBX itself.");
                if (importer.optimizeGameObjects)
                    failures.Add($"{plan.Id} currently optimizes away bone transforms required by runtime deformation QA.");

                var avatar = AssetDatabase.LoadAllAssetsAtPath(plan.ModelPath)
                    .OfType<Avatar>()
                    .FirstOrDefault(item => item != null && item.isValid && item.isHuman);
                if (avatar == null)
                {
                    failures.Add(
                        $"{plan.Id} production FBX did not generate a valid Humanoid Avatar. " +
                        "Fix the FBX bone mapping/pose instead of shipping a generic rig.");
                }

                var model = AssetDatabase.LoadAssetAtPath<GameObject>(plan.ModelPath);
                var animator = model != null ? model.GetComponentInChildren<Animator>(true) : null;
                if (animator == null || animator.avatar == null || !animator.avatar.isValid || !animator.avatar.isHuman)
                    failures.Add($"{plan.Id} imported model root is not bound to its valid Humanoid Avatar.");
            }

            return failures.Distinct().OrderBy(item => item, StringComparer.Ordinal).ToArray();
        }
    }
}
