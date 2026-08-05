using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

namespace Havenline.Editor
{
    /// <summary>
    /// Produces real rendered proof frames and blocks Android builds when the pictures are
    /// blank, too dark, visually flat, excessively zoomed out, duplicated, unresponsive,
    /// or missing the required camp subjects. A verified release additionally requires a
    /// human approval record that is cryptographically tied to the exact rendered proof set.
    /// </summary>
    public static class HavenlineRenderProofGate
    {
        public const int SchemaVersion = 1;
        public const string ReviewDirectory = "Builds/Review";
        public const string ReportPath = ReviewDirectory + "/HAVENLINE-render-proof-report.json";
        public const string ApprovalPath = "Builds/Acceptance/HAVENLINE-render-approval.json";

        private const string WideProof = "HAVENLINE-premium-frozen-outpost.png";
        private const string CloseProof = "HAVENLINE-premium-close-camera.png";
        private const string PhoneProof = "HAVENLINE-premium-phone-wide.png";
        private const string TabletProof = "HAVENLINE-premium-tablet.png";
        private const string FoldProof = "HAVENLINE-premium-fold-unfolded.png";
        private const string NightProof = "HAVENLINE-premium-night-readability.png";

        private static readonly FrameSpec[] RequiredFrames =
        {
            new FrameSpec("wide_landscape", WideProof, 1920, 1080, 8.60f, false),
            new FrameSpec("close_landscape", CloseProof, 1920, 1080, 6.80f, false),
            new FrameSpec("phone_landscape", PhoneProof, 2400, 1080, 7.15f, false),
            new FrameSpec("tablet_landscape", TabletProof, 2560, 1600, 7.15f, false),
            new FrameSpec("fold_unfolded", FoldProof, 2176, 1812, 7.15f, false),
            new FrameSpec("night_readability", NightProof, 1920, 1080, 7.15f, true)
        };

        private static readonly string[] FingerprintRoots =
        {
            "Assets/Havenline",
            "Packages",
            "ProjectSettings"
        };

        [Serializable]
        public sealed class FrameAnalysis
        {
            public string id;
            public string file;
            public string sha256;
            public int width;
            public int height;
            public long fileBytes;
            public float orthographicSize;
            public bool nightSimulation;
            public float meanLuminance;
            public float luminanceStandardDeviation;
            public float meanSaturation;
            public float colorfulPixelRatio;
            public float darkPixelRatio;
            public float clippedBlackRatio;
            public float clippedWhiteRatio;
            public float edgeDensity;
            public int quantizedColorCount;
            public float centerBorderContrast;
            public int visibleUiGraphics;
            public float playerScreenHeight;
            public float furnaceScreenHeight;
            public float campScreenWidth;
            public float campScreenHeight;
            public bool playerVisible;
            public bool furnaceVisible;
            public bool leftShelterVisible;
            public bool rightShelterVisible;
            public bool storageVisible;
            public bool passed;
            public string[] failures = Array.Empty<string>();
        }

        [Serializable]
        public sealed class RenderProofReport
        {
            public int schemaVersion;
            public string commit;
            public string sourceFingerprint;
            public string generatedUtc;
            public string proofSetSha256;
            public bool automatedPassed;
            public bool humanApprovalRequired;
            public bool humanApprovalPassed;
            public bool qualityGatePassed;
            public float wideClosePerceptualDifference;
            public float closeToWideDetailRatio;
            public FrameAnalysis[] frames = Array.Empty<FrameAnalysis>();
            public string[] failures = Array.Empty<string>();
        }

        [Serializable]
        public sealed class RenderApproval
        {
            public int schemaVersion;
            public bool approved;
            public string approvedBy;
            public string approvalNote;
            public string commit;
            public string sourceFingerprint;
            public string proofSetSha256;
        }

        private readonly struct FrameSpec
        {
            public readonly string Id;
            public readonly string FileName;
            public readonly int Width;
            public readonly int Height;
            public readonly float OrthographicSize;
            public readonly bool NightSimulation;

            public FrameSpec(
                string id,
                string fileName,
                int width,
                int height,
                float orthographicSize,
                bool nightSimulation)
            {
                Id = id;
                FileName = fileName;
                Width = width;
                Height = height;
                OrthographicSize = orthographicSize;
                NightSimulation = nightSimulation;
            }
        }

