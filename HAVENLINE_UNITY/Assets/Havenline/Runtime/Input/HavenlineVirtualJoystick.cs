using UnityEngine;
using UnityEngine.EventSystems;

namespace Havenline
{
    public sealed class HavenlineVirtualJoystick : MonoBehaviour, IPointerDownHandler, IDragHandler, IPointerUpHandler
    {
        [SerializeField] private RectTransform handle;
        [SerializeField, Min(16f)] private float movementRadius = 72f;
        [SerializeField, Range(0f, 0.95f)] private float deadZone = 0.12f;

        private RectTransform _root;
        private Canvas _canvas;
        private Vector2 _value;

        public Vector2 Value => _value;

        private void Awake()
        {
            _root = transform as RectTransform;
            _canvas = GetComponentInParent<Canvas>();

            if (_root == null || handle == null || _canvas == null)
            {
                Debug.LogError("HAVENLINE joystick requires a RectTransform root, handle, and parent Canvas.", this);
                enabled = false;
            }
        }

        public void OnPointerDown(PointerEventData eventData)
        {
            UpdateValue(eventData);
        }

        public void OnDrag(PointerEventData eventData)
        {
            UpdateValue(eventData);
        }

        public void OnPointerUp(PointerEventData eventData)
        {
            _value = Vector2.zero;
            handle.anchoredPosition = Vector2.zero;
        }

        private void UpdateValue(PointerEventData eventData)
        {
            if (!RectTransformUtility.ScreenPointToLocalPointInRectangle(
                    _root,
                    eventData.position,
                    eventData.pressEventCamera,
                    out var localPoint))
            {
                return;
            }

            var normalized = Vector2.ClampMagnitude(localPoint / movementRadius, 1f);
            var magnitude = normalized.magnitude;
            _value = magnitude <= deadZone
                ? Vector2.zero
                : normalized.normalized * Mathf.InverseLerp(deadZone, 1f, magnitude);

            handle.anchoredPosition = normalized * movementRadius;
        }
    }
}
