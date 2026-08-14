using System;
using UnityEngine;

namespace Havenline
{
    public enum HavenlineFeedbackKind
    {
        ActionImpact = 0,
        Damage = 1,
        Death = 2,
        Upgrade = 3
    }

    public readonly struct HavenlineFeedbackPulse
    {
        public HavenlineFeedbackPulse(
            HavenlineFeedbackKind kind,
            AutomaticActionKind action,
            Vector3 worldPosition,
            float strength)
        {
            Kind = kind;
            Action = action;
            WorldPosition = worldPosition;
            Strength = Mathf.Clamp01(strength);
        }

        public HavenlineFeedbackKind Kind { get; }
        public AutomaticActionKind Action { get; }
        public Vector3 WorldPosition { get; }
        public float Strength { get; }
    }

    /// <summary>
    /// Allocation-free runtime signal used to synchronize camera response with the same animation
    /// impact moments that perform harvesting, construction and combat. Listeners own presentation;
    /// gameplay state never depends on feedback delivery.
    /// </summary>
    public static class HavenlineFeedbackBus
    {
        public static event Action<HavenlineFeedbackPulse> Pulse;

        public static void PublishActionImpact(AutomaticActionKind action, Vector3 worldPosition)
        {
            if (action == AutomaticActionKind.None)
                return;
            Pulse?.Invoke(new HavenlineFeedbackPulse(
                HavenlineFeedbackKind.ActionImpact,
                action,
                worldPosition,
                StrengthFor(action)));
        }

        public static void PublishDamage(Vector3 worldPosition) =>
            Pulse?.Invoke(new HavenlineFeedbackPulse(
                HavenlineFeedbackKind.Damage,
                AutomaticActionKind.Combat,
                worldPosition,
                0.82f));

        public static void PublishDeath(Vector3 worldPosition) =>
            Pulse?.Invoke(new HavenlineFeedbackPulse(
                HavenlineFeedbackKind.Death,
                AutomaticActionKind.Combat,
                worldPosition,
                1f));

        public static void PublishUpgrade(Vector3 worldPosition) =>
            Pulse?.Invoke(new HavenlineFeedbackPulse(
                HavenlineFeedbackKind.Upgrade,
                AutomaticActionKind.Deposit,
                worldPosition,
                0.68f));

        private static float StrengthFor(AutomaticActionKind action) => action switch
        {
            AutomaticActionKind.GatherWood => 0.30f,
            AutomaticActionKind.GatherStone => 0.36f,
            AutomaticActionKind.GatherMetal => 0.38f,
            AutomaticActionKind.GatherFuel => 0.24f,
            AutomaticActionKind.Deposit => 0.18f,
            AutomaticActionKind.Rescue => 0.22f,
            AutomaticActionKind.Build => 0.42f,
            AutomaticActionKind.Repair => 0.38f,
            AutomaticActionKind.Combat => 0.58f,
            _ => 0.20f
        };
    }
}
