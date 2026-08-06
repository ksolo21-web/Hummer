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
        StartingChoice = 0,
        LockedCrew = 1,
        UnlockedCrew = 2
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
        [SerializeField] private HavenlineCharacterAvailability availability = HavenlineCharacterAvailability.LockedCrew;
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
        public bool IsStartingChoice => availability == HavenlineCharacterAvailability.StartingChoice;

        public bool IsSelectable(ISet<HavenlineCharacterId> unlockedCharacters)
        {
            if (IsStartingChoice || availability == HavenlineCharacterAvailability.UnlockedCrew)
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
            if (availability == HavenlineCharacterAvailability.LockedCrew && unlockLevel <= 0)
                problems.Add("locked crew requires a positive unlock level");

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

        public List<HavenlineCharacterDefinition> GetStartingChoices()
        {
            var result = new List<HavenlineCharacterDefinition>();
            for (var index = 0; index < characters.Count; index++)
            {
                var candidate = characters[index];
                if (candidate != null && candidate.IsStartingChoice)
                    result.Add(candidate);
            }

            return result;
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

            var startingChoices = GetStartingChoices();
            if (startingChoices.Count != 2)
                failures.Add($"Exactly two starting choices are required; found {startingChoices.Count}.");
            if (!startingChoices.Exists(item => item.CharacterId == HavenlineCharacterId.Character1))
                failures.Add("Character 1 must be a starting choice.");
            if (!startingChoices.Exists(item => item.CharacterId == HavenlineCharacterId.Character2))
                failures.Add("Character 2 must be a starting choice.");

            return failures.ToArray();
        }
    }
}
