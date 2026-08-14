using System;
using System.Collections.Generic;
using System.Linq;
using UnityEditor;
using UnityEditor.Animations;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Havenline.Editor
{
    /// <summary>
    /// Corrects the generated animation-controller contract after deterministic studio generation.
    /// AutomaticActionKind has stable semantic values; animation transitions must use those values
    /// explicitly rather than relying on the incidental order of generated clip names.
    /// </summary>
    [InitializeOnLoad]
    internal static class HavenlineAnimationControllerQualityPass
    {
        internal const string PlayerControllerPath =
            "Assets/Havenline/Art/Production/Animation/HAVENLINE_Player.controller";
        internal const string SurvivorControllerPath =
            "Assets/Havenline/Art/Production/Animation/HAVENLINE_Survivor.controller";
        internal const string WolfControllerPath =
            "Assets/Havenline/Art/Production/Animation/HAVENLINE_Wolf.controller";

        private static readonly IReadOnlyDictionary<AutomaticActionKind, string> PlayerActions =
            new Dictionary<AutomaticActionKind, string>
            {
                [AutomaticActionKind.GatherWood] = "Chop",
                [AutomaticActionKind.GatherStone] = "Mine",
                [AutomaticActionKind.GatherMetal] = "Salvage",
                [AutomaticActionKind.GatherFuel] = "Salvage",
                [AutomaticActionKind.Deposit] = "Deposit",
                [AutomaticActionKind.Rescue] = "Rescue",
                [AutomaticActionKind.Build] = "Build",
                [AutomaticActionKind.Repair] = "Repair",
                [AutomaticActionKind.Combat] = "Attack"
            };

        private static readonly IReadOnlyDictionary<AutomaticActionKind, string> SurvivorActions =
            new Dictionary<AutomaticActionKind, string>
            {
                [AutomaticActionKind.GatherWood] = "Gather",
                [AutomaticActionKind.GatherStone] = "Gather",
                [AutomaticActionKind.GatherMetal] = "Gather",
                [AutomaticActionKind.GatherFuel] = "Gather",
                [AutomaticActionKind.Deposit] = "Deposit",
                [AutomaticActionKind.Build] = "Build",
                [AutomaticActionKind.Repair] = "Repair",
                [AutomaticActionKind.Combat] = "Defend"
            };

        private static readonly IReadOnlyDictionary<AutomaticActionKind, string> WolfActions =
            new Dictionary<AutomaticActionKind, string>
            {
                [AutomaticActionKind.Combat] = "Attack"
            };

        static HavenlineAnimationControllerQualityPass()
        {
            EditorSceneManager.sceneSaving -= OnSceneSaving;
            EditorSceneManager.sceneSaving += OnSceneSaving;
        }

        private static void OnSceneSaving(Scene scene, string path)
        {
            if (!string.Equals(path, Reference.ScenePath, StringComparison.Ordinal))
                return;
            Apply();
        }

        [MenuItem("HAVENLINE Premium/Animation/Fix Automatic Action Mapping")]
        private static void ApplyFromMenu()
        {
            Apply();
            Require();
            Debug.Log("HAVENLINE automatic-action animation mapping is explicit and validated.");
        }

        internal static void Apply()
        {
            PatchController(PlayerControllerPath, PlayerActions, "CarryIdle");
            PatchController(SurvivorControllerPath, SurvivorActions, "Carry");
            PatchController(WolfControllerPath, WolfActions, null);
            AssetDatabase.SaveAssets();
        }

        internal static IReadOnlyList<string> Inspect()
        {
            var failures = new List<string>();
            InspectController(PlayerControllerPath, PlayerActions, failures);
            InspectController(SurvivorControllerPath, SurvivorActions, failures);
            InspectController(WolfControllerPath, WolfActions, failures);
            return failures.Distinct().OrderBy(item => item, StringComparer.Ordinal).ToArray();
        }

        internal static void Require()
        {
            var failures = Inspect();
            if (failures.Count > 0)
            {
                throw new BuildFailedException(
                    "HAVENLINE animation quality gate blocked the build:\n - " +
                    string.Join("\n - ", failures));
            }
        }

        private static void PatchController(
            string path,
            IReadOnlyDictionary<AutomaticActionKind, string> mapping,
            string carryStateName)
        {
            var controller = AssetDatabase.LoadAssetAtPath<AnimatorController>(path);
            if (controller == null || controller.layers.Length == 0)
                return;

            var machine = controller.layers[0].stateMachine;
            var states = machine.states
                .Select(child => child.state)
                .Where(state => state != null)
                .ToDictionary(state => state.name, StringComparer.Ordinal);

            foreach (var transition in machine.anyStateTransitions.ToArray())
                machine.RemoveAnyStateTransition(transition);

            foreach (var pair in mapping)
            {
                if (!states.TryGetValue(pair.Value, out var target))
                    continue;
                var transition = machine.AddAnyStateTransition(target);
                transition.hasExitTime = false;
                transition.duration = pair.Key == AutomaticActionKind.Combat ? 0.045f : 0.065f;
                transition.canTransitionToSelf = false;
                transition.AddCondition(AnimatorConditionMode.If, 0f, "Action");
                transition.AddCondition(AnimatorConditionMode.Equals, (int)pair.Key, "ActionType");
            }

            if (states.TryGetValue("Hit", out var hit))
            {
                var transition = machine.AddAnyStateTransition(hit);
                transition.hasExitTime = false;
                transition.duration = 0.035f;
                transition.canTransitionToSelf = true;
                transition.AddCondition(AnimatorConditionMode.If, 0f, "Hit");
            }
            if (states.TryGetValue("Dead", out var dead))
            {
                var transition = machine.AddAnyStateTransition(dead);
                transition.hasExitTime = false;
                transition.duration = 0.045f;
                transition.canTransitionToSelf = false;
                transition.AddCondition(AnimatorConditionMode.If, 0f, "Dead");
            }

            ConfigureCarryIdle(states, carryStateName);
            EditorUtility.SetDirty(controller);
        }

        private static void ConfigureCarryIdle(
            IReadOnlyDictionary<string, AnimatorState> states,
            string carryStateName)
        {
            if (string.IsNullOrWhiteSpace(carryStateName) ||
                !states.TryGetValue(carryStateName, out var carry) ||
                !states.TryGetValue("Idle", out var idle))
                return;

            foreach (var state in states.Values)
            {
                foreach (var transition in state.transitions.ToArray())
                {
                    if (transition.conditions.Any(condition => condition.parameter == "CarryAmount"))
                        state.RemoveTransition(transition);
                }
            }

            var idleToCarry = idle.AddTransition(carry);
            idleToCarry.hasExitTime = false;
            idleToCarry.duration = 0.10f;
            idleToCarry.AddCondition(AnimatorConditionMode.Greater, 0.5f, "CarryAmount");
            idleToCarry.AddCondition(AnimatorConditionMode.Less, 0.055f, "Speed");

            var carryToIdle = carry.AddTransition(idle);
            carryToIdle.hasExitTime = false;
            carryToIdle.duration = 0.10f;
            carryToIdle.AddCondition(AnimatorConditionMode.Less, 0.5f, "CarryAmount");

            if (states.TryGetValue("Walk", out var walk))
            {
                var carryToWalk = carry.AddTransition(walk);
                carryToWalk.hasExitTime = false;
                carryToWalk.duration = 0.10f;
                carryToWalk.AddCondition(AnimatorConditionMode.Greater, 0.055f, "Speed");

                var walkToCarry = walk.AddTransition(carry);
                walkToCarry.hasExitTime = false;
                walkToCarry.duration = 0.10f;
                walkToCarry.AddCondition(AnimatorConditionMode.Less, 0.05f, "Speed");
                walkToCarry.AddCondition(AnimatorConditionMode.Greater, 0.5f, "CarryAmount");
            }
        }

        private static void InspectController(
            string path,
            IReadOnlyDictionary<AutomaticActionKind, string> mapping,
            ICollection<string> failures)
        {
            var controller = AssetDatabase.LoadAssetAtPath<AnimatorController>(path);
            if (controller == null || controller.layers.Length == 0)
            {
                failures.Add("Animation controller is missing or invalid: " + path);
                return;
            }

            var machine = controller.layers[0].stateMachine;
            foreach (var pair in mapping)
            {
                var expectedValue = (int)pair.Key;
                var found = machine.anyStateTransitions.Any(transition =>
                    transition.destinationState != null &&
                    transition.destinationState.name == pair.Value &&
                    transition.conditions.Any(condition =>
                        condition.parameter == "Action" &&
                        condition.mode == AnimatorConditionMode.If) &&
                    transition.conditions.Any(condition =>
                        condition.parameter == "ActionType" &&
                        condition.mode == AnimatorConditionMode.Equals &&
                        Mathf.Abs(condition.threshold - expectedValue) < 0.001f));
                if (!found)
                {
                    failures.Add(
                        $"{System.IO.Path.GetFileName(path)} does not map {pair.Key} ({expectedValue}) to {pair.Value}.");
                }
            }
        }
    }

    public sealed class HavenlineAnimationControllerQualityGate : IPreprocessBuildWithReport
    {
        public int callbackOrder => -8500;

        public void OnPreprocessBuild(BuildReport report)
        {
            HavenlineAnimationControllerQualityPass.Apply();
            HavenlineAnimationControllerQualityPass.Require();
        }
    }
}
