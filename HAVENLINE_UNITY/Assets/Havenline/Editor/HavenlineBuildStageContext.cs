namespace Havenline.Editor
{
    /// <summary>
    /// Process-local build stage selected by HavenlineBuildPipeline. It exists so pre-build
    /// character gates can distinguish a non-promotable device-test package from a verified
    /// release without relying on filenames, editor prefs, or mutable project settings.
    /// </summary>
    internal enum HavenlineBuildStage
    {
        None = 0,
        DeviceTest = 1,
        VerifiedRelease = 2
    }

    internal static class HavenlineBuildStageContext
    {
        internal static HavenlineBuildStage Current { get; private set; }
        internal static bool IsDeviceTest => Current == HavenlineBuildStage.DeviceTest;
        internal static bool IsVerifiedRelease => Current == HavenlineBuildStage.VerifiedRelease;

        internal static void Set(HavenlineBuildStage stage) => Current = stage;
        internal static void Clear() => Current = HavenlineBuildStage.None;
    }
}
