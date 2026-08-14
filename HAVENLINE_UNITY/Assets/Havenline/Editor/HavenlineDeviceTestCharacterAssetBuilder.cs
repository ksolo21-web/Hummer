using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.Animations;
using UnityEngine;

namespace Havenline.Editor
{
    /// <summary>
    /// Builds the runtime C1-C4 roster from checksum-pinned review FBXs for a device-test APK.
    /// It intentionally lives beside, rather than inside, the approved-production builder so
    /// release promotion can never accidentally inherit this relaxed human-approval policy.
    /// </summary>
    internal static class HavenlineDeviceTestCharacterAssetBuilder
    {
        internal static HavenlineCharacterRoster Build()
        {
            HavenlineDeviceTestCharacterGate.Require();
            HavenlineProductionHumanoidRigGate.Require();
            var manifest = HavenlinePremiumBuildGate.RequireProductionContent();

            Directory.CreateDirectory("Assets/Havenline/Resources");
            var definitions = new List<HavenlineCharacterDefinition>(
                HavenlineProductionCharacterAssetBuilder.Plans.Count);

            foreach (var plan in HavenlineProductionCharacterAssetBuilder.Plans)
            {
                Directory.CreateDirectory(plan.Folder);
                var prefab = BuildGameplayPrefab(plan, manifest);
                var portrait = RequirePortraitSprite(plan);
                definitions.Add(BuildDefinition(plan, prefab, portrait));
            }

            var rosterPath = HavenlineProductionCharacterAssetBuilder.RosterPath;
            var roster = AssetDatabase.LoadAssetAtPath<HavenlineCharacterRoster>(rosterPath);
            if (roster == null)
            {
                roster = ScriptableObject.CreateInstance<HavenlineCharacterRoster>();
                AssetDatabase.CreateAsset(roster, rosterPath);
            }

            var serializedRoster = new SerializedObject(roster);
            var characters = serializedRoster.FindProperty("characters")
                ?? throw new InvalidOperationException("HavenlineCharacterRoster.characters serialized field was not found.");
            characters.arraySize = definitions.Count;
            for (var index = 0; index < definitions.Count; index++)
                characters.GetArrayElementAtIndex(index).objectReferenceValue = definitions[index];
            serializedRoster.ApplyModifiedPropertiesWithoutUndo();
            EditorUtility.SetDirty(roster);

            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);

            var failures = roster.ValidateRoster();
            if (failures.Length > 0)
                throw new InvalidOperationException(
                    "Generated HAVENLINE device-test roster failed validation:\n - " +
                    string.Join("\n - ", failures));

            return roster;
        }

