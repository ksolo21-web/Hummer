using System;
using System.Collections.Generic;
using System.IO;
using UnityEngine;
using UnityEngine.Profiling;

namespace Havenline
{
    public enum HavenlineFrameMode
    {
        Auto = 0,
        Quality60 = 1,
        Balanced90 = 2,
        Performance120 = 3
    }

    public enum HavenlineQualityTier
    {
        Safe = 0,
        Balanced = 1,
        High = 2,
        Ultra = 3
    }

    [Serializable]
    public sealed class HavenlinePerformanceReport
    {
        public string deviceModel;
        public string operatingSystem;
        public int width;
        public int height;
        public double displayRefreshRate;
        public int targetFrameRate;
        public string frameMode;
        public string qualityTier;
        public float averageFps;
        public float p95FrameTimeMs;
        public float p99FrameTimeMs;
        public float averageCpuFrameTimeMs;
        public float averageGpuFrameTimeMs;
        public long peakMemoryBytes;
        public float sessionSeconds;
        public int qualityDownshifts;
        public int qualityUpshifts;
        public long capturedUtcTicks;
    }

    [DisallowMultipleComponent]
    public sealed class HavenlinePerformance : MonoBehaviour
    {
        private const string ModeKey = "havenline.performance.frame_mode";
        private const int FrameWindow = 360;
        private const float EvaluationSeconds = 3f;
        private const float WarmupSeconds = 8f;

        private readonly float[] frameTimesMs = new float[FrameWindow];
        private readonly FrameTiming[] frameTimings = new FrameTiming[4];
        private int frameIndex;
        private int frameCount;
        private float evaluationClock;
        private float sessionClock;
        private int badWindows;
        private int goodWindows;
        private int qualityDownshifts;
        private int qualityUpshifts;
        private float accumulatedCpuMs;
        private float accumulatedGpuMs;
        private int timingSamples;
        private long peakMemory;

        public HavenlineFrameMode FrameMode { get; private set; }
        public HavenlineQualityTier QualityTier { get; private set; }
        public int TargetFrameRate { get; private set; }
        public double DisplayRefreshRate { get; private set; }
        public float AverageFps { get; private set; }
        public float P95FrameTimeMs { get; private set; }
        public float P99FrameTimeMs { get; private set; }
        public event Action<HavenlineQualityTier, int> QualityChanged;

        private void Awake()
        {
            DontDestroyOnLoad(gameObject);
            QualitySettings.vSyncCount = 0;
            Screen.sleepTimeout = SleepTimeout.NeverSleep;
            FrameMode = (HavenlineFrameMode)Mathf.Clamp(PlayerPrefs.GetInt(ModeKey, 0), 0, 3);
            ReconfigureForDisplay(true);
        }

        private void Update()
        {
            var delta = Mathf.Clamp(Time.unscaledDeltaTime, 0.0001f, 0.25f);
            frameTimesMs[frameIndex] = delta * 1000f;
            frameIndex = (frameIndex + 1) % FrameWindow;
            frameCount = Mathf.Min(frameCount + 1, FrameWindow);
            sessionClock += delta;
            evaluationClock += delta;
            peakMemory = Math.Max(peakMemory, Profiler.GetTotalAllocatedMemoryLong());

            FrameTimingManager.CaptureFrameTimings();
            var timingCount = FrameTimingManager.GetLatestTimings((uint)frameTimings.Length, frameTimings);
            if (timingCount > 0)
            {
                var timing = frameTimings[0];
                if (timing.cpuFrameTime > 0d) accumulatedCpuMs += (float)timing.cpuFrameTime;
                if (timing.gpuFrameTime > 0d) accumulatedGpuMs += (float)timing.gpuFrameTime;
                timingSamples++;
            }

            if (evaluationClock < EvaluationSeconds || frameCount < 60) return;
            evaluationClock = 0f;
            EvaluateWindow();
        }

