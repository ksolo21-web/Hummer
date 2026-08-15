using System;
using System.Linq;
using System.Runtime.CompilerServices;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

namespace Havenline.Editor
{
    /// <summary>
    /// Final deterministic HUD binding pass. Earlier visual polish intentionally owns layout and
    /// opacity; this pass runs after it and restores HAVENLINE's authored sliced sprites so a
    /// built-in Unity skin can never leak back into the shipping top cards.
    /// </summary>
    [InitializeOnLoad]
    internal static class ZZZZHavenlineR32HudSpriteFinalizer
    {
        static ZZZZHavenlineR32HudSpriteFinalizer()
        {
            RuntimeHelpers.RunClassConstructor(typeof(HavenlineExampleGameHudPolish).TypeHandle);
            RuntimeHelpers.RunClassConstructor(typeof(ZZZHavenlineR30PremiumComposition).TypeHandle);
            EditorSceneManager.sceneSaving -= OnSceneSaving;
            EditorSceneManager.sceneSaving += OnSceneSaving;
        }

        private static void OnSceneSaving(Scene scene, string path)
        {
            if (!string.Equals(path, Reference.ScenePath, StringComparison.Ordinal))
                return;
            Apply(scene);
        }

        internal static void Apply(Scene scene)
        {
            if (!scene.IsValid() || !scene.isLoaded)
                return;

            var hud = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
                .FirstOrDefault(item => item.name == HavenlineExampleGameHudPolish.GameplayHudName)
                ?.gameObject;
            if (hud == null)
                return;

            foreach (var name in new[]
                     {
                         "ResourcesPanel", "ObjectivePanel", "FurnacePanel",
                         "ContextPanel", "HelperPanel", "ThreatPanel"
                     })
            {
                var image = hud.GetComponentsInChildren<Image>(true)
                    .FirstOrDefault(candidate => string.Equals(candidate.name, name, StringComparison.Ordinal));
                if (image == null)
                    continue;

                image.sprite = HavenlineStudioUiAssets.Resolve(name);
                image.type = HavenlineStudioUiAssets.ShouldSlice(name)
                    ? Image.Type.Sliced
                    : Image.Type.Simple;
                image.raycastTarget = false;

                if (HavenlineStudioUiAssets.IsTopStatusPanel(name))
                {
                    var color = image.color;
                    color.a = 1f;
                    image.color = color;
                }
                EditorUtility.SetDirty(image);
            }
        }
    }
}