        private static GameObject BuildGameplayPrefab(
            HavenlineProductionCharacterAssetBuilder.CharacterPlan plan,
            HavenlinePremiumBuildGate.ProductionArtManifest manifest)
        {
            var modelAsset = AssetDatabase.LoadAssetAtPath<GameObject>(plan.ModelPath)
                ?? throw new FileNotFoundException($"Staged {plan.Id} model failed to import as GameObject.", plan.ModelPath);
            var controller = AssetDatabase.LoadAssetAtPath<AnimatorController>(manifest.playerController)
                ?? throw new FileNotFoundException("Production player AnimatorController is missing.", manifest.playerController);

            var wrapper = new GameObject(plan.Id + "_Gameplay");
            try
            {
                var visual = PrefabUtility.InstantiatePrefab(modelAsset, wrapper.transform) as GameObject
                    ?? throw new InvalidOperationException($"Could not instantiate staged character model: {plan.ModelPath}");
                visual.name = "Visual";
                GroundVisualAndValidateScale(plan, visual);

                var bounds = CalculateRendererBounds(visual);
                var height = bounds.size.y;
                var characterController = wrapper.AddComponent<CharacterController>();
                characterController.height = height;
                characterController.radius = Mathf.Clamp(height * 0.20f, 0.30f, 0.44f);
                characterController.center = new Vector3(0f, height * 0.5f, 0f);
                characterController.stepOffset = Mathf.Clamp(height * 0.18f, 0.20f, 0.38f);
                characterController.skinWidth = 0.035f;
                characterController.minMoveDistance = 0f;

                var unityAnimator = visual.GetComponentInChildren<Animator>(true) ?? visual.AddComponent<Animator>();
                unityAnimator.runtimeAnimatorController = controller;
                unityAnimator.applyRootMotion = false;
                unityAnimator.updateMode = AnimatorUpdateMode.Normal;
                unityAnimator.cullingMode = AnimatorCullingMode.CullUpdateTransforms;

                var actorAnimator = wrapper.AddComponent<HavenlineActorAnimator>();
                actorAnimator.Configure(unityAnimator);
                var motionPolish = wrapper.AddComponent<HavenlineHumanoidMotionPolish>();
                motionPolish.Configure(unityAnimator, actorAnimator);

                var inventory = wrapper.AddComponent<HavenlineInventory>();
                var carryRoot = new GameObject("VisibleCarriedStack").transform;
                carryRoot.SetParent(wrapper.transform, false);
                carryRoot.localPosition = new Vector3(0f, height * 0.58f, -characterController.radius * 0.95f);
                carryRoot.localRotation = Quaternion.Euler(0f, 180f, 0f);
                var carryVisual = carryRoot.gameObject.AddComponent<HavenlineCarryVisual>();
                carryVisual.Configure(
                    BuildCarrySlots(carryRoot, manifest.logModel, "Wood", Reference.VisibleCarrySlots, 0.26f),
                    BuildCarrySlots(carryRoot, manifest.stoneResourceModel, "Stone", Reference.VisibleCarrySlots, 0.18f),
                    BuildCarrySlots(carryRoot, manifest.metalResourceModel, "Metal", Reference.VisibleCarrySlots, 0.17f),
                    BuildCarrySlots(carryRoot, manifest.fuelResourceModel, "Fuel", Reference.VisibleCarrySlots, 0.19f));
                inventory.Configure(carryRoot, carryVisual, Reference.CarryCapacity);

                var input = wrapper.AddComponent<HavenlineInputRouter>();
                var player = wrapper.AddComponent<HavenlinePlayerController>();
                player.Configure(input, visual.transform, actorAnimator);
                var automaticActions = wrapper.AddComponent<HavenlineAutomaticActionController>();
                automaticActions.Configure(player);

                var prefab = PrefabUtility.SaveAsPrefabAsset(wrapper, plan.PrefabPath);
                if (prefab == null)
                    throw new InvalidOperationException($"Unity failed to save device-test prefab: {plan.PrefabPath}");
                return prefab;
            }
            finally
            {
                UnityEngine.Object.DestroyImmediate(wrapper);
            }
        }

        private static HavenlineCharacterDefinition BuildDefinition(
            HavenlineProductionCharacterAssetBuilder.CharacterPlan plan,
            GameObject prefab,
            Sprite portrait)
        {
            var definition = AssetDatabase.LoadAssetAtPath<HavenlineCharacterDefinition>(plan.DefinitionPath);
            if (definition == null)
            {
                definition = ScriptableObject.CreateInstance<HavenlineCharacterDefinition>();
                AssetDatabase.CreateAsset(definition, plan.DefinitionPath);
            }

            var serialized = new SerializedObject(definition);
            Require(serialized, "characterId").enumValueIndex = (int)plan.Id - 1;
            Require(serialized, "displayName").stringValue = plan.Id.ToString().Replace("Character", "Character ");
            Require(serialized, "roleName").stringValue = plan.Availability == HavenlineCharacterAvailability.StartingLead
                ? "Playable Lead"
                : "Core Companion";
            Require(serialized, "roleDescriptor").stringValue = "Device-test reviewed survivor";
            Require(serialized, "availability").enumValueIndex = (int)plan.Availability;
            Require(serialized, "unlockLevel").intValue = 0;
            Require(serialized, "portrait").objectReferenceValue = portrait;
            Require(serialized, "gameplayPrefab").objectReferenceValue = prefab;
            serialized.ApplyModifiedPropertiesWithoutUndo();
            EditorUtility.SetDirty(definition);
            return definition;
        }

