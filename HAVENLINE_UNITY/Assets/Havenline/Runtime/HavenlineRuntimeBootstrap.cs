using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
    /// <summary>
    /// Applies runtime-only safeguards that must survive phone, tablet and foldable
    /// configuration changes without adding permanent action UI or changing gameplay.
    /// </summary>
    public static class HavenlineRuntimeBootstrap
    {
        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void InitializeShippingScene()
        {
            foreach (var camera in Object.FindObjectsByType<Camera>(FindObjectsInactive.Include, FindObjectsSortMode.None))
            {
                camera.allowDynamicResolution = true;
                if (camera.CompareTag("MainCamera") && camera.GetComponent<HavenlineAdaptiveCameraFraming>() == null)
                    camera.gameObject.AddComponent<HavenlineAdaptiveCameraFraming>();
            }

            foreach (var barricade in Object.FindObjectsByType<HavenlineBarricade>(FindObjectsInactive.Include, FindObjectsSortMode.None))
                barricade.Configure(HierarchyPath(barricade.transform), 160f);
        }

        private static string HierarchyPath(Transform transform)
        {
            var names = new Stack<string>();
            for (var current = transform; current != null; current = current.parent)
                names.Push(current.name);
            return string.Join("/", names);
        }
    }

    [DisallowMultipleComponent]
    [RequireComponent(typeof(Camera))]
    public sealed class HavenlineAdaptiveCameraFraming : MonoBehaviour
    {
        [SerializeField] private float narrowAspectMaximumScale = 1.24f;
        [SerializeField] private float transitionSharpness = 8f;

        private Camera cameraComponent;
        private int lastWidth;
        private int lastHeight;
        private float targetSize = Reference.CameraSize;

        private void Awake()
        {
            cameraComponent = GetComponent<Camera>();
            cameraComponent.allowDynamicResolution = true;
            Recalculate();
            cameraComponent.orthographicSize = targetSize;
        }

        private void LateUpdate()
        {
            if (Screen.width != lastWidth || Screen.height != lastHeight)
                Recalculate();

            cameraComponent.orthographicSize = Mathf.Lerp(
                cameraComponent.orthographicSize,
                targetSize,
                1f - Mathf.Exp(-transitionSharpness * Time.unscaledDeltaTime));
        }

        private void Recalculate()
        {
            lastWidth = Mathf.Max(1, Screen.width);
            lastHeight = Mathf.Max(1, Screen.height);
            var aspect = lastWidth / (float)lastHeight;
            const float referenceAspect = 16f / 9f;
            var narrowScale = Mathf.Clamp(referenceAspect / Mathf.Max(0.75f, aspect), 1f, narrowAspectMaximumScale);
            targetSize = Reference.CameraSize * narrowScale;
        }
    }
}
