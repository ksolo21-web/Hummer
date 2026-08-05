using System;
using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
    public enum AutomaticActionKind
    {
        None = 0,
        GatherWood = 1,
        GatherStone = 2,
        GatherMetal = 3,
        GatherFuel = 4,
        Deposit = 5,
        Rescue = 6,
        Build = 7,
        Repair = 8,
        Combat = 9
    }

    public abstract class HavenlineInteractable : MonoBehaviour
    {
        private static readonly HashSet<HavenlineInteractable> Registered = new();
        private static readonly List<HavenlineInteractable> Snapshot = new(64);

        public static IReadOnlyList<HavenlineInteractable> ActiveTargets
        {
            get
            {
                Snapshot.Clear();
                foreach (var target in Registered)
                {
                    if (target != null && target.isActiveAndEnabled)
                        Snapshot.Add(target);
                }
                return Snapshot;
            }
        }

        public abstract AutomaticActionKind ActionKind { get; }
        public abstract bool CanInteract(HavenlinePlayerController actor);
        public abstract void TickInteraction(HavenlinePlayerController actor, float deltaTime);

        public virtual int Priority => 0;
        public virtual float InteractionRange => Reference.InteractionRadius;
        public virtual float NormalizedProgress => -1f;
        public virtual string ContextLabel => ActionKind.ToString();
        public virtual bool AllowWhileMoving => false;
        public virtual Vector3 InteractionPoint => transform.position;

        protected virtual void OnEnable() => Registered.Add(this);
        protected virtual void OnDisable() => Registered.Remove(this);
        public virtual void OnSelected(HavenlinePlayerController actor) { }
        public virtual void OnDeselected(HavenlinePlayerController actor) { }
    }

    [DisallowMultipleComponent]
    public sealed class HavenlineAutomaticActionController : MonoBehaviour
    {
        [SerializeField] private HavenlinePlayerController player;
        [SerializeField] private float rescanInterval = 0.075f;
        [SerializeField] private float targetHysteresis = 0.32f;
        [SerializeField] private float movementCancelThreshold = 0.12f;
        [SerializeField] private float facingWeight = 0.35f;

        private HavenlineInteractable current;
        private float scanClock;

        public HavenlineInteractable CurrentTarget => current;
        public AutomaticActionKind CurrentAction => current != null ? current.ActionKind : AutomaticActionKind.None;
        public float CurrentProgress => current != null ? current.NormalizedProgress : -1f;
        public string CurrentLabel => current != null ? current.ContextLabel : string.Empty;
        public event Action<AutomaticActionKind, string, float> ContextChanged;

        public void Configure(HavenlinePlayerController controlledPlayer) => player = controlledPlayer;

        private void Awake()
        {
            if (player == null)
                player = GetComponent<HavenlinePlayerController>();
        }

        private void Update()
        {
            if (player == null)
                return;

            scanClock -= Time.unscaledDeltaTime;
            if (scanClock <= 0f)
            {
                scanClock = rescanInterval;
                SelectBestTarget();
            }

            if (current == null)
            {
                PublishContext();
                return;
            }

            var distance = HorizontalDistance(player.transform.position, current.InteractionPoint);
            if (!current.isActiveAndEnabled || !CanContinueCurrent() ||
                distance > current.InteractionRange + targetHysteresis)
            {
                SetCurrent(null);
                return;
            }

            if (player.MoveInputMagnitude > movementCancelThreshold && !current.AllowWhileMoving)
            {
                player.ActorAnimator?.EndAction();
                PublishContext();
                return;
            }

            player.Face(current.InteractionPoint);
            player.ActorAnimator?.BeginAction(current.ActionKind);
            current.TickInteraction(player, Time.deltaTime);
            PublishContext();
        }

        private void SelectBestTarget()
        {
            if (current != null && current.isActiveAndEnabled && CanContinueCurrent())
            {
                var retainedDistance = HorizontalDistance(player.transform.position, current.InteractionPoint);
                if (retainedDistance <= current.InteractionRange + targetHysteresis)
                    return;
            }

            HavenlineInteractable best = null;
            var bestScore = float.NegativeInfinity;
            var forward = player.VisualForward;
            var playerPosition = player.transform.position;

            foreach (var candidate in HavenlineInteractable.ActiveTargets)
            {
                if (candidate == null || candidate == current || !candidate.CanInteract(player))
                    continue;

                var offset = Vector3.ProjectOnPlane(candidate.InteractionPoint - playerPosition, Vector3.up);
                var distance = offset.magnitude;
                if (distance > candidate.InteractionRange)
                    continue;

                var direction = distance > 0.001f ? offset / distance : forward;
                var facing = Mathf.InverseLerp(-1f, 1f, Vector3.Dot(forward, direction));
                var distanceScore = 1f - Mathf.Clamp01(distance / Mathf.Max(0.01f, candidate.InteractionRange));
                var score = candidate.Priority + distanceScore * 10f + facing * facingWeight;
                if (score <= bestScore)
                    continue;

                best = candidate;
                bestScore = score;
            }

            SetCurrent(best);
        }

        private bool CanContinueCurrent()
        {
            if (current == null)
                return false;

            // Selecting a survivor changes its state from Trapped to Rescuing. Keep that
            // context alive while progress is below 100%, then release it on completion.
            if (current.ActionKind == AutomaticActionKind.Rescue)
                return current.NormalizedProgress >= 0f && current.NormalizedProgress < 0.999f;

            return current.CanInteract(player);
        }

        private void SetCurrent(HavenlineInteractable target)
        {
            if (ReferenceEquals(current, target))
                return;

            current?.OnDeselected(player);
            current = target;
            current?.OnSelected(player);
            if (current == null)
                player.ActorAnimator?.EndAction();
            PublishContext();
        }

        private void PublishContext() =>
            ContextChanged?.Invoke(CurrentAction, CurrentLabel, CurrentProgress);

        private static float HorizontalDistance(Vector3 a, Vector3 b)
        {
            a.y = 0f;
            b.y = 0f;
            return Vector3.Distance(a, b);
        }
    }
}