        public void SetFrameMode(HavenlineFrameMode mode)
        {
            FrameMode = mode;
            PlayerPrefs.SetInt(ModeKey, (int)mode);
            PlayerPrefs.Save();
            ReconfigureForDisplay(true);
        }

        private void ReconfigureForDisplay(bool resetQuality)
        {
            DisplayRefreshRate = Math.Max(1d, Screen.currentResolution.refreshRateRatio.value);
            var supported = QuantizeSupportedRefresh(DisplayRefreshRate);
            switch (FrameMode)
            {
                case HavenlineFrameMode.Quality60:
                    TargetFrameRate = Reference.MinimumFrameRate;
                    break;
                case HavenlineFrameMode.Balanced90:
                    TargetFrameRate = Mathf.Min(Reference.BalancedFrameRate, supported);
                    break;
                case HavenlineFrameMode.Performance120:
                    TargetFrameRate = Mathf.Min(Reference.MaximumFrameRate, supported);
                    break;
                default:
                    TargetFrameRate = supported;
                    break;
            }
            TargetFrameRate = Mathf.Max(Reference.MinimumFrameRate, TargetFrameRate);
            Application.targetFrameRate = TargetFrameRate;

            if (!resetQuality) return;
            ApplyQuality(TargetFrameRate <= 60 ? HavenlineQualityTier.Ultra : HavenlineQualityTier.High, false);
            badWindows = 0;
            goodWindows = 0;
        }

        private static int QuantizeSupportedRefresh(double refresh)
        {
            if (refresh >= 118d) return Reference.MaximumFrameRate;
            if (refresh >= 88d) return Reference.BalancedFrameRate;
            return Reference.MinimumFrameRate;
        }

        private void EvaluateWindow()
        {
            var samples = new List<float>(frameCount);
            var total = 0f;
            for (var index = 0; index < frameCount; index++)
            {
                var value = frameTimesMs[index];
                if (value <= 0f) continue;
                samples.Add(value);
                total += value;
            }
            if (samples.Count == 0) return;

            samples.Sort();
            var averageMs = total / samples.Count;
            AverageFps = 1000f / Mathf.Max(0.01f, averageMs);
            P95FrameTimeMs = Percentile(samples, 0.95f);
            P99FrameTimeMs = Percentile(samples, 0.99f);
            if (sessionClock < WarmupSeconds) return;

            var frameBudget = 1000f / TargetFrameRate;
            var struggling = AverageFps < TargetFrameRate * 0.90f || P95FrameTimeMs > frameBudget * 1.28f;
            var stable = AverageFps >= TargetFrameRate * 0.97f && P95FrameTimeMs <= frameBudget * 1.12f;

            if (struggling)
            {
                badWindows++;
                goodWindows = 0;
                if (badWindows >= 2 && QualityTier > HavenlineQualityTier.Safe)
                {
                    ApplyQuality(StepTier(QualityTier, -1), true);
                    qualityDownshifts++;
                    badWindows = 0;
                }
                return;
            }

            if (stable)
            {
                goodWindows++;
                badWindows = 0;
                var maximumTier = TargetFrameRate <= 60 ? HavenlineQualityTier.Ultra : HavenlineQualityTier.High;
                if (goodWindows >= 5 && QualityTier < maximumTier)
                {
                    ApplyQuality(StepTier(QualityTier, 1), true);
                    qualityUpshifts++;
                    goodWindows = 0;
                }
                return;
            }

            badWindows = Mathf.Max(0, badWindows - 1);
            goodWindows = Mathf.Max(0, goodWindows - 1);
        }

        private static HavenlineQualityTier StepTier(HavenlineQualityTier current, int direction)
        {
            var value = Mathf.Clamp((int)current + direction, (int)HavenlineQualityTier.Safe, (int)HavenlineQualityTier.Ultra);
            return (HavenlineQualityTier)value;
        }

