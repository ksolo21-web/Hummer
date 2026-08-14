using System;
using UnityEngine;

namespace Havenline
{
    /// <summary>
    /// Lightweight Humanoid fallback motion for the current deterministic placeholder clips.
    /// The approved C1-C4 models arrive in a neutral/T pose; until final authored humanoid clips
    /// replace the generated motion set, this layer gives the characters a readable relaxed pose,
    /// locomotion, carrying posture and action silhouettes instead of rigid root-only bobbing.
    ///
    /// It only runs on a valid Humanoid Avatar and can be disabled without changing gameplay.
    /// </summary>
    [DefaultExecutionOrder(250)]
    public sealed class HavenlineHumanoidMotionPolish : MonoBehaviour
    {
        [SerializeField] private Animator animator;
        [SerializeField] private HavenlineActorAnimator actor;
        [SerializeField] private bool enableFallbackMotion = true;

        private BonePose hips;
        private BonePose spine;
        private BonePose chest;
        private BonePose neck;
        private BonePose head;
        private BonePose leftArm;
        private BonePose leftForearm;
        private BonePose rightArm;
        private BonePose rightForearm;
        private BonePose leftUpperLeg;
        private BonePose leftLowerLeg;
        private BonePose rightUpperLeg;
        private BonePose rightLowerLeg;
        private float locomotionPhase;
        private bool ready;

        public bool IsReady => ready;

        public void Configure(Animator humanoidAnimator, HavenlineActorAnimator owner)
        {
            animator = humanoidAnimator;
            actor = owner;
            CaptureRig();
        }

        private void Awake()
        {
            if (animator == null)
                animator = GetComponent<Animator>() ?? GetComponentInParent<Animator>();
            CaptureRig();
        }

        private void OnEnable() => CaptureRig();

        private void LateUpdate()
        {
            if (!enableFallbackMotion || !ready || animator == null || actor == null)
                return;

            ResetPose();

            var speed = Mathf.Clamp01(actor.MotionSpeed);
            locomotionPhase += Time.deltaTime * Mathf.Lerp(2.4f, 8.6f, speed);
            var cycle = Mathf.Sin(locomotionPhase);
            var halfCycle = Mathf.Sin(locomotionPhase + Mathf.PI * 0.5f);

            ApplyRelaxedUpperBody(speed, cycle);
            ApplyLocomotion(speed, cycle, halfCycle);
            ApplyCarry(actor.CarryAmount);

            if (actor.CurrentAction != AutomaticActionKind.None)
                ApplyAction(actor.CurrentAction);
            else
                ApplyBreathing();
        }

        private void CaptureRig()
        {
            ready = false;
            if (animator == null || actor == null || animator.avatar == null ||
                !animator.avatar.isValid || !animator.avatar.isHuman)
                return;

            try
            {
                hips = BonePose.Capture(animator, HumanBodyBones.Hips);
                spine = BonePose.Capture(animator, HumanBodyBones.Spine);
                chest = BonePose.Capture(animator, HumanBodyBones.Chest);
                neck = BonePose.Capture(animator, HumanBodyBones.Neck);
                head = BonePose.Capture(animator, HumanBodyBones.Head);
                leftArm = BonePose.Capture(animator, HumanBodyBones.LeftUpperArm);
                leftForearm = BonePose.Capture(animator, HumanBodyBones.LeftLowerArm);
                rightArm = BonePose.Capture(animator, HumanBodyBones.RightUpperArm);
                rightForearm = BonePose.Capture(animator, HumanBodyBones.RightLowerArm);
                leftUpperLeg = BonePose.Capture(animator, HumanBodyBones.LeftUpperLeg);
                leftLowerLeg = BonePose.Capture(animator, HumanBodyBones.LeftLowerLeg);
                rightUpperLeg = BonePose.Capture(animator, HumanBodyBones.RightUpperLeg);
                rightLowerLeg = BonePose.Capture(animator, HumanBodyBones.RightLowerLeg);
                ready = leftArm.Transform != null && rightArm.Transform != null &&
                        leftUpperLeg.Transform != null && rightUpperLeg.Transform != null;
            }
            catch (InvalidOperationException)
            {
                ready = false;
            }
        }

        private void ResetPose()
        {
            hips.Reset();
            spine.Reset();
            chest.Reset();
            neck.Reset();
            head.Reset();
            leftArm.Reset();
            leftForearm.Reset();
            rightArm.Reset();
            rightForearm.Reset();
            leftUpperLeg.Reset();
            leftLowerLeg.Reset();
            rightUpperLeg.Reset();
            rightLowerLeg.Reset();
        }

        private void ApplyRelaxedUpperBody(float speed, float cycle)
        {
            // Bring the neutral/T-pose arms down into a readable relaxed survivor silhouette.
            var strideLift = cycle * speed * 6.5f;
            leftArm.Rotate(new Vector3(0f, speed * -3.5f, -70f + strideLift));
            rightArm.Rotate(new Vector3(0f, speed * 3.5f, 70f - strideLift));
            leftForearm.Rotate(new Vector3(0f, 0f, -8f - speed * 4f));
            rightForearm.Rotate(new Vector3(0f, 0f, 8f + speed * 4f));
        }

