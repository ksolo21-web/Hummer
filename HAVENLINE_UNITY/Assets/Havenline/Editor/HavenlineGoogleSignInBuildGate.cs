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
    /// Prevents a repeat of certificate/package OAuth failures by requiring the exact Android
    /// package, OAuth client, Firebase project and signing SHA-256 to be locked before release.
    /// A concrete provider implementation is also mandatory; the interface alone is not enough.
    /// </summary>
    public static class HavenlineGoogleSignInBuildGate
    {
        public const string ConfigurationPath =
            "Assets/Havenline/Config/HavenlineGoogleSignInConfiguration.asset";

        [MenuItem("HAVENLINE Premium/Validate Google Sign-In Configuration")]
        public static void ValidateFromMenu()
        {
            var failures = Validate();
            if (failures.Count > 0)
                throw new BuildFailedException(BuildMessage(failures));

            Debug.Log("HAVENLINE Google sign-in release gate passed.");
        }

        public static List<string> Validate()
        {
            var failures = new List<string>();
            var configuration = AssetDatabase.LoadAssetAtPath<HavenlineGoogleSignInConfiguration>(
                ConfigurationPath);
            if (configuration == null)
            {
                failures.Add($"Google sign-in configuration asset is missing at {ConfigurationPath}.");
                return failures;
            }

            failures.AddRange(configuration.ValidateConfiguration());

            var applicationIdentifier = PlayerSettings.GetApplicationIdentifier(BuildTargetGroup.Android);
            if (!string.Equals(
                    configuration.AndroidPackageName,
                    applicationIdentifier,
                    StringComparison.Ordinal))
            {
                failures.Add(
                    $"Google OAuth package '{configuration.AndroidPackageName}' does not match " +
                    $"Unity Android application id '{applicationIdentifier}'.");
            }

            var providers = TypeCache.GetTypesDerivedFrom<MonoBehaviour>()
                .Where(type =>
                    !type.IsAbstract &&
                    typeof(IHavenlineGoogleSignInProvider).IsAssignableFrom(type))
                .ToArray();
            if (providers.Length == 0)
            {
                failures.Add(
                    "No concrete IHavenlineGoogleSignInProvider exists. " +
                    "A mock, email-only fallback, or UI-only sign-in screen cannot satisfy release.");
            }

            var configurationText = string.Join(
                " ",
                configuration.AndroidPackageName,
                configuration.WebClientId,
                configuration.FirebaseProjectId,
                configuration.SigningCertificateSha256).ToLowerInvariant();
            if (configurationText.Contains("placeholder") ||
                configurationText.Contains("example") ||
                configurationText.Contains("changeme"))
            {
                failures.Add("Google sign-in configuration still contains placeholder values.");
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

        private static string BuildMessage(IEnumerable<string> failures)
        {
            return "HAVENLINE Google sign-in release gate failed:\n- " +
                   string.Join("\n- ", failures);
        }
    }

    public sealed class HavenlineGoogleSignInBuildPreprocessor : IPreprocessBuildWithReport
    {
        public int callbackOrder => -1200;

        public void OnPreprocessBuild(BuildReport report)
        {
            HavenlineGoogleSignInBuildGate.RequireForRelease(report);
        }
    }
}
