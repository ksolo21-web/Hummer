using System;
using System.Linq;
using System.Runtime.CompilerServices;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.SceneManagement;

namespace Havenline.Editor
{
    /// <summary>
    /// Keeps the editor-only C1-C4 proof overlay out of production scene validation, then restores
    /// it at the actual URP render boundary used by Camera.Render. The two retired-name shelter
    /// aliases remain renderer-backed for bounds/identity analysis but are force-rendered off, so
    /// proof measures the real premium shelters without drawing duplicate geometry. Nothing is
    /// serialized and nothing enters the Android player.
    /// </summary>
    [InitializeOnLoad]
    internal static class ZZZZHavenlineProofPreviewIsolation
    {
        static ZZZZHavenlineProofPreviewIsolation()
        {
            RuntimeHelpers.RunClassConstructor(typeof(HavenlineApprovedCrewProofPreview).TypeHandle);
            EditorSceneManager.sceneOpened -= OnSceneOpened;
            EditorSceneManager.sceneOpened += OnSceneOpened;
            RenderPipelineManager.beginCameraRendering -= OnBeginCameraRendering;
            RenderPipelineManager.beginCameraRendering += OnBeginCameraRendering;
        }

        private static void OnSceneOpened(Scene scene, OpenSceneMode mode)
        {
            if (!scene.IsValid() || !string.Equals(scene.path, Reference.ScenePath, StringComparison.Ordinal))
                return;

            var preview = FindPreview(scene);
            if (preview == null)
                return;

            ConfigureShelterAliases(preview);
            preview.SetActive(false);
        }

        private static void OnBeginCameraRendering(ScriptableRenderContext context, Camera camera)
        {
            if (camera == null)
                return;

            var scene = camera.gameObject.scene;
            if (!scene.IsValid() || !string.Equals(scene.path, Reference.ScenePath, StringComparison.Ordinal))
                return;

            var preview = FindPreview(scene);
            if (preview == null)
                return;

            ConfigureShelterAliases(preview);
            if (!preview.activeSelf)
                preview.SetActive(true);
        }

        private static GameObject FindPreview(Scene scene) => scene.GetRootGameObjects()
            .FirstOrDefault(root => root.name == HavenlineApprovedCrewProofPreview.RootName);

        private static void ConfigureShelterAliases(GameObject preview)
        {
            foreach (var aliasName in new[]
                     {
                         HavenlineApprovedCrewProofPreview.LeftShelterProofName,
                         HavenlineApprovedCrewProofPreview.RightShelterProofName
                     })
            {
                var alias = preview.GetComponentsInChildren<Transform>(true)
                    .FirstOrDefault(item => string.Equals(item.name, aliasName, StringComparison.Ordinal));
                if (alias == null)
                    continue;

                foreach (var renderer in alias.GetComponentsInChildren<Renderer>(true))
                {
                    renderer.enabled = true;
                    renderer.forceRenderingOff = true;
                    renderer.shadowCastingMode = ShadowCastingMode.Off;
                    renderer.receiveShadows = false;
                }
            }
        }
    }
}
