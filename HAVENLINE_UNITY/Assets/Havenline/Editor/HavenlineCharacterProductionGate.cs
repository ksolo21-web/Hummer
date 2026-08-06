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
    /// Prevents a release-candidate APK from shipping with primitive or incomplete character
    /// placeholders. Device-test builds may still run while production art is being authored.
    /// </summary>
    public static class HavenlineCharacterProductionGate
    {
        public const string RosterPath =
            "Assets/Havenline/Art/Characters/HavenlineCharacterRoster.asset";

        private static readonly HavenlineCharacterId[] RequiredIds =
        {
            HavenlineCharacterId.Character1,
            HavenlineCharacterId.Character2,
            HavenlineCharacterId.Character3,
            HavenlineCharacterId.Character4
        };

        private static readonly string[] RequiredAnimationTokens =
        {
            "idle",
            "walk",
            "run",
            "gather",
            "carry",
            "deposit",
            "warm",
            "build"
        };

        [MenuItem("HAVENLINE Premium/Validate Production Characters")]
        public static void ValidateFromMenu()
        {
            var failures = Validate();
            if (failures.Count > 0)
                throw new BuildFailedException(BuildMessage(failures));

            Debug.Log("HAVENLINE production character gate passed for all four characters.");
        }

        public static List<string> Validate()
        {
            var failures = new List<string>();
            var roster = AssetDatabase.LoadAssetAtPath<HavenlineCharacterRoster>(RosterPath);
            if (roster == null)
            {
                failures.Add($"Character roster asset is missing at {RosterPath}.");
                return failures;
            }

            failures.AddRange(roster.ValidateRoster());

            foreach (var id in RequiredIds)
            {
                if (!roster.TryGet(id, out var definition) || definition == null)
                    continue;

                ValidateDefinition(id, definition, failures);
            }

            return failures.Distinct(StringComparer.Ordinal).ToList();
        }

        public static void RequireForRelease(BuildReport report)
        {
            var outputPath = report?.summary.outputPath ?? string.Empty;
            var releaseCandidate = outputPath.IndexOf(
                "release-candidate",
                StringComparison.OrdinalIgnoreCase) >= 0;

            if (!releaseCandidate)
                return;

            var failures = Validate();
            if (failures.Count > 0)
                throw new BuildFailedException(BuildMessage(failures));
        }

        private static void ValidateDefinition(
            HavenlineCharacterId expectedId,
            HavenlineCharacterDefinition definition,
            ICollection<string> failures)
        {
            if (definition.CharacterId != expectedId)
                failures.Add($"Roster slot {expectedId} points to {definition.CharacterId}.");

            var expectedStartingChoice =
                expectedId == HavenlineCharacterId.Character1 ||
                expectedId == HavenlineCharacterId.Character2;
            if (definition.IsStartingChoice != expectedStartingChoice)
            {
                failures.Add(
                    $"{expectedId} starting-choice status is incorrect. " +
                    "Only Character 1 and Character 2 may start unlocked.");
            }

            if (definition.Portrait == null)
                failures.Add($"{expectedId} has no production portrait.");

            var prefab = definition.GameplayPrefab;
            if (prefab == null)
            {
                failures.Add($"{expectedId} has no production gameplay prefab.");
                return;
            }

            var animators = prefab.GetComponentsInChildren<Animator>(true);
            if (animators.Length != 1)
                failures.Add($"{expectedId} must contain exactly one Animator; found {animators.Length}.");

            var animator = animators.FirstOrDefault();
            if (animator != null)
            {
                if (animator.avatar == null || !animator.avatar.isHuman)
                    failures.Add($"{expectedId} must use a valid humanoid Avatar.");
                if (animator.runtimeAnimatorController == null)
                {
                    failures.Add($"{expectedId} has no runtime Animator Controller.");
                }
                else
                {
                    var clipNames = animator.runtimeAnimatorController.animationClips
                        .Where(clip => clip != null)
                        .Select(clip => clip.name.ToLowerInvariant())
                        .ToArray();
                    foreach (var token in RequiredAnimationTokens)
                    {
                        if (!clipNames.Any(name => name.Contains(token)))
                            failures.Add($"{expectedId} is missing a required '{token}' animation clip.");
                    }
                }
            }

            var renderers = prefab.GetComponentsInChildren<SkinnedMeshRenderer>(true);
            if (renderers.Length == 0)
            {
                failures.Add($"{expectedId} contains no SkinnedMeshRenderer.");
                return;
            }

            var vertexCount = renderers
                .Where(renderer => renderer.sharedMesh != null)
                .Sum(renderer => renderer.sharedMesh.vertexCount);
            if (vertexCount < 6000)
            {
                failures.Add(
                    $"{expectedId} has only {vertexCount:N0} skinned vertices; " +
                    "the production character is still below the approved-detail floor.");
            }
            if (vertexCount > 120000)
                failures.Add($"{expectedId} has {vertexCount:N0} skinned vertices and is not mobile-optimized.");

            var materialCount = renderers
                .SelectMany(renderer => renderer.sharedMaterials)
                .Where(material => material != null)
                .Distinct()
                .Count();
            if (materialCount < 4)
            {
                failures.Add(
                    $"{expectedId} has only {materialCount} distinct materials; " +
                    "skin, parka/fabric, fur, leather and hardware must remain readable.");
            }

            var suspiciousNames = prefab.GetComponentsInChildren<Transform>(true)
                .Select(item => item.name)
                .Where(name =>
                    name.IndexOf("placeholder", StringComparison.OrdinalIgnoreCase) >= 0 ||
                    name.Equals("Cube", StringComparison.OrdinalIgnoreCase) ||
                    name.Equals("Capsule", StringComparison.OrdinalIgnoreCase) ||
                    name.Equals("Sphere", StringComparison.OrdinalIgnoreCase))
                .ToArray();
            if (suspiciousNames.Length > 0)
                failures.Add($"{expectedId} still contains placeholder objects: {string.Join(", ", suspiciousNames)}.");
        }

        private static string BuildMessage(IEnumerable<string> failures)
        {
            return "HAVENLINE production character gate failed:\n- " +
                   string.Join("\n- ", failures);
        }
    }

    public sealed class HavenlineCharacterProductionBuildPreprocessor : IPreprocessBuildWithReport
    {
        public int callbackOrder => -1100;

        public void OnPreprocessBuild(BuildReport report)
        {
            HavenlineCharacterProductionGate.RequireForRelease(report);
        }
    }
}
