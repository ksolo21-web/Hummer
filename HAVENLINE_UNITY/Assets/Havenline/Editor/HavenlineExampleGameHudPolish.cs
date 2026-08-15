using System;
using System.Collections.Generic;
using System.Linq;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

namespace Havenline.Editor
{
    /// <summary>
    /// Keeps the shipping HUD visually subordinate to the play space. The reference-game target
    /// communicates warmth, gathering and combat primarily through the world and the character,
    /// so HAVENLINE must not rebuild into a dashboard of large permanent cards or fake action
    /// buttons. Compact top cards are intentionally opaque: their small footprint preserves the
    /// play space while preventing bright world geometry from reading through the text/glyphs.
    /// Secondary/transient HUD remains translucent.
    /// </summary>
    [InitializeOnLoad]
    internal static class HavenlineExampleGameHudPolish
    {
        internal const string GameplayHudName = "GameplayHUD";
        internal const float TopCardAlpha = 1f;
        internal const float MinimumReadableTopCardAlpha = 0.99f;
        internal const float MaximumSecondaryPanelAlpha = 0.84f;

        static HavenlineExampleGameHudPolish()
        {
            EditorSceneManager.sceneSaving -= OnSceneSaving;
            EditorSceneManager.sceneSaving += OnSceneSaving;
        }

        private static void OnSceneSaving(Scene scene, string path)
        {
            if (!string.Equals(path, Reference.ScenePath, StringComparison.Ordinal))
                return;
            Apply(scene);
        }

        [MenuItem("HAVENLINE Premium/Polish Sparse Gameplay HUD")]
        private static void ApplyFromMenu()
        {
            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            Apply(scene);
            EditorSceneManager.MarkSceneDirty(scene);
            EditorSceneManager.SaveScene(scene);
            HavenlineExampleGameHudGate.Require(scene);
            Debug.Log("HAVENLINE sparse example-game HUD polish applied and validated.");
        }

        internal static void Apply(Scene scene)
        {
            if (!scene.IsValid())
                return;

            var hudRoot = AllObjects(scene).FirstOrDefault(item => item.name == GameplayHudName);
            if (hudRoot == null)
                return;

            var pill = AssetDatabase.GetBuiltinExtraResource<Sprite>("UI/Skin/UISprite.psd");
            PolishPanel(hudRoot, "ObjectivePanel", new Vector2(460f, 60f), TopCardAlpha, pill, true, true);
            PolishPanel(hudRoot, "ResourcesPanel", new Vector2(408f, 60f), TopCardAlpha, pill, false, true);
            PolishPanel(hudRoot, "FurnacePanel", new Vector2(235f, 60f), TopCardAlpha, pill, false, true);
            PolishPanel(hudRoot, "ContextPanel", new Vector2(430f, 64f), 0.72f, pill, false, false);
            PolishPanel(hudRoot, "HelperPanel", new Vector2(228f, 56f), 0.68f, pill, false, false);
            PolishPanel(hudRoot, "ThreatPanel", new Vector2(228f, 56f), 0.72f, pill, false, false);

            var warmth = Find(hudRoot, "WarmthIndicator");
            if (warmth != null)
                warmth.SetActive(false);

            foreach (var accent in hudRoot.GetComponentsInChildren<Transform>(true)
                         .Where(item => item.name.StartsWith("HudAccent_", StringComparison.Ordinal)))
                accent.gameObject.SetActive(false);

            var joystickBase = Find(hudRoot, "JoystickBase")?.GetComponent<Image>();
            if (joystickBase != null)
            {
                SetRect(joystickBase.rectTransform, new Vector2(0f, 0f), new Vector2(112f, 108f), new Vector2(158f, 158f));
                joystickBase.color = new Color(0.11f, 0.25f, 0.34f, 0.30f);
                var knobSprite = AssetDatabase.GetBuiltinExtraResource<Sprite>("UI/Skin/Knob.psd");
                if (knobSprite != null)
                    joystickBase.sprite = knobSprite;
                joystickBase.preserveAspect = true;
            }

            var joystickKnob = Find(hudRoot, "JoystickKnob")?.GetComponent<Image>();
            if (joystickKnob != null)
            {
                SetRect(joystickKnob.rectTransform, new Vector2(0.5f, 0.5f), Vector2.zero, new Vector2(66f, 66f));
                joystickKnob.color = new Color(0.66f, 0.84f, 0.94f, 0.58f);
                var knobSprite = AssetDatabase.GetBuiltinExtraResource<Sprite>("UI/Skin/Knob.psd");
                if (knobSprite != null)
                    joystickKnob.sprite = knobSprite;
                joystickKnob.preserveAspect = true;
            }

            var progressBackground = Find(hudRoot, "ContextProgressBackground")?.GetComponent<Image>();
            if (progressBackground != null)
            {
                progressBackground.color = new Color(0.015f, 0.055f, 0.08f, 0.72f);
                var rect = progressBackground.rectTransform;
                rect.sizeDelta = new Vector2(382f, 8f);
                if (pill != null)
                {
                    progressBackground.sprite = pill;
                    progressBackground.type = Image.Type.Sliced;
                }
            }

            var progress = Find(hudRoot, "ContextProgress")?.GetComponent<Image>();
            if (progress != null)
                progress.color = new Color(1f, 0.43f, 0.08f, 0.96f);

            foreach (var button in hudRoot.GetComponentsInChildren<Button>(true))
                button.gameObject.SetActive(false);
        }

