using System;
using UnityEngine;

namespace Havenline
{
    public enum ResourceKind { Wood, Stone, Metal, Fuel }
    public enum HelperState { Trapped, Following, Gathering, Delivering, Repairing, Defending }

    public static class Reference
    {
        public const string ProductName = "HAVENLINE";
        public const string PackageId = "com.kaleb.havenline";
        public const string ScenePath = "Assets/Havenline/Scenes/FrozenOutpost.unity";
        public const string ReferenceApkSha256 = "17996ba270e6b56505d3273fca1915f977f6d892b4949f37c66098ac6efcfa67";

        public const float CameraSize = 14.8f;
        public static readonly Vector3 CameraOffset = new(0f, 7f, 7f);
        public const float CameraFocusHeight = 0.82f;
        public const float CameraLookAhead = 1.15f;

        public static readonly Vector3 PlayerSpawn = new(0f, 0.08f, 6.2f);
        public const float WalkSpeed = 3.85f;
        public const float RunSpeed = 5.75f;
        public const float Acceleration = 30f;
        public const float Deceleration = 36f;
        public const int CarryCapacity = 6;
        public const float InteractionRadius = 1.85f;
        public const float BoundX = 14.2f;
        public const float BoundZ = 16.2f;
        public const float FallRecoveryY = -2.2f;

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
            new(-8.4f,0f,5.8f), new(-10.4f,0f,1.8f), new(8.8f,0f,5.2f), new(10.5f,0f,0.7f),
            new(-8.8f,0f,-7.2f), new(8.9f,0f,-7.0f)
        };

        public static readonly Vector3[] StoneNodes =
        {
            new(-5.4f,0f,8.4f), new(5.6f,0f,8.2f), new(-10.4f,0f,-3.6f), new(10.4f,0f,-3.4f)
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
        public string artVersion;
        public string approvedBy;
        public bool sceneAuthored;
        public bool cameraContract;
        public bool movementContract;
        public bool gatheringContract;
        public bool carryingContract;
        public bool depositContract;
        public bool furnaceContract;
        public bool helperContract;
        public bool defenseContract;
        public bool premiumArtContract;
        public bool animationContract;
        public bool visualQualityContract;
        public bool uiContract;
        public bool audioContract;
        public bool releaseCandidate;
        public string apkSha256;
        public string[] validationFailures;
        public string[] proofFrames;
    }
}
