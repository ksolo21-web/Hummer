using System;
using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
    public sealed class HavenlineActorAnimator : MonoBehaviour
    {
        private static readonly int SpeedHash = Animator.StringToHash("Speed");
        private static readonly int CarryHash = Animator.StringToHash("CarryAmount");
        private static readonly int ActionTypeHash = Animator.StringToHash("ActionType");
        private static readonly int ActionHash = Animator.StringToHash("Action");
        private static readonly int ActionEndHash = Animator.StringToHash("ActionEnd");
        private static readonly int HitHash = Animator.StringToHash("Hit");
        private static readonly int DeadHash = Animator.StringToHash("Dead");

        [SerializeField] private Animator animator;
        private AutomaticActionKind currentAction;
        private bool impactQueued;

        public AutomaticActionKind CurrentAction => currentAction;

        private void Awake()
        {
            if (animator == null)
                animator = GetComponentInChildren<Animator>(true);
        }

        public void Configure(Animator productionAnimator) => animator = productionAnimator;

        public void SetMotion(float normalizedSpeed)
        {
            if (animator != null)
                animator.SetFloat(SpeedHash, Mathf.Clamp01(normalizedSpeed), 0.08f, Time.deltaTime);
        }

        public void SetCarryAmount(int amount)
        {
            if (animator != null)
                animator.SetInteger(CarryHash, Mathf.Max(0, amount));
        }

        public void BeginAction(AutomaticActionKind action)
        {
            if (action == AutomaticActionKind.None || currentAction == action)
                return;

            currentAction = action;
            impactQueued = false;
            if (animator == null)
                return;

            animator.SetInteger(ActionTypeHash, (int)action);
            animator.ResetTrigger(ActionEndHash);
            animator.SetTrigger(ActionHash);
        }

        public void EndAction()
        {
            if (currentAction == AutomaticActionKind.None)
                return;

            currentAction = AutomaticActionKind.None;
            impactQueued = false;
            if (animator == null)
                return;

            animator.SetInteger(ActionTypeHash, 0);
            animator.SetTrigger(ActionEndHash);
        }

        public bool ConsumeImpact(ref float elapsed, float fallbackSeconds)
        {
            elapsed += Time.deltaTime;
            if (!impactQueued && elapsed < Mathf.Max(0.05f, fallbackSeconds))
                return false;

            impactQueued = false;
            elapsed = 0f;
            HavenlineFeedbackBus.PublishActionImpact(currentAction, transform.position);
            return true;
        }

        // Production animation clips call these animation-event entry points on the authored
        // contact frame. ConsumeImpact publishes the presentation pulse when gameplay consumes
        // that same frame, keeping camera feedback synchronized with actual harvesting/damage.
        public void ActionImpact() => impactQueued = true;
        public void PulseAction() => impactQueued = true;

        public void PlayHit()
        {
            HavenlineFeedbackBus.PublishDamage(transform.position);
            if (animator != null)
                animator.SetTrigger(HitHash);
        }

        public void PlayDeath()
        {
            currentAction = AutomaticActionKind.None;
            HavenlineFeedbackBus.PublishDeath(transform.position);
            if (animator != null)
                animator.SetTrigger(DeadHash);
        }
    }
}
