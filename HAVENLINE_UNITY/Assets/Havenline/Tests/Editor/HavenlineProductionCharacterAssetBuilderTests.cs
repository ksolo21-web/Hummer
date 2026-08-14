using System.Linq;
using NUnit.Framework;
using Havenline.Editor;

namespace Havenline.Tests
{
    public sealed class HavenlineProductionCharacterAssetBuilderTests
    {
        [Test]
        public void CharacterPlansUseCanonicalFourCharacterProductionPaths()
        {
            var plans = HavenlineProductionCharacterAssetBuilder.Plans;
            Assert.That(plans.Count, Is.EqualTo(4));

            for (var index = 0; index < plans.Count; index++)
            {
                var number = index + 1;
                var expectedId = (HavenlineCharacterId)number;
                var expectedRoot = $"Assets/Havenline/Art/Characters/Production/Character{number}";
                var plan = plans[index];

                Assert.That(plan.Id, Is.EqualTo(expectedId));
                Assert.That(plan.Folder, Is.EqualTo(expectedRoot));
                Assert.That(plan.ModelPath, Is.EqualTo($"{expectedRoot}/Character{number}_production.fbx"));
                Assert.That(plan.PortraitPath, Is.EqualTo($"{expectedRoot}/Character{number}_portrait.png"));
                Assert.That(plan.PrefabPath, Is.EqualTo($"{expectedRoot}/Character{number}_gameplay.prefab"));
                Assert.That(plan.DefinitionPath, Is.EqualTo($"{expectedRoot}/Character{number}_definition.asset"));
            }
        }

        [Test]
        public void ExactlyCharacter1AndCharacter2AreStartingLeads()
        {
            var leads = HavenlineProductionCharacterAssetBuilder.Plans
                .Where(plan => plan.Availability == HavenlineCharacterAvailability.StartingLead)
                .Select(plan => plan.Id)
                .ToArray();

            Assert.That(leads, Is.EqualTo(new[]
            {
                HavenlineCharacterId.Character1,
                HavenlineCharacterId.Character2
            }));
        }

        [Test]
        public void Character3AndCharacter4AreCoreCompanions()
        {
            var companions = HavenlineProductionCharacterAssetBuilder.Plans
                .Where(plan => plan.Availability == HavenlineCharacterAvailability.CoreCompanion)
                .Select(plan => plan.Id)
                .ToArray();

            Assert.That(companions, Is.EqualTo(new[]
            {
                HavenlineCharacterId.Character3,
                HavenlineCharacterId.Character4
            }));
        }

        [Test]
        public void ImportHeightGateRejectsObviouslyWrongUnitScales()
        {
            Assert.That(HavenlineProductionCharacterAssetBuilder.MinimumApprovedHeight, Is.GreaterThan(1f));
            Assert.That(HavenlineProductionCharacterAssetBuilder.MaximumApprovedHeight, Is.LessThan(3f));
            Assert.That(HavenlineProductionCharacterAssetBuilder.MinimumApprovedHeight,
                Is.LessThan(HavenlineProductionCharacterAssetBuilder.MaximumApprovedHeight));
        }
    }
}