        private static Sprite RequirePortraitSprite(HavenlineProductionCharacterAssetBuilder.CharacterPlan plan)
        {
            var texture = AssetDatabase.LoadAssetAtPath<Texture2D>(plan.PortraitPath)
                ?? throw new FileNotFoundException($"Device-test portrait image is missing for {plan.Id}.", plan.PortraitPath);
            var importer = AssetImporter.GetAtPath(plan.PortraitPath) as TextureImporter
                ?? throw new InvalidOperationException($"Portrait importer is not a TextureImporter: {plan.PortraitPath}");
            if (importer.textureType != TextureImporterType.Sprite || importer.spriteImportMode != SpriteImportMode.Single)
            {
                importer.textureType = TextureImporterType.Sprite;
                importer.spriteImportMode = SpriteImportMode.Single;
                importer.alphaIsTransparency = true;
                importer.mipmapEnabled = false;
                importer.SaveAndReimport();
            }

            return AssetDatabase.LoadAssetAtPath<Sprite>(plan.PortraitPath)
                ?? throw new InvalidOperationException($"Device-test portrait did not import as Sprite: {plan.PortraitPath}");
        }

        private static void GroundVisualAndValidateScale(
            HavenlineProductionCharacterAssetBuilder.CharacterPlan plan,
            GameObject visual)
        {
            var bounds = CalculateRendererBounds(visual);
            if (bounds.size.y < HavenlineProductionCharacterAssetBuilder.MinimumApprovedHeight ||
                bounds.size.y > HavenlineProductionCharacterAssetBuilder.MaximumApprovedHeight)
            {
                throw new InvalidOperationException(
                    $"{plan.Id} staged FBX imports at {bounds.size.y:0.000} Unity units tall; " +
                    $"expected {HavenlineProductionCharacterAssetBuilder.MinimumApprovedHeight:0.00}–" +
                    $"{HavenlineProductionCharacterAssetBuilder.MaximumApprovedHeight:0.00}. " +
                    "Fix the staged FBX instead of silently rescaling the character.");
            }

            visual.transform.position -= new Vector3(bounds.center.x, bounds.min.y, bounds.center.z);
        }

        private static Bounds CalculateRendererBounds(GameObject root)
        {
            var renderers = root.GetComponentsInChildren<Renderer>(true);
            if (renderers.Length == 0)
                throw new InvalidOperationException($"Character model {root.name} contains no renderers.");
            var bounds = renderers[0].bounds;
            for (var index = 1; index < renderers.Length; index++)
                bounds.Encapsulate(renderers[index].bounds);
            return bounds;
        }

        private static GameObject[] BuildCarrySlots(
            Transform parent,
            string assetPath,
            string prefix,
            int count,
            float targetHeight)
        {
            var asset = AssetDatabase.LoadAssetAtPath<GameObject>(assetPath)
                ?? throw new FileNotFoundException($"Carry resource failed to import: {assetPath}", assetPath);
            var slots = new GameObject[count];
            for (var index = 0; index < count; index++)
            {
                var slot = new GameObject($"{prefix}_{index + 1}");
                slot.transform.SetParent(parent, false);
                var row = index / 4;
                var column = index % 4;
                slot.transform.localPosition = new Vector3((column - 1.5f) * 0.16f, 0.15f + row * 0.18f, -0.08f - row * 0.08f);
                slot.transform.localRotation = Quaternion.Euler(0f, column * 12f, prefix.Contains("Wood", StringComparison.Ordinal) ? 90f : 0f);
                var visual = PrefabUtility.InstantiatePrefab(asset, slot.transform) as GameObject
                    ?? throw new InvalidOperationException($"Could not instantiate carry resource: {assetPath}");
                visual.name = prefix + "Visual";
                ScaleToHeight(visual, targetHeight);
                slots[index] = slot;
            }
            return slots;
        }

        private static void ScaleToHeight(GameObject root, float targetHeight)
        {
            var renderers = root.GetComponentsInChildren<Renderer>(true);
            if (renderers.Length == 0)
                return;
            var bounds = renderers[0].bounds;
            for (var index = 1; index < renderers.Length; index++)
                bounds.Encapsulate(renderers[index].bounds);
            if (bounds.size.y > 0.001f)
                root.transform.localScale *= targetHeight / bounds.size.y;
        }

        private static SerializedProperty Require(SerializedObject serialized, string fieldName) =>
            serialized.FindProperty(fieldName)
            ?? throw new InvalidOperationException(
                $"Serialized field '{fieldName}' was not found on {serialized.targetObject.GetType().Name}.");
    }
}
