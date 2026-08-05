using UnityEngine;
using UnityEngine.InputSystem;

namespace Havenline
{
    public sealed class HavenlineSafeArea : MonoBehaviour
    {
        private Rect last;
        private RectTransform rectTransform;

        private void Awake()
        {
            rectTransform = (RectTransform)transform;
            Apply();
        }

        private void Update()
        {
            if (Screen.safeArea != last)
                Apply();
        }

        private void Apply()
        {
            last = Screen.safeArea;
            var min = last.position;
            var max = last.position + last.size;
            min.x /= Mathf.Max(1f, Screen.width);
            min.y /= Mathf.Max(1f, Screen.height);
            max.x /= Mathf.Max(1f, Screen.width);
            max.y /= Mathf.Max(1f, Screen.height);
            rectTransform.anchorMin = min;
            rectTransform.anchorMax = max;
            rectTransform.offsetMin = rectTransform.offsetMax = Vector2.zero;
        }
    }
}
