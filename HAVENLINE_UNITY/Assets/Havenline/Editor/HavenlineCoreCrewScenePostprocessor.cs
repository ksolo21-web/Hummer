using System;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Havenline.Editor
{
    /// <summary>
    /// Converts the legacy authored generic-player shell into the shipping four-character
    /// runtime binding immediately before packaging. The world authoring path remains intact;
    /// only the obsolete player/input shell is removed.
    /// </summary>
    public static class HavenlineCoreCrewScenePostprocessor
    {
        public const string ShippingRootName = "HAVENLINE_FROZEN_OUTPOST_SHIPPING";
        public const string RuntimeRootName = "CoreCrewRuntime";
        public const string SpawnAnchorName = "CoreCrewSpawn";
        public const string InstancesRootName = "CoreCrewInstances";

        public static void ApplyToShippingScene(HavenlineCharacterRoster roster)
        {
            if (roster == null)
                throw new ArgumentNullException(nameof(roster));
            var rosterFailures = roster.ValidateRoster();
            if (rosterFailures.Length > 0)
                throw new InvalidOperationException("Cannot bind an invalid core-character roster:\n - " + string.Join("\n - ", rosterFailures));
            if (!File.Exists(Reference.ScenePath))
                throw new FileNotFoundException("HAVENLINE shipping scene does not exist before core-crew postprocessing.", Reference.ScenePath);

            var scene = EditorSceneManager.OpenScene(Reference.ScenePath, OpenSceneMode.Single);
            var shippingRoot = scene.GetRootGameObjects().FirstOrDefault(root => root.name == ShippingRootName)
                ?? throw new InvalidOperationException($"Shipping scene root '{ShippingRootName}' was not found.");

            RemoveLegacyPlayerShell(shippingRoot);
            var cameraRig = shippingRoot.GetComponentInChildren<HavenlineCameraRig>(true)
                ?? throw new InvalidOperationException("Shipping scene has no HavenlineCameraRig for core-crew binding.");
            var hud = shippingRoot.GetComponentInChildren<HavenlineHud>(true)
                ?? throw new InvalidOperationException("Shipping scene has no HavenlineHud for core-crew binding.");

            var runtimeRoot = FindOrCreateDirectChild(shippingRoot.transform, RuntimeRootName);
            var spawnAnchor = FindOrCreateDirectChild(runtimeRoot.transform, SpawnAnchorName).transform;
            spawnAnchor.SetPositionAndRotation(Reference.PlayerSpawn, Quaternion.identity);
            var instancesRoot = FindOrCreateDirectChild(runtimeRoot.transform, InstancesRootName).transform;
            instancesRoot.localPosition = Vector3.zero;
            instancesRoot.localRotation = Quaternion.identity;

            var bootstrap = runtimeRoot.GetComponent<HavenlineCoreCrewRuntimeBootstrap>()
                ?? runtimeRoot.AddComponent<HavenlineCoreCrewRuntimeBootstrap>();
            bootstrap.Configure(roster, spawnAnchor, instancesRoot, cameraRig, hud);

            // The actual lead is not known in the editor. Keep scene framing deterministic around
            // the neutral spawn anchor; runtime profile selection retargets camera and HUD.
            cameraRig.Configure(spawnAnchor);
            hud.RebindControlledPlayer(null);

            EditorSceneManager.MarkSceneDirty(scene);
            EditorSceneManager.SaveScene(scene, Reference.ScenePath);
            EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(Reference.ScenePath, true) };
            AssetDatabase.SaveAssets();
        }

        public static string[] ValidateShippingCrewBinding(Scene scene)
        {
            if (!scene.IsValid() || !scene.isLoaded)
                return new[] { "Shipping scene must be valid and loaded for crew-binding validation." };

            var failures = new System.Collections.Generic.List<string>();
            var roots = scene.GetRootGameObjects();
            var shippingRoot = roots.FirstOrDefault(root => root.name == ShippingRootName);
            if (shippingRoot == null)
                return new[] { $"Shipping scene root '{ShippingRootName}' is missing." };

            var bootstraps = shippingRoot.GetComponentsInChildren<HavenlineCoreCrewRuntimeBootstrap>(true);
            if (bootstraps.Length != 1)
                failures.Add($"Shipping scene must contain exactly one core-crew runtime bootstrap; found {bootstraps.Length}.");
            else
            {
                var bootstrap = bootstraps[0];
                if (bootstrap.Roster == null)
                    failures.Add("Core-crew runtime bootstrap has no roster.");
                else
                    failures.AddRange(bootstrap.Roster.ValidateRoster().Select(message => "Core roster: " + message));
                if (bootstrap.SpawnAnchor == null)
                    failures.Add("Core-crew runtime bootstrap has no spawn anchor.");
            }

            var scenePlayers = shippingRoot.GetComponentsInChildren<HavenlinePlayerController>(true);
            if (scenePlayers.Length != 0)
                failures.Add($"Shipping scene still contains {scenePlayers.Length} authored player controller(s); approved core characters must spawn from the roster at runtime.");

            var looseInputs = shippingRoot.GetComponentsInChildren<HavenlineInputRouter>(true);
            if (looseInputs.Length != 0)
                failures.Add($"Shipping scene still contains {looseInputs.Length} authored movement input router(s); input must belong to the selected lead prefab.");

            return failures.Distinct(StringComparer.Ordinal).ToArray();
        }

        private static void RemoveLegacyPlayerShell(GameObject shippingRoot)
        {
            foreach (var player in shippingRoot.GetComponentsInChildren<HavenlinePlayerController>(true).ToArray())
            {
                if (player != null)
                    UnityEngine.Object.DestroyImmediate(player.gameObject);
            }

            foreach (var input in shippingRoot.GetComponentsInChildren<HavenlineInputRouter>(true).ToArray())
            {
                if (input != null)
                    UnityEngine.Object.DestroyImmediate(input.gameObject);
            }
        }

        private static GameObject FindOrCreateDirectChild(Transform parent, string name)
        {
            for (var index = 0; index < parent.childCount; index++)
            {
                var child = parent.GetChild(index);
                if (child.name == name)
                    return child.gameObject;
            }

            var created = new GameObject(name);
            created.transform.SetParent(parent, false);
            return created;
        }
    }
}
