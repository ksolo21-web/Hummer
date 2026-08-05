using System;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEngine;

namespace Havenline.Editor
{
    /// <summary>
    /// Generates purpose-built UI sprites instead of stretching the complete HUD atlas over
    /// every Image. Panels keep stable sliced corners while controls remain true circles.
    /// The top status row uses a separate near-opaque rounded panel so world props cannot
    /// read through objective/resource cards as stray glyphs on narrow foldable layouts.
    /// </summary>
    internal static class HavenlineStudioUiAssets
    {
        private const int TextureSize = 128;
        private const string Root = "Assets/Havenline/Art/Production/UI";
        private const string PanelPath = Root + "/HAVENLINE_UI_Panel.asset";
        private const string TopPanelPath = Root + "/HAVENLINE_UI_TopPanel.asset";
        private const string ControlPath = Root + "/HAVENLINE_UI_Control.asset";
        private const string WarmthPath = Root + "/HAVENLINE_UI_Warmth.asset";
        private const string SolidPath = Root + "/HAVENLINE_UI_Solid.asset";

        internal const byte StandardPanelInteriorAlpha = 208;
        internal const byte TopPanelInteriorAlpha = 248;

        private static bool generated;

        internal static Sprite Resolve(string imageName)
        {
            Ensure();
            var path = PathFor(imageName);
            var sprite = AssetDatabase.LoadAllAssetsAtPath(path).OfType<Sprite>().FirstOrDefault();
            if (sprite == null)
                throw new InvalidOperationException($"HAVENLINE UI sprite failed to generate: {path}");
            return sprite;
        }

        internal static bool ShouldSlice(string imageName) =>
            !IsControl(imageName) && !IsWarmth(imageName) && !IsSolid(imageName);

        internal static bool IsTopStatusPanel(string name) =>
            string.Equals(name, "ResourcesPanel", StringComparison.Ordinal) ||
            string.Equals(name, "ObjectivePanel", StringComparison.Ordinal) ||
            string.Equals(name, "FurnacePanel", StringComparison.Ordinal);

        private static void Ensure()
        {
            if (generated)
                return;
            generated = true;
            Directory.CreateDirectory(Root);
            CreateSpriteAsset(PanelPath, UiShape.Panel);
            CreateSpriteAsset(TopPanelPath, UiShape.TopPanel);
            CreateSpriteAsset(ControlPath, UiShape.Control);
            CreateSpriteAsset(WarmthPath, UiShape.Warmth);
            CreateSpriteAsset(SolidPath, UiShape.Solid);
            AssetDatabase.SaveAssets();
        }

        private static string PathFor(string imageName)
        {
            if (IsTopStatusPanel(imageName)) return TopPanelPath;
            if (IsControl(imageName)) return ControlPath;
            if (IsWarmth(imageName)) return WarmthPath;
            if (IsSolid(imageName)) return SolidPath;
            return PanelPath;
        }

        private static bool IsControl(string name) =>
            name.Contains("Joystick", StringComparison.OrdinalIgnoreCase);

        private static bool IsWarmth(string name) =>
            name.Contains("Warmth", StringComparison.OrdinalIgnoreCase);

        private static bool IsSolid(string name) =>
            name.Contains("Dimmer", StringComparison.OrdinalIgnoreCase) ||
            name.Contains("Progress", StringComparison.OrdinalIgnoreCase) ||
            name.Contains("Accent", StringComparison.OrdinalIgnoreCase);

        private static void CreateSpriteAsset(string path, UiShape shape)
        {
            AssetDatabase.DeleteAsset(path);
            var texture = new Texture2D(TextureSize, TextureSize, TextureFormat.RGBA32, false, false)
            {
                name = System.IO.Path.GetFileNameWithoutExtension(path) + "_Texture",
                filterMode = FilterMode.Bilinear,
                wrapMode = TextureWrapMode.Clamp,
                hideFlags = HideFlags.None
            };
            var pixels = new Color32[TextureSize * TextureSize];
            for (var y = 0; y < TextureSize; y++)
            {
                for (var x = 0; x < TextureSize; x++)
                    pixels[y * TextureSize + x] = Pixel(shape, x, y);
            }
            texture.SetPixels32(pixels);
            texture.Apply(false, false);
            AssetDatabase.CreateAsset(texture, path);

            var border = shape is UiShape.Panel or UiShape.TopPanel
                ? new Vector4(28f, 28f, 28f, 28f)
                : Vector4.zero;
            var sprite = Sprite.Create(
                texture,
                new Rect(0f, 0f, TextureSize, TextureSize),
                new Vector2(0.5f, 0.5f),
                100f,
                0,
                SpriteMeshType.FullRect,
                border);
            sprite.name = System.IO.Path.GetFileNameWithoutExtension(path) + "_Sprite";
            sprite.hideFlags = HideFlags.None;
            AssetDatabase.AddObjectToAsset(sprite, texture);
            EditorUtility.SetDirty(texture);
            EditorUtility.SetDirty(sprite);
            AssetDatabase.ImportAsset(path,
                ImportAssetOptions.ForceSynchronousImport | ImportAssetOptions.ForceUpdate);
        }

        private static Color32 Pixel(UiShape shape, int x, int y)
        {
            if (shape == UiShape.Solid)
                return new Color32(255, 255, 255, 255);

            var centered = new Vector2(
                (x + 0.5f) / TextureSize * 2f - 1f,
                (y + 0.5f) / TextureSize * 2f - 1f);

            if (shape is UiShape.Panel or UiShape.TopPanel)
            {
                var outside = RoundedBoxDistance(centered, new Vector2(0.93f, 0.93f), 0.20f);
                if (outside > 0f)
                    return new Color32(255, 255, 255, 0);
                var edge = Mathf.Clamp01(-outside / 0.085f);
                var interiorAlpha = shape == UiShape.TopPanel
                    ? TopPanelInteriorAlpha
                    : StandardPanelInteriorAlpha;
                var alpha = (byte)Mathf.RoundToInt(Mathf.Lerp(255f, interiorAlpha, edge));
                return new Color32(255, 255, 255, alpha);
            }

            var radius = centered.magnitude;
            if (radius > 0.98f)
                return new Color32(255, 255, 255, 0);
            var ring = Mathf.SmoothStep(0f, 1f, Mathf.InverseLerp(0.76f, 0.94f, radius));
            var fillAlpha = shape == UiShape.Warmth ? 76f : 96f;
            var ringAlpha = shape == UiShape.Warmth ? 255f : 225f;
            var alphaValue = (byte)Mathf.RoundToInt(Mathf.Lerp(fillAlpha, ringAlpha, ring));
            return new Color32(255, 255, 255, alphaValue);
        }

        private static float RoundedBoxDistance(Vector2 point, Vector2 halfSize, float radius)
        {
            var q = new Vector2(Mathf.Abs(point.x), Mathf.Abs(point.y)) - halfSize + Vector2.one * radius;
            return new Vector2(Mathf.Max(q.x, 0f), Mathf.Max(q.y, 0f)).magnitude +
                   Mathf.Min(Mathf.Max(q.x, q.y), 0f) - radius;
        }

        private enum UiShape
        {
            Panel,
            TopPanel,
            Control,
            Warmth,
            Solid
        }
    }
}
