using UnityEditor;
using UnityEditor.Build;
using UnityEngine;

namespace Havenline.Editor
{
    /// <summary>
    /// Clean-checkout CI entry points. HAVENLINE's deterministic world library is generated,
    /// then the R31 structural source replacement and R32 human-review correction passes are
    /// applied before the unchanged premium content and scene gates are enforced. Build stage
    /// stays explicit so a device-test character review path can never masquerade as release.
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
            HavenlineR31ProductionArtUpgrade.ApplyToGeneratedProduction();
            HavenlineR32ProductionArtUpgrade.ApplyToGeneratedProduction();

            var prepared = HavenlinePremiumBuildGate.InspectProductionContent();
            if (!prepared.Passed)
            {
                throw new BuildFailedException(
                    "HAVENLINE deterministic R32 production preparation failed the unchanged premium content gate:\n - " +
                    string.Join("\n - ", prepared.Failures));
            }

            preparedInCurrentEditorProcess = true;
            Debug.Log("HAVENLINE deterministic R32 production content is prepared and premium-gate clean.");
        }

        public static void BuildAndroidDeviceTest()
        {
            HavenlineBuildStageContext.Set(HavenlineBuildStage.DeviceTest);
            try
            {
                PrepareGeneratedProductionContent();
                HavenlineBuildPipeline.BuildAndroidReviewCandidate();
            }
            finally
            {
                HavenlineBuildStageContext.Clear();
            }
        }

        public static void BuildVerifiedRelease()
        {
            HavenlineBuildStageContext.Set(HavenlineBuildStage.VerifiedRelease);
            try
            {
                PrepareGeneratedProductionContent();
                HavenlineBuildPipeline.BuildVerifiedReleaseCandidate();
            }
            finally
            {
                HavenlineBuildStageContext.Clear();
            }
        }

        [MenuItem("HAVENLINE Premium/CI/Prepare Deterministic Production Content")]
        private static void PrepareFromMenu() => PrepareGeneratedProductionContent();
    }
}