        private static void PolishPanel(
            GameObject root,
            string name,
            Vector2 size,
            float alpha,
            Sprite sprite,
            bool active,
            bool topCard)
        {
            var panel = Find(root, name);
            if (panel == null)
                return;

            var rect = panel.transform as RectTransform;
            if (rect != null)
                rect.sizeDelta = size;

            var image = panel.GetComponent<Image>();
            if (image != null)
            {
                var finalAlpha = topCard
                    ? Mathf.Max(alpha, MinimumReadableTopCardAlpha)
                    : Mathf.Min(alpha, MaximumSecondaryPanelAlpha);
                image.color = new Color(0.025f, 0.075f, 0.105f, finalAlpha);
                if (sprite != null)
                {
                    image.sprite = sprite;
                    image.type = Image.Type.Sliced;
                }
                image.raycastTarget = false;
            }

            panel.SetActive(active);
        }

        private static void SetRect(RectTransform rect, Vector2 anchor, Vector2 position, Vector2 size)
        {
            if (rect == null)
                return;
            rect.anchorMin = anchor;
            rect.anchorMax = anchor;
            rect.pivot = anchor;
            rect.anchoredPosition = position;
            rect.sizeDelta = size;
        }

        private static GameObject Find(GameObject root, string name) => root
            .GetComponentsInChildren<Transform>(true)
            .FirstOrDefault(item => string.Equals(item.name, name, StringComparison.Ordinal))
            ?.gameObject;

        private static GameObject[] AllObjects(Scene scene) => scene.GetRootGameObjects()
            .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
            .Select(item => item.gameObject)
            .Distinct()
            .ToArray();
    }

    /// <summary>
    /// Release invariant for the reference-game HUD language. Functional HUD validation still
    /// lives in the normal scene gate; this one prevents visual drift back to permanent cards,
    /// decorative rails or action-looking controls while preserving top-card readability.
    /// </summary>
    public sealed class HavenlineExampleGameHudGate : IProcessSceneWithReport
    {
        public int callbackOrder => 1260;

        public void OnProcessScene(Scene scene, BuildReport report)
        {
            if (string.Equals(scene.path, Reference.ScenePath, StringComparison.Ordinal))
                Require(scene);
        }

        internal static void Require(Scene scene)
        {
            var failures = Inspect(scene);
            if (failures.Count > 0)
            {
                throw new BuildFailedException(
                    "HAVENLINE example-game HUD quality gate blocked the build:\n - " +
                    string.Join("\n - ", failures));
            }
        }

