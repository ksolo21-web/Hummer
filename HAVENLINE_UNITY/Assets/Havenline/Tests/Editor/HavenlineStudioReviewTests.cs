using System;
using System.IO;
using Havenline.Editor;
using NUnit.Framework;
using UnityEngine;

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

            ValidatePremiumProof(wide, "wide phone proof");
            ValidatePremiumProof(close, "close phone proof");
            ValidatePremiumProof(foldable, "foldable proof");
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
