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
    /// Builds the four runtime character prefabs and the canonical character roster from
    /// human-approved production FBXs. Imported FBXs remain immutable; gameplay components,
    /// carry visuals and controller geometry live on wrapper prefabs.
    /// </summary>
    public static class HavenlineProductionCharacterAssetBuilder
    {
        public const string CharacterProductionRoot = "Assets/Havenline/Art/Characters/Production";
        public const string RosterPath = "Assets/Havenline/Resources/HavenlineCoreCharacterRoster.asset";
        public const float MinimumApprovedHeight = 1.25f;
        public const float MaximumApprovedHeight = 2.25f;

        public sealed class CharacterPlan
        {
            public HavenlineCharacterId Id { get; }
            public string Folder { get; }
            public string ModelPath { get; }
            public string PortraitPath { get; }
            public string PrefabPath { get; }
            public string DefinitionPath { get; }
            public HavenlineCharacterAvailability Availability { get; }

            public CharacterPlan(HavenlineCharacterId id, HavenlineCharacterAvailability availability)
            {
                Id = id;
                Availability = availability;
                Folder = $"{CharacterProductionRoot}/{id}";
                ModelPath = $"{Folder}/{id}_production.fbx";
                PortraitPath = $"{Folder}/{id}_portrait.png";
                PrefabPath = $"{Folder}/{id}_gameplay.prefab";
                DefinitionPath = $"{Folder}/{id}_definition.asset";
            }
        }

        public static IReadOnlyList<CharacterPlan> Plans { get; } = new[]
        {
            new CharacterPlan(HavenlineCharacterId.Character1, HavenlineCharacterAvailability.StartingLead),
            new CharacterPlan(HavenlineCharacterId.Character2, HavenlineCharacterAvailability.StartingLead),
            new CharacterPlan(HavenlineCharacterId.Character3, HavenlineCharacterAvailability.CoreCompanion),
            new CharacterPlan(HavenlineCharacterId.Character4, HavenlineCharacterAvailability.CoreCompanion)
        };

        [MenuItem("HAVENLINE Premium/Characters/Build Approved Gameplay Roster")]
        public static void BuildFromMenu()
        {
            BuildApprovedGameplayRoster();
            Debug.Log($"HAVENLINE approved four-character gameplay roster built: {RosterPath}");
        }

        [MenuItem("HAVENLINE Premium/Characters/Inspect Gameplay Roster Prerequisites")]
        public static void InspectPrerequisitesFromMenu()
        {
            var failures = InspectPrerequisites();
            if (failures.Count > 0)
                throw new InvalidOperationException("HAVENLINE character gameplay assets are not ready:\n - " + string.Join("\n - ", failures));

            Debug.Log("HAVENLINE character gameplay roster prerequisites are present.");
        }

        public static HavenlineCharacterRoster BuildApprovedGameplayRoster()
        {
            HavenlineCharacterBuildPreprocessor.RequireApprovedCharacters();
            var manifest = HavenlinePremiumBuildGate.RequireProductionContent();
            var prerequisiteFailures = InspectPrerequisites();
            if (prerequisiteFailures.Count > 0)
                throw new InvalidOperationException("HAVENLINE character gameplay asset build blocked:\n - " + string.Join("\n - ", prerequisiteFailures));

            Directory.CreateDirectory("Assets/Havenline/Resources");
            var definitions = new List<HavenlineCharacterDefinition>(Plans.Count);
            foreach (var plan in Plans)
            {
                Directory.CreateDirectory(plan.Folder);
                var prefab = BuildGameplayPrefab(plan, manifest);
                var portrait = RequirePortraitSprite(plan);
                definitions.Add(BuildDefinition(plan, prefab, portrait));
            }

            var roster = AssetDatabase.LoadAssetAtPath<HavenlineCharacterRoster>(RosterPath);
            if (roster == null)
            {
                roster = ScriptableObject.CreateInstance<HavenlineCharacterRoster>();
                AssetDatabase.CreateAsset(roster, RosterPath);
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
                throw new InvalidOperationException("Generated HAVENLINE character roster failed validation:\n - " + string.Join("\n - ", failures));

            return roster;
        }

        public static List<string> InspectPrerequisites()
        {
            var failures = new List<string>();
            foreach (var plan in Plans)
            {
                if (AssetDatabase.LoadAssetAtPath<GameObject>(plan.ModelPath) == null)
                    failures.Add($"{plan.Id} approved production FBX is missing or not imported: {plan.ModelPath}");

                var texture = AssetDatabase.LoadAssetAtPath<Texture2D>(plan.PortraitPath);
                if (texture == null)
                    failures.Add($"{plan.Id} approved portrait image is missing: {plan.PortraitPath}");
            }
            return failures;
        }

        private static GameObject BuildGameplayPrefab(
            CharacterPlan plan,
            HavenlinePremiumBuildGate.ProductionArtManifest manifest)
        {
            var modelAsset = AssetDatabase.LoadAssetAtPath<GameObject>(plan.ModelPath)
                ?? throw new FileNotFoundException($"Approved {plan.Id} model failed to import as GameObject.", plan.ModelPath);
            var controller = AssetDatabase.LoadAssetAtPath<AnimatorController>(manifest.playerController)
                ?? throw new FileNotFoundException("Production player AnimatorController is missing.", manifest.playerController);

            var wrapper = new GameObject(plan.Id + "_Gameplay");
            try
            {
                var visual = PrefabUtility.InstantiatePrefab(modelAsset, wrapper.transform) as GameObject
                    ?? throw new InvalidOperationException($"Could not instantiate approved production model: {plan.ModelPath}");
                visual.name = "Visual";
                GroundVisualAndValidateScale(plan, visual);

                var bounds = CalculateRendererBounds(visual);
                var controllerHeight = bounds.size.y;
                var characterController = wrapper.AddComponent<CharacterController>();
                characterController.height = controllerHeight;
                characterController.radius = Mathf.Clamp(controllerHeight * 0.20f, 0.30f, 0.44f);
                characterController.center = new Vector3(0f, controllerHeight * 0.5f, 0f);
                characterController.stepOffset = Mathf.Clamp(controllerHeight * 0.18f, 0.20f, 0.38f);
                characterController.skinWidth = 0.035f;
                characterController.minMoveDistance = 0f;

                var unityAnimator = visual.GetComponentInChildren<Animator>(true) ?? visual.AddComponent<Animator>();
                unityAnimator.runtimeAnimatorController = controller;
                unityAnimator.applyRootMotion = false;
                unityAnimator.updateMode = AnimatorUpdateMode.Normal;
                unityAnimator.cullingMode = AnimatorCullingMode.CullUpdateTransforms;

                var actorAnimator = wrapper.AddComponent<HavenlineActorAnimator>();
                actorAnimator.Configure(unityAnimator);

                var inventory = wrapper.AddComponent<HavenlineInventory>();
                var carryRoot = new GameObject("VisibleCarriedStack").transform;
                carryRoot.SetParent(wrapper.transform, false);
                carryRoot.localPosition = new Vector3(0f, controllerHeight * 0.58f, -characterController.radius * 0.95f);
                carryRoot.localRotation = Quaternion.Euler(0f, 180f, 0f);
                var carryVisual = carryRoot.gameObject.AddComponent<HavenlineCarryVisual>();
                carryVisual.Configure(
                    BuildCarrySlots(carryRoot, manifest.logModel, "Wood", 8, 0.26f),
                    BuildCarrySlots(carryRoot, manifest.stoneResourceModel, "Stone", 8, 0.18f),
                    BuildCarrySlots(carryRoot, manifest.metalResourceModel, "Metal", 8, 0.17f),
                    BuildCarrySlots(carryRoot, manifest.fuelResourceModel, "Fuel", 8, 0.19f));
                inventory.Configure(carryRoot, carryVisual, Reference.CarryCapacity);

                var input = wrapper.AddComponent<HavenlineInputRouter>();
                var player = wrapper.AddComponent<HavenlinePlayerController>();
                player.Configure(input, visual.transform, actorAnimator);
                var automaticActions = wrapper.AddComponent<HavenlineAutomaticActionController>();
                automaticActions.Configure(player);

                var prefab = PrefabUtility.SaveAsPrefabAsset(wrapper, plan.PrefabPath);
                if (prefab == null)
                    throw new InvalidOperationException($"Unity failed to save {plan.Id} gameplay prefab: {plan.PrefabPath}");
                return prefab;
            }
            finally
            {
                UnityEngine.Object.DestroyImmediate(wrapper);
            }
        }

        private static HavenlineCharacterDefinition BuildDefinition(
            CharacterPlan plan,
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
            Require(serialized, "roleDescriptor").stringValue = plan.Availability == HavenlineCharacterAvailability.StartingLead
                ? "Direct-control survivor"
                : "Persistent expedition crew";
            Require(serialized, "availability").enumValueIndex = (int)plan.Availability;
            Require(serialized, "unlockLevel").intValue = 0;
            Require(serialized, "portrait").objectReferenceValue = portrait;
            Require(serialized, "gameplayPrefab").objectReferenceValue = prefab;
            serialized.ApplyModifiedPropertiesWithoutUndo();
            EditorUtility.SetDirty(definition);
            return definition;
        }

        private static Sprite RequirePortraitSprite(CharacterPlan plan)
        {
            var texture = AssetDatabase.LoadAssetAtPath<Texture2D>(plan.PortraitPath)
                ?? throw new FileNotFoundException($"Approved portrait image is missing for {plan.Id}.", plan.PortraitPath);
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
                ?? throw new InvalidOperationException($"Approved portrait did not import as a Sprite: {plan.PortraitPath}");
        }

        private static void GroundVisualAndValidateScale(CharacterPlan plan, GameObject visual)
        {
            var bounds = CalculateRendererBounds(visual);
            if (bounds.size.y < MinimumApprovedHeight || bounds.size.y > MaximumApprovedHeight)
            {
                throw new InvalidOperationException(
                    $"{plan.Id} approved FBX imports at {bounds.size.y:0.000} Unity units tall; " +
                    $"expected {MinimumApprovedHeight:0.00}–{MaximumApprovedHeight:0.00}. " +
                    "Fix the production FBX import/export scale instead of silently rescaling the approved character.");
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
            ?? throw new InvalidOperationException($"Serialized field '{fieldName}' was not found on {serialized.targetObject.GetType().Name}.");
    }
}
