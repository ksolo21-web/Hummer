using UnityEditor;
using UnityEditor.Build;
using UnityEngine;

namespace Havenline.Editor
{
    /// <summary>
    /// Clean-checkout CI entry points.
    ///
    /// IMPORTANT: the former ProceduralArtStudio -> R31 -> R32 generation chain is retired.
    /// It produced the realistic/prototype survival-game drift that failed human visual review.
    /// A clean checkout must never silently recreate that rejected visual direction.
    ///
    /// Production preparation now fails closed until a replacement stylized animated production
    /// set, grounded in the approved example-video art language and approved 2D hero sheets,
    /// has been explicitly human-approved through the normal production and visual gates.
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

            var visualDirectionFailures =
                HavenlineVisualDirectionBuildGate.ValidateContract(HavenlineBuildStageContext.Current);
            if (visualDirectionFailures.Count > 0)
            {
                throw new BuildFailedException(
                    "HAVENLINE visual-direction contract failed before production preparation:\n - " +
                    string.Join("\n - ", visualDirectionFailures));
            }

            // DO NOT restore these calls:
            // HavenlineProceduralArtStudio.GenerateForCi();
            // HavenlineR31ProductionArtUpgrade.ApplyToGeneratedProduction();
            // HavenlineR32ProductionArtUpgrade.ApplyToGeneratedProduction();
            // HavenlineR32VisualRecoveryPass.ApplyToGeneratedProduction();
            //
            // They are retained only as historical source while the stylized replacement is
            // being authored. They are not an approved production-content generator.

            var prepared = HavenlinePremiumBuildGate.InspectProductionContent();
            if (!prepared.Passed)
            {
                throw new BuildFailedException(
                    "HAVENLINE production preparation is intentionally blocked. " +
                    "The rejected R28/R31/R32 realistic/prototype art will not be regenerated. " +
                    "Author the replacement stylized animated production set first, then obtain " +
                    "explicit human approval from actual Unity gameplay frames. Current gate failures:\n - " +
                    string.Join("\n - ", prepared.Failures));
            }

            preparedInCurrentEditorProcess = true;
            Debug.Log("HAVENLINE stylized animated production content is prepared and approval-gate clean.");
        }

        public static void BuildAndroidDeviceTest()
        {
            HavenlineBuildStageContext.Set(HavenlineBuildStage.DeviceTest);
            try
            {
                PrepareGeneratedProductionContent();
                HavenlineDeviceTestCharacterMaterialRestorer.PrepareForProof();
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

        [MenuItem("HAVENLINE Premium/CI/Validate Stylized Production Readiness")]
        private static void PrepareFromMenu() => PrepareGeneratedProductionContent();
    }
}
