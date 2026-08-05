using System;
using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
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
