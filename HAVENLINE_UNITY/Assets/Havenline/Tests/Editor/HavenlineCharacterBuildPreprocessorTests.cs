using System.Collections.Generic;
using NUnit.Framework;
using UnityEditor.Build;
using Havenline.Editor;

namespace Havenline.Tests
{
    public sealed class HavenlineCharacterBuildPreprocessorTests
    {
        [Test]
        public void CleanCharacterApprovalValidationAllowsBuildToContinue()
        {
            Assert.DoesNotThrow(() =>
                HavenlineCharacterBuildPreprocessor.RequireApprovedCharacters(
                    () => new List<string>()));
        }

        [Test]
        public void AnyCharacterApprovalFailureBlocksBuildPlayer()
        {
            var exception = Assert.Throws<BuildFailedException>(() =>
                HavenlineCharacterBuildPreprocessor.RequireApprovedCharacters(
                    () => new List<string>
                    {
                        "Character2 is still pending human visual approval."
                    }));

            Assert.That(exception.Message, Does.Contain("four-character approval gate"));
            Assert.That(exception.Message, Does.Contain("Character2 is still pending human visual approval."));
        }

        [Test]
        public void FailureListIsNormalizedBeforeBuildIsRejected()
        {
            var exception = Assert.Throws<BuildFailedException>(() =>
                HavenlineCharacterBuildPreprocessor.RequireApprovedCharacters(
                    () => new List<string>
                    {
                        "Character4 approval has no reviewer identity.",
                        "",
                        "Character1 is still pending human visual approval.",
                        "Character4 approval has no reviewer identity."
                    }));

            Assert.That(exception.Message, Does.Contain("Character1 is still pending human visual approval."));
            Assert.That(exception.Message, Does.Contain("Character4 approval has no reviewer identity."));
            Assert.That(
                CountOccurrences(exception.Message, "Character4 approval has no reviewer identity."),
                Is.EqualTo(1));
        }

        private static int CountOccurrences(string text, string value)
        {
            var count = 0;
            var index = 0;
            while ((index = text.IndexOf(value, index, System.StringComparison.Ordinal)) >= 0)
            {
                count++;
                index += value.Length;
            }
            return count;
        }
    }
}
