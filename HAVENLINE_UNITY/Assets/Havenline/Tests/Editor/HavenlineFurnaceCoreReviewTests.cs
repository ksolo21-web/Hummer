using Havenline.Editor;
using NUnit.Framework;
using UnityEngine;

namespace Havenline.Tests
{
    public sealed class HavenlineFurnaceCoreReviewTests
    {
        [Test]
        public void FurnaceCoreMeshesCannotCollapseIntoIconsBillboardsOrHorizontalSlashes()
        {
            var tongue = HavenlinePremiumFlameMeshFactory.CreateFlameTongueMesh();
            var ember = HavenlinePremiumFlameMeshFactory.CreateEmberMesh();
            try
            {
                Assert.That(tongue, Is.Not.Null);
                Assert.That(tongue.vertexCount, Is.GreaterThanOrEqualTo(48),
                    "The flame must use multiple radial rings rather than two front/back faces.");
                Assert.That(tongue.triangles.Length, Is.GreaterThanOrEqualTo(280));
                Assert.That(tongue.bounds.size.y, Is.GreaterThan(1.38f));
                Assert.That(tongue.bounds.size.x, Is.GreaterThan(0.68f));
                Assert.That(tongue.bounds.size.z, Is.GreaterThan(0.50f),
                    "The flame tongue must have substantial radial depth and cannot collapse into a billboard.");
                Assert.That(tongue.bounds.size.z / tongue.bounds.size.x, Is.GreaterThan(0.66f),
                    "The furnace flame cannot be a thin extruded icon.");
                Assert.That(tongue.bounds.size.y / tongue.bounds.size.x, Is.GreaterThan(1.72f),
                    "The flame volume must remain visibly vertical instead of reading as a slash.");

                Assert.That(ember, Is.Not.Null);
                Assert.That(ember.vertexCount, Is.GreaterThanOrEqualTo(50));
                Assert.That(ember.triangles.Length, Is.GreaterThanOrEqualTo(240));
                Assert.That(ember.bounds.size.x, Is.GreaterThan(0.9f));
                Assert.That(ember.bounds.size.y, Is.GreaterThan(0.9f));
                Assert.That(ember.bounds.size.z, Is.GreaterThan(0.9f));
            }
            finally
            {
                Object.DestroyImmediate(tongue);
                Object.DestroyImmediate(ember);
            }
        }

        [Test]
        public void FurnaceMaterialsRemainReadableBeforeEmissionAndDoNotClipToWhite()
        {
            var outerBase = HavenlinePremiumFlameMeshFactory.FlameOuterBase;
            var outerEmission = HavenlinePremiumFlameMeshFactory.FlameOuterEmission;
            var innerBase = HavenlinePremiumFlameMeshFactory.FlameInnerBase;
            var innerEmission = HavenlinePremiumFlameMeshFactory.FlameInnerEmission;

            Assert.That(outerBase.r, Is.InRange(0.62f, 0.78f),
                "Outer flame base must remain visible before an emissive shader variant warms up.");
            Assert.That(outerBase.g, Is.InRange(0.055f, 0.14f));
            Assert.That(outerEmission.r, Is.InRange(0.90f, 1.20f),
                "Outer emission must be warm but restrained.");
            Assert.That(innerBase.r, Is.InRange(0.92f, 1f));
            Assert.That(innerBase.g, Is.InRange(0.24f, 0.40f));
            Assert.That(innerEmission.r, Is.InRange(1.20f, 1.60f),
                "Inner emission must not be driven into white clipping.");
            Assert.That(innerEmission.g, Is.InRange(0.25f, 0.45f));
        }
    }
}
