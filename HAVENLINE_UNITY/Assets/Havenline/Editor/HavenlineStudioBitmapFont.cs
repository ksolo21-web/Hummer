using System;
using System.Collections.Generic;
using System.IO;
using UnityEditor;
using UnityEngine;

namespace Havenline.Editor
{
    internal static class HavenlineStudioBitmapFont
    {
        private const int AtlasSize = 512;
        private const int Columns = 16;
        private const int CellWidth = 30;
        private const int CellHeight = 34;
        private const int PixelScale = 4;
        private const int GlyphWidth = 20;
        private const int GlyphHeight = 28;
        private const string AtlasPath =
            "Assets/Havenline/Art/Production/UI/HAVENLINE_UI_FontAtlas.png";
        private const string MaterialPath =
            "Assets/Havenline/Art/Production/UI/HAVENLINE_UI_FontMaterial.mat";

        private static readonly IReadOnlyDictionary<char, string[]> Glyphs = BuildGlyphs();

        internal static void Generate(string fontPath)
        {
            AssetDatabase.DeleteAsset(fontPath);
            AssetDatabase.DeleteAsset(MaterialPath);

            var texture = new Texture2D(AtlasSize, AtlasSize, TextureFormat.RGBA32, false, false)
            {
                name = "HAVENLINE_UI_FontAtlas",
                filterMode = FilterMode.Bilinear,
                wrapMode = TextureWrapMode.Clamp
            };
            var clear = new Color32(0, 0, 0, 0);
            var pixels = new Color32[AtlasSize * AtlasSize];
            Array.Fill(pixels, clear);
            texture.SetPixels32(pixels);

            var characters = new List<CharacterInfo>();
            for (var code = 32; code <= 126; code++)
            {
                var character = (char)code;
                var glyphIndex = code - 32;
                var column = glyphIndex % Columns;
                var row = glyphIndex / Columns;
                var cellX = column * CellWidth;
                var cellY = AtlasSize - (row + 1) * CellHeight;
                var glyphX = cellX + (CellWidth - GlyphWidth) / 2;
                var glyphY = cellY + (CellHeight - GlyphHeight) / 2;

                if (character != ' ')
                    DrawGlyph(texture, glyphX, glyphY, PatternFor(character));

                var u0 = glyphX / (float)AtlasSize;
                var v0 = glyphY / (float)AtlasSize;
                var u1 = (glyphX + GlyphWidth) / (float)AtlasSize;
                var v1 = (glyphY + GlyphHeight) / (float)AtlasSize;
                characters.Add(new CharacterInfo
                {
                    index = code,
                    uvBottomLeft = new Vector2(u0, v0),
                    uvBottomRight = new Vector2(u1, v0),
                    uvTopLeft = new Vector2(u0, v1),
                    uvTopRight = new Vector2(u1, v1),
                    minX = 0,
                    maxX = character == ' ' ? 8 : GlyphWidth,
                    minY = -3,
                    maxY = GlyphHeight - 3,
                    advance = character == ' ' ? 12 : 22,
                    glyphWidth = character == ' ' ? 8 : GlyphWidth,
                    glyphHeight = GlyphHeight,
                    size = 24,
                    style = FontStyle.Normal
                });
            }

            texture.Apply(false, false);
            Directory.CreateDirectory(Path.GetDirectoryName(AtlasPath) ?? string.Empty);
            File.WriteAllBytes(AtlasPath, texture.EncodeToPNG());
            UnityEngine.Object.DestroyImmediate(texture);
            AssetDatabase.ImportAsset(AtlasPath,
                ImportAssetOptions.ForceSynchronousImport | ImportAssetOptions.ForceUpdate);
            if (AssetImporter.GetAtPath(AtlasPath) is TextureImporter importer)
            {
                importer.textureType = TextureImporterType.Default;
                importer.alphaIsTransparency = true;
                importer.mipmapEnabled = false;
                importer.filterMode = FilterMode.Bilinear;
                importer.wrapMode = TextureWrapMode.Clamp;
                importer.textureCompression = TextureImporterCompression.Uncompressed;
                importer.maxTextureSize = AtlasSize;
                importer.SaveAndReimport();
            }

            var atlas = AssetDatabase.LoadAssetAtPath<Texture2D>(AtlasPath);
            if (atlas == null)
                throw new InvalidOperationException("HAVENLINE static font atlas failed to import.");
            var shader = Shader.Find("UI/Default") ?? Shader.Find("Unlit/Transparent");
            if (shader == null)
                throw new InvalidOperationException("HAVENLINE static font could not find a UI shader.");
            var material = new Material(shader)
            {
                name = "HAVENLINE_UI_FontMaterial",
                mainTexture = atlas,
                hideFlags = HideFlags.None
            };
            AssetDatabase.CreateAsset(material, MaterialPath);

            var font = new Font
            {
                name = "HAVENLINE_UI_Geometric_Static",
                material = material,
                characterInfo = characters.ToArray()
            };
            AssetDatabase.CreateAsset(font, fontPath);
            EditorUtility.SetDirty(font);
            AssetDatabase.SaveAssets();
            AssetDatabase.ImportAsset(fontPath,
                ImportAssetOptions.ForceSynchronousImport | ImportAssetOptions.ForceUpdate);
        }

        private static string[] PatternFor(char character)
        {
            var upper = char.ToUpperInvariant(character);
            return Glyphs.TryGetValue(upper, out var pattern) ? pattern : Glyphs['?'];
        }

