using System;
using System.Collections.Generic;
using System.IO;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEngine;

namespace Havenline.Editor
{
    /// <summary>
    /// Fail-closed art-direction contract for Havenline.
    /// Device-test builds may continue while art is blocked so we can iterate on hardware,
    /// but verified-release builds cannot ship until the animated visual language and the
    /// approved 2D character identities have received explicit human approval.
    /// </summary>
    public sealed class HavenlineVisualDirectionBuildGate : IPreprocessBuildWithReport
    {
        internal const string ContractPath =
            "Assets/Havenline/Reference/HAVENLINE_VISUAL_DIRECTION_CONTRACT.json";

        public int callbackOrder => -2600;

        [Serializable]
        private sealed class VisualDirectionContract
        {
            public int schemaVersion;
            public string visualDirectionVersion;
            public Authority authority;
            public Style style;
            public Characters characters;
            public Fab fab;
            public World world;
            public HumanGate humanGate;
        }

        [Serializable]
        private sealed class Authority
        {
            public bool exampleVideoIsVisualAuthority;
            public bool approved2DCharacterTurnaroundsAreIdentityAuthority;
            public bool referenceGameRemainsGameplayAuthority;
        }

        [Serializable]
        private sealed class Style
        {
            public string targetStyle;
            public bool photorealisticSurvivalAestheticAllowed;
            public bool genericUnityAssetPackLookAllowed;
            public bool environmentMustMatchAnimatedLanguage;
            public bool oneUnifiedAnimatedArtLanguage;
            public bool styleMismatchIsAutomaticRejection;
        }

        [Serializable]
        private sealed class Characters
        {
            public bool customOrRemodeledCharactersAllowed;
            public bool fabHeroReplacementAllowed;
            public bool fabSupportingAssetsAllowed;
            public bool mustMatchApproved2DIdentity;
            public string requiredPipeline;
        }

        [Serializable]
        private sealed class Fab
        {
            public string role;
            public string[] allowedExamples = Array.Empty<string>();
            public string[] disallowedExamples = Array.Empty<string>();
        }

        [Serializable]
        private sealed class World
        {
            public bool charactersStylized;
            public bool snowIceRocksTreesTerrainStylized;
            public bool buildingsAndPropsStylized;
            public bool wolvesStylized;
            public bool furnaceFireSmokeSnowFxStylized;
            public bool lightingStylizedCinematic;
            public bool mobileGameplayReadabilityRequired;
        }

        [Serializable]
        private sealed class HumanGate
        {
            public bool humanVisualApprovalRequired;
            public string shippingVisualStatus;
            public string approvedBy;
            public string approvalNote;
        }

        public void OnPreprocessBuild(BuildReport report)
        {
            var failures = ValidateContract(HavenlineBuildStageContext.Current);
            if (failures.Count == 0)
                return;

            throw new BuildFailedException(
                "HAVENLINE visual-direction gate failed:\n - " +
                string.Join("\n - ", failures));
        }

