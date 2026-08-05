using System;
using System.IO;
using System.Linq;
using Havenline.Editor;
using NUnit.Framework;
using UnityEngine;

namespace Havenline.Tests
{
    public sealed class HavenlineReferenceContractTests
    {
        [Test]
        public void CameraKeepsTheCartoonSurvivorCloseAndReadable()
        {
            Assert.That(Reference.CameraSize, Is.EqualTo(9.45f).Within(0.0001f));
            Assert.That(Reference.CameraSize, Is.LessThanOrEqualTo(9.5f));
            Assert.That(Reference.CameraOffset, Is.EqualTo(new Vector3(0f, 5.25f, 8.15f)));
            Assert.That(Reference.CameraOffset.z, Is.GreaterThan(Reference.CameraOffset.y));
            Assert.That(Reference.CameraLookAhead, Is.LessThan(0.75f));
            Assert.That(Reference.PlayerSpawn, Is.EqualTo(new Vector3(0f, 0.08f, 6.2f)));
        }

        [Test]
        public void MovementCarryAndAutomaticActionRangesUseTheLockedDesign()
        {
            Assert.That(Reference.WalkSpeed, Is.EqualTo(3.9f).Within(0.0001f));
            Assert.That(Reference.RunSpeed, Is.EqualTo(5.85f).Within(0.0001f));
            Assert.That(Reference.CarryCapacity, Is.EqualTo(8));
            Assert.That(Reference.InteractionRadius, Is.EqualTo(1.9f).Within(0.0001f));
            Assert.That(Reference.CombatRadius, Is.GreaterThan(Reference.InteractionRadius));
            Assert.That(Reference.DepositRadius, Is.GreaterThan(Reference.InteractionRadius));
            Assert.That(Reference.ClampToWorld(new Vector3(99f, 0f, -99f)),
                Is.EqualTo(new Vector3(Reference.BoundX, 0f, -Reference.BoundZ)));
        }

        [Test]
        public void AutomaticActionKindsCoverTheCompleteOpeningLoop()
        {
            var values = Enum.GetValues(typeof(AutomaticActionKind)).Cast<AutomaticActionKind>().ToArray();
            CollectionAssert.Contains(values, AutomaticActionKind.GatherWood);
            CollectionAssert.Contains(values, AutomaticActionKind.GatherStone);
            CollectionAssert.Contains(values, AutomaticActionKind.Deposit);
            CollectionAssert.Contains(values, AutomaticActionKind.Rescue);
            CollectionAssert.Contains(values, AutomaticActionKind.Build);
            CollectionAssert.Contains(values, AutomaticActionKind.Repair);
            CollectionAssert.Contains(values, AutomaticActionKind.Combat);
            Assert.That(typeof(HavenlineAutomaticActionController).IsSealed, Is.True);
            Assert.That(typeof(HavenlineResourceNode).IsSubclassOf(typeof(HavenlineInteractable)), Is.True);
            Assert.That(typeof(HavenlineFurnace).IsSubclassOf(typeof(HavenlineInteractable)), Is.True);
            Assert.That(typeof(HavenlineEnemy).IsSubclassOf(typeof(HavenlineInteractable)), Is.True);
        }

        [Test]
        public void FrameTargetsAreExactlySixtyNinetyAndOneTwenty()
        {
            Assert.That(Reference.MinimumFrameRate, Is.EqualTo(60));
            Assert.That(Reference.BalancedFrameRate, Is.EqualTo(90));
            Assert.That(Reference.MaximumFrameRate, Is.EqualTo(120));
            Assert.That(Enum.GetValues(typeof(HavenlineFrameMode)).Length, Is.EqualTo(4));
            Assert.That(Enum.GetValues(typeof(HavenlineQualityTier)).Length, Is.EqualTo(4));
        }

        [Test]
        public void FrozenOutpostStillOpensIntoTheConnectedWorld()
        {
            Assert.That(Reference.Furnace, Is.EqualTo(new Vector3(0f, 0f, 0.2f)));
            Assert.That(Reference.Survivor, Is.EqualTo(new Vector3(7.1f, 0f, -2.8f)));
            Assert.That(Reference.NorthBarricade, Is.EqualTo(new Vector3(0f, 0f, -10.7f)));
            Assert.That(Reference.SouthBarricade, Is.EqualTo(new Vector3(0f, 0f, 11.7f)));
            Assert.That(Reference.ForestGate, Is.EqualTo(new Vector3(0f, 0f, -14.8f)));
        }

