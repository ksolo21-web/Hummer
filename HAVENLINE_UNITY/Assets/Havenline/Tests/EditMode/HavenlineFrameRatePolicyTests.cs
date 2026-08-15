using NUnit.Framework;

namespace Havenline.Tests.EditMode
{
    public sealed class HavenlineFrameRatePolicyTests
    {
        [TestCase(59.94, HavenlineFrameRatePolicy.ProductionFloorFps)]
        [TestCase(60.0, HavenlineFrameRatePolicy.ProductionFloorFps)]
        [TestCase(89.0, HavenlineFrameRatePolicy.HighRefresh90Fps)]
        [TestCase(90.0, HavenlineFrameRatePolicy.HighRefresh90Fps)]
        [TestCase(118.0, HavenlineFrameRatePolicy.HighRefresh90Fps)]
        [TestCase(119.0, HavenlineFrameRatePolicy.HighRefresh120Fps)]
        [TestCase(120.0, HavenlineFrameRatePolicy.HighRefresh120Fps)]
        [TestCase(144.0, HavenlineFrameRatePolicy.HighRefresh120Fps)]
        public void SelectTargetFrameRate_UsesSupportedHavenlineTier(double refreshRate, int expected)
        {
            Assert.That(HavenlineFrameRatePolicy.SelectTargetFrameRate(refreshRate), Is.EqualTo(expected));
        }
    }
}
