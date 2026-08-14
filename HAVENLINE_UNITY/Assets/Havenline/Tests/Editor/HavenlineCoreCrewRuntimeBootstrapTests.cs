using NUnit.Framework;

namespace Havenline.Tests
{
    public sealed class HavenlineCoreCrewRuntimeBootstrapTests
    {
        [Test]
        public void MissingProfileRequiresOnboardingInsteadOfDefaultingCharacter1()
        {
            var result = HavenlineCoreCrewRuntimeBootstrap.TryResolveSavedLead(
                null,
                out var lead,
                out var failure);

            Assert.That(result, Is.False);
            Assert.That((int)lead, Is.EqualTo(0));
            Assert.That(failure, Does.Contain("profile is missing"));
        }

        [Test]
        public void UnverifiedProfileCannotSpawnTheCoreCrew()
        {
            var profile = new HavenlinePlayerProfileData();
            profile.SetLead(HavenlineCharacterId.Character1);

            var result = HavenlineCoreCrewRuntimeBootstrap.TryResolveSavedLead(
                profile,
                out _,
                out var failure);

            Assert.That(result, Is.False);
            Assert.That(failure, Does.Contain("verified Google identity"));
        }

        [TestCase(HavenlineCharacterId.Character1)]
        [TestCase(HavenlineCharacterId.Character2)]
        public void ValidSavedLeadIsResolvedWithoutChangingSelection(HavenlineCharacterId expectedLead)
        {
            var profile = new HavenlinePlayerProfileData
            {
                googleSubjectId = "verified-subject",
                googleEmail = "player@example.invalid",
                playerName = "Player"
            };
            profile.SetLead(expectedLead);

            var result = HavenlineCoreCrewRuntimeBootstrap.TryResolveSavedLead(
                profile,
                out var resolvedLead,
                out var failure);

            Assert.That(result, Is.True, failure);
            Assert.That(resolvedLead, Is.EqualTo(expectedLead));
            Assert.That(profile.CoreCompanionIds, Has.Length.EqualTo(3));
        }
    }
}
