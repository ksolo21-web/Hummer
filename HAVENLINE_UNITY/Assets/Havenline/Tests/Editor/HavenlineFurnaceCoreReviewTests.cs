using System.Linq;
using Havenline.Editor;
using NUnit.Framework;
using UnityEditor;
using UnityEngine;

namespace Havenline.Tests
{
    public sealed class HavenlineFurnaceCoreReviewTests
    {
        [Test]
        public void AuthoredFurnaceCoreHasThreeVolumetricTonguesAndEmberBed()
        {
            var owner = new GameObject("FurnaceCoreReviewOwner");
            try
            {
                var pulse = HavenlinePremiumFlameAuthoring.Build(owner.transform);
                Assert.That(pulse, Is.Not.Null);
                Assert.That(pulse.name, Is.EqualTo("FurnaceFlameVisual"));
                Assert.That(pulse.GetComponentsInChildren<ParticleSystem>(true), Is.Empty,
                    "The stable furnace core must not be built from particle billboards.");

                var renderers = pulse.GetComponentsInChildren<MeshRenderer>(true);
                var tongueRenderers = renderers
                    .Where(renderer => renderer.name.StartsWith("FlameTongue_"))
                    .ToArray();
                var emberRenderers = renderers
                    .Where(renderer => renderer.name.StartsWith("Ember_"))
                    .ToArray();

                Assert.That(tongueRenderers.Length, Is.EqualTo(6),
                    "Three outer/inner flame tongue pairs are required.");
                Assert.That(emberRenderers.Length, Is.GreaterThanOrEqualTo(7),
                    "A readable glowing ember bed is required beneath the flame tongues.");
                Assert.That(renderers.Any(renderer => renderer.name == "FlameTongue_Main_Outer"), Is.True);
                Assert.That(renderers.Any(renderer => renderer.name == "FlameTongue_Left_Outer"), Is.True);
                Assert.That(renderers.Any(renderer => renderer.name == "FlameTongue_Right_Outer"), Is.True);

                var tongueMesh = tongueRenderers
                    .First(renderer => renderer.name == "FlameTongue_Main_Outer")
                    .GetComponent<MeshFilter>()
                    .sharedMesh;
                Assert.That(tongueMesh, Is.Not.Null);
                Assert.That(AssetDatabase.GetAssetPath(tongueMesh),
                    Is.EqualTo(HavenlinePremiumFlameMeshFactory.FlameTongueMeshPath));
                Assert.That(tongueMesh.vertexCount, Is.GreaterThanOrEqualTo(24));
                Assert.That(tongueMesh.bounds.size.y, Is.GreaterThan(1.35f));
                Assert.That(tongueMesh.bounds.size.x, Is.GreaterThan(0.70f));
                Assert.That(tongueMesh.bounds.size.z, Is.GreaterThan(0.24f),
                    "The flame tongue must have authored depth and cannot collapse into a flat slash.");

                var combined = renderers[0].bounds;
                for (var index = 1; index < renderers.Length; index++)
                    combined.Encapsulate(renderers[index].bounds);
                Assert.That(combined.size.x, Is.GreaterThan(0.72f));
                Assert.That(combined.size.y, Is.GreaterThan(0.85f));
                Assert.That(combined.size.z, Is.GreaterThan(0.20f));
                Assert.That(combined.size.y / combined.size.x, Is.GreaterThan(0.82f),
                    "The complete firebox core must read vertically rather than as horizontal bars.");

                foreach (var renderer in tongueRenderers)
                {
                    var materialPath = AssetDatabase.GetAssetPath(renderer.sharedMaterial);
                    Assert.That(
                        materialPath == HavenlinePremiumVisualAssets.FlameOuterMaterialPath ||
                        materialPath == HavenlinePremiumVisualAssets.FlameInnerMaterialPath,
                        Is.True,
                        renderer.name + " is not using an authored HAVENLINE flame material.");
                }
            }
            finally
            {
                Object.DestroyImmediate(owner);
            }
        }
    }
}
