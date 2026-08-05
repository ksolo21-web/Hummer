using System;
using System.IO;
using System.Linq;
using Havenline.Editor;
using NUnit.Framework;
using UnityEditor;
using UnityEngine;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

namespace Havenline.Tests
{
    [TestFixture]
    [Category("StudioReview")]
    public sealed class HavenlineStudioReviewTests
    {
        [Test]
        [Timeout(900000)]
        public void DeterministicStudioAuthorsAndValidatesPremiumReviewScene()
        {
            HavenlineProceduralArtStudio.GenerateForCi();

            var reviewRoot = "Builds/Review/HAVENLINE-Studio";
            var wide = Path.Combine(reviewRoot, "HAVENLINE-studio-wide-1920x1080.png");
            var close = Path.Combine(reviewRoot, "HAVENLINE-studio-close-1920x1080.png");
            var foldable = Path.Combine(reviewRoot, "HAVENLINE-studio-foldable-2208x1840.png");
            Assert.That(File.Exists(Path.Combine(reviewRoot, "HAVENLINE-studio-report.json")), Is.True);
            Assert.That(File.Exists(wide), Is.True);
            Assert.That(File.Exists(close), Is.True);
            Assert.That(File.Exists(foldable), Is.True);
            Assert.That(new FileInfo(wide).Length, Is.GreaterThan(100_000));
            Assert.That(new FileInfo(close).Length, Is.GreaterThan(100_000));

            ValidateTopHudCards();
            ValidateConstructionStageReadability();
            ValidatePremiumProof(wide, "wide phone proof");
            ValidatePremiumProof(close, "close phone proof");
            ValidatePremiumProof(foldable, "foldable proof");
        }

        private static void ValidateTopHudCards()
        {
            var images = SceneManager.GetActiveScene()
                .GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Image>(true))
                .ToArray();
            var topNames = new[] { "ResourcesPanel", "ObjectivePanel", "FurnacePanel" };
            foreach (var name in topNames)
            {
                var image = images.SingleOrDefault(candidate => candidate.name == name);
                Assert.That(image, Is.Not.Null, "Shipping HUD is missing " + name);
                Assert.That(image.color.a, Is.GreaterThanOrEqualTo(0.99f),
                    name + " must be opaque enough to prevent world props reading through as UI glyphs.");
                Assert.That(image.sprite, Is.Not.Null, name + " has no rounded panel sprite.");
                Assert.That(AssetDatabase.GetAssetPath(image.sprite), Does.Contain("HAVENLINE_UI_TopPanel.asset"),
                    name + " is not using the dedicated top-status panel sprite.");
                var texture = image.sprite.texture;
                var centerAlpha = texture.GetPixel(texture.width / 2, texture.height / 2).a;
                Assert.That(centerAlpha, Is.GreaterThanOrEqualTo(0.96f),
                    name + " panel texture remains too translucent at its center.");
            }

            var context = images.SingleOrDefault(candidate => candidate.name == "ContextPanel");
            Assert.That(context, Is.Not.Null);
            Assert.That(AssetDatabase.GetAssetPath(context.sprite), Does.Contain("HAVENLINE_UI_Panel.asset"),
                "Context UI should retain the lighter standard overlay instead of using the heavy top card.");
        }

        private static void ValidateConstructionStageReadability()
        {
            var sites = SceneManager.GetActiveScene()
                .GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<HavenlineConstructionSite>(true))
                .OrderBy(site => site.BuildId, StringComparer.Ordinal)
                .ToArray();
            Assert.That(sites.Length, Is.EqualTo(2),
                "The premium frozen outpost requires north and south barricade construction sites.");

