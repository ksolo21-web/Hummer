using Havenline.Editor;
using NUnit.Framework;
using UnityEngine;

namespace Havenline.Tests
{
    public sealed class HavenlineFurnaceCoreReviewTests
    {
        [Test]
        public void FurnaceCoreMeshesCannotCollapseIntoBillboardsOrHorizontalSlashes()
        {
            var tongue = HavenlinePremiumFlameMeshFactory.CreateFlameTongueMesh();
            var ember = HavenlinePremiumFlameMeshFactory.CreateEmberMesh();
            try
            {
                Assert.That(tongue, Is.Not.Null);
                Assert.That(tongue.vertexCount, Is.GreaterThanOrEqualTo(24));
                Assert.That(tongue.triangles.Length, Is.GreaterThanOrEqualTo(120));
                Assert.That(tongue.bounds.size.y, Is.GreaterThan(1.35f));
                Assert.That(tongue.bounds.size.x, Is.GreaterThan(0.70f));
                Assert.That(tongue.bounds.size.z, Is.GreaterThan(0.24f),
                    "The flame tongue must have authored depth and cannot collapse into a billboard.");
                Assert.That(tongue.bounds.size.y / tongue.bounds.size.x, Is.GreaterThan(1.75f),
                    "The flame silhouette must remain visibly vertical instead of reading as a slash.");

                Assert.That(ember, Is.Not.Null);
                Assert.That(ember.vertexCount, Is.GreaterThanOrEqualTo(60));
                Assert.That(ember.triangles.Length, Is.GreaterThanOrEqualTo(300));
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
    }
}
