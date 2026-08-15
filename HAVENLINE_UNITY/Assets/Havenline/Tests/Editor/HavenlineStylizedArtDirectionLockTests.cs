using System;
using System.IO;
using NUnit.Framework;
using UnityEngine;

namespace Havenline.Tests
{
    public sealed class HavenlineStylizedArtDirectionLockTests
    {
        private const string VisualContractPath =
            "Assets/Havenline/Reference/HAVENLINE_VISUAL_DIRECTION_CONTRACT.json";
        private const string ProductionManifestPath =
            "Assets/Havenline/Art/Production/HAVENLINE_PRODUCTION_ART.json";
        private const string CiEntryPath =
            "Assets/Havenline/Editor/HavenlineCiBuildEntryPoints.cs";

        [Serializable]
        private sealed class VisualContract
        {
            public Authority authority;
            public Style style;
            public Characters characters;
            public Fab fab;
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
        }

        [Serializable]
        private sealed class Fab
        {
            public string role;
        }

        [Serializable]
        private sealed class HumanGate
        {
            public bool humanVisualApprovalRequired;
            public string shippingVisualStatus;
        }

        [Serializable]
        private sealed class ProductionManifest
        {
            public string artVersion;
            public bool approved;
            public string approvedBy;
            public string approvalNote;
        }

        [Test]
        public void ExampleVideoAndApproved2DTurnaroundsRemainTheVisualAuthorities()
        {
            Assert.That(File.Exists(VisualContractPath), Is.True);
            var contract = JsonUtility.FromJson<VisualContract>(File.ReadAllText(VisualContractPath));
            Assert.That(contract, Is.Not.Null);
            Assert.That(contract.authority.exampleVideoIsVisualAuthority, Is.True);
            Assert.That(contract.authority.approved2DCharacterTurnaroundsAreIdentityAuthority, Is.True);
            Assert.That(contract.authority.referenceGameRemainsGameplayAuthority, Is.True);
            Assert.That(contract.style.targetStyle, Is.EqualTo("stylized-animated-3d-survival"));
            Assert.That(contract.style.photorealisticSurvivalAestheticAllowed, Is.False);
            Assert.That(contract.style.genericUnityAssetPackLookAllowed, Is.False);
            Assert.That(contract.style.oneUnifiedAnimatedArtLanguage, Is.True);
            Assert.That(contract.style.styleMismatchIsAutomaticRejection, Is.True);
        }

        [Test]
        public void FabCannotReplaceTheApprovedFourHeroes()
        {
            var contract = JsonUtility.FromJson<VisualContract>(File.ReadAllText(VisualContractPath));
            Assert.That(contract.characters.customOrRemodeledCharactersAllowed, Is.True);
            Assert.That(contract.characters.fabHeroReplacementAllowed, Is.False);
            Assert.That(contract.characters.fabSupportingAssetsAllowed, Is.True);
            Assert.That(contract.characters.mustMatchApproved2DIdentity, Is.True);
            Assert.That(contract.fab.role, Is.EqualTo("supporting-assets-only"));
        }

        [Test]
        public void RejectedR28R31R32ProductionArtCannotPretendToBeApproved()
        {
            var manifest = JsonUtility.FromJson<ProductionManifest>(File.ReadAllText(ProductionManifestPath));
            Assert.That(manifest.approved, Is.False);
            Assert.That(manifest.approvedBy, Is.Empty);
            Assert.That(manifest.artVersion, Does.Contain("blocked"));
            Assert.That(manifest.approvalNote, Does.Contain("rejected"));

            var contract = JsonUtility.FromJson<VisualContract>(File.ReadAllText(VisualContractPath));
            Assert.That(contract.humanGate.humanVisualApprovalRequired, Is.True);
            Assert.That(contract.humanGate.shippingVisualStatus, Is.EqualTo("blocked"));
        }

        [Test]
        public void CleanCheckoutCiDoesNotExecuteTheRejectedLegacyArtChain()
        {
            var source = File.ReadAllText(CiEntryPath).Replace("\r\n", "\n");
            Assert.That(source, Does.Not.Contain("\n            HavenlineProceduralArtStudio.GenerateForCi();\n"));
            Assert.That(source, Does.Not.Contain("\n            HavenlineR31ProductionArtUpgrade.ApplyToGeneratedProduction();\n"));
            Assert.That(source, Does.Not.Contain("\n            HavenlineR32ProductionArtUpgrade.ApplyToGeneratedProduction();\n"));
            Assert.That(source, Does.Not.Contain("\n            HavenlineR32VisualRecoveryPass.ApplyToGeneratedProduction();\n"));
        }
    }
}
