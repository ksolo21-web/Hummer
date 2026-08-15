using UnityEngine;

namespace Havenline
{
    /// <summary>
    /// Central runtime policy for Havenline high-refresh presentation.
    /// 60 FPS is the production floor. 90/120 FPS are enabled only when the
    /// active display advertises sufficient refresh headroom.
    /// </summary>
    public static class HavenlineFrameRatePolicy
    {
        public const int ProductionFloorFps = 60;
        public const int HighRefresh90Fps = 90;
        public const int HighRefresh120Fps = 120;

        public static int SelectTargetFrameRate(double displayRefreshRate)
        {
            if (displayRefreshRate >= 119.0)
            {
                return HighRefresh120Fps;
            }

            if (displayRefreshRate >= 89.0)
            {
                return HighRefresh90Fps;
            }

            return ProductionFloorFps;
        }

        public static int ApplyForCurrentDisplay()
        {
            // On Android/iOS Unity uses Application.targetFrameRate rather than
            // QualitySettings.vSyncCount as the effective frame-rate request.
            QualitySettings.vSyncCount = 0;

            var refreshRate = Screen.currentResolution.refreshRateRatio.value;
            var target = SelectTargetFrameRate(refreshRate);
            Application.targetFrameRate = target;
            return target;
        }
    }
}
