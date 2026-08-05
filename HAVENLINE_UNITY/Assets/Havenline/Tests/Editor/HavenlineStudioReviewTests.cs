using System.IO;
using Havenline.Editor;
using NUnit.Framework;

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
            Assert.That(File.Exists(Path.Combine(reviewRoot, "HAVENLINE-studio-report.json")), Is.True);
            Assert.That(File.Exists(Path.Combine(reviewRoot, "HAVENLINE-studio-wide-1920x1080.png")), Is.True);
            Assert.That(File.Exists(Path.Combine(reviewRoot, "HAVENLINE-studio-close-1920x1080.png")), Is.True);
            Assert.That(File.Exists(Path.Combine(reviewRoot, "HAVENLINE-studio-foldable-2208x1840.png")), Is.True);
            Assert.That(new FileInfo(Path.Combine(reviewRoot, "HAVENLINE-studio-wide-1920x1080.png")).Length,
                Is.GreaterThan(100_000));
            Assert.That(new FileInfo(Path.Combine(reviewRoot, "HAVENLINE-studio-close-1920x1080.png")).Length,
                Is.GreaterThan(100_000));
        }
    }
}
