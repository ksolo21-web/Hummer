using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using NUnit.Framework;
using UnityEngine;

namespace Havenline.Tests
{
    public sealed class HavenlineCrewContractTests
    {
        private readonly List<UnityEngine.Object> createdObjects = new List<UnityEngine.Object>();

        [TearDown]
        public void TearDown()
        {
            for (var index = 0; index < createdObjects.Count; index++)
            {
                if (createdObjects[index] != null)
                    UnityEngine.Object.DestroyImmediate(createdObjects[index]);
            }
            createdObjects.Clear();
        }

        [Test]
        public void Character1SelectionMakesCharacter2AndCharacters3And4Companions()
        {
            var roster = CreateRoster();

            var companions = roster.GetCompanionsFor(HavenlineCharacterId.Character1)
                .Select(item => item.CharacterId)
                .ToArray();

            CollectionAssert.AreEquivalent(
                new[]
                {
                    HavenlineCharacterId.Character2,
                    HavenlineCharacterId.Character3,
                    HavenlineCharacterId.Character4
                },
                companions);
        }

        [Test]
        public void Character2SelectionMakesCharacter1AndCharacters3And4Companions()
        {
            var roster = CreateRoster();

            var companions = roster.GetCompanionsFor(HavenlineCharacterId.Character2)
                .Select(item => item.CharacterId)
                .ToArray();

            CollectionAssert.AreEquivalent(
                new[]
                {
                    HavenlineCharacterId.Character1,
                    HavenlineCharacterId.Character3,
                    HavenlineCharacterId.Character4
                },
                companions);
        }

        [Test]
        public void Characters3And4CannotBeSelectedAsStartingLead()
        {
            var profile = new HavenlinePlayerProfileData();

            Assert.Throws<ArgumentOutOfRangeException>(
                () => profile.SetLead(HavenlineCharacterId.Character3));
            Assert.Throws<ArgumentOutOfRangeException>(
                () => profile.SetLead(HavenlineCharacterId.Character4));
        }

        [Test]
        public void SelectingLeadActivatesAllFourCoreCharacters()
        {
            var profile = new HavenlinePlayerProfileData();
            profile.SetLead(HavenlineCharacterId.Character1);

            Assert.That(profile.SelectedLead, Is.EqualTo(HavenlineCharacterId.Character1));
            Assert.That(profile.UnselectedLeadCompanion, Is.EqualTo(HavenlineCharacterId.Character2));
            Assert.That(profile.CoreCompanionIds, Has.Length.EqualTo(3));

            foreach (HavenlineCharacterId id in Enum.GetValues(typeof(HavenlineCharacterId)))
                Assert.That(profile.IsActiveCrewMember(id), Is.True, $"{id} should be active crew.");
        }

        [Test]
        public void ProfileCannotSaveWithoutVerifiedGoogleIdentity()
        {
            var profile = new HavenlinePlayerProfileData
            {
                playerName = "Explorer"
            };
            profile.SetLead(HavenlineCharacterId.Character2);

            Assert.That(
                profile.ValidateForSave(),
                Is.EqualTo("A verified Google identity is required before saving a HAVENLINE profile."));
        }

        private HavenlineCharacterRoster CreateRoster()
        {
            var roster = ScriptableObject.CreateInstance<HavenlineCharacterRoster>();
            createdObjects.Add(roster);

            var definitions = new List<HavenlineCharacterDefinition>
            {
                CreateDefinition(HavenlineCharacterId.Character1, HavenlineCharacterAvailability.StartingLead),
                CreateDefinition(HavenlineCharacterId.Character2, HavenlineCharacterAvailability.StartingLead),
                CreateDefinition(HavenlineCharacterId.Character3, HavenlineCharacterAvailability.CoreCompanion),
                CreateDefinition(HavenlineCharacterId.Character4, HavenlineCharacterAvailability.CoreCompanion)
            };

            SetPrivateField(roster, "characters", definitions);
            return roster;
        }

        private HavenlineCharacterDefinition CreateDefinition(
            HavenlineCharacterId id,
            HavenlineCharacterAvailability availability)
        {
            var definition = ScriptableObject.CreateInstance<HavenlineCharacterDefinition>();
            createdObjects.Add(definition);
            SetPrivateField(definition, "characterId", id);
            SetPrivateField(definition, "availability", availability);
            return definition;
        }

        private static void SetPrivateField<TTarget, TValue>(
            TTarget target,
            string fieldName,
            TValue value)
        {
            var field = typeof(TTarget).GetField(
                fieldName,
                BindingFlags.Instance | BindingFlags.NonPublic);
            Assert.That(field, Is.Not.Null, $"Missing private field {typeof(TTarget).Name}.{fieldName}");
            field.SetValue(target, value);
        }
    }
}
