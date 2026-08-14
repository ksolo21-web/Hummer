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
    /// Last-line build safety gate for the four canonical HAVENLINE characters.
    ///
    /// The normal premium pipeline validates production content before authoring the
    /// shipping scene. This preprocessor deliberately sits at Unity's BuildPlayer
    /// boundary as well, so a direct BuildPipeline.BuildPlayer invocation cannot
    /// accidentally package generic, pending, hash-mismatched, or unreviewed character
    /// content.
    /// </summary>
    public sealed class HavenlineCharacterBuildPreprocessor : IPreprocessBuildWithReport
    {
        public int callbackOrder => -10000;

        public void OnPreprocessBuild(BuildReport report)
        {
            RequireApprovedCharacters(HavenlineCharacterApprovalGate.Validate);
        }

        [MenuItem("HAVENLINE Premium/Validate Approved Core Characters")]
        public static void ValidateApprovedCharactersFromMenu()
        {
            RequireApprovedCharacters(HavenlineCharacterApprovalGate.Validate);
            Debug.Log("HAVENLINE core-character approval gate passed for Character1–Character4.");
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