        private static void DrawGlyph(Texture2D texture, int originX, int originY, IReadOnlyList<string> pattern)
        {
            var color = new Color32(255, 255, 255, 255);
            for (var row = 0; row < 7; row++)
            {
                for (var column = 0; column < 5; column++)
                {
                    if (pattern[row][column] != '1')
                        continue;
                    var baseX = originX + column * PixelScale;
                    var baseY = originY + (6 - row) * PixelScale;
                    for (var y = 0; y < PixelScale; y++)
                        for (var x = 0; x < PixelScale; x++)
                            texture.SetPixel(baseX + x, baseY + y, color);
                }
            }
        }

        private static IReadOnlyDictionary<char, string[]> BuildGlyphs() => new Dictionary<char, string[]>
        {
            ['A'] = P("01110","10001","10001","11111","10001","10001","10001"),
            ['B'] = P("11110","10001","10001","11110","10001","10001","11110"),
            ['C'] = P("01111","10000","10000","10000","10000","10000","01111"),
            ['D'] = P("11110","10001","10001","10001","10001","10001","11110"),
            ['E'] = P("11111","10000","10000","11110","10000","10000","11111"),
            ['F'] = P("11111","10000","10000","11110","10000","10000","10000"),
            ['G'] = P("01111","10000","10000","10111","10001","10001","01111"),
            ['H'] = P("10001","10001","10001","11111","10001","10001","10001"),
            ['I'] = P("11111","00100","00100","00100","00100","00100","11111"),
            ['J'] = P("00111","00010","00010","00010","10010","10010","01100"),
            ['K'] = P("10001","10010","10100","11000","10100","10010","10001"),
            ['L'] = P("10000","10000","10000","10000","10000","10000","11111"),
            ['M'] = P("10001","11011","10101","10101","10001","10001","10001"),
            ['N'] = P("10001","11001","10101","10011","10001","10001","10001"),
            ['O'] = P("01110","10001","10001","10001","10001","10001","01110"),
            ['P'] = P("11110","10001","10001","11110","10000","10000","10000"),
            ['Q'] = P("01110","10001","10001","10001","10101","10010","01101"),
            ['R'] = P("11110","10001","10001","11110","10100","10010","10001"),
            ['S'] = P("01111","10000","10000","01110","00001","00001","11110"),
            ['T'] = P("11111","00100","00100","00100","00100","00100","00100"),
            ['U'] = P("10001","10001","10001","10001","10001","10001","01110"),
            ['V'] = P("10001","10001","10001","10001","10001","01010","00100"),
            ['W'] = P("10001","10001","10001","10101","10101","10101","01010"),
            ['X'] = P("10001","10001","01010","00100","01010","10001","10001"),
            ['Y'] = P("10001","10001","01010","00100","00100","00100","00100"),
            ['Z'] = P("11111","00001","00010","00100","01000","10000","11111"),
            ['0'] = P("01110","10001","10011","10101","11001","10001","01110"),
            ['1'] = P("00100","01100","00100","00100","00100","00100","01110"),
            ['2'] = P("01110","10001","00001","00010","00100","01000","11111"),
            ['3'] = P("11110","00001","00001","01110","00001","00001","11110"),
            ['4'] = P("00010","00110","01010","10010","11111","00010","00010"),
            ['5'] = P("11111","10000","10000","11110","00001","00001","11110"),
            ['6'] = P("01110","10000","10000","11110","10001","10001","01110"),
            ['7'] = P("11111","00001","00010","00100","01000","01000","01000"),
            ['8'] = P("01110","10001","10001","01110","10001","10001","01110"),
            ['9'] = P("01110","10001","10001","01111","00001","00001","01110"),
            ['-'] = P("00000","00000","00000","11111","00000","00000","00000"),
            ['/'] = P("00001","00010","00010","00100","01000","01000","10000"),
            [':'] = P("00000","00100","00100","00000","00100","00100","00000"),
            ['.'] = P("00000","00000","00000","00000","00000","00110","00110"),
            [','] = P("00000","00000","00000","00000","00110","00110","00100"),
            ['!'] = P("00100","00100","00100","00100","00100","00000","00100"),
            ['?'] = P("01110","10001","00001","00010","00100","00000","00100"),
            ['+'] = P("00000","00100","00100","11111","00100","00100","00000"),
            ['='] = P("00000","11111","00000","11111","00000","00000","00000"),
            ['('] = P("00010","00100","01000","01000","01000","00100","00010"),
            [')'] = P("01000","00100","00010","00010","00010","00100","01000"),
            ['['] = P("01110","01000","01000","01000","01000","01000","01110"),
            [']'] = P("01110","00010","00010","00010","00010","00010","01110"),
            ['_'] = P("00000","00000","00000","00000","00000","00000","11111"),
            ['#'] = P("01010","11111","01010","01010","11111","01010","00000"),
            ['%'] = P("11001","11010","00100","01000","10110","00110","00000"),
            ['&'] = P("01100","10010","10100","01000","10101","10010","01101"),
            ['*'] = P("00000","10101","01110","11111","01110","10101","00000"),
            ['<'] = P("00010","00100","01000","10000","01000","00100","00010"),
            ['>'] = P("01000","00100","00010","00001","00010","00100","01000"),
            ['|'] = P("00100","00100","00100","00100","00100","00100","00100"),
            ['@'] = P("01110","10001","10111","10101","10111","10000","01110"),
            ['\''] = P("00100","00100","00000","00000","00000","00000","00000"),
            ['\"'] = P("01010","01010","00000","00000","00000","00000","00000"),
            [';'] = P("00000","00100","00100","00000","00100","00100","01000")
        };

        private static string[] P(params string[] rows) => rows;
    }
}
