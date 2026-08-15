using System;
using System.Linq;
using System.Runtime.CompilerServices;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Havenline.Editor
{
    /// <summary>
    /// Keeps the editor-only C1-C4/shelter proof overlay out of production scene validation.
    /// Scene-open validation sees the transient root inactive; Camera.Render activates it just
    /// before proof capture so the proof gate still measures the real staged crew and premium
    /// shelter aliases. Nothing is serialized and nothing enters the Android player.
    /// </summary>
    [InitializeOnLoad]
    internal static class ZZZZHavenlineProofPreviewIsolation
    {
        static ZZZZHavenlineProofPreviewIsolation()
        {
            RuntimeHelpers.RunClassConstructor(typeof(HavenlineApprovedCrewProofPreview).TypeHandle);
            EditorSceneManager.sceneOpened -= OnSceneOpened;
            EditorSceneManager.sceneOpened += OnSceneOpened;
            Camera.onPreCull -= OnCameraPreCull;
            Camera.onPreCull += OnCameraPreCull;
        }

        private static void OnSceneOpened(Scene scene, OpenSceneMode mode)
        {
            if (!scene.IsValid() || !string.Equals(scene.path, Reference.ScenePath, StringComparison.Ordinal))
                return;
            SetPreviewActive(scene, false);
        }

        private static void OnCameraPreCull(Camera camera)
        {
            if (camera == null)
                return;
            var scene = camera.gameObject.scene;
            if (!scene.IsValid() || !string.Equals(scene.path, Reference.ScenePath, StringComparison.Ordinal))
                return;
            SetPreviewActive(scene, true);
        }

        private static void SetPreviewActive(Scene scene, bool active)
        {
            var preview = scene.GetRootGameObjects()
                .FirstOrDefault(root => root.name == HavenlineApprovedCrewProofPreview.RootName);
            if (preview != null && preview.activeSelf != active)
                preview.SetActive(active);
        }
    }
}