        private sealed class CapturedFrame
        {
            public FrameAnalysis Analysis;
            public float[] Thumbnail;
        }

        private sealed class CanvasState
        {
            public Canvas Canvas;
            public RenderMode RenderMode;
            public Camera WorldCamera;
            public float PlaneDistance;
        }

        private sealed class LightState
        {
            public Light Light;
            public float Intensity;
            public Color Color;
        }

        [MenuItem("HAVENLINE Premium/Capture and Validate Render Proof")]
        public static void CaptureAndValidateFromMenu()
        {
            var report = CaptureAndValidate(false);
            if (!report.automatedPassed)
                throw new BuildFailedException(BuildFailureMessage(report));
            Debug.Log($"HAVENLINE render-proof quality gate passed. Proof set: {report.proofSetSha256}");
        }

        [MenuItem("HAVENLINE Premium/Write Render Approval Template")]
        public static void WriteRenderApprovalTemplateFromMenu()
        {
            var report = CaptureAndValidate(false);
            if (!report.automatedPassed)
                throw new BuildFailedException(BuildFailureMessage(report));
            WriteApprovalTemplate(report);
            Debug.Log($"HAVENLINE render approval template written: {ApprovalPath}");
        }

        public static RenderProofReport RequireForBuild(BuildReport buildReport)
        {
            var outputPath = buildReport?.summary.outputPath ?? string.Empty;
            var releaseBuild = outputPath.Contains("release-candidate", StringComparison.OrdinalIgnoreCase);
            var report = CaptureAndValidate(releaseBuild);
            if (!report.qualityGatePassed)
                throw new BuildFailedException(BuildFailureMessage(report));
            return report;
        }

        public static RenderProofReport CaptureAndValidate(bool requireHumanApproval)
        {
            Directory.CreateDirectory(ReviewDirectory);
            Directory.CreateDirectory(Path.GetDirectoryName(ApprovalPath) ?? "Builds/Acceptance");

            var failures = new List<string>();
            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            var camera = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Camera>(true))
                .SingleOrDefault(candidate => candidate.CompareTag("MainCamera"));
            if (camera == null)
                throw new BuildFailedException("Render-proof gate could not find the tagged MainCamera in the shipping scene.");
            if (!camera.orthographic)
                failures.Add("Shipping camera must be orthographic for the approved HAVENLINE composition.");

            var canvasStates = PrepareCanvasesForCameraProof(scene, camera);
            var lightStates = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Light>(true))
                .Select(light => new LightState { Light = light, Intensity = light.intensity, Color = light.color })
                .ToArray();
            var originalAmbientIntensity = RenderSettings.ambientIntensity;
            var originalAmbientLight = RenderSettings.ambientLight;
            var originalOrtho = camera.orthographicSize;

            var captured = new List<CapturedFrame>();
            try
            {
                foreach (var spec in RequiredFrames)
                {
                    RestoreLights(lightStates, originalAmbientIntensity, originalAmbientLight);
                    if (spec.NightSimulation)
                        ApplyNightReadabilitySimulation(lightStates);
                    captured.Add(CaptureFrame(scene, camera, spec));
                }
            }
            finally
            {
                camera.orthographicSize = originalOrtho;
                RestoreLights(lightStates, originalAmbientIntensity, originalAmbientLight);
                RestoreCanvases(canvasStates);
            }

            var wide = captured.Single(frame => frame.Analysis.id == "wide_landscape");
            var close = captured.Single(frame => frame.Analysis.id == "close_landscape");
            var wideCloseDifference = PerceptualDifference(wide.Thumbnail, close.Thumbnail);
            if (wideCloseDifference < 0.055f)
                failures.Add($"Wide and close proof frames are effectively duplicates ({wideCloseDifference:0.000}); the camera proof is not trustworthy.");

            var detailRatio = wide.Analysis.edgeDensity > 0.0001f
                ? close.Analysis.edgeDensity / wide.Analysis.edgeDensity
                : 0f;
            if (detailRatio < 0.92f)
                failures.Add($"Close proof is not at least as detailed as the wide proof (detail ratio {detailRatio:0.00}).");

            var distinctHashes = captured.Select(frame => frame.Analysis.sha256).Distinct(StringComparer.Ordinal).Count();
            if (distinctHashes != captured.Count)
                failures.Add("Two or more required device/aspect proof frames are byte-identical; responsive rendering was not demonstrated.");

