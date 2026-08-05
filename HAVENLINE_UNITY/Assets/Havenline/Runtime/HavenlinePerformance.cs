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

    /// <summary>
    /// Display-aware frame pacing for 60/90/120 Hz devices with reversible quality scaling.
    /// It measures sustained frame time instead of claiming a rate from configuration alone.
    /// </summary>
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
            var milliseconds = delta * 1000f;
            frameTimesMs[frameIndex] = milliseconds;
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
                if (timing.cpuFrameTime > 0d)
                    accumulatedCpuMs += (float)timing.cpuFrameTime;
                if (timing.gpuFrameTime > 0d)
                    accumulatedGpuMs += (float)timing.gpuFrameTime;
                timingSamples++;
            }

            if (evaluationClock < EvaluationSeconds || frameCount < 60)
                return;
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
            TargetFrameRate = FrameMode switch
            {
                HavenlineFrameMode.Quality60 => Reference.MinimumFrameRate,
                HavenlineFrameMode.Balanced90 => Mathf.Min(Reference.BalancedFrameRate, supported),
                HavenlineFrameMode.Performance120 => Mathf.Min(Reference.MaximumFrameRate, supported),
                _ => supported
            };
            TargetFrameRate = Mathf.Max(Reference.MinimumFrameRate, TargetFrameRate);
            Application.targetFrameRate = TargetFrameRate;

            if (resetQuality)
            {
                var startingTier = TargetFrameRate <= 60
                    ? HavenlineQualityTier.Ultra
                    : HavenlineQualityTier.High;
                ApplyQuality(startingTier, false);
                badWindows = 0;
                goodWindows = 0;
            }
        }

        private static int QuantizeSupportedRefresh(double refresh)
        {
            if (refresh >= 118d)
                return Reference.MaximumFrameRate;
            if (refresh >= 88d)
                return Reference.BalancedFrameRate;
            return Reference.MinimumFrameRate;
        }

        private void EvaluateWindow()
        {
            var samples = new List<float>(frameCount);
            var total = 0f;
            for (var index = 0; index < frameCount; index++)
            {
                var value = frameTimesMs[index];
                if (value <= 0f)
                    continue;
                samples.Add(value);
                total += value;
            }
            if (samples.Count == 0)
                return;

            samples.Sort();
            var averageMs = total / samples.Count;
            AverageFps = 1000f / Mathf.Max(0.01f, averageMs);
            P95FrameTimeMs = Percentile(samples, 0.95f);
            P99FrameTimeMs = Percentile(samples, 0.99f);

            if (sessionClock < WarmupSeconds)
                return;

            var frameBudget = 1000f / TargetFrameRate;
            var struggling = AverageFps < TargetFrameRate * 0.90f || P95FrameTimeMs > frameBudget * 1.28f;
            var stable = AverageFps >= TargetFrameRate * 0.97f && P95FrameTimeMs <= frameBudget * 1.12f;

            if (struggling)
            {
                badWindows++;
                goodWindows = 0;
                if (badWindows >= 2 && QualityTier > HavenlineQualityTier.Safe)
                {
                    ApplyQuality(QualityTier - 1, true);
                    qualityDownshifts++;
                    badWindows = 0;
                }
            }
            else if (stable)
            {
                goodWindows++;
                badWindows = 0;
                var maximumTier = TargetFrameRate <= 60 ? HavenlineQualityTier.Ultra : HavenlineQualityTier.High;
                if (goodWindows >= 5 && QualityTier < maximumTier)
                {
                    ApplyQuality(QualityTier + 1, true);
                    qualityUpshifts++;
                    goodWindows = 0;
                }
            }
            else
            {
                badWindows = Mathf.Max(0, badWindows - 1);
                goodWindows = Mathf.Max(0, goodWindows - 1);
            }
        }

        private void ApplyQuality(HavenlineQualityTier tier, bool notify)
        {
            QualityTier = tier;
            switch (tier)
            {
                case HavenlineQualityTier.Ultra:
                    ScalableBufferManager.ResizeBuffers(1f, 1f);
                    QualitySettings.shadowDistance = 48f;
                    QualitySettings.lodBias = 1.5f;
                    QualitySettings.maximumLODLevel = 0;
                    QualitySettings.antiAliasing = 4;
                    QualitySettings.anisotropicFiltering = AnisotropicFiltering.ForceEnable;
                    QualitySettings.realtimeReflectionProbes = true;
                    break;
                case HavenlineQualityTier.High:
                    ScalableBufferManager.ResizeBuffers(0.94f, 0.94f);
                    QualitySettings.shadowDistance = 40f;
                    QualitySettings.lodBias = 1.25f;
                    QualitySettings.maximumLODLevel = 0;
                    QualitySettings.antiAliasing = 4;
                    QualitySettings.anisotropicFiltering = AnisotropicFiltering.Enable;
                    QualitySettings.realtimeReflectionProbes = true;
                    break;
                case HavenlineQualityTier.Balanced:
                    ScalableBufferManager.ResizeBuffers(0.84f, 0.84f);
                    QualitySettings.shadowDistance = 32f;
                    QualitySettings.lodBias = 1.0f;
                    QualitySettings.maximumLODLevel = 1;
                    QualitySettings.antiAliasing = 2;
                    QualitySettings.anisotropicFiltering = AnisotropicFiltering.Enable;
                    QualitySettings.realtimeReflectionProbes = false;
                    break;
                default:
                    ScalableBufferManager.ResizeBuffers(0.74f, 0.74f);
                    QualitySettings.shadowDistance = 24f;
                    QualitySettings.lodBias = 0.8f;
                    QualitySettings.maximumLODLevel = 1;
                    QualitySettings.antiAliasing = 2;
                    QualitySettings.anisotropicFiltering = AnisotropicFiltering.Disable;
                    QualitySettings.realtimeReflectionProbes = false;
                    break;
            }

            Shader.SetGlobalFloat("_HavenlineQualityTier", (float)QualityTier);
            if (notify)
                QualityChanged?.Invoke(QualityTier, TargetFrameRate);
        }

        private static float Percentile(IReadOnlyList<float> sorted, float percentile)
        {
            if (sorted.Count == 0)
                return 0f;
            var index = Mathf.Clamp(Mathf.CeilToInt((sorted.Count - 1) * percentile), 0, sorted.Count - 1);
            return sorted[index];
        }

        public HavenlinePerformanceReport CaptureReport()
        {
            var cpu = timingSamples > 0 ? accumulatedCpuMs / timingSamples : 0f;
            var gpu = timingSamples > 0 ? accumulatedGpuMs / timingSamples : 0f;
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
                averageCpuFrameTimeMs = cpu,
                averageGpuFrameTimeMs = gpu,
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
            if (focused)
                ReconfigureForDisplay(false);
            else
                WriteReport();
        }

        private void OnApplicationPause(bool paused)
        {
            if (paused)
                WriteReport();
            else
                ReconfigureForDisplay(false);
        }

        private void OnDestroy()
        {
            ScalableBufferManager.ResizeBuffers(1f, 1f);
            WriteReport();
        }
    }
}
