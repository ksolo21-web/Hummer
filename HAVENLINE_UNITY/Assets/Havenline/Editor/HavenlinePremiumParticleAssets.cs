using System;
using UnityEditor;
using UnityEngine;
using UnityEngine.Rendering;

namespace Havenline.Editor
{
    /// <summary>
    /// Creates soft transparent particle materials for furnace fire, sparks, smoke, snow and
    /// impact feedback. Opaque world materials are never reused as billboard particles.
    /// </summary>
    internal static class HavenlinePremiumParticleAssets
    {
        private const string Root = "Assets/Havenline/Art/Production/VFX/Materials";
        private const string TexturePath = Root + "/HAVENLINE_SoftParticle.asset";
        private const string FlamePath = Root + "/HAVENLINE_FlameParticle.mat";
        private const string SparkPath = Root + "/HAVENLINE_SparkParticle.mat";
        private const string SmokePath = Root + "/HAVENLINE_SmokeParticle.mat";
        private const string SnowPath = Root + "/HAVENLINE_SnowParticle.mat";
        private const string ImpactPath = Root + "/HAVENLINE_ImpactParticle.mat";

        private static bool ensured;

        internal static void Ensure()
        {
            if (ensured)
                return;
            ensured = true;
            EnsureFolder("Assets/Havenline/Art/Production/VFX", "Materials");
            var texture = CreateSoftTexture();
            AssetDatabase.DeleteAsset(TexturePath);
            AssetDatabase.CreateAsset(texture, TexturePath);
            CreateMaterial(FlamePath, texture, true);
            CreateMaterial(SparkPath, texture, true);
            CreateMaterial(SmokePath, texture, false);
            CreateMaterial(SnowPath, texture, false);
            CreateMaterial(ImpactPath, texture, true);
            AssetDatabase.SaveAssets();
        }

        internal static Material Resolve(string effectName)
        {
            Ensure();
            var path = effectName switch
            {
                "FurnaceFire" => FlamePath,
                "FurnaceSparks" => SparkPath,
                "FurnaceSmoke" => SmokePath,
                "Snowfall" => SnowPath,
                _ => ImpactPath
            };
            return AssetDatabase.LoadAssetAtPath<Material>(path) ??
                   throw new InvalidOperationException("HAVENLINE particle material failed to generate: " + path);
        }

        private static Texture2D CreateSoftTexture()
        {
            const int size = 96;
            var texture = new Texture2D(size, size, TextureFormat.RGBA32, false, true)
            {
                name = "HAVENLINE_SoftParticle",
                filterMode = FilterMode.Bilinear,
                wrapMode = TextureWrapMode.Clamp,
                hideFlags = HideFlags.None
            };
            var pixels = new Color32[size * size];
            for (var y = 0; y < size; y++)
            {
                for (var x = 0; x < size; x++)
                {
                    var uv = new Vector2(
                        (x + 0.5f) / size * 2f - 1f,
                        (y + 0.5f) / size * 2f - 1f);
                    var distance = uv.magnitude;
                    var alpha = Mathf.Pow(Mathf.Clamp01(1f - distance), 1.65f);
                    alpha *= Mathf.SmoothStep(0f, 1f, Mathf.InverseLerp(1f, 0.68f, distance));
                    pixels[y * size + x] = new Color32(255, 255, 255,
                        (byte)Mathf.RoundToInt(alpha * 255f));
                }
            }
            texture.SetPixels32(pixels);
            texture.Apply(false, false);
            return texture;
        }

        private static void CreateMaterial(string path, Texture texture, bool additive)
        {
            AssetDatabase.DeleteAsset(path);
            var shader = Shader.Find("Universal Render Pipeline/Particles/Unlit") ??
                         Shader.Find("Particles/Standard Unlit") ??
                         Shader.Find("Universal Render Pipeline/Unlit");
            if (shader == null)
                throw new InvalidOperationException("HAVENLINE could not find a transparent particle shader.");

            var material = new Material(shader)
            {
                name = System.IO.Path.GetFileNameWithoutExtension(path),
                renderQueue = (int)RenderQueue.Transparent,
                enableInstancing = true
            };
            if (material.HasProperty("_BaseMap")) material.SetTexture("_BaseMap", texture);
            if (material.HasProperty("_MainTex")) material.SetTexture("_MainTex", texture);
            if (material.HasProperty("_BaseColor")) material.SetColor("_BaseColor", Color.white);
            if (material.HasProperty("_Color")) material.SetColor("_Color", Color.white);
            if (material.HasProperty("_Surface")) material.SetFloat("_Surface", 1f);
            if (material.HasProperty("_Blend")) material.SetFloat("_Blend", additive ? 1f : 0f);
            if (material.HasProperty("_SrcBlend")) material.SetFloat("_SrcBlend", (float)BlendMode.SrcAlpha);
            if (material.HasProperty("_DstBlend"))
                material.SetFloat("_DstBlend", additive ? (float)BlendMode.One : (float)BlendMode.OneMinusSrcAlpha);
            if (material.HasProperty("_ZWrite")) material.SetFloat("_ZWrite", 0f);
            material.SetOverrideTag("RenderType", "Transparent");
            material.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
            if (additive)
                material.EnableKeyword("_ALPHAPREMULTIPLY_ON");
            AssetDatabase.CreateAsset(material, path);
        }

        private static void EnsureFolder(string parent, string name)
        {
            var path = parent + "/" + name;
            if (!AssetDatabase.IsValidFolder(path))
                AssetDatabase.CreateFolder(parent, name);
        }
    }
}