            var stageNames = new[] { "ConstructionStageA", "ConstructionStageB", "ConstructionStageC" };
            var minimumHeights = new[] { 0.69f, 0.99f, 1.19f };
            var minimumWidths = new[] { 1.75f, 2.50f, 3.05f };
            foreach (var site in sites)
            {
                var previousHeight = 0f;
                var previousWidth = 0f;
                for (var index = 0; index < stageNames.Length; index++)
                {
                    var stage = site.GetComponentsInChildren<Transform>(true)
                        .Select(transform => transform.gameObject)
                        .SingleOrDefault(candidate => candidate.name == stageNames[index]);
                    Assert.That(stage, Is.Not.Null, $"{site.BuildId} is missing {stageNames[index]}.");

                    var bounds = RendererBounds(stage);
                    var width = Mathf.Max(bounds.size.x, bounds.size.z);
                    Assert.That(bounds.size.y, Is.GreaterThanOrEqualTo(minimumHeights[index]),
                        $"{site.BuildId} {stageNames[index]} is too short and reads like punctuation from the gameplay camera.");
                    Assert.That(width, Is.GreaterThanOrEqualTo(minimumWidths[index]),
                        $"{site.BuildId} {stageNames[index]} is too narrow to read as a barricade under construction.");
                    Assert.That(bounds.size.y, Is.GreaterThan(previousHeight + 0.12f),
                        $"{site.BuildId} stage heights do not communicate visible construction growth.");
                    Assert.That(width, Is.GreaterThan(previousWidth + 0.35f),
                        $"{site.BuildId} stage widths do not communicate visible construction growth.");
                    previousHeight = bounds.size.y;
                    previousWidth = width;
                }
            }
        }

        private static Bounds RendererBounds(GameObject root)
        {
            var wasActive = root.activeSelf;
            root.SetActive(true);
            try
            {
                var renderers = root.GetComponentsInChildren<Renderer>(true);
                Assert.That(renderers, Is.Not.Empty, root.name + " has no renderable construction geometry.");
                var bounds = renderers[0].bounds;
                for (var index = 1; index < renderers.Length; index++)
                    bounds.Encapsulate(renderers[index].bounds);
                Assert.That(bounds.size.sqrMagnitude, Is.GreaterThan(0.000001f),
                    root.name + " has collapsed renderer bounds.");
                return bounds;
            }
            finally
            {
                root.SetActive(wasActive);
            }
        }

        private static void ValidatePremiumProof(string path, string label)
        {
            var texture = new Texture2D(2, 2, TextureFormat.RGB24, false, false);
            try
            {
                Assert.That(texture.LoadImage(File.ReadAllBytes(path), false), Is.True, label + " could not be decoded");
                long samples = 0;
                long dark = 0;
                long snow = 0;
                long warm = 0;
                long cold = 0;
                double luminance = 0d;
                double luminanceSquared = 0d;
                for (var y = 0; y < texture.height; y += 4)
                {
                    for (var x = 0; x < texture.width; x += 4)
                    {
                        var color = texture.GetPixel(x, y).linear;
                        var value = color.r * 0.2126f + color.g * 0.7152f + color.b * 0.0722f;
                        samples++;
                        luminance += value;
                        luminanceSquared += value * value;
                        if (value < 0.13f) dark++;
                        if (value > 0.62f && color.b > 0.55f) snow++;
                        if (color.r > 0.45f && color.r > color.g * 1.10f && color.r > color.b * 1.32f) warm++;
                        if (color.b > 0.28f && color.b > color.r * 1.08f) cold++;
                    }
                }

                var mean = luminance / samples;
                var deviation = Math.Sqrt(Math.Max(0d, luminanceSquared / samples - mean * mean));
                Assert.That(dark / (double)samples, Is.GreaterThan(0.035d), label + " lacks atmospheric dark depth");
                Assert.That(snow / (double)samples, Is.GreaterThan(0.055d), label + " lacks readable pale snow");
                Assert.That(warm / (double)samples, Is.GreaterThan(0.0012d), label + " lacks furnace/fire warmth");
                Assert.That(cold / (double)samples, Is.GreaterThan(0.045d), label + " lacks a cold winter palette");
                Assert.That(deviation, Is.GreaterThan(0.13d), label + " remains visually flat");

                AssertHudRegion(texture, label, 0f, 0.76f, 0.30f, 1f, false);
                AssertHudRegion(texture, label, 0f, 0f, 0.27f, 0.34f, true);
            }
            finally
            {
                UnityEngine.Object.DestroyImmediate(texture);
            }
        }

        private static void AssertHudRegion(
            Texture2D texture,
            string label,
            float minimumX,
            float minimumY,
            float maximumX,
            float maximumY,
            bool joystickRegion)
        {
            var startX = Mathf.FloorToInt(texture.width * minimumX);
            var endX = Mathf.CeilToInt(texture.width * maximumX);
            var startY = Mathf.FloorToInt(texture.height * minimumY);
            var endY = Mathf.CeilToInt(texture.height * maximumY);
            long samples = 0;
            long interfacePixels = 0;
            for (var y = startY; y < endY; y += 3)
            {
                for (var x = startX; x < endX; x += 3)
                {
                    var color = texture.GetPixel(x, y).linear;
                    samples++;
                    if (joystickRegion)
                    {
                        if (color.b > 0.18f && color.g > color.r * 1.15f)
                            interfacePixels++;
                    }
                    else
                    {
                        var value = color.r * 0.2126f + color.g * 0.7152f + color.b * 0.0722f;
                        if (value > 0.025f && value < 0.24f && color.b > color.r)
                            interfacePixels++;
                    }
                }
            }
            var minimumRatio = joystickRegion ? 0.018d : 0.055d;
            Assert.That(interfacePixels / (double)Math.Max(1, samples), Is.GreaterThan(minimumRatio),
                label + (joystickRegion ? " is missing the lower-left movement HUD" : " is missing the upper HUD panels"));
        }
    }
}