        private void ApplyLocomotion(float speed, float cycle, float halfCycle)
        {
            if (speed <= 0.015f)
                return;

            var stride = Mathf.Lerp(8f, 23f, speed);
            var knee = Mathf.Lerp(5f, 19f, speed);
            leftUpperLeg.Rotate(new Vector3(cycle * stride, 0f, 0f));
            rightUpperLeg.Rotate(new Vector3(-cycle * stride, 0f, 0f));
            leftLowerLeg.Rotate(new Vector3(Mathf.Max(0f, -halfCycle) * knee, 0f, 0f));
            rightLowerLeg.Rotate(new Vector3(Mathf.Max(0f, halfCycle) * knee, 0f, 0f));
            hips.Rotate(new Vector3(0f, cycle * speed * 2.1f, cycle * speed * 1.1f));
            spine.Rotate(new Vector3(speed * 2f, -cycle * speed * 1.7f, -cycle * speed * 1.2f));
        }

        private void ApplyCarry(int amount)
        {
            if (amount <= 0)
                return;

            var load = Mathf.Clamp01(Mathf.Log10(amount + 1f) / 2f);
            leftArm.Rotate(new Vector3(-4f - load * 5f, -6f, 10f + load * 4f));
            rightArm.Rotate(new Vector3(-4f - load * 5f, 6f, -10f - load * 4f));
            leftForearm.Rotate(new Vector3(-8f - load * 8f, 0f, 8f));
            rightForearm.Rotate(new Vector3(-8f - load * 8f, 0f, -8f));
            spine.Rotate(new Vector3(load * 3.5f, 0f, 0f));
        }

        private void ApplyBreathing()
        {
            var breath = Mathf.Sin(Time.time * 1.72f);
            spine.Rotate(new Vector3(breath * 0.75f, 0f, 0f));
            chest.Rotate(new Vector3(breath * 0.85f, 0f, 0f));
            head.Rotate(new Vector3(-breath * 0.22f, 0f, 0f));
        }

        private void ApplyAction(AutomaticActionKind action)
        {
            var speed = action is AutomaticActionKind.Deposit or AutomaticActionKind.Rescue
                ? 3.3f
                : action == AutomaticActionKind.Combat ? 7.8f : 5.6f;
            var wave = 0.5f - 0.5f * Mathf.Cos(Time.time * speed * Mathf.PI * 2f);
            var contact = Mathf.SmoothStep(0f, 1f, wave);

            switch (action)
            {
                case AutomaticActionKind.GatherWood:
                    leftArm.Rotate(new Vector3(-7f, -4f, 28f - contact * 19f));
                    rightArm.Rotate(new Vector3(-10f, 4f, -35f + contact * 31f));
                    leftForearm.Rotate(new Vector3(-12f, 0f, 19f));
                    rightForearm.Rotate(new Vector3(-18f, 0f, -22f));
                    chest.Rotate(new Vector3(7f + contact * 9f, -6f + contact * 12f, 0f));
                    break;

                case AutomaticActionKind.GatherStone:
                case AutomaticActionKind.GatherMetal:
                    leftArm.Rotate(new Vector3(-10f, -5f, 32f - contact * 24f));
                    rightArm.Rotate(new Vector3(-10f, 5f, -32f + contact * 24f));
                    leftForearm.Rotate(new Vector3(-18f, 0f, 18f));
                    rightForearm.Rotate(new Vector3(-18f, 0f, -18f));
                    chest.Rotate(new Vector3(9f + contact * 11f, 0f, 0f));
                    break;

                case AutomaticActionKind.GatherFuel:
                case AutomaticActionKind.Deposit:
                    leftArm.Rotate(new Vector3(-8f, -6f, 18f));
                    rightArm.Rotate(new Vector3(-8f, 6f, -18f));
                    leftForearm.Rotate(new Vector3(-18f - contact * 8f, 0f, 11f));
                    rightForearm.Rotate(new Vector3(-18f - contact * 8f, 0f, -11f));
                    chest.Rotate(new Vector3(8f + contact * 5f, 0f, 0f));
                    break;

                case AutomaticActionKind.Rescue:
                case AutomaticActionKind.Build:
                case AutomaticActionKind.Repair:
                    leftArm.Rotate(new Vector3(-11f, -5f, 24f - contact * 10f));
                    rightArm.Rotate(new Vector3(-11f, 5f, -24f + contact * 10f));
                    leftForearm.Rotate(new Vector3(-22f, 0f, 14f));
                    rightForearm.Rotate(new Vector3(-22f, 0f, -14f));
                    spine.Rotate(new Vector3(12f + contact * 6f, 0f, 0f));
                    leftUpperLeg.Rotate(new Vector3(-5f, 0f, 0f));
                    rightUpperLeg.Rotate(new Vector3(-5f, 0f, 0f));
                    break;

                case AutomaticActionKind.Combat:
                    rightArm.Rotate(new Vector3(-9f, 10f + contact * 16f, -34f + contact * 31f));
                    rightForearm.Rotate(new Vector3(-20f, 0f, -20f + contact * 15f));
                    leftArm.Rotate(new Vector3(-8f, -7f, 16f));
                    leftForearm.Rotate(new Vector3(-17f, 0f, 12f));
                    chest.Rotate(new Vector3(2f, -11f + contact * 23f, 0f));
                    hips.Rotate(new Vector3(0f, -4f + contact * 8f, 0f));
                    break;
            }
        }

        [Serializable]
        private struct BonePose
        {
            public Transform Transform;
            public Quaternion BaseRotation;

            public static BonePose Capture(Animator animator, HumanBodyBones bone)
            {
                var transform = animator.GetBoneTransform(bone);
                return new BonePose
                {
                    Transform = transform,
                    BaseRotation = transform != null ? transform.localRotation : Quaternion.identity
                };
            }

            public void Reset()
            {
                if (Transform != null)
                    Transform.localRotation = BaseRotation;
            }

            public void Rotate(Vector3 euler)
            {
                if (Transform != null)
                    Transform.localRotation *= Quaternion.Euler(euler);
            }
        }
    }
}
