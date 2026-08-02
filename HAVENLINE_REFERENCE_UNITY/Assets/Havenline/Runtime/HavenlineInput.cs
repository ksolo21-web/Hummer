using UnityEngine;
using UnityEngine.InputSystem;

namespace Havenline
{
    public sealed class HavenlineInputRouter : MonoBehaviour
    {
        [SerializeField] private float joystickRadiusPixels = 125f;
        private int joystickTouch = -1;
        private int dashTouch = -1;
        private Vector2 joystickOrigin;
        public Vector2 Move { get; private set; }
        public bool DashHeld { get; private set; }

        private void Update()
        {
            Move = ReadDesktop();
            DashHeld = Keyboard.current?.leftShiftKey.isPressed == true || Gamepad.current?.rightTrigger.isPressed == true;
            ReadTouches();
        }

        private static Vector2 ReadDesktop()
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
            if (Gamepad.current != null && Gamepad.current.leftStick.ReadValue().sqrMagnitude > result.sqrMagnitude)
                result = Gamepad.current.leftStick.ReadValue();
            return Vector2.ClampMagnitude(result, 1f);
        }

        private void ReadTouches()
        {
            var touchscreen = Touchscreen.current;
            if (touchscreen == null) return;
            var width = Screen.width;
            var height = Screen.height;
            foreach (var touch in touchscreen.touches)
            {
                if (!touch.press.isPressed)
                {
                    if (touch.touchId.ReadValue() == joystickTouch) joystickTouch = -1;
                    if (touch.touchId.ReadValue() == dashTouch) dashTouch = -1;
                    continue;
                }

                var id = touch.touchId.ReadValue();
                var position = touch.position.ReadValue();
                if (joystickTouch < 0 && position.x < width * 0.48f && position.y < height * 0.52f)
                {
                    joystickTouch = id;
                    joystickOrigin = position;
                }
                if (dashTouch < 0 && position.x > width * 0.62f && position.y < height * 0.46f)
                    dashTouch = id;

                if (id == joystickTouch)
                    Move = Vector2.ClampMagnitude((position - joystickOrigin) / joystickRadiusPixels, 1f);
            }
            if (joystickTouch < 0 && ReadDesktop() == Vector2.zero) Move = Vector2.zero;
            DashHeld |= dashTouch >= 0;
        }
    }

    public sealed class HavenlineSafeArea : MonoBehaviour
    {
        private Rect last;
        private RectTransform rectTransform;
        private void Awake() { rectTransform = (RectTransform)transform; Apply(); }
        private void Update() { if (Screen.safeArea != last) Apply(); }
        private void Apply()
        {
            last = Screen.safeArea;
            var min = last.position;
            var max = last.position + last.size;
            min.x /= Screen.width; min.y /= Screen.height;
            max.x /= Screen.width; max.y /= Screen.height;
            rectTransform.anchorMin = min; rectTransform.anchorMax = max;
            rectTransform.offsetMin = rectTransform.offsetMax = Vector2.zero;
        }
    }
}
