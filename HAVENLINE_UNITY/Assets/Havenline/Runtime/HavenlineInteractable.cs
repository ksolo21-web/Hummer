using System;
using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
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
}
