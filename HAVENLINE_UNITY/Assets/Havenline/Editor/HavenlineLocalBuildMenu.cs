using UnityEditor;
using UnityEngine;

namespace Havenline.Editor
{
    public static class HavenlineLocalBuildMenu
    {
        [MenuItem("HAVENLINE/Build Android Review APK Locally", priority = 20)]
        public static void BuildAndroidReviewApkLocally()
        {
            if (!EditorUserBuildSettings.activeBuildTarget.Equals(BuildTarget.Android))
            {
                var switched = EditorUserBuildSettings.SwitchActiveBuildTarget(
                    BuildTargetGroup.Android,
                    BuildTarget.Android);

                if (!switched)
                {
                    Debug.LogError(
                        "HAVENLINE could not switch to Android. Install Android Build Support, Android SDK & NDK Tools, and OpenJDK from Unity Hub first.");
                    return;
                }
            }

            HavenlineProductionPipeline.BuildReviewCandidate();
        }

        [MenuItem("HAVENLINE/Prepare Frozen Outpost Without Building", priority = 21)]
        public static void PrepareFrozenOutpostOnly()
        {
            HavenlineProductionPipeline.Prepare();
        }
    }
}
