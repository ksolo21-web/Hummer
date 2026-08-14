using System;
using System.Collections.Generic;
using System.Linq;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;

namespace Havenline.Editor
{
    /// <summary>
    /// Last-line build safety gate for the four canonical HAVENLINE characters.
    ///
    /// A direct BuildPipeline.BuildPlayer invocation cannot bypass human character approval,
    /// gameplay-prefab/roster generation, or conversion of the legacy authored generic-player
    /// shell into the saved-profile C1-C4 runtime binding.
    /// </summary>
    public sealed class HavenlineCharacterBuildPreprocessor : IPreprocessBuildWithReport
    {
        public int callbackOrder => -10000;

        public void OnPreprocessBuild(BuildReport report)
        {
            RequireApprovedCharacters(HavenlineCharacterApprovalGate.Validate);
            var roster = HavenlineProductionCharacterAssetBuilder.BuildApprovedGameplayRoster();
            HavenlineCoreCrewScenePostprocessor.ApplyToShippingScene(roster);

            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            var bindingFailures = HavenlineCoreCrewScenePostprocessor.ValidateShippingCrewBinding(scene);
            if (bindingFailures.Length > 0)
            {
                throw new BuildFailedException(
                    "HAVENLINE Android/production build blocked by shipping core-crew scene validation:\n - " +
                    string.Join("\n - ", bindingFailures));
            }
        }

        [MenuItem("HAVENLINE Premium/Validate Approved Core Characters")]
        public static void ValidateApprovedCharactersFromMenu()
        {
            RequireApprovedCharacters(HavenlineCharacterApprovalGate.Validate);
            Debug.Log("HAVENLINE core-character approval gate passed for Character1–Character4.");
        }

        [MenuItem("HAVENLINE Premium/Characters/Build Roster and Patch Shipping Scene")]
        public static void BuildRosterAndPatchShippingSceneFromMenu()
        {
            RequireApprovedCharacters(HavenlineCharacterApprovalGate.Validate);
            var roster = HavenlineProductionCharacterAssetBuilder.BuildApprovedGameplayRoster();
            HavenlineCoreCrewScenePostprocessor.ApplyToShippingScene(roster);
            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            var failures = HavenlineCoreCrewScenePostprocessor.ValidateShippingCrewBinding(scene);
            if (failures.Length > 0)
                throw new BuildFailedException("HAVENLINE core-crew scene binding failed:\n - " + string.Join("\n - ", failures));
            Debug.Log("HAVENLINE shipping scene is bound to the approved four-character runtime roster.");
        }

        public static void RequireApprovedCharacters()
        {
            RequireApprovedCharacters(HavenlineCharacterApprovalGate.Validate);
        }

        internal static void RequireApprovedCharacters(Func<List<string>> validate)
        {
            if (validate == null)
                throw new ArgumentNullException(nameof(validate));

            var failures = validate()
                ?.Where(message => !string.IsNullOrWhiteSpace(message))
                .Distinct(StringComparer.Ordinal)
                .OrderBy(message => message, StringComparer.Ordinal)
                .ToArray()
                ?? Array.Empty<string>();

            if (failures.Length == 0)
                return;

            throw new BuildFailedException(
                "HAVENLINE Android/production build blocked by the four-character approval gate:\n - " +
                string.Join("\n - ", failures));
        }
    }
}
