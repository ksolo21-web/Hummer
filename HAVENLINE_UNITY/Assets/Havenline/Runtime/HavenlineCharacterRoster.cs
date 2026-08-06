using System;
using System.Collections.Generic;
using UnityEngine;

namespace Havenline
{
    public enum HavenlineCharacterId
    {
        Character1 = 1,
        Character2 = 2,
        Character3 = 3,
        Character4 = 4
    }

    public enum HavenlineCharacterAvailability
    {
        StartingLead = 0,
        CoreCompanion = 1,
        UnlockableCrew = 2
    }

    [CreateAssetMenu(
        fileName = "HavenlineCharacter",
        menuName = "HAVENLINE/Characters/Character Definition")]
    public sealed class HavenlineCharacterDefinition : ScriptableObject
    {
        [SerializeField] private HavenlineCharacterId characterId = HavenlineCharacterId.Character1;
        [SerializeField] private string displayName = "Character";
        [SerializeField] private string roleName = "Survivor";
        [SerializeField] private string roleDescriptor = "Balanced";
        [SerializeField] private HavenlineCharacterAvailability availability = HavenlineCharacterAvailability.CoreCompanion;
        [SerializeField, Min(0)] private int unlockLevel;
        [SerializeField] private Sprite portrait;
        [SerializeField] private GameObject gameplayPrefab;

        public HavenlineCharacterId CharacterId => characterId;
        public string DisplayName => displayName;
        public string RoleName => roleName;
        public string RoleDescriptor => roleDescriptor;
        public HavenlineCharacterAvailability Availability => availability;
        public int UnlockLevel => unlockLevel;
        public Sprite Portrait => portrait;
        public GameObject GameplayPrefab => gameplayPrefab;
        public bool IsStartingLead => availability == HavenlineCharacterAvailability.StartingLead;
        public bool IsCoreCompanion => availability == HavenlineCharacterAvailability.CoreCompanion;

        public bool IsAvailable(ISet<HavenlineCharacterId> unlockedCharacters)
        {
            if (IsStartingLead || IsCoreCompanion)
                return true;

            return unlockedCharacters != null && unlockedCharacters.Contains(characterId);
        }

        public string ValidateDefinition()
        {
            var problems = new List<string>();
            if (string.IsNullOrWhiteSpace(displayName))
                problems.Add("display name is empty");
            if (string.IsNullOrWhiteSpace(roleName))
                problems.Add("role name is empty");
            if (portrait == null)
                problems.Add("portrait is missing");
            if (gameplayPrefab == null)
                problems.Add("gameplay prefab is missing");
            if (availability == HavenlineCharacterAvailability.UnlockableCrew && unlockLevel <= 0)
                problems.Add("unlockable crew requires a positive unlock level");

            return problems.Count == 0
                ? string.Empty
                : $"{characterId}: {string.Join(", ", problems)}";
        }
    }

    [CreateAssetMenu(
        fileName = "HavenlineCharacterRoster",
        menuName = "HAVENLINE/Characters/Character Roster")]
    public sealed class HavenlineCharacterRoster : ScriptableObject
    {
        [SerializeField] private List<HavenlineCharacterDefinition> characters =
            new List<HavenlineCharacterDefinition>();

        public IReadOnlyList<HavenlineCharacterDefinition> Characters => characters;

        public bool TryGet(HavenlineCharacterId characterId, out HavenlineCharacterDefinition definition)
        {
            for (var index = 0; index < characters.Count; index++)
            {
                var candidate = characters[index];
                if (candidate != null && candidate.CharacterId == characterId)
                {
                    definition = candidate;
                    return true;
                }
            }

            definition = null;
            return false;
        }

        public List<HavenlineCharacterDefinition> GetStartingLeads()
        {
            var result = new List<HavenlineCharacterDefinition>();
            for (var index = 0; index < characters.Count; index++)
            {
                var candidate = characters[index];
                if (candidate != null && candidate.IsStartingLead)
                    result.Add(candidate);
            }

            return result;
        }

        public List<HavenlineCharacterDefinition> GetCompanionsFor(
            HavenlineCharacterId selectedLead)
        {
            if (selectedLead != HavenlineCharacterId.Character1 &&
                selectedLead != HavenlineCharacterId.Character2)
            {
                throw new ArgumentOutOfRangeException(
                    nameof(selectedLead),
                    selectedLead,
                    "Only Character 1 or Character 2 may be the playable lead.");
            }

            var companions = new List<HavenlineCharacterDefinition>(3);
            var otherLead = selectedLead == HavenlineCharacterId.Character1
                ? HavenlineCharacterId.Character2
                : HavenlineCharacterId.Character1;

            AddRequired(otherLead, companions);
            AddRequired(HavenlineCharacterId.Character3, companions);
            AddRequired(HavenlineCharacterId.Character4, companions);
            return companions;
        }

        public string[] ValidateRoster()
        {
            var failures = new List<string>();
            var ids = new HashSet<HavenlineCharacterId>();

            for (var index = 0; index < characters.Count; index++)
            {
                var candidate = characters[index];
                if (candidate == null)
                {
                    failures.Add($"Roster entry {index} is null.");
                    continue;
                }

                if (!ids.Add(candidate.CharacterId))
                    failures.Add($"Duplicate roster entry for {candidate.CharacterId}.");

                var definitionFailure = candidate.ValidateDefinition();
                if (!string.IsNullOrEmpty(definitionFailure))
                    failures.Add(definitionFailure);
            }

            foreach (HavenlineCharacterId id in Enum.GetValues(typeof(HavenlineCharacterId)))
            {
                if (!ids.Contains(id))
                    failures.Add($"Roster is missing {id}.");
            }

            var startingLeads = GetStartingLeads();
            if (startingLeads.Count != 2)
                failures.Add($"Exactly two starting playable leads are required; found {startingLeads.Count}.");
            if (!startingLeads.Exists(item => item.CharacterId == HavenlineCharacterId.Character1))
                failures.Add("Character 1 must be a starting playable lead.");
            if (!startingLeads.Exists(item => item.CharacterId == HavenlineCharacterId.Character2))
                failures.Add("Character 2 must be a starting playable lead.");

            ValidateAvailability(HavenlineCharacterId.Character3, HavenlineCharacterAvailability.CoreCompanion, failures);
            ValidateAvailability(HavenlineCharacterId.Character4, HavenlineCharacterAvailability.CoreCompanion, failures);

            return failures.ToArray();
        }

        private void ValidateAvailability(
            HavenlineCharacterId id,
            HavenlineCharacterAvailability expected,
            ICollection<string> failures)
        {
            if (!TryGet(id, out var definition))
                return;
            if (definition.Availability != expected)
                failures.Add($"{id} must be configured as {expected}.");
        }

        private void AddRequired(
            HavenlineCharacterId id,
            ICollection<HavenlineCharacterDefinition> destination)
        {
            if (!TryGet(id, out var definition) || definition == null)
                throw new InvalidOperationException($"Character roster is missing required crew member {id}.");

            destination.Add(definition);
        }
    }
}
