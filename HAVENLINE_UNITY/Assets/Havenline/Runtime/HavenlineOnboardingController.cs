using System;
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
    /// </summary>
    public sealed class HavenlineOnboardingController : MonoBehaviour
    {
        [SerializeField] private HavenlineCharacterRoster roster;
        [SerializeField] private Transform gameplaySpawnPoint;
        [SerializeField] private HavenlineOnboardingStep currentStep = HavenlineOnboardingStep.SignIn;

        private readonly HavenlinePlayerProfileStore profileStore = new HavenlinePlayerProfileStore();
        private HavenlinePlayerProfileData profile;
        private GameObject spawnedCharacter;

        public event Action<HavenlineOnboardingStep> StepChanged;
        public event Action<HavenlineCharacterDefinition> CharacterSelected;

        public HavenlineOnboardingStep CurrentStep => currentStep;
        public HavenlinePlayerProfileData Profile => profile;

        private void Awake()
        {
            if (profileStore.TryLoad(out var loaded, out var failure))
            {
                profile = loaded;
                currentStep = profile.HasStarterSelection
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
            if (!definition.IsStartingChoice)
                throw new InvalidOperationException($"{characterId} is not a starting playable character.");

            profile.SetStarter(characterId);
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

        public GameObject SpawnSelectedCharacter()
        {
            if (roster == null)
                throw new InvalidOperationException("Character roster is not assigned.");
            if (!profile.HasStarterSelection)
                throw new InvalidOperationException("No playable character has been selected.");
            if (!roster.TryGet(profile.SelectedCharacter, out var definition))
                throw new InvalidOperationException($"Selected character {profile.SelectedCharacter} is missing from the roster.");
            if (definition.GameplayPrefab == null)
                throw new InvalidOperationException($"{definition.CharacterId} has no production gameplay prefab.");

            if (spawnedCharacter != null)
                Destroy(spawnedCharacter);

            var position = gameplaySpawnPoint != null ? gameplaySpawnPoint.position : transform.position;
            var rotation = gameplaySpawnPoint != null ? gameplaySpawnPoint.rotation : transform.rotation;
            spawnedCharacter = Instantiate(definition.GameplayPrefab, position, rotation);
            spawnedCharacter.name = definition.CharacterId + "_Playable";
            MoveTo(HavenlineOnboardingStep.Complete);
            return spawnedCharacter;
        }

        public bool IsCrewMemberUnlocked(HavenlineCharacterId characterId)
        {
            if (profile == null)
                return false;

            if (characterId == HavenlineCharacterId.Character1 ||
                characterId == HavenlineCharacterId.Character2)
            {
                return true;
            }

            return profile.BuildUnlockedSet().Contains(characterId);
        }

        private void MoveTo(HavenlineOnboardingStep step)
        {
            currentStep = step;
            StepChanged?.Invoke(step);
        }
    }
}
