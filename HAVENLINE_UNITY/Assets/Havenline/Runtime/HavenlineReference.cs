using System;
using UnityEngine;

namespace Havenline
{
    public enum ResourceKind { Wood, Stone, Metal, Fuel }
    public enum HelperState { Trapped, Rescuing, Following, Gathering, Delivering, Building, Repairing, Defending, Healing }

    public static class Reference
    {
        public const string ProductName = "HAVENLINE";
        public const string PackageId = "com.kaleb.havenline";
        public const string ScenePath = "Assets/Havenline/Scenes/FrozenOutpost.unity";
        public const string ReferenceApkSha256 = "17996ba270e6b56505d3273fca1915f977f6d892b4949f37c66098ac6efcfa67";

        // Premium playable framing: closer characters with a lower, more dimensional angle.
        public const float CameraSize = 9.45f;
        public static readonly Vector3 CameraOffset = new(0f, 5.25f, 8.15f);
        public const float CameraFocusHeight = 0.82f;
        public const float CameraLookAhead = 0.62f;
        public const float CameraFollowSharpness = 8.6f;
        public const float TurnSharpness = 16f;

        public static readonly Vector3 PlayerSpawn = new(0f, 0.08f, 6.2f);
        public const float WalkSpeed = 3.9f;
        public const float RunSpeed = 5.85f;
        public const float Acceleration = 34f;
        public const float Deceleration = 42f;
        public const int CarryCapacity = 8;
        public const float InteractionRadius = 1.9f;
        public const float CombatRadius = 2.25f;
        public const float DepositRadius = 2.35f;
        public const float RescueRadius = 2.1f;
        public const float BuildRadius = 2.15f;
        public const float BoundX = 14.2f;
        public const float BoundZ = 16.2f;
        public const float FallRecoveryY = -2.2f;

        public const int MinimumFrameRate = 60;
        public const int BalancedFrameRate = 90;
        public const int MaximumFrameRate = 120;

        public static readonly Vector3 Furnace = new(0f, 0f, 0.2f);
        public static readonly Vector3 Campfire = new(2.7f, 0f, 2.1f);
        public static readonly Vector3 Storage = new(-2.8f, 0f, 2.25f);
        public static readonly Vector3 TentLeft = new(-6.6f, 0f, -3.8f);
        public static readonly Vector3 TentRight = new(6.6f, 0f, -3.8f);
        public static readonly Vector3 Survivor = new(7.1f, 0f, -2.8f);
        public static readonly Vector3 NorthBarricade = new(0f, 0f, -10.7f);
        public static readonly Vector3 SouthBarricade = new(0f, 0f, 11.7f);
        public static readonly Vector3 ForestGate = new(0f, 0f, -14.8f);

        public static readonly Vector3[] WoodNodes =
        {
            new(-5.9f,0f,6.0f), new(-8.2f,0f,2.6f), new(6.4f,0f,5.6f), new(8.8f,0f,1.1f),
            new(-8.1f,0f,-6.4f), new(8.2f,0f,-6.1f)
        };

        public static readonly Vector3[] StoneNodes =
        {
            new(-4.8f,0f,8.2f), new(5.0f,0f,8.0f), new(-8.9f,0f,-3.7f), new(9.0f,0f,-3.5f)
        };

        public static Vector3 ClampToWorld(Vector3 position)
        {
            position.x = Mathf.Clamp(position.x, -BoundX, BoundX);
            position.z = Mathf.Clamp(position.z, -BoundZ, BoundZ);
            return position;
        }

        public static bool IsValidSavedPosition(Vector3 position) =>
            position.y > FallRecoveryY && Mathf.Abs(position.x) <= BoundX && Mathf.Abs(position.z) <= BoundZ;
    }

    [Serializable]
    public sealed class EvidenceSnapshot
    {
        public string commit;
        public string sourceFingerprint;
        public string artVersion;
        public string approvedBy;
        public bool sceneAuthored;
        public bool cameraContract;
        public bool movementContract;
        public bool automaticActionContract;
        public bool gatheringContract;
        public bool carryingContract;
        public bool depositContract;
        public bool furnaceContract;
        public bool helperContract;
        public bool constructionContract;
        public bool defenseContract;
        public bool saveResumeContract;
        public bool premiumArtContract;
        public bool animationContract;
        public bool visualQualityContract;
        public bool uiContract;
        public bool audioContract;
        public bool performanceContract;
        public bool releaseCandidate;
        public int targetFrameRate;
        public float averageFps;
        public float p95FrameTimeMs;
        public float p99FrameTimeMs;
        public long peakMemoryBytes;
        public string deviceModel;
        public string qualityTier;
        public string apkSha256;
        public string[] validationFailures;
        public string[] proofFrames;
    }
}
