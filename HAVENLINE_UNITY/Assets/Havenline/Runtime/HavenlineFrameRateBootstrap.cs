using UnityEngine;

namespace Havenline
{
    /// <summary>
    /// Applies Havenline's display-aware 60/90/120 FPS policy before the first scene loads.
    /// </summary>
    public static class HavenlineFrameRateBootstrap
    {
        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.BeforeSceneLoad)]
        private static void ApplyFrameRatePolicy()
        {
            HavenlineFrameRatePolicy.ApplyForCurrentDisplay();
        }
    }
}