        [Test]
        [Timeout(900000)]
        public void ProductionManifestHasStrictBlockedOrApprovedLifecycleState()
        {
            Assert.That(File.Exists(HavenlinePremiumBuildGate.ManifestPath), Is.True);
            var json = File.ReadAllText(HavenlinePremiumBuildGate.ManifestPath);
            var manifest = JsonUtility.FromJson<HavenlinePremiumBuildGate.ProductionArtManifest>(json);
            Assert.That(manifest, Is.Not.Null);
            Assert.That(manifest.schemaVersion, Is.EqualTo(HavenlinePremiumBuildGate.CurrentSchemaVersion));
            Assert.That(manifest.minimumEnvironmentModels, Is.GreaterThanOrEqualTo(28));
            Assert.That(manifest.minimumAnimationClips, Is.GreaterThanOrEqualTo(32));
            Assert.That(manifest.minimumAudioClips, Is.GreaterThanOrEqualTo(36));

            if (!manifest.approved)
            {
                Assert.That(manifest.artVersion, Does.Contain("blocked"));
                Assert.That(manifest.approvedBy, Is.Null.Or.Empty);
                return;
            }

            Assert.That(manifest.artVersion, Does.Not.Contain("blocked"));
            Assert.That(manifest.approvedBy, Is.Not.Null.And.Not.Empty);
            Assert.That(manifest.approvalNote, Does.Contain("Revision 25"));
            Assert.That(manifest.approvalNote, Does.Contain("device-test"));

            HavenlineCiBuildEntryPoints.PrepareGeneratedProductionContent();
            var production = HavenlinePremiumBuildGate.InspectProductionContent();
            Assert.That(production.Passed, Is.True,
                "Approved production manifest failed after deterministic clean-checkout preparation:\n - " +
                string.Join("\n - ", production.Failures));
        }

        [Test]
        public void DeviceTestAndVerifiedReleaseAreSeparatePreparedBuildStages()
        {
            var legacyDeviceTest = typeof(HavenlineBuildPipeline).GetMethod(
                nameof(HavenlineBuildPipeline.BuildAndroidReviewCandidate));
            var legacyVerifiedRelease = typeof(HavenlineBuildPipeline).GetMethod(
                nameof(HavenlineBuildPipeline.BuildVerifiedReleaseCandidate));
            var preparedDeviceTest = typeof(HavenlineCiBuildEntryPoints).GetMethod(
                nameof(HavenlineCiBuildEntryPoints.BuildAndroidDeviceTest));
            var preparedVerifiedRelease = typeof(HavenlineCiBuildEntryPoints).GetMethod(
                nameof(HavenlineCiBuildEntryPoints.BuildVerifiedRelease));
            var preparation = typeof(HavenlineCiBuildEntryPoints).GetMethod(
                nameof(HavenlineCiBuildEntryPoints.PrepareGeneratedProductionContent));

            Assert.That(legacyDeviceTest, Is.Not.Null);
            Assert.That(legacyVerifiedRelease, Is.Not.Null);
            Assert.That(preparedDeviceTest, Is.Not.Null);
            Assert.That(preparedVerifiedRelease, Is.Not.Null);
            Assert.That(preparation, Is.Not.Null);
            Assert.That(preparedDeviceTest!.IsStatic, Is.True);
            Assert.That(preparedVerifiedRelease!.IsStatic, Is.True);
            Assert.That(preparation!.IsStatic, Is.True);
            Assert.That(preparedDeviceTest.ReturnType, Is.EqualTo(typeof(void)));
            Assert.That(preparedVerifiedRelease.ReturnType, Is.EqualTo(typeof(void)));
            Assert.That(preparation.ReturnType, Is.EqualTo(typeof(void)));
            Assert.That(preparedDeviceTest.Name, Is.Not.EqualTo(preparedVerifiedRelease.Name));
        }
    }
}
