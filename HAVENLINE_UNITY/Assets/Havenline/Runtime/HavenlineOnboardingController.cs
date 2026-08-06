using System;
using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
    public enum HavenlineOnboardingStep
    {
        SignIn = 0,
        ProfileSetup = 1,
        ChooseStarter = 2,
        Crew = 3,
        Complete = 4
    }

    /// <summary>
    /// Owns the durable onboarding state. A concrete Google sign-in integration supplies the
    /// verified subject id/email through AcceptGoogleIdentity; this class deliberately never
    /// creates a fake production identity.
    ///
    /// Character 1 and Character 2 are the only starting playable leads. The unselected lead
    /// automatically joins Character 3 and Character 4 as the player's three-person companion
    /// crew in the world.
    /// </summary>
    public sealed class HavenlineOnboardingController : MonoBehaviour
    {
        [SerializeField] private HavenlineCharacterRoster roster;
        [SerializeField] private Transform gameplaySpawnPoint;
        [SerializeField] private Transform[] companionSpawnPoints = Array.Empty<Transform>();
        [SerializeField] private Vector3[] fallbackCompanionOffsets =
        {
            new Vector3(-1.35f, 0f, -1.7f),
            new Vector3(1.35f, 0f, -1.7f),
            new Vector3(0f, 0f, -2.8f)
        };
        [SerializeField] private HavenlineOnboardingStep currentStep = HavenlineOnboardingStep.SignIn;

        private readonly HavenlinePlayerProfileStore profileStore = new HavenlinePlayerProfileStore();
        private readonly List<GameObject> spawnedCompanions = new List<GameObject>(3);
        private HavenlinePlayerProfileData profile;
        private GameObject spawnedLead;

        public event Action<HavenlineOnboardingStep> StepChanged;
        public event Action<HavenlineCharacterDefinition> CharacterSelected;
        public event Action<GameObject, IReadOnlyList<GameObject>> CrewSpawned;

        public HavenlineOnboardingStep CurrentStep => currentStep;
        public HavenlinePlayerProfileData Profile => profile;
        public GameObject SpawnedLead => spawnedLead;
        public IReadOnlyList<GameObject> SpawnedCompanions => spawnedCompanions;

        private void Awake()
        {
            if (profileStore.TryLoad(out var loaded, out var failure))
            {
                profile = loaded;
                currentStep = profile.HasLeadSelection
                    ? HavenlineOnboardingStep.Crew
                    : HavenlineOnboardingStep.ProfileSetup;
                return;
            }

            if (!string.IsNullOrEmpty(failure))
                Debug.LogWarning($"HAVENLINE profile was not loaded: {failure}");

            profile = new HavenlinePlayerProfileData();
            currentStep = HavenlineOnboardingStep.SignIn;
        }

        public void AcceptGoogleIdentity(string verifiedSubjectId, string email)
        {
            if (string.IsNullOrWhiteSpace(verifiedSubjectId))
                throw new ArgumentException("Verified Google subject id is required.", nameof(verifiedSubjectId));

            profile.googleSubjectId = verifiedSubjectId.Trim();
            profile.googleEmail = email?.Trim() ?? string.Empty;
            MoveTo(HavenlineOnboardingStep.ProfileSetup);
        }

        public void SaveProfileName(string playerName)
        {
            if (!profile.HasGoogleIdentity)
                throw new InvalidOperationException("Google sign-in must complete before profile setup.");
            if (string.IsNullOrWhiteSpace(playerName))
                throw new ArgumentException("Player name is required.", nameof(playerName));

            profile.playerName = playerName.Trim();
            MoveTo(HavenlineOnboardingStep.ChooseStarter);
        }

        public HavenlineCharacterDefinition SelectStarter(HavenlineCharacterId characterId)
        {
            if (currentStep != HavenlineOnboardingStep.ChooseStarter)
                throw new InvalidOperationException("Starter selection is not currently active.");
            if (roster == null)
                throw new InvalidOperationException("Character roster is not assigned.");
            if (!roster.TryGet(characterId, out var definition))
                throw new InvalidOperationException($"Character roster does not contain {characterId}.");
            if (!definition.IsStartingLead)
                throw new InvalidOperationException($"{characterId} is not a starting playable lead.");

            profile.SetLead(characterId);
            CharacterSelected?.Invoke(definition);
            return definition;
        }

        public void ConfirmStarterAndEnterCrew()
        {
            var validationFailure = profile.ValidateForSave();
            if (!string.IsNullOrEmpty(validationFailure))
                throw new InvalidOperationException(validationFailure);

            profileStore.Save(profile);
            MoveTo(HavenlineOnboardingStep.Crew);
        }

        public GameObject SpawnSelectedCrew()
        {
            if (roster == null)
                throw new InvalidOperationException("Character roster is not assigned.");
            if (!profile.HasLeadSelection)
                throw new InvalidOperationException("No playable lead has been selected.");
            if (!roster.TryGet(profile.SelectedLead, out var leadDefinition))
                throw new InvalidOperationException($"Selected lead {profile.SelectedLead} is missing from the roster.");
            if (leadDefinition.GameplayPrefab == null)
                throw new InvalidOperationException($"{leadDefinition.CharacterId} has no production gameplay prefab.");

            DestroySpawnedCrew();

            var leadPosition = gameplaySpawnPoint != null
                ? gameplaySpawnPoint.position
                : transform.position;
            var leadRotation = gameplaySpawnPoint != null
                ? gameplaySpawnPoint.rotation
                : transform.rotation;

            spawnedLead = Instantiate(
                leadDefinition.GameplayPrefab,
                leadPosition,
                leadRotation);
            spawnedLead.name = leadDefinition.CharacterId + "_PlayableLead";
            EnsurePlayableLeadComponents(spawnedLead);

            var companionDefinitions = roster.GetCompanionsFor(profile.SelectedLead);
            if (companionDefinitions.Count != 3)
                throw new InvalidOperationException("HAVENLINE requires exactly three core companions.");

            for (var index = 0; index < companionDefinitions.Count; index++)
            {
                var definition = companionDefinitions[index];
                if (definition.GameplayPrefab == null)
                    throw new InvalidOperationException($"{definition.CharacterId} has no production gameplay prefab.");

                var offset = ResolveCompanionOffset(index);
                var position = ResolveCompanionPosition(index, leadPosition, leadRotation, offset);
                var rotation = ResolveCompanionRotation(index, leadRotation);
                var companion = Instantiate(definition.GameplayPrefab, position, rotation);
                companion.name = definition.CharacterId + "_Companion";
                ConfigureAsCompanion(companion, definition.CharacterId, offset);
                spawnedCompanions.Add(companion);
            }

            MoveTo(HavenlineOnboardingStep.Complete);
            CrewSpawned?.Invoke(spawnedLead, spawnedCompanions);
            return spawnedLead;
        }

        /// <summary>
        /// Compatibility wrapper for older callers. It now spawns the entire four-character
        /// crew and returns the selected playable lead.
        /// </summary>
        public GameObject SpawnSelectedCharacter() => SpawnSelectedCrew();

        public bool IsCrewMemberActive(HavenlineCharacterId characterId) =>
            profile != null && profile.IsActiveCrewMember(characterId);

        public bool IsCrewMemberUnlocked(HavenlineCharacterId characterId) =>
            IsCrewMemberActive(characterId) ||
            (profile != null && profile.BuildUnlockedSet().Contains(characterId));

        public void DestroySpawnedCrew()
        {
            if (spawnedLead != null)
            {
                Destroy(spawnedLead);
                spawnedLead = null;
            }

            for (var index = 0; index < spawnedCompanions.Count; index++)
            {
                if (spawnedCompanions[index] != null)
                    Destroy(spawnedCompanions[index]);
            }
            spawnedCompanions.Clear();
        }

        private void EnsurePlayableLeadComponents(GameObject lead)
        {
            var playerController = lead.GetComponent<HavenlinePlayerController>();
            if (playerController == null)
                throw new InvalidOperationException($"{lead.name} has no HavenlinePlayerController.");

            playerController.enabled = true;
            var automaticActions = lead.GetComponent<HavenlineAutomaticActionController>();
            if (automaticActions != null)
                automaticActions.enabled = true;
            var inputRouters = lead.GetComponentsInChildren<HavenlineInputRouter>(true);
            for (var index = 0; index < inputRouters.Length; index++)
                inputRouters[index].enabled = true;
        }

        private void ConfigureAsCompanion(
            GameObject companion,
            HavenlineCharacterId characterId,
            Vector3 formationOffset)
        {
            var playerController = companion.GetComponent<HavenlinePlayerController>();
            if (playerController != null)
                playerController.enabled = false;

            var automaticActions = companion.GetComponent<HavenlineAutomaticActionController>();
            if (automaticActions != null)
                automaticActions.enabled = false;

            var inputRouters = companion.GetComponentsInChildren<HavenlineInputRouter>(true);
            for (var index = 0; index < inputRouters.Length; index++)
                inputRouters[index].enabled = false;

            var agent = companion.GetComponent<HavenlineCompanionAgent>();
            if (agent == null)
                agent = companion.AddComponent<HavenlineCompanionAgent>();
            agent.Configure(characterId, spawnedLead.transform, formationOffset);
        }

        private Vector3 ResolveCompanionOffset(int index)
        {
            if (fallbackCompanionOffsets != null && index < fallbackCompanionOffsets.Length)
                return fallbackCompanionOffsets[index];

            return new Vector3((index - 1) * 1.35f, 0f, -2f - index * 0.45f);
        }

        private Vector3 ResolveCompanionPosition(
            int index,
            Vector3 leadPosition,
            Quaternion leadRotation,
            Vector3 offset)
        {
            if (companionSpawnPoints != null &&
                index < companionSpawnPoints.Length &&
                companionSpawnPoints[index] != null)
            {
                return companionSpawnPoints[index].position;
            }

            return leadPosition + leadRotation * offset;
        }

        private Quaternion ResolveCompanionRotation(int index, Quaternion fallback)
        {
            if (companionSpawnPoints != null &&
                index < companionSpawnPoints.Length &&
                companionSpawnPoints[index] != null)
            {
                return companionSpawnPoints[index].rotation;
            }

            return fallback;
        }

        private void MoveTo(HavenlineOnboardingStep step)
        {
            currentStep = step;
            StepChanged?.Invoke(step);
        }
    }
}
