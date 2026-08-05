using System;
using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
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
}
