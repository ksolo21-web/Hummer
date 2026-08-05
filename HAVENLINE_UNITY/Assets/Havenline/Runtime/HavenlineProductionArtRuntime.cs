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

    [CreateAssetMenu(menuName = "HAVENLINE/Production Audio Profile", fileName = "HAVENLINE_AudioProfile")]
    public sealed class HavenlineAudioProfile : ScriptableObject
    {
        [SerializeField] private HavenlineAudioEntry[] entries = Array.Empty<HavenlineAudioEntry>();
        [SerializeField, Range(0f, 1f)] private float masterVolume = 1f;
        [SerializeField, Range(0f, 1f)] private float ambienceVolume = 0.72f;
        [SerializeField, Range(0f, 1f)] private float effectsVolume = 0.92f;
        [SerializeField, Range(0f, 1f)] private float interfaceVolume = 0.82f;

        private Dictionary<HavenlineAudioCue, HavenlineAudioEntry> lookup;

        public float MasterVolume => masterVolume;
        public float AmbienceVolume => ambienceVolume;
        public float EffectsVolume => effectsVolume;
        public float InterfaceVolume => interfaceVolume;
        public IReadOnlyList<HavenlineAudioEntry> Entries => entries;

        public void Configure(
            HavenlineAudioEntry[] configuredEntries,
            float configuredMaster = 1f,
            float configuredAmbience = 0.72f,
            float configuredEffects = 0.92f,
            float configuredInterface = 0.82f)
        {
            entries = configuredEntries ?? Array.Empty<HavenlineAudioEntry>();
            masterVolume = Mathf.Clamp01(configuredMaster);
            ambienceVolume = Mathf.Clamp01(configuredAmbience);
            effectsVolume = Mathf.Clamp01(configuredEffects);
            interfaceVolume = Mathf.Clamp01(configuredInterface);
            RebuildLookup();
        }

        public bool TryGet(HavenlineAudioCue cue, out HavenlineAudioEntry entry)
        {
            if (lookup == null)
                RebuildLookup();
            return lookup.TryGetValue(cue, out entry);
        }

        private void OnEnable() => RebuildLookup();

        private void RebuildLookup()
        {
            lookup = new Dictionary<HavenlineAudioCue, HavenlineAudioEntry>();
            foreach (var entry in entries)
            {
                if (entry == null || entry.clips == null || entry.clips.Length == 0)
                    continue;
                lookup[entry.cue] = entry;
            }
        }
    }

    [CreateAssetMenu(menuName = "HAVENLINE/Typography Profile", fileName = "HAVENLINE_Typography")]
    public sealed class HavenlineTypographyProfile : ScriptableObject
    {
        [SerializeField] private Font runtimeFont;
        [SerializeField] private string preferredSystemFamily = "sans-serif";
        [SerializeField] private int headlineSize = 44;
        [SerializeField] private int objectiveSize = 31;
        [SerializeField] private int bodySize = 25;
        [SerializeField] private int compactSize = 20;
        [SerializeField] private Color primaryText = new(0.94f, 0.98f, 1f, 1f);
        [SerializeField] private Color secondaryText = new(0.72f, 0.82f, 0.9f, 1f);
        [SerializeField] private Color warningText = new(1f, 0.54f, 0.18f, 1f);

        public Font RuntimeFont => runtimeFont;
        public string PreferredSystemFamily => preferredSystemFamily;
        public int HeadlineSize => headlineSize;
        public int ObjectiveSize => objectiveSize;
        public int BodySize => bodySize;
        public int CompactSize => compactSize;
        public Color PrimaryText => primaryText;
        public Color SecondaryText => secondaryText;
        public Color WarningText => warningText;

        public void Configure(Font font, string family, int headline, int objective, int body, int compact)
        {
            runtimeFont = font;
            preferredSystemFamily = string.IsNullOrWhiteSpace(family) ? "sans-serif" : family;
            headlineSize = Mathf.Max(24, headline);
            objectiveSize = Mathf.Max(20, objective);
            bodySize = Mathf.Max(18, body);
            compactSize = Mathf.Max(16, compact);
        }
    }

    /// <summary>
    /// Lightweight mobile audio router with source pooling, cue throttling and separate
    /// ambience/effects/interface volume buses. It avoids per-play allocations and does
    /// not require an editor-created AudioMixer asset to remain fully functional offline.
    /// </summary>
    public sealed class HavenlineAudioRig : MonoBehaviour
    {
        [SerializeField] private HavenlineAudioProfile profile;
        [SerializeField] private AudioSource ambienceSource;
        [SerializeField] private AudioSource interfaceSource;
        [SerializeField] private AudioSource[] effectSources = Array.Empty<AudioSource>();
        [SerializeField, Range(2, 16)] private int sourcePoolSize = 8;

        private readonly Dictionary<HavenlineAudioCue, float> lastPlayedAt = new();
        private int nextSource;

        public HavenlineAudioProfile Profile => profile;

        public void Configure(HavenlineAudioProfile configuredProfile, AudioSource ambience, AudioSource ui, AudioSource[] effects)
        {
            profile = configuredProfile;
            ambienceSource = ambience;
            interfaceSource = ui;
            effectSources = effects ?? Array.Empty<AudioSource>();
            EnsureSources();
        }

        private void Awake()
        {
            EnsureSources();
            ApplyVolumes();
        }

        public bool Play(HavenlineAudioCue cue, Vector3 worldPosition, bool interfaceCue = false)
        {
            if (profile == null || !profile.TryGet(cue, out var entry) || entry.clips.Length == 0)
                return false;

            var now = Time.unscaledTime;
            if (lastPlayedAt.TryGetValue(cue, out var last) && now - last < entry.minimumRetriggerSeconds)
                return false;
            lastPlayedAt[cue] = now;

            var clipIndex = Mathf.Abs((Time.frameCount * 31 + (int)cue * 17) % entry.clips.Length);
            var clip = entry.clips[clipIndex];
            if (clip == null)
                return false;

            var source = interfaceCue ? interfaceSource : NextEffectSource();
            if (source == null)
                return false;
            source.transform.position = worldPosition;
            source.clip = clip;
            source.loop = false;
            source.spatialBlend = interfaceCue ? 0f : entry.spatialBlend;
            source.pitch = Mathf.Lerp(entry.minimumPitch, entry.maximumPitch,
                Mathf.Repeat((Time.frameCount + (int)cue * 0.37f) * 0.6180339f, 1f));
            source.volume = entry.volume * profile.MasterVolume *
                (interfaceCue ? profile.InterfaceVolume : profile.EffectsVolume);
            source.Play();
            return true;
        }

        public bool StartAmbience(HavenlineAudioCue cue)
        {
            if (ambienceSource == null || profile == null ||
                !profile.TryGet(cue, out var entry) || entry.clips.Length == 0)
                return false;
            ambienceSource.clip = entry.clips[0];
            ambienceSource.loop = true;
            ambienceSource.spatialBlend = 0f;
            ambienceSource.pitch = 1f;
            ambienceSource.volume = entry.volume * profile.MasterVolume * profile.AmbienceVolume;
            if (!ambienceSource.isPlaying)
                ambienceSource.Play();
            return true;
        }

        public void StopAmbience() => ambienceSource?.Stop();

        public void ApplyVolumes()
        {
            if (profile == null)
                return;
            if (ambienceSource != null)
                ambienceSource.volume = profile.MasterVolume * profile.AmbienceVolume;
            if (interfaceSource != null)
                interfaceSource.volume = profile.MasterVolume * profile.InterfaceVolume;
        }

        private AudioSource NextEffectSource()
        {
            EnsureSources();
            if (effectSources.Length == 0)
                return null;
            for (var offset = 0; offset < effectSources.Length; offset++)
            {
                var index = (nextSource + offset) % effectSources.Length;
                if (effectSources[index] != null && !effectSources[index].isPlaying)
                {
                    nextSource = (index + 1) % effectSources.Length;
                    return effectSources[index];
                }
            }
            var fallback = effectSources[nextSource % effectSources.Length];
            nextSource = (nextSource + 1) % effectSources.Length;
            return fallback;
        }

        private void EnsureSources()
        {
            if (ambienceSource == null)
                ambienceSource = CreateSource("Ambience", 0f);
            if (interfaceSource == null)
                interfaceSource = CreateSource("Interface", 0f);
            if (effectSources != null && effectSources.Length >= sourcePoolSize)
                return;

            var sources = new List<AudioSource>();
            if (effectSources != null)
                sources.AddRange(effectSources);
            while (sources.Count < sourcePoolSize)
                sources.Add(CreateSource($"Effect_{sources.Count + 1:00}", 1f));
            effectSources = sources.ToArray();
        }

        private AudioSource CreateSource(string sourceName, float spatialBlend)
        {
            var child = new GameObject(sourceName);
            child.transform.SetParent(transform, false);
            var source = child.AddComponent<AudioSource>();
            source.playOnAwake = false;
            source.loop = false;
            source.spatialBlend = spatialBlend;
            source.dopplerLevel = 0f;
            source.rolloffMode = AudioRolloffMode.Linear;
            source.minDistance = 1.5f;
            source.maxDistance = 24f;
            return source;
        }
    }
}
