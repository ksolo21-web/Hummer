using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
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
