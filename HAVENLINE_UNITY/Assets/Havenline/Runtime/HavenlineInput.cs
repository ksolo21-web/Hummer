using UnityEngine;
using UnityEngine.InputSystem;

namespace Havenline
{
    /// <summary>
    /// HAVENLINE exposes one permanent gameplay control: movement. Full-stick movement
    /// transitions into running automatically; all world actions are proximity-driven.
    /// </summary>
    public sealed class HavenlineInputRouter : MonoBehaviour
    {
        [SerializeField] private float joystickRadiusPixels = 125f;
        [SerializeField] private float automaticRunThreshold = 0.92f;
        [SerializeField] private float automaticRunDelay = 0.28f;

        private int joystickTouch = -1;
        private Vector2 joystickOrigin;
        private float fullTiltTime;

        public Vector2 Move { get; private set; }
        public bool DashHeld { get; private set; }

        private void Update()
        {
            Move = ReadDesktopAndController();
            ReadTouchMovement();

            if (Move.magnitude >= automaticRunThreshold)
                fullTiltTime += Time.unscaledDeltaTime;
            else
                fullTiltTime = 0f;

            DashHeld = fullTiltTime >= automaticRunDelay;
        }

        private static Vector2 ReadDesktopAndController()
        {
            var result = Vector2.zero;
            var keyboard = Keyboard.current;
            if (keyboard != null)
            {
                if (keyboard.wKey.isPressed || keyboard.upArrowKey.isPressed) result.y += 1f;
                if (keyboard.sKey.isPressed || keyboard.downArrowKey.isPressed) result.y -= 1f;
                if (keyboard.dKey.isPressed || keyboard.rightArrowKey.isPressed) result.x += 1f;
                if (keyboard.aKey.isPressed || keyboard.leftArrowKey.isPressed) result.x -= 1f;
            }

            if (Gamepad.current != null)
            {
                var stick = Gamepad.current.leftStick.ReadValue();
                if (stick.sqrMagnitude > result.sqrMagnitude)
                    result = stick;
            }
            return Vector2.ClampMagnitude(result, 1f);
        }

        private void ReadTouchMovement()
        {
            var touchscreen = Touchscreen.current;
            if (touchscreen == null)
                return;

            foreach (var touch in touchscreen.touches)
            {
                var id = touch.touchId.ReadValue();
                if (!touch.press.isPressed)
                {
                    if (id == joystickTouch)
                        joystickTouch = -1;
                    continue;
                }

                var position = touch.position.ReadValue();
                if (joystickTouch < 0 && position.x < Screen.width * 0.48f && position.y < Screen.height * 0.58f)
                {
                    joystickTouch = id;
                    joystickOrigin = position;
                }

                if (id == joystickTouch)
                    Move = Vector2.ClampMagnitude((position - joystickOrigin) / joystickRadiusPixels, 1f);
            }

            if (joystickTouch < 0 && ReadDesktopAndController() == Vector2.zero)
                Move = Vector2.zero;
        }
    }

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