        internal static IReadOnlyList<string> Inspect(Scene scene)
        {
            var failures = new List<string>();
            var objects = scene.GetRootGameObjects()
                .SelectMany(root => root.GetComponentsInChildren<Transform>(true))
                .Select(item => item.gameObject)
                .Distinct()
                .ToArray();
            var hud = objects.FirstOrDefault(item => item.name == HavenlineExampleGameHudPolish.GameplayHudName);
            if (hud == null)
                return new[] { "GameplayHUD is missing from the shipping scene." };

            RequireInactive(hud, "WarmthIndicator", failures,
                "Permanent warmth-circle control must stay hidden; warmth is communicated in-world.");
            foreach (var accent in hud.GetComponentsInChildren<Transform>(true)
                         .Where(item => item.name.StartsWith("HudAccent_", StringComparison.Ordinal)))
            {
                if (accent.gameObject.activeSelf)
                    failures.Add("Decorative HUD edge bars must remain disabled in shipping presentation.");
            }

            var activeButtons = hud.GetComponentsInChildren<Button>(true)
                .Where(button => button.gameObject.activeSelf)
                .Select(button => button.name)
                .ToArray();
            if (activeButtons.Length > 0)
            {
                failures.Add(
                    "Gameplay HUD contains permanent buttons even though actions are proximity-driven: " +
                    string.Join(", ", activeButtons));
            }

            CheckTopPanel(hud, "ObjectivePanel", 480f, 64f, failures);
            CheckTopPanel(hud, "ResourcesPanel", 420f, 64f, failures);
            CheckTopPanel(hud, "FurnacePanel", 250f, 64f, failures);

            var objective = Find(hud, "ObjectivePanel");
            if (objective == null || !objective.activeSelf)
                failures.Add("The one short objective pill must remain available as the permanent top HUD element.");
            var resources = Find(hud, "ResourcesPanel");
            if (resources != null && resources.activeSelf)
                failures.Add("Resource panel must start hidden and appear only when the player is carrying resources.");
            var furnace = Find(hud, "FurnacePanel");
            if (furnace != null && furnace.activeSelf)
                failures.Add("Furnace status panel must be transient rather than permanently visible.");

            var joystick = Find(hud, "JoystickBase")?.transform as RectTransform;
            if (joystick == null || joystick.sizeDelta.x > 170f || joystick.sizeDelta.y > 170f)
                failures.Add("Movement joystick is missing or visually oversized.");

            return failures.Distinct().OrderBy(item => item, StringComparer.Ordinal).ToArray();
        }

        private static void CheckTopPanel(
            GameObject hud,
            string name,
            float maximumWidth,
            float maximumHeight,
            ICollection<string> failures)
        {
            var panel = Find(hud, name);
            if (panel == null)
            {
                failures.Add(name + " is missing.");
                return;
            }
            var rect = panel.transform as RectTransform;
            if (rect == null || rect.sizeDelta.x > maximumWidth || rect.sizeDelta.y > maximumHeight)
                failures.Add($"{name} exceeds compact HUD size limit {maximumWidth:0}×{maximumHeight:0}.");
            var image = panel.GetComponent<Image>();
            if (image == null || image.color.a < HavenlineExampleGameHudPolish.MinimumReadableTopCardAlpha)
                failures.Add($"{name} must be opaque enough to keep world geometry from reading through HUD text.");
        }

        private static void RequireInactive(
            GameObject hud,
            string name,
            ICollection<string> failures,
            string message)
        {
            var item = Find(hud, name);
            if (item != null && item.activeSelf)
                failures.Add(message);
        }

        private static GameObject Find(GameObject root, string name) => root
            .GetComponentsInChildren<Transform>(true)
            .FirstOrDefault(item => string.Equals(item.name, name, StringComparison.Ordinal))
            ?.gameObject;
    }
}
