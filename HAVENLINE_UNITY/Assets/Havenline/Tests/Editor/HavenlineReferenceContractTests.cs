using System;
using System.IO;
using System.Linq;
using Havenline.Editor;
using NUnit.Framework;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.UI;

namespace Havenline.Tests
{
    public sealed class HavenlineReferenceContractTests
    {
        [Test]
        public void CameraKeepsTheCartoonSurvivorCloseAndReadable()
        {
            Assert.That(Reference.CameraSize, Is.EqualTo(7.15f).Within(0.0001f));
            Assert.That(Reference.CameraSize, Is.LessThanOrEqualTo(7.2f));
            Assert.That(Reference.CameraOffset, Is.EqualTo(new Vector3(0f, 6.80f, 8.60f)));
            Assert.That(Reference.CameraOffset.z, Is.GreaterThan(Reference.CameraOffset.y));
            Assert.That(Reference.CameraLookAhead, Is.LessThan(0.75f));
            Assert.That(Reference.PlayerSpawn, Is.EqualTo(new Vector3(0f, 0.08f, 6.2f)));
        }

        [Test]
        public void MovementUncappedCarryAndAutomaticActionRangesUseTheLockedDesign()
        {
            Assert.That(Reference.WalkSpeed, Is.EqualTo(3.9f).Within(0.0001f));
            Assert.That(Reference.RunSpeed, Is.EqualTo(5.85f).Within(0.0001f));
            Assert.That(Reference.UnlimitedCarry, Is.True);
            Assert.That(Reference.CarryCapacity, Is.EqualTo(0));
            Assert.That(Reference.VisibleCarrySlots, Is.GreaterThanOrEqualTo(24));
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

            var scene = EditorSceneManager.OpenScene(Reference.ScenePath);
            var objects = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
                .Select(transform => transform.gameObject)
                .ToArray();

            Assert.That(objects.Any(item => item.name == "ReferenceGradeVisualRebuild"), Is.True,
                "The rejected arena composition was authored without the reference-grade rebuild pass.");

            var mainCamera = objects.SelectMany(item => item.GetComponents<Camera>())
                .Single(camera => camera.CompareTag("MainCamera"));
            Assert.That(mainCamera.orthographicSize, Is.LessThanOrEqualTo(7.2f));

            foreach (var hiddenName in new[] { "IceShelf", "SnowIsland", "WarmthBoundary" })
            {
                var rejectedVisuals = objects.Where(item => item.name == hiddenName).ToArray();
                Assert.That(rejectedVisuals, Is.Not.Empty,
                    $"Rejected circular-arena visual is missing from the authored lifecycle check: {hiddenName}");
                Assert.That(rejectedVisuals
                        .SelectMany(item => item.GetComponentsInChildren<Renderer>(true))
                        .All(renderer => !renderer.enabled),
                    Is.True, $"Rejected circular-arena visual is still rendered: {hiddenName}");
            }

            for (var level = 1; level <= 4; level++)
            {
                var stage = objects.Single(item => item.name == $"FurnaceLevel{level}");
                Assert.That(stage.GetComponentsInChildren<Transform>(true)
                        .Any(item => item.name == $"ReferenceFurnaceAssemblyL{level}"), Is.True,
                    $"Furnace level {level} did not receive the rounded reference-grade machine assembly.");
            }

            var resourcesPanel = objects.Single(item => item.name == "ResourcesPanel").GetComponent<Image>();
            Assert.That(resourcesPanel.rectTransform.sizeDelta.x, Is.LessThanOrEqualTo(430f));
            Assert.That(resourcesPanel.rectTransform.sizeDelta.y, Is.LessThanOrEqualTo(72f));
            Assert.That(objects.Where(item => item.name.StartsWith("HudAccent_", StringComparison.Ordinal))
                .All(item => !item.activeSelf), Is.True);

            var hudFonts = objects.SelectMany(item => item.GetComponents<Text>())
                .Select(text => text.font)
                .Where(font => font != null)
                .Distinct()
                .ToArray();
            Assert.That(hudFonts, Has.Length.EqualTo(1));
            Assert.That(hudFonts[0].name, Does.Contain("Rounded_Geometric"));
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
