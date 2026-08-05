using System;
using System.Linq;
using UnityEditor;
using UnityEngine;

namespace Havenline.Editor
{
    /// <summary>
    /// Finishes deterministic studio prefabs after Unity imports them. The guard prevents
    /// recursive import loops while guaranteeing that the authored environment always has
    /// the premium shadow-casting winter key required by the shipping-scene contract.
    /// </summary>
    public sealed class HavenlineStudioGeneratedAssetPostprocessor : AssetPostprocessor
    {
        private const string EnvironmentPath =
            "Assets/Havenline/Art/Production/Environment/HAVENLINE_FrozenOutpost_Environment.prefab";
        private static bool patching;

        private static void OnPostprocessAllAssets(
            string[] importedAssets,
            string[] deletedAssets,
            string[] movedAssets,
            string[] movedFromAssetPaths)
        {
            if (patching || !importedAssets.Contains(EnvironmentPath, StringComparer.Ordinal))
                return;

            patching = true;
            try
            {
                var root = PrefabUtility.LoadPrefabContents(EnvironmentPath);
                try
                {
                    var directional = root.GetComponentsInChildren<Light>(true)
                        .FirstOrDefault(light => light.type == LightType.Directional);
                    if (directional != null)
                        return;

                    var lightObject = new GameObject("WinterKeyLight");
                    lightObject.transform.SetParent(root.transform, false);
                    lightObject.transform.localRotation = Quaternion.Euler(46f, -34f, 0f);
                    directional = lightObject.AddComponent<Light>();
                    directional.type = LightType.Directional;
                    directional.color = new Color(0.77f, 0.87f, 1f);
                    directional.intensity = 1.18f;
                    directional.shadows = LightShadows.Soft;
                    directional.shadowStrength = 0.68f;
                    directional.shadowBias = 0.035f;
                    directional.shadowNormalBias = 0.35f;
                    PrefabUtility.SaveAsPrefabAsset(root, EnvironmentPath);
                }
                finally
                {
                    PrefabUtility.UnloadPrefabContents(root);
                }
            }
            finally
            {
                patching = false;
            }
        }
    }
}