        internal static List<string> ValidateContract(HavenlineBuildStage stage)
        {
            var failures = new List<string>();
            if (!File.Exists(ContractPath))
            {
                failures.Add($"Visual-direction contract is missing at {ContractPath}.");
                return failures;
            }

            VisualDirectionContract contract;
            try
            {
                contract = JsonUtility.FromJson<VisualDirectionContract>(File.ReadAllText(ContractPath));
            }
            catch (Exception exception)
            {
                failures.Add($"Visual-direction contract is invalid JSON: {exception.Message}");
                return failures;
            }

            if (contract == null)
            {
                failures.Add("Visual-direction contract is empty.");
                return failures;
            }

            if (contract.schemaVersion != 1)
                failures.Add($"Visual-direction schema must be 1; found {contract.schemaVersion}.");
            if (!string.Equals(contract.visualDirectionVersion, "1.0", StringComparison.Ordinal))
                failures.Add("visualDirectionVersion must be 1.0.");

            if (contract.authority == null)
            {
                failures.Add("Visual-direction authority block is missing.");
            }
            else
            {
                if (!contract.authority.exampleVideoIsVisualAuthority)
                    failures.Add("The approved example video must remain the visual-style authority.");
                if (!contract.authority.approved2DCharacterTurnaroundsAreIdentityAuthority)
                    failures.Add("The approved 2D character turnarounds must remain the character identity authority.");
                if (!contract.authority.referenceGameRemainsGameplayAuthority)
                    failures.Add("The verified reference game must remain the gameplay authority.");
            }

            if (contract.style == null)
            {
                failures.Add("Visual-direction style block is missing.");
            }
            else
            {
                if (!string.Equals(contract.style.targetStyle, "stylized-animated-3d-survival", StringComparison.Ordinal))
                    failures.Add("Target style must be stylized-animated-3d-survival.");
                if (contract.style.photorealisticSurvivalAestheticAllowed)
                    failures.Add("Photorealistic/real-world survival styling is not allowed.");
                if (contract.style.genericUnityAssetPackLookAllowed)
                    failures.Add("A generic Unity asset-pack survival look is not allowed.");
                if (!contract.style.environmentMustMatchAnimatedLanguage)
                    failures.Add("The environment must match the animated visual language.");
                if (!contract.style.oneUnifiedAnimatedArtLanguage)
                    failures.Add("Characters and world must use one unified animated art language.");
                if (!contract.style.styleMismatchIsAutomaticRejection)
                    failures.Add("Visual-style mismatch must remain an automatic rejection.");
            }

            if (contract.characters == null)
            {
                failures.Add("Visual-direction characters block is missing.");
            }
            else
            {
                if (!contract.characters.customOrRemodeledCharactersAllowed)
                    failures.Add("Custom or remodeled production characters must be allowed.");
                if (contract.characters.fabHeroReplacementAllowed)
                    failures.Add("Fab/marketplace characters may not replace the four approved heroes.");
                if (!contract.characters.fabSupportingAssetsAllowed)
                    failures.Add("Fab may remain available for supporting assets and animation support.");
                if (!contract.characters.mustMatchApproved2DIdentity)
                    failures.Add("Production characters must match the approved 2D identities.");
                if (string.IsNullOrWhiteSpace(contract.characters.requiredPipeline) ||
                    !contract.characters.requiredPipeline.StartsWith("approved-2d->", StringComparison.Ordinal))
                {
                    failures.Add("The production character pipeline must begin from the approved 2D artwork.");
                }
            }

            if (contract.fab == null ||
                !string.Equals(contract.fab.role, "supporting-assets-only", StringComparison.Ordinal))
            {
                failures.Add("Fab must be restricted to a supporting-assets-only role.");
            }

            if (contract.world == null)
            {
                failures.Add("Visual-direction world block is missing.");
            }
            else if (!contract.world.charactersStylized ||
                     !contract.world.snowIceRocksTreesTerrainStylized ||
                     !contract.world.buildingsAndPropsStylized ||
                     !contract.world.wolvesStylized ||
                     !contract.world.furnaceFireSmokeSnowFxStylized ||
                     !contract.world.lightingStylizedCinematic ||
                     !contract.world.mobileGameplayReadabilityRequired)
            {
                failures.Add("Every major world category must remain locked to the stylized animated direction and mobile readability target.");
            }

            if (contract.humanGate == null)
            {
                failures.Add("Human visual gate is missing.");
                return failures;
            }
            if (!contract.humanGate.humanVisualApprovalRequired)
                failures.Add("Human visual approval may not be bypassed.");

            if (stage == HavenlineBuildStage.VerifiedRelease)
            {
                if (!string.Equals(contract.humanGate.shippingVisualStatus, "approved", StringComparison.OrdinalIgnoreCase))
                    failures.Add("Verified release is blocked until the shipping visual status is explicitly approved.");
                if (string.IsNullOrWhiteSpace(contract.humanGate.approvedBy))
                    failures.Add("Verified release requires a named human visual approver.");
                if (string.IsNullOrWhiteSpace(contract.humanGate.approvalNote))
                    failures.Add("Verified release requires a visual approval note tied to actual Unity gameplay frames.");
            }

            return failures;
        }
    }
}
