using System;
using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
    public enum HavenlineAudioCue
    {
        WinterWind,
        FurnaceLoop,
        CampfireLoop,
        FootstepSnowA,
        FootstepSnowB,
        RunSnowA,
        RunSnowB,
        ChopSwing,
        ChopImpact,
        MineSwing,
        MineImpact,
        SalvageImpact,
        FuelPickup,
        ResourcePickup,
        ResourceStack,
        DepositWood,
        DepositStone,
        DepositMetal,
        DepositFuel,
        FurnaceUpgrade,
        FurnaceDamage,
        FurnaceRepair,
        RescueThaw,
        RescueComplete,
        BuildPlace,
        BuildComplete,
        BarricadeHit,
        BarricadeRepair,
        PlayerAttack,
        PlayerHit,
        WolfGrowl,
        WolfAttack,
        WolfHit,
        WolfDefeated,
        GateOpen,
        UiConfirm,
        UiBack,
        UiWarning
    }

    [Serializable]
    public sealed class HavenlineAudioEntry
    {
        public HavenlineAudioCue cue;
        public AudioClip[] clips = Array.Empty<AudioClip>();
        [Range(0f, 1f)] public float volume = 1f;
        [Range(0.5f, 1.5f)] public float minimumPitch = 0.96f;
        [Range(0.5f, 1.5f)] public float maximumPitch = 1.04f;
        [Range(0f, 1f)] public float spatialBlend = 1f;
        [Min(0f)] public float minimumRetriggerSeconds = 0.04f;
    }

}
