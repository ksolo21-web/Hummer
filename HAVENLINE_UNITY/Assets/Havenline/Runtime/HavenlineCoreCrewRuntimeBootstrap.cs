using System;
using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
    /// <summary>
    /// Spawns the approved four-character core crew into the authored shipping scene after a
    /// valid saved profile identifies Character 1 or Character 2 as the playable lead.
    ///
    /// Fresh installs deliberately remain onboarding-required. This class never invents a
    /// default lead and never substitutes a generic player model for an approved character.
    /// </summary>
    [DisallowMultipleComponent]
    public sealed class HavenlineCoreCrewRuntimeBootstrap : MonoBehaviour
    {
        [SerializeField] private HavenlineCharacterRoster roster;
        [SerializeField] private Transform spawnAnchor;
        [SerializeField] private Transform companionRoot;
        [SerializeField] private HavenlineCameraRig cameraRig;
        [SerializeField] private HavenlineHud hud;

        private readonly List<HavenlineCompanionAgent> companions = new(3);
        private HavenlinePlayerProfileStore profileStore;

        public HavenlineCharacterRoster Roster => roster;
        public Transform SpawnAnchor => spawnAnchor;
        public HavenlinePlayerController ControlledLead { get; private set; }
        public IReadOnlyList<HavenlineCompanionAgent> Companions => companions;
        public bool RequiresOnboarding { get; private set; }
        public string OnboardingReason { get; private set; } = string.Empty;

        public event Action<string> OnboardingRequired;
        public event Action<HavenlinePlayerController> CrewSpawned;

        public void Configure(
            HavenlineCharacterRoster characterRoster,
            Transform crewSpawnAnchor,
            Transform crewParent,
            HavenlineCameraRig gameplayCamera,
            HavenlineHud gameplayHud)
        {
            roster = characterRoster;
            spawnAnchor = crewSpawnAnchor;
            companionRoot = crewParent;
            cameraRig = gameplayCamera;
            hud = gameplayHud;
        }

        private void Awake()
        {
            profileStore = new HavenlinePlayerProfileStore();
        }

        private void Start()
        {
            TrySpawnSavedCrew();
        }

        public bool TrySpawnSavedCrew()
        {
            if (ControlledLead != null)
                return true;

            if (!TryValidateSceneDependencies(out var dependencyFailure))
            {
                SetOnboardingRequired(dependencyFailure);
                return false;
            }

            if (!profileStore.TryLoad(out var profile, out var loadFailure))
            {
                SetOnboardingRequired(string.IsNullOrWhiteSpace(loadFailure)
                    ? "No saved HAVENLINE profile exists. Google sign-in, player name and C1/C2 lead selection are required."
                    : $"Saved HAVENLINE profile could not be loaded: {loadFailure}");
                return false;
            }

            if (!TryResolveSavedLead(profile, out var selectedLead, out var profileFailure))
            {
                SetOnboardingRequired(profileFailure);
                return false;
            }

            SpawnCrew(selectedLead);
            return true;
        }

        public HavenlinePlayerController SpawnCrew(HavenlineCharacterId selectedLead)
        {
            if (ControlledLead != null)
                throw new InvalidOperationException("The HAVENLINE core crew has already been spawned.");
            if (!TryValidateSceneDependencies(out var failure))
                throw new InvalidOperationException(failure);
            if (selectedLead != HavenlineCharacterId.Character1 && selectedLead != HavenlineCharacterId.Character2)
                throw new ArgumentOutOfRangeException(nameof(selectedLead), selectedLead, "Only Character 1 or Character 2 may be the playable lead.");

            if (!roster.TryGet(selectedLead, out var leadDefinition) || leadDefinition?.GameplayPrefab == null)
                throw new InvalidOperationException($"Character roster has no gameplay prefab for selected lead {selectedLead}.");

            var parent = companionRoot != null ? companionRoot : transform;
            var leadObject = Instantiate(
                leadDefinition.GameplayPrefab,
                spawnAnchor.position,
                spawnAnchor.rotation,
                parent);
            leadObject.name = selectedLead + "_ControlledLead";
            ControlledLead = RequireRuntimeCharacter(leadObject, selectedLead, playable: true);

            companions.Clear();
            var definitions = roster.GetCompanionsFor(selectedLead);
            if (definitions.Count != Reference.CoreCrewSize - 1)
                throw new InvalidOperationException($"Expected exactly three core companions; roster returned {definitions.Count}.");
            if (Reference.CompanionFormationOffsets.Length != definitions.Count)
                throw new InvalidOperationException("Runtime reference must provide exactly one formation offset per core companion.");

            for (var index = 0; index < definitions.Count; index++)
            {
                var definition = definitions[index];
                if (definition?.GameplayPrefab == null)
                    throw new InvalidOperationException($"Core companion entry {index} has no gameplay prefab.");

                var offset = Reference.CompanionFormationOffsets[index];
                var companionObject = Instantiate(
                    definition.GameplayPrefab,
                    spawnAnchor.TransformPoint(offset),
                    spawnAnchor.rotation,
                    parent);
                companionObject.name = definition.CharacterId + "_CoreCompanion";
                DisableDirectControl(companionObject, definition.CharacterId);

                var companion = companionObject.GetComponent<HavenlineCompanionAgent>()
                    ?? companionObject.AddComponent<HavenlineCompanionAgent>();
                companion.Configure(definition.CharacterId, ControlledLead.transform, offset);
                companions.Add(companion);
            }

            RequiresOnboarding = false;
            OnboardingReason = string.Empty;
            cameraRig.Configure(ControlledLead.transform);
            hud.RebindControlledPlayer(ControlledLead);
            CrewSpawned?.Invoke(ControlledLead);
            return ControlledLead;
        }

        internal static bool TryResolveSavedLead(
            HavenlinePlayerProfileData profile,
            out HavenlineCharacterId selectedLead,
            out string failure)
        {
            selectedLead = default;
            failure = string.Empty;

            if (profile == null)
            {
                failure = "Saved HAVENLINE profile is missing.";
                return false;
            }
            if (!profile.HasGoogleIdentity)
            {
                failure = "Saved HAVENLINE profile is not bound to a verified Google identity.";
                return false;
            }
            if (!profile.HasLeadSelection)
            {
                failure = "Saved HAVENLINE profile has no Character 1/Character 2 playable-lead selection.";
                return false;
            }

            selectedLead = profile.SelectedLead;
            return true;
        }

        private bool TryValidateSceneDependencies(out string failure)
        {
            failure = string.Empty;
            if (roster == null)
            {
                failure = "Core character roster is not configured in the shipping scene.";
                return false;
            }

            var rosterFailures = roster.ValidateRoster();
            if (rosterFailures.Length > 0)
            {
                failure = "Core character roster is invalid: " + string.Join(" | ", rosterFailures);
                return false;
            }
            if (spawnAnchor == null)
            {
                failure = "Core crew spawn anchor is missing from the shipping scene.";
                return false;
            }
            if (cameraRig == null)
            {
                failure = "Gameplay camera rig is not configured for core crew binding.";
                return false;
            }
            if (hud == null)
            {
                failure = "Gameplay HUD is not configured for core crew binding.";
                return false;
            }
            return true;
        }

        private HavenlinePlayerController RequireRuntimeCharacter(
            GameObject instance,
            HavenlineCharacterId characterId,
            bool playable)
        {
            var player = instance.GetComponent<HavenlinePlayerController>()
                ?? throw new InvalidOperationException($"{characterId} gameplay prefab is missing HavenlinePlayerController on its root.");
            var input = instance.GetComponent<HavenlineInputRouter>()
                ?? throw new InvalidOperationException($"{characterId} gameplay prefab is missing HavenlineInputRouter on its root.");
            var actions = instance.GetComponent<HavenlineAutomaticActionController>()
                ?? throw new InvalidOperationException($"{characterId} gameplay prefab is missing HavenlineAutomaticActionController on its root.");
            _ = instance.GetComponent<HavenlineInventory>()
                ?? throw new InvalidOperationException($"{characterId} gameplay prefab is missing HavenlineInventory on its root.");
            _ = instance.GetComponent<HavenlineActorAnimator>()
                ?? throw new InvalidOperationException($"{characterId} gameplay prefab is missing HavenlineActorAnimator on its root.");
            _ = instance.GetComponent<CharacterController>()
                ?? throw new InvalidOperationException($"{characterId} gameplay prefab is missing CharacterController on its root.");

            input.enabled = playable;
            actions.enabled = playable;
            player.enabled = playable;
            return player;
        }

        private void DisableDirectControl(GameObject instance, HavenlineCharacterId characterId)
        {
            RequireRuntimeCharacter(instance, characterId, playable: false);
        }

        private void SetOnboardingRequired(string reason)
        {
            RequiresOnboarding = true;
            OnboardingReason = string.IsNullOrWhiteSpace(reason)
                ? "HAVENLINE onboarding is required before the core crew can spawn."
                : reason;
            Debug.LogWarning(OnboardingReason, this);
            OnboardingRequired?.Invoke(OnboardingReason);
        }
    }
}
