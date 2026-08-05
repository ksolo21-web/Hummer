using UnityEditor;
using UnityEditor.Build;
using UnityEngine;

namespace Havenline.Editor
{
    /// <summary>
    /// Clean-checkout CI entry points. HAVENLINE's approved production library is generated
    /// deterministically from normal Unity source before the unchanged premium content and
    /// scene gates are enforced. No missing asset is waived and no release stage is promoted.
    /// </summary>
    public static class HavenlineCiBuildEntryPoints
    {
        private static bool preparedInCurrentEditorProcess;

        public static void PrepareGeneratedProductionContent()
        {
            if (preparedInCurrentEditorProcess)
            {
                var existing = HavenlinePremiumBuildGate.InspectProductionContent();
                if (existing.Passed)
                    return;
            }

            HavenlineProceduralArtStudio.GenerateForCi();
            var prepared = HavenlinePremiumBuildGate.InspectProductionContent();
            if (!prepared.Passed)
            {
                throw new BuildFailedException(
                    "HAVENLINE deterministic production preparation failed the unchanged premium content gate:\n - " +
                    string.Join("\n - ", prepared.Failures));
            }

            preparedInCurrentEditorProcess = true;
            Debug.Log("HAVENLINE deterministic production content is prepared and premium-gate clean.");
        }

        public static void BuildAndroidDeviceTest()
        {
            PrepareGeneratedProductionContent();
            HavenlineBuildPipeline.BuildAndroidReviewCandidate();
        }

        public static void BuildVerifiedRelease()
        {
            PrepareGeneratedProductionContent();
            HavenlineBuildPipeline.BuildVerifiedReleaseCandidate();
        }

        [MenuItem("HAVENLINE Premium/CI/Prepare Deterministic Production Content")]
        private static void PrepareFromMenu() => PrepareGeneratedProductionContent();
    }
}
