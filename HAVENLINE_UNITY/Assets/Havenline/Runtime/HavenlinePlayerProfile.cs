using System;
using System.Collections.Generic;
using System.IO;
using UnityEngine;

namespace Havenline
{
    [Serializable]
    public sealed class HavenlinePlayerProfileData
    {
        public const int CurrentSchemaVersion = 1;

        public int schemaVersion = CurrentSchemaVersion;
        public string googleSubjectId = string.Empty;
        public string googleEmail = string.Empty;
        public string playerName = string.Empty;
        public int selectedCharacterId;
        public List<int> unlockedCharacterIds = new List<int>();

        public bool HasGoogleIdentity => !string.IsNullOrWhiteSpace(googleSubjectId);
        public bool HasStarterSelection =>
            selectedCharacterId == (int)HavenlineCharacterId.Character1 ||
            selectedCharacterId == (int)HavenlineCharacterId.Character2;

        public HavenlineCharacterId SelectedCharacter =>
            (HavenlineCharacterId)selectedCharacterId;

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

        public void SetStarter(HavenlineCharacterId characterId)
        {
            if (characterId != HavenlineCharacterId.Character1 &&
                characterId != HavenlineCharacterId.Character2)
            {
                throw new ArgumentOutOfRangeException(
                    nameof(characterId),
                    characterId,
                    "Only Character 1 and Character 2 are valid starting selections.");
            }

            selectedCharacterId = (int)characterId;
            Unlock(characterId);
        }

        public void Unlock(HavenlineCharacterId characterId)
        {
            var raw = (int)characterId;
            if (!unlockedCharacterIds.Contains(raw))
                unlockedCharacterIds.Add(raw);
        }

        public string ValidateForSave()
        {
            if (!HasGoogleIdentity)
                return "A verified Google identity is required before saving a HAVENLINE profile.";
            if (string.IsNullOrWhiteSpace(playerName))
                return "Player name is required.";
            if (!HasStarterSelection)
                return "A starting playable character must be selected.";
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
