using System;
using System.Collections.Generic;
using System.IO;
using UnityEngine;

namespace Havenline
{
    [Serializable]
    public sealed class HavenlinePlayerProfileData
    {
        public const int CurrentSchemaVersion = 2;

        public int schemaVersion = CurrentSchemaVersion;
        public string googleSubjectId = string.Empty;
        public string googleEmail = string.Empty;
        public string playerName = string.Empty;
        public int selectedCharacterId;
        public List<int> unlockedCharacterIds = new List<int>();

        public bool HasGoogleIdentity => !string.IsNullOrWhiteSpace(googleSubjectId);
        public bool HasLeadSelection =>
            selectedCharacterId == (int)HavenlineCharacterId.Character1 ||
            selectedCharacterId == (int)HavenlineCharacterId.Character2;

        public HavenlineCharacterId SelectedLead =>
            (HavenlineCharacterId)selectedCharacterId;

        public HavenlineCharacterId UnselectedLeadCompanion
        {
            get
            {
                if (!HasLeadSelection)
                    throw new InvalidOperationException("No playable lead has been selected.");

                return SelectedLead == HavenlineCharacterId.Character1
                    ? HavenlineCharacterId.Character2
                    : HavenlineCharacterId.Character1;
            }
        }

        public HavenlineCharacterId[] CoreCompanionIds
        {
            get
            {
                if (!HasLeadSelection)
                    return Array.Empty<HavenlineCharacterId>();

                return new[]
                {
                    UnselectedLeadCompanion,
                    HavenlineCharacterId.Character3,
                    HavenlineCharacterId.Character4
                };
            }
        }

        public HashSet<HavenlineCharacterId> BuildUnlockedSet()
        {
            var result = new HashSet<HavenlineCharacterId>();
            for (var index = 0; index < unlockedCharacterIds.Count; index++)
            {
                var raw = unlockedCharacterIds[index];
                if (Enum.IsDefined(typeof(HavenlineCharacterId), raw))
                    result.Add((HavenlineCharacterId)raw);
            }

            return result;
        }

        public void SetLead(HavenlineCharacterId characterId)
        {
            if (characterId != HavenlineCharacterId.Character1 &&
                characterId != HavenlineCharacterId.Character2)
            {
                throw new ArgumentOutOfRangeException(
                    nameof(characterId),
                    characterId,
                    "Only Character 1 and Character 2 are valid playable leads.");
            }

            selectedCharacterId = (int)characterId;
            UnlockCoreCrew();
        }

        public void Unlock(HavenlineCharacterId characterId)
        {
            var raw = (int)characterId;
            if (!unlockedCharacterIds.Contains(raw))
                unlockedCharacterIds.Add(raw);
        }

        public void UnlockCoreCrew()
        {
            Unlock(HavenlineCharacterId.Character1);
            Unlock(HavenlineCharacterId.Character2);
            Unlock(HavenlineCharacterId.Character3);
            Unlock(HavenlineCharacterId.Character4);
        }

        public bool IsActiveCrewMember(HavenlineCharacterId characterId)
        {
            if (!HasLeadSelection)
                return false;

            if (characterId == SelectedLead)
                return true;

            var companions = CoreCompanionIds;
            for (var index = 0; index < companions.Length; index++)
            {
                if (companions[index] == characterId)
                    return true;
            }

            return false;
        }

        public string ValidateForSave()
        {
            if (!HasGoogleIdentity)
                return "A verified Google identity is required before saving a HAVENLINE profile.";
            if (string.IsNullOrWhiteSpace(playerName))
                return "Player name is required.";
            if (!HasLeadSelection)
                return "Character 1 or Character 2 must be selected as the playable lead.";
            if (CoreCompanionIds.Length != 3)
                return "The active crew must include the unselected lead plus Characters 3 and 4.";
            return string.Empty;
        }
    }

    public sealed class HavenlinePlayerProfileStore
    {
        private const string FileName = "havenline-player-profile.json";

        public string ProfilePath => Path.Combine(Application.persistentDataPath, FileName);

        public bool TryLoad(out HavenlinePlayerProfileData profile, out string failure)
        {
            profile = null;
            failure = string.Empty;

            try
            {
                if (!File.Exists(ProfilePath))
                    return false;

                var json = File.ReadAllText(ProfilePath);
                profile = JsonUtility.FromJson<HavenlinePlayerProfileData>(json);
                if (profile == null)
                {
                    failure = "Saved profile JSON could not be parsed.";
                    return false;
                }

                if (profile.schemaVersion == 1)
                {
                    profile.schemaVersion = HavenlinePlayerProfileData.CurrentSchemaVersion;
                    if (profile.HasLeadSelection)
                        profile.UnlockCoreCrew();
                    SaveMigrated(profile);
                }

                if (profile.schemaVersion != HavenlinePlayerProfileData.CurrentSchemaVersion)
                {
                    failure = $"Unsupported profile schema {profile.schemaVersion}.";
                    profile = null;
                    return false;
                }

                return true;
            }
            catch (Exception exception)
            {
                failure = exception.Message;
                profile = null;
                return false;
            }
        }

        public void Save(HavenlinePlayerProfileData profile)
        {
            if (profile == null)
                throw new ArgumentNullException(nameof(profile));

            var validationFailure = profile.ValidateForSave();
            if (!string.IsNullOrEmpty(validationFailure))
                throw new InvalidOperationException(validationFailure);

            profile.schemaVersion = HavenlinePlayerProfileData.CurrentSchemaVersion;
            profile.UnlockCoreCrew();
            WriteAtomically(profile);
        }

        private void SaveMigrated(HavenlinePlayerProfileData profile)
        {
            profile.schemaVersion = HavenlinePlayerProfileData.CurrentSchemaVersion;
            WriteAtomically(profile);
        }

        private void WriteAtomically(HavenlinePlayerProfileData profile)
        {
            var directory = Path.GetDirectoryName(ProfilePath);
            if (!string.IsNullOrEmpty(directory))
                Directory.CreateDirectory(directory);

            var temporaryPath = ProfilePath + ".tmp";
            File.WriteAllText(temporaryPath, JsonUtility.ToJson(profile, true) + "\n");

            if (File.Exists(ProfilePath))
                File.Delete(ProfilePath);
            File.Move(temporaryPath, ProfilePath);
        }
    }
}