        private void ApplyQuality(HavenlineQualityTier tier, bool notify)
        {
            QualityTier = tier;
            switch (tier)
            {
                case HavenlineQualityTier.Ultra:
                    ApplyRenderSettings(1f, 48f, 1.5f, 0, 4, AnisotropicFiltering.ForceEnable, true);
                    break;
                case HavenlineQualityTier.High:
                    ApplyRenderSettings(0.94f, 40f, 1.25f, 0, 4, AnisotropicFiltering.Enable, true);
                    break;
                case HavenlineQualityTier.Balanced:
                    ApplyRenderSettings(0.84f, 32f, 1f, 1, 2, AnisotropicFiltering.Enable, false);
                    break;
                default:
                    ApplyRenderSettings(0.74f, 24f, 0.8f, 1, 2, AnisotropicFiltering.Disable, false);
                    break;
            }
            Shader.SetGlobalFloat("_HavenlineQualityTier", (float)QualityTier);
            if (notify) QualityChanged?.Invoke(QualityTier, TargetFrameRate);
        }

        private static void ApplyRenderSettings(
            float scale,
            float shadowDistance,
            float lodBias,
            int maximumLod,
            int antialiasing,
            AnisotropicFiltering anisotropy,
            bool realtimeReflections)
        {
            ScalableBufferManager.ResizeBuffers(scale, scale);
            QualitySettings.shadowDistance = shadowDistance;
            QualitySettings.lodBias = lodBias;
            QualitySettings.maximumLODLevel = maximumLod;
            QualitySettings.antiAliasing = antialiasing;
            QualitySettings.anisotropicFiltering = anisotropy;
            QualitySettings.realtimeReflectionProbes = realtimeReflections;
        }

        private static float Percentile(IReadOnlyList<float> sorted, float percentile)
        {
            if (sorted.Count == 0) return 0f;
            var index = Mathf.Clamp(Mathf.CeilToInt((sorted.Count - 1) * percentile), 0, sorted.Count - 1);
            return sorted[index];
        }

        public HavenlinePerformanceReport CaptureReport()
        {
            return new HavenlinePerformanceReport
            {
                deviceModel = SystemInfo.deviceModel,
                operatingSystem = SystemInfo.operatingSystem,
                width = Screen.width,
                height = Screen.height,
                displayRefreshRate = DisplayRefreshRate,
                targetFrameRate = TargetFrameRate,
                frameMode = FrameMode.ToString(),
                qualityTier = QualityTier.ToString(),
                averageFps = AverageFps,
                p95FrameTimeMs = P95FrameTimeMs,
                p99FrameTimeMs = P99FrameTimeMs,
                averageCpuFrameTimeMs = timingSamples > 0 ? accumulatedCpuMs / timingSamples : 0f,
                averageGpuFrameTimeMs = timingSamples > 0 ? accumulatedGpuMs / timingSamples : 0f,
                peakMemoryBytes = peakMemory,
                sessionSeconds = sessionClock,
                qualityDownshifts = qualityDownshifts,
                qualityUpshifts = qualityUpshifts,
                capturedUtcTicks = DateTime.UtcNow.Ticks
            };
        }

        public string WriteReport()
        {
            var path = Path.Combine(Application.persistentDataPath, "havenline-performance-report.json");
            try
            {
                File.WriteAllText(path, JsonUtility.ToJson(CaptureReport(), true));
            }
            catch (Exception exception)
            {
                Debug.LogError($"HAVENLINE performance report failed: {exception.Message}");
            }
            return path;
        }

        private void OnApplicationFocus(bool focused)
        {
            if (focused) ReconfigureForDisplay(false);
            else WriteReport();
        }

        private void OnApplicationPause(bool paused)
        {
            if (paused) WriteReport();
            else ReconfigureForDisplay(false);
        }

        private void OnDestroy()
        {
            ScalableBufferManager.ResizeBuffers(1f, 1f);
            WriteReport();
        }
    }
}
