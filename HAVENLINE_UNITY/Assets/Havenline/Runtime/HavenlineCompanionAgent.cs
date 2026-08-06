using System;
using UnityEngine;

namespace Havenline
{
    public enum HavenlineCompanionMode
    {
        FollowLead = 0,
        HoldPosition = 1,
        AssignedJob = 2
    }

    /// <summary>
    /// Runtime foundation for the unselected lead plus Characters 3 and 4. The agent follows
    /// the selected playable lead in formation until a camp system assigns a helper job anchor.
    /// </summary>
    [DisallowMultipleComponent]
    public sealed class HavenlineCompanionAgent : MonoBehaviour
    {
        [SerializeField] private HavenlineCharacterId characterId;
        [SerializeField] private HavenlineCompanionMode mode = HavenlineCompanionMode.FollowLead;
        [SerializeField] private Transform leader;
        [SerializeField] private Transform jobAnchor;
        [SerializeField] private Vector3 formationOffset;
        [SerializeField, Min(0.1f)] private float followSpeed = 3.25f;
        [SerializeField, Min(1f)] private float turnSpeedDegrees = 540f;
        [SerializeField, Min(0.05f)] private float stopDistance = 0.22f;
        [SerializeField, Min(2f)] private float recoveryDistance = 14f;
        [SerializeField] private HavenlineActorAnimator actorAnimator;

        private CharacterController characterController;
        private Rigidbody body;
        private Vector3 holdPosition;

        public HavenlineCharacterId CharacterId => characterId;
        public HavenlineCompanionMode Mode => mode;
        public Transform Leader => leader;
        public Transform JobAnchor => jobAnchor;

        public event Action<HavenlineCompanionMode> ModeChanged;

        private void Awake()
        {
            characterController = GetComponent<CharacterController>();
            body = GetComponent<Rigidbody>();
            if (actorAnimator == null)
                actorAnimator = GetComponent<HavenlineActorAnimator>();
            holdPosition = transform.position;
        }

        public void Configure(
            HavenlineCharacterId id,
            Transform playableLeader,
            Vector3 localFormationOffset)
        {
            characterId = id;
            leader = playableLeader != null
                ? playableLeader
                : throw new ArgumentNullException(nameof(playableLeader));
            formationOffset = localFormationOffset;
            jobAnchor = null;
            SetMode(HavenlineCompanionMode.FollowLead);
        }

        public void AssignJob(Transform anchor)
        {
            jobAnchor = anchor != null
                ? anchor
                : throw new ArgumentNullException(nameof(anchor));
            SetMode(HavenlineCompanionMode.AssignedJob);
        }

        public void ClearJobAndFollow()
        {
            jobAnchor = null;
            SetMode(HavenlineCompanionMode.FollowLead);
        }

        public void Hold()
        {
            holdPosition = transform.position;
            jobAnchor = null;
            SetMode(HavenlineCompanionMode.HoldPosition);
        }

        private void Update()
        {
            if (!TryGetTarget(out var targetPosition, out var targetForward))
            {
                actorAnimator?.SetMotion(0f);
                return;
            }

            targetPosition.y = transform.position.y;
            var offset = targetPosition - transform.position;
            var distance = offset.magnitude;

            if (mode == HavenlineCompanionMode.FollowLead && distance > recoveryDistance)
            {
                transform.position = targetPosition;
                transform.rotation = Quaternion.LookRotation(targetForward, Vector3.up);
                actorAnimator?.SetMotion(0f);
                return;
            }

            if (distance <= stopDistance)
            {
                actorAnimator?.SetMotion(0f);
                RotateToward(targetForward);
                return;
            }

            var direction = offset / Mathf.Max(distance, 0.0001f);
            var movement = direction * Mathf.Min(followSpeed * Time.deltaTime, distance - stopDistance);
            Move(movement);
            RotateToward(direction);
            actorAnimator?.SetMotion(Mathf.Clamp01(distance / 2f));
        }

        private bool TryGetTarget(out Vector3 targetPosition, out Vector3 targetForward)
        {
            switch (mode)
            {
                case HavenlineCompanionMode.FollowLead:
                    if (leader == null)
                    {
                        targetPosition = default;
                        targetForward = transform.forward;
                        return false;
                    }

                    targetPosition = leader.TransformPoint(formationOffset);
                    targetForward = leader.forward;
                    return true;

                case HavenlineCompanionMode.AssignedJob:
                    if (jobAnchor == null)
                    {
                        targetPosition = default;
                        targetForward = transform.forward;
                        return false;
                    }

                    targetPosition = jobAnchor.position;
                    targetForward = jobAnchor.forward;
                    return true;

                case HavenlineCompanionMode.HoldPosition:
                    targetPosition = holdPosition;
                    targetForward = transform.forward;
                    return true;

                default:
                    targetPosition = default;
                    targetForward = transform.forward;
                    return false;
            }
        }

        private void Move(Vector3 movement)
        {
            if (characterController != null && characterController.enabled)
            {
                characterController.Move(movement);
                return;
            }

            if (body != null && body.isKinematic)
            {
                body.MovePosition(body.position + movement);
                return;
            }

            transform.position += movement;
        }

        private void RotateToward(Vector3 direction)
        {
            direction.y = 0f;
            if (direction.sqrMagnitude <= 0.0001f)
                return;

            var targetRotation = Quaternion.LookRotation(direction.normalized, Vector3.up);
            transform.rotation = Quaternion.RotateTowards(
                transform.rotation,
                targetRotation,
                turnSpeedDegrees * Time.deltaTime);
        }

        private void SetMode(HavenlineCompanionMode nextMode)
        {
            if (mode == nextMode)
                return;

            mode = nextMode;
            ModeChanged?.Invoke(mode);
        }
    }
}