            foreach (var frame in captured)
                failures.AddRange(frame.Analysis.failures.Select(message => $"{frame.Analysis.id}: {message}"));

            var sourceFingerprint = ComputeSourceFingerprint();
            var proofSetSha = ComputeProofSetSha(captured.Select(frame => frame.Analysis));
            var automatedPassed = failures.Count == 0;
            var humanApprovalPassed = !requireHumanApproval;

            var report = new RenderProofReport
            {
                schemaVersion = SchemaVersion,
                commit = Environment.GetEnvironmentVariable("GITHUB_SHA") ?? "local",
                sourceFingerprint = sourceFingerprint,
                generatedUtc = DateTime.UtcNow.ToString("O"),
                proofSetSha256 = proofSetSha,
                automatedPassed = automatedPassed,
                humanApprovalRequired = requireHumanApproval,
                wideClosePerceptualDifference = wideCloseDifference,
                closeToWideDetailRatio = detailRatio,
                frames = captured.Select(frame => frame.Analysis).ToArray()
            };

            if (automatedPassed && requireHumanApproval)
            {
                humanApprovalPassed = ValidateHumanApproval(report, failures);
                if (!humanApprovalPassed && !File.Exists(ApprovalPath))
                    WriteApprovalTemplate(report);
            }

            report.humanApprovalPassed = humanApprovalPassed;
            report.qualityGatePassed = automatedPassed && humanApprovalPassed;
            report.failures = failures.Distinct().OrderBy(message => message, StringComparer.Ordinal).ToArray();
            File.WriteAllText(ReportPath, JsonUtility.ToJson(report, true) + "\n");
            return report;
        }

        private static CapturedFrame CaptureFrame(Scene scene, Camera camera, FrameSpec spec)
        {
            var path = Path.Combine(ReviewDirectory, spec.FileName);
            var texture = new RenderTexture(spec.Width, spec.Height, 24, RenderTextureFormat.ARGB32)
            {
                antiAliasing = 4,
                useMipMap = false,
                autoGenerateMips = false
            };
            var previousTarget = camera.targetTexture;
            var previousActive = RenderTexture.active;
            camera.orthographicSize = spec.OrthographicSize;
            camera.targetTexture = texture;
            RenderTexture.active = texture;
            Canvas.ForceUpdateCanvases();
            camera.Render();

            var image = new Texture2D(spec.Width, spec.Height, TextureFormat.RGB24, false, false);
            image.ReadPixels(new Rect(0, 0, spec.Width, spec.Height), 0, 0);
            image.Apply(false, false);
            File.WriteAllBytes(path, image.EncodeToPNG());

            var analysis = AnalyzeFrame(scene, camera, spec, path, image);
            var thumbnail = BuildThumbnail(image, 96, 54);

            camera.targetTexture = previousTarget;
            RenderTexture.active = previousActive;
            UnityEngine.Object.DestroyImmediate(image);
            UnityEngine.Object.DestroyImmediate(texture);
            return new CapturedFrame { Analysis = analysis, Thumbnail = thumbnail };
        }

        private static FrameAnalysis AnalyzeFrame(
            Scene scene,
            Camera camera,
            FrameSpec spec,
            string path,
            Texture2D image)
        {
            var failures = new List<string>();
            var pixels = image.GetPixels32();
            var step = Math.Max(1, pixels.Length / 350000);
            double sum = 0d;
            double sumSquared = 0d;
            double saturationSum = 0d;
            var sampled = 0;
            var colorful = 0;
            var dark = 0;
            var black = 0;
            var white = 0;
            var colors = new HashSet<int>();

            for (var index = 0; index < pixels.Length; index += step)
            {
                var pixel = pixels[index];
                var r = pixel.r / 255f;
                var g = pixel.g / 255f;
                var b = pixel.b / 255f;
                var luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b;
                var maximum = Mathf.Max(r, Mathf.Max(g, b));
                var minimum = Mathf.Min(r, Mathf.Min(g, b));
                var saturation = maximum <= 0.0001f ? 0f : (maximum - minimum) / maximum;
                sum += luminance;
                sumSquared += luminance * luminance;
                saturationSum += saturation;
                sampled++;
                if (saturation >= 0.22f) colorful++;
                if (luminance <= 0.10f) dark++;
                if (luminance <= 0.02f) black++;
                if (luminance >= 0.98f) white++;
                colors.Add(((pixel.r >> 3) << 10) | ((pixel.g >> 3) << 5) | (pixel.b >> 3));
            }

            var mean = sampled > 0 ? (float)(sum / sampled) : 0f;
            var variance = sampled > 0 ? Math.Max(0d, sumSquared / sampled - mean * mean) : 0d;
            var standardDeviation = (float)Math.Sqrt(variance);
            var meanSaturation = sampled > 0 ? (float)(saturationSum / sampled) : 0f;
            var edgeDensity = ComputeEdgeDensity(pixels, spec.Width, spec.Height);
            var centerMean = RegionMeanLuminance(pixels, spec.Width, spec.Height, 0.25f, 0.22f, 0.75f, 0.78f);
            var borderMean = BorderMeanLuminance(pixels, spec.Width, spec.Height, 0.11f);
            var centerBorderContrast = Mathf.Abs(centerMean - borderMean);
            var visibleUiGraphics = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Graphic>(true))
                .Count(graphic => graphic.enabled && graphic.gameObject.activeInHierarchy && graphic.color.a > 0.05f);

            var playerBounds = SubjectViewportBounds(scene, camera, "Player");
            var furnaceBounds = SubjectViewportBounds(scene, camera, "Furnace");
            var leftShelterBounds = SubjectViewportBounds(scene, camera, "StartingTent");
            var rightShelterBounds = SubjectViewportBounds(scene, camera, "RescueShelter");
            var storageBounds = SubjectViewportBounds(scene, camera, "SupplyStorage");
            var campBounds = Combine(playerBounds, furnaceBounds, leftShelterBounds, rightShelterBounds, storageBounds);

            var playerVisible = IsMeaningfullyVisible(playerBounds);
            var furnaceVisible = IsMeaningfullyVisible(furnaceBounds);
            var leftVisible = IsMeaningfullyVisible(leftShelterBounds);
            var rightVisible = IsMeaningfullyVisible(rightShelterBounds);
            var storageVisible = IsMeaningfullyVisible(storageBounds);
            var playerHeight = Height(playerBounds);
            var furnaceHeight = Height(furnaceBounds);
            var campWidth = Width(campBounds);
            var campHeight = Height(campBounds);

            var minimumPlayerHeight = spec.Id == "wide_landscape" ? 0.045f : 0.060f;
            var minimumFurnaceHeight = spec.Id == "wide_landscape" ? 0.070f : 0.085f;
            if (spec.Id == "close_landscape")
            {
                minimumPlayerHeight = 0.075f;
                minimumFurnaceHeight = 0.105f;
            }

            if (new FileInfo(path).Length < 80_000)
                failures.Add("proof PNG is implausibly small and likely blank or visually empty.");
            if (mean < (spec.NightSimulation ? 0.12f : 0.20f))
                failures.Add($"mean luminance is too dark ({mean:0.000}).");
            if (mean > 0.90f)
                failures.Add($"mean luminance is washed out ({mean:0.000}).");
            if (standardDeviation < 0.075f)
                failures.Add($"image is visually flat ({standardDeviation:0.000} luminance standard deviation).");
            if ((float)dark / Math.Max(1, sampled) > (spec.NightSimulation ? 0.62f : 0.45f))
                failures.Add("too much of the frame is unreadably dark.");
            if ((float)black / Math.Max(1, sampled) > 0.18f)
                failures.Add("clipped-black area is excessive.");
            if ((float)white / Math.Max(1, sampled) > 0.36f)
                failures.Add("clipped-white snow/highlight area is excessive.");
            if (meanSaturation < (spec.NightSimulation ? 0.055f : 0.080f))
                failures.Add($"color saturation is too weak ({meanSaturation:0.000}).");
            if ((float)colorful / Math.Max(1, sampled) < (spec.NightSimulation ? 0.07f : 0.12f))
                failures.Add("too little of the frame contains meaningful color.");
            if (colors.Count < 256)
                failures.Add($"frame has insufficient color/detail complexity ({colors.Count} quantized colors).");
            if (edgeDensity < 0.014f)
                failures.Add($"frame lacks readable modeled detail ({edgeDensity:0.000} edge density).");
            if (edgeDensity > 0.48f)
                failures.Add($"frame is excessively noisy or aliased ({edgeDensity:0.000} edge density).");
            if (centerBorderContrast < 0.012f)
                failures.Add("center gameplay area does not separate clearly from the surrounding environment.");
            if (visibleUiGraphics < 4)
                failures.Add($"fewer than four visible UI graphics were rendered ({visibleUiGraphics}).");
            if (!playerVisible)
                failures.Add("player is not visibly framed.");
            if (!furnaceVisible)
                failures.Add("hero furnace is not visibly framed.");
            if (!leftVisible || !rightVisible || !storageVisible)
                failures.Add("the inhabited camp composition does not visibly include both shelters and storage.");
            if (playerHeight < minimumPlayerHeight)
                failures.Add($"player is too small in frame ({playerHeight:0.000}; require {minimumPlayerHeight:0.000}).");
            if (furnaceHeight < minimumFurnaceHeight)
                failures.Add($"furnace is too small in frame ({furnaceHeight:0.000}; require {minimumFurnaceHeight:0.000}).");
            if (campWidth < 0.34f || campHeight < 0.27f)
                failures.Add($"camp does not occupy enough of the gameplay frame ({campWidth:0.00} x {campHeight:0.00}).");

            var analysis = new FrameAnalysis
            {
                id = spec.Id,
                file = spec.FileName,
                sha256 = Sha256(path),
                width = spec.Width,
                height = spec.Height,
                fileBytes = new FileInfo(path).Length,
                orthographicSize = spec.OrthographicSize,
                nightSimulation = spec.NightSimulation,
                meanLuminance = mean,
                luminanceStandardDeviation = standardDeviation,
                meanSaturation = meanSaturation,
                colorfulPixelRatio = (float)colorful / Math.Max(1, sampled),
                darkPixelRatio = (float)dark / Math.Max(1, sampled),
                clippedBlackRatio = (float)black / Math.Max(1, sampled),
                clippedWhiteRatio = (float)white / Math.Max(1, sampled),
                edgeDensity = edgeDensity,
                quantizedColorCount = colors.Count,
                centerBorderContrast = centerBorderContrast,
                visibleUiGraphics = visibleUiGraphics,
                playerScreenHeight = playerHeight,
                furnaceScreenHeight = furnaceHeight,
                campScreenWidth = campWidth,
                campScreenHeight = campHeight,
                playerVisible = playerVisible,
                furnaceVisible = furnaceVisible,
                leftShelterVisible = leftVisible,
                rightShelterVisible = rightVisible,
                storageVisible = storageVisible,
                passed = failures.Count == 0,
                failures = failures.ToArray()
            };
            return analysis;
        }

        private static List<CanvasState> PrepareCanvasesForCameraProof(Scene scene, Camera camera)
        {
            var states = new List<CanvasState>();
            foreach (var canvas in scene.GetRootGameObjects().SelectMany(root => root.GetComponentsInChildren<Canvas>(true)))
            {
                states.Add(new CanvasState
                {
                    Canvas = canvas,
                    RenderMode = canvas.renderMode,
                    WorldCamera = canvas.worldCamera,
                    PlaneDistance = canvas.planeDistance
                });
                if (canvas.renderMode == RenderMode.ScreenSpaceOverlay)
                {
                    canvas.renderMode = RenderMode.ScreenSpaceCamera;
                    canvas.worldCamera = camera;
                    canvas.planeDistance = Mathf.Max(camera.nearClipPlane + 0.1f, 1f);
                }
            }
            return states;
        }

        private static void RestoreCanvases(IEnumerable<CanvasState> states)
        {
            foreach (var state in states)
            {
                if (state.Canvas == null)
                    continue;
                state.Canvas.renderMode = state.RenderMode;
                state.Canvas.worldCamera = state.WorldCamera;
                state.Canvas.planeDistance = state.PlaneDistance;
            }
        }

        private static void ApplyNightReadabilitySimulation(IEnumerable<LightState> states)
        {
            foreach (var state in states)
            {
                if (state.Light == null)
                    continue;
                if (state.Light.type == LightType.Directional)
                    state.Light.intensity = state.Intensity * 0.28f;
                else if (state.Light.name.Contains("Furnace", StringComparison.OrdinalIgnoreCase) ||
                         state.Light.name.Contains("Camp", StringComparison.OrdinalIgnoreCase))
                    state.Light.intensity = Mathf.Max(state.Intensity, 1.1f);
                else
                    state.Light.intensity = state.Intensity * 0.62f;
            }
            RenderSettings.ambientIntensity = 0.34f;
            RenderSettings.ambientLight = new Color(0.16f, 0.22f, 0.32f);
        }

        private static void RestoreLights(
            IEnumerable<LightState> states,
            float ambientIntensity,
            Color ambientLight)
        {
            foreach (var state in states)
            {
                if (state.Light == null)
                    continue;
                state.Light.intensity = state.Intensity;
                state.Light.color = state.Color;
            }
            RenderSettings.ambientIntensity = ambientIntensity;
            RenderSettings.ambientLight = ambientLight;
        }

        private static Rect SubjectViewportBounds(Scene scene, Camera camera, string objectName)
        {
            var transform = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
                .FirstOrDefault(candidate => string.Equals(candidate.name, objectName, StringComparison.Ordinal));
            if (transform == null)
                return new Rect(float.NaN, float.NaN, 0f, 0f);

            var renderers = transform.GetComponentsInChildren<Renderer>(true)
                .Where(renderer => renderer.enabled && renderer.gameObject.activeInHierarchy)
                .ToArray();
            if (renderers.Length == 0)
                return new Rect(float.NaN, float.NaN, 0f, 0f);

            var min = new Vector2(float.PositiveInfinity, float.PositiveInfinity);
            var max = new Vector2(float.NegativeInfinity, float.NegativeInfinity);
            var anyInFront = false;
            foreach (var renderer in renderers)
            {
                var bounds = renderer.bounds;
                foreach (var corner in BoundsCorners(bounds))
                {
                    var viewport = camera.WorldToViewportPoint(corner);
                    if (viewport.z <= 0f)
                        continue;
                    anyInFront = true;
                    min = Vector2.Min(min, viewport);
                    max = Vector2.Max(max, viewport);
                }
            }
            return anyInFront
                ? Rect.MinMaxRect(min.x, min.y, max.x, max.y)
                : new Rect(float.NaN, float.NaN, 0f, 0f);
        }

        private static IEnumerable<Vector3> BoundsCorners(Bounds bounds)
        {
            var min = bounds.min;
            var max = bounds.max;
            yield return new Vector3(min.x, min.y, min.z);
            yield return new Vector3(min.x, min.y, max.z);
            yield return new Vector3(min.x, max.y, min.z);
            yield return new Vector3(min.x, max.y, max.z);
            yield return new Vector3(max.x, min.y, min.z);
            yield return new Vector3(max.x, min.y, max.z);
            yield return new Vector3(max.x, max.y, min.z);
            yield return new Vector3(max.x, max.y, max.z);
        }

        private static Rect Combine(params Rect[] rects)
        {
            var valid = rects.Where(rect => !float.IsNaN(rect.xMin) && rect.width > 0f && rect.height > 0f).ToArray();
            if (valid.Length == 0)
                return new Rect(float.NaN, float.NaN, 0f, 0f);
            var xMin = valid.Min(rect => rect.xMin);
            var yMin = valid.Min(rect => rect.yMin);
            var xMax = valid.Max(rect => rect.xMax);
            var yMax = valid.Max(rect => rect.yMax);
            return Rect.MinMaxRect(xMin, yMin, xMax, yMax);
        }

        private static bool IsMeaningfullyVisible(Rect rect) =>
            !float.IsNaN(rect.xMin) && rect.width > 0.005f && rect.height > 0.005f &&
            rect.xMax > 0.01f && rect.xMin < 0.99f && rect.yMax > 0.01f && rect.yMin < 0.99f;

        private static float Width(Rect rect) => float.IsNaN(rect.xMin) ? 0f : Mathf.Max(0f, rect.width);
        private static float Height(Rect rect) => float.IsNaN(rect.yMin) ? 0f : Mathf.Max(0f, rect.height);

        private static float[] BuildThumbnail(Texture2D image, int width, int height)
        {
            var values = new float[width * height * 3];
            for (var y = 0; y < height; y++)
            {
                for (var x = 0; x < width; x++)
                {
                    var color = image.GetPixelBilinear((x + 0.5f) / width, (y + 0.5f) / height);
                    var index = (y * width + x) * 3;
                    values[index] = color.r;
                    values[index + 1] = color.g;
                    values[index + 2] = color.b;
                }
            }
            return values;
        }

        private static float PerceptualDifference(IReadOnlyList<float> first, IReadOnlyList<float> second)
        {
            if (first == null || second == null || first.Count != second.Count || first.Count == 0)
                return 1f;
            double difference = 0d;
            for (var index = 0; index < first.Count; index++)
                difference += Math.Abs(first[index] - second[index]);
            return (float)(difference / first.Count);
        }

        private static float ComputeEdgeDensity(Color32[] pixels, int width, int height)
        {
            var edges = 0;
            var samples = 0;
            const int step = 4;
            for (var y = 0; y < height - step; y += step)
            {
                for (var x = 0; x < width - step; x += step)
                {
                    var center = Luminance(pixels[y * width + x]);
                    var right = Luminance(pixels[y * width + x + step]);
                    var up = Luminance(pixels[(y + step) * width + x]);
                    var gradient = Mathf.Abs(center - right) + Mathf.Abs(center - up);
                    if (gradient >= 0.16f)
                        edges++;
                    samples++;
                }
            }
            return samples > 0 ? (float)edges / samples : 0f;
        }

        private static float RegionMeanLuminance(
            Color32[] pixels,
            int width,
            int height,
            float xMin,
            float yMin,
            float xMax,
            float yMax)
        {
            var startX = Mathf.Clamp(Mathf.FloorToInt(width * xMin), 0, width - 1);
            var endX = Mathf.Clamp(Mathf.CeilToInt(width * xMax), startX + 1, width);
            var startY = Mathf.Clamp(Mathf.FloorToInt(height * yMin), 0, height - 1);
            var endY = Mathf.Clamp(Mathf.CeilToInt(height * yMax), startY + 1, height);
            double sum = 0d;
            var count = 0;
            for (var y = startY; y < endY; y += 3)
            {
                for (var x = startX; x < endX; x += 3)
                {
                    sum += Luminance(pixels[y * width + x]);
                    count++;
                }
            }
            return count > 0 ? (float)(sum / count) : 0f;
        }

        private static float BorderMeanLuminance(Color32[] pixels, int width, int height, float thickness)
        {
            double sum = 0d;
            var count = 0;
            var xEdge = Mathf.Max(1, Mathf.RoundToInt(width * thickness));
            var yEdge = Mathf.Max(1, Mathf.RoundToInt(height * thickness));
            for (var y = 0; y < height; y += 3)
            {
                for (var x = 0; x < width; x += 3)
                {
                    if (x >= xEdge && x < width - xEdge && y >= yEdge && y < height - yEdge)
                        continue;
                    sum += Luminance(pixels[y * width + x]);
                    count++;
                }
            }
            return count > 0 ? (float)(sum / count) : 0f;
        }

        private static float Luminance(Color32 pixel) =>
            (0.2126f * pixel.r + 0.7152f * pixel.g + 0.0722f * pixel.b) / 255f;

        private static bool ValidateHumanApproval(RenderProofReport report, ICollection<string> failures)
        {
            if (!File.Exists(ApprovalPath))
            {
                failures.Add($"Verified release requires human review of the rendered proof set. Approval file is missing: {ApprovalPath}");
                return false;
            }

            RenderApproval approval;
            try
            {
                approval = JsonUtility.FromJson<RenderApproval>(File.ReadAllText(ApprovalPath));
            }
            catch (Exception exception)
            {
                failures.Add($"Render approval JSON is invalid: {exception.Message}");
                return false;
            }
            if (approval == null)
            {
                failures.Add("Render approval JSON is empty.");
                return false;
            }
            if (approval.schemaVersion != SchemaVersion)
                failures.Add($"Render approval schema {approval.schemaVersion} does not match required schema {SchemaVersion}.");
            if (!approval.approved)
                failures.Add("Rendered proof has not been approved by a human reviewer.");
            if (string.IsNullOrWhiteSpace(approval.approvedBy))
                failures.Add("Rendered proof approval has no reviewer recorded.");
            if (string.IsNullOrWhiteSpace(approval.approvalNote))
                failures.Add("Rendered proof approval has no review note.");
            if (!string.Equals(approval.commit, report.commit, StringComparison.OrdinalIgnoreCase))
                failures.Add("Rendered proof approval belongs to a different commit.");
            if (!string.Equals(approval.sourceFingerprint, report.sourceFingerprint, StringComparison.OrdinalIgnoreCase))
                failures.Add("Rendered proof approval belongs to different source or project settings.");
            if (!string.Equals(approval.proofSetSha256, report.proofSetSha256, StringComparison.OrdinalIgnoreCase))
                failures.Add("Rendered proof approval hashes do not match the newly rendered images.");
            return failures.Count == 0;
        }

        private static void WriteApprovalTemplate(RenderProofReport report)
        {
            var approval = new RenderApproval
            {
                schemaVersion = SchemaVersion,
                approved = false,
                approvedBy = string.Empty,
                approvalNote = "Review all six rendered proof frames before setting approved=true.",
                commit = report.commit,
                sourceFingerprint = report.sourceFingerprint,
                proofSetSha256 = report.proofSetSha256
            };
            Directory.CreateDirectory(Path.GetDirectoryName(ApprovalPath) ?? "Builds/Acceptance");
            File.WriteAllText(ApprovalPath, JsonUtility.ToJson(approval, true) + "\n");
        }

        private static string ComputeProofSetSha(IEnumerable<FrameAnalysis> frames)
        {
            using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
            foreach (var frame in frames.OrderBy(frame => frame.file, StringComparer.Ordinal))
            {
                hash.AppendData(Encoding.UTF8.GetBytes(frame.file));
                hash.AppendData(new byte[] { 0 });
                hash.AppendData(Encoding.UTF8.GetBytes(frame.sha256));
                hash.AppendData(new byte[] { 0 });
            }
            return BitConverter.ToString(hash.GetHashAndReset()).Replace("-", string.Empty).ToLowerInvariant();
        }

        private static string ComputeSourceFingerprint()
        {
            using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
            var files = new List<string>();
            foreach (var root in FingerprintRoots)
            {
                if (Directory.Exists(root))
                    files.AddRange(Directory.GetFiles(root, "*", SearchOption.AllDirectories));
            }
            foreach (var path in files
                         .Select(path => path.Replace('\\', '/'))
                         .Where(IncludeInFingerprint)
                         .OrderBy(path => path, StringComparer.Ordinal))
            {
                hash.AppendData(Encoding.UTF8.GetBytes(path));
                hash.AppendData(new byte[] { 0 });
                using var stream = File.OpenRead(path);
                var buffer = new byte[1024 * 128];
                int read;
                while ((read = stream.Read(buffer, 0, buffer.Length)) > 0)
                    hash.AppendData(buffer, 0, read);
                hash.AppendData(new byte[] { 0 });
            }
            return BitConverter.ToString(hash.GetHashAndReset()).Replace("-", string.Empty).ToLowerInvariant();
        }

        private static bool IncludeInFingerprint(string path)
        {
            if (path.Contains("/Generated/", StringComparison.OrdinalIgnoreCase) ||
                path.Contains("/Tests/", StringComparison.OrdinalIgnoreCase) ||
                path.EndsWith("/Scenes/FrozenOutpost.unity", StringComparison.OrdinalIgnoreCase) ||
                path.EndsWith(".DS_Store", StringComparison.OrdinalIgnoreCase))
                return false;
            return true;
        }

        private static string Sha256(string path)
        {
            using var algorithm = SHA256.Create();
            using var stream = File.OpenRead(path);
            return BitConverter.ToString(algorithm.ComputeHash(stream)).Replace("-", string.Empty).ToLowerInvariant();
        }

        private static string BuildFailureMessage(RenderProofReport report)
        {
            var detail = report.failures == null || report.failures.Length == 0
                ? "unknown render-proof failure"
                : string.Join("\n - ", report.failures);
            return "HAVENLINE rendered visual quality gate blocked the build:\n - " + detail +
                   $"\nReview report: {ReportPath}";
        }
    }

    public sealed class HavenlineRenderProofBuildPreprocessor : IPreprocessBuildWithReport
    {
        public int callbackOrder => -1000;

        public void OnPreprocessBuild(BuildReport report)
        {
            HavenlineRenderProofGate.RequireForBuild(report);
        }
    }
}
