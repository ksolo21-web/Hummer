using System;
using Havenline.Editor;
using NUnit.Framework;
using UnityEngine;

namespace Havenline.Tests
{
    public sealed class HavenlineReferenceContractTests
    {
        [Test]
        public void CameraAndSpawnMatchVerifiedReference()
        {
            Assert.That(Reference.CameraSize, Is.EqualTo(14.8f).Within(0.0001f));
            Assert.That(Reference.CameraOffset, Is.EqualTo(new Vector3(0f, 7f, 7f)));
            Assert.That(Reference.PlayerSpawn, Is.EqualTo(new Vector3(0f, 0.08f, 6.2f)));
        }

        [Test]
        public void MovementInventoryAndWorldBoundsMatchVerifiedReference()
        {
            Assert.That(Reference.WalkSpeed, Is.EqualTo(3.85f).Within(0.0001f));
            Assert.That(Reference.RunSpeed, Is.EqualTo(5.75f).Within(0.0001f));
            Assert.That(Reference.CarryCapacity, Is.EqualTo(6));
            Assert.That(Reference.InteractionRadius, Is.EqualTo(1.85f).Within(0.0001f));
            Assert.That(Reference.ClampToWorld(new Vector3(99f, 0f, -99f)),
                Is.EqualTo(new Vector3(Reference.BoundX, 0f, -Reference.BoundZ)));
        }

        [Test]
        public void FrozenOutpostLandmarksMatchVerifiedReference()
        {
            Assert.That(Reference.Furnace, Is.EqualTo(new Vector3(0f, 0f, 0.2f)));
            Assert.That(Reference.Survivor, Is.EqualTo(new Vector3(7.1f, 0f, -2.8f)));
            Assert.That(Reference.NorthBarricade, Is.EqualTo(new Vector3(0f, 0f, -10.7f)));
            Assert.That(Reference.SouthBarricade, Is.EqualTo(new Vector3(0f, 0f, 11.7f)));
            Assert.That(Reference.ForestGate, Is.EqualTo(new Vector3(0f, 0f, -14.8f)));
        }

        [Test]
        public void AndroidBuildEntryPointIsPresent()
        {
            var method = typeof(HavenlineBuildPipeline).GetMethod(
                nameof(HavenlineBuildPipeline.BuildAndroidReviewCandidate));
            Assert.That(method, Is.Not.Null);
            Assert.That(method!.IsStatic, Is.True);
            Assert.That(method.ReturnType, Is.EqualTo(typeof(void)));
        }
    }
}
