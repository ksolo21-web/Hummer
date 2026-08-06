using System;
using UnityEngine;

namespace Havenline
{
    [Serializable]
    public sealed class HavenlineGoogleIdentity
    {
        public string subjectId = string.Empty;
        public string email = string.Empty;
        public string displayName = string.Empty;

        public bool IsValid => !string.IsNullOrWhiteSpace(subjectId);
    }

    public interface IHavenlineGoogleSignInProvider
    {
        bool IsConfigured { get; }
        bool IsBusy { get; }
        event Action<HavenlineGoogleIdentity> SignInSucceeded;
        event Action<string> SignInFailed;
        void BeginSignIn();
        void SignOut();
    }

    [CreateAssetMenu(
        fileName = "HavenlineGoogleSignInConfiguration",
        menuName = "HAVENLINE/Identity/Google Sign-In Configuration")]
    public sealed class HavenlineGoogleSignInConfiguration : ScriptableObject
    {
        public const string DurableIdentityContract = "verified Google subject id";
        public const string SigningCertificateContract = "signing certificate SHA-256";

        [SerializeField] private string androidPackageName = "";
        [SerializeField] private string webClientId = "";
        [SerializeField] private string firebaseProjectId = "";
        [SerializeField] private string signingCertificateSha256 = "";

        public string AndroidPackageName => androidPackageName?.Trim() ?? string.Empty;
        public string WebClientId => webClientId?.Trim() ?? string.Empty;
        public string FirebaseProjectId => firebaseProjectId?.Trim() ?? string.Empty;
        public string SigningCertificateSha256 =>
            (signingCertificateSha256 ?? string.Empty)
            .Replace(":", string.Empty)
            .Replace(" ", string.Empty)
            .Trim()
            .ToUpperInvariant();

        public string[] ValidateConfiguration()
        {
            var failures = new System.Collections.Generic.List<string>();
            if (string.IsNullOrWhiteSpace(AndroidPackageName))
                failures.Add("Android package name is missing.");
            if (string.IsNullOrWhiteSpace(WebClientId) ||
                !WebClientId.EndsWith(".apps.googleusercontent.com", StringComparison.OrdinalIgnoreCase))
            {
                failures.Add("A valid Google OAuth web client id is missing.");
            }
            if (string.IsNullOrWhiteSpace(FirebaseProjectId))
                failures.Add("Firebase project id is missing.");
            if (SigningCertificateSha256.Length != 64 ||
                !IsHex(SigningCertificateSha256))
            {
                failures.Add("Signing certificate SHA-256 must contain exactly 64 hexadecimal characters.");
            }
            return failures.ToArray();
        }

        private static bool IsHex(string value)
        {
            for (var index = 0; index < value.Length; index++)
            {
                var character = value[index];
                var hexadecimal =
                    character >= '0' && character <= '9' ||
                    character >= 'A' && character <= 'F';
                if (!hexadecimal)
                    return false;
            }
            return true;
        }
    }

    /// <summary>
    /// Connects a concrete Android Google sign-in provider to HAVENLINE onboarding. The
    /// provider must return a verified stable Google subject id. Email alone is not accepted
    /// as the durable account key.
    /// </summary>
    public sealed class HavenlineGoogleSignInCoordinator : MonoBehaviour
    {
        [SerializeField] private MonoBehaviour providerBehaviour;
        [SerializeField] private HavenlineOnboardingController onboarding;

        private IHavenlineGoogleSignInProvider provider;

        public bool IsConfigured => provider != null && provider.IsConfigured;
        public bool IsBusy => provider != null && provider.IsBusy;
        public event Action<string> StatusChanged;

        private void Awake()
        {
            provider = providerBehaviour as IHavenlineGoogleSignInProvider;
            if (onboarding == null)
                onboarding = GetComponent<HavenlineOnboardingController>();
        }

        private void OnEnable()
        {
            if (provider == null)
                provider = providerBehaviour as IHavenlineGoogleSignInProvider;
            if (provider == null)
                return;

            provider.SignInSucceeded += HandleSignInSucceeded;
            provider.SignInFailed += HandleSignInFailed;
        }

        private void OnDisable()
        {
            if (provider == null)
                return;

            provider.SignInSucceeded -= HandleSignInSucceeded;
            provider.SignInFailed -= HandleSignInFailed;
        }

        public void ContinueWithGoogle()
        {
            if (provider == null)
                throw new InvalidOperationException("No concrete Google sign-in provider is assigned.");
            if (!provider.IsConfigured)
                throw new InvalidOperationException("Google sign-in provider is not configured for this signed Android app.");
            if (provider.IsBusy)
                return;

            StatusChanged?.Invoke("Signing in with Google…");
            provider.BeginSignIn();
        }

        public void SignOut()
        {
            provider?.SignOut();
            StatusChanged?.Invoke("Signed out");
        }

        private void HandleSignInSucceeded(HavenlineGoogleIdentity identity)
        {
            if (identity == null || !identity.IsValid)
            {
                HandleSignInFailed("Google sign-in returned no verified subject id.");
                return;
            }
            if (onboarding == null)
                throw new InvalidOperationException("HAVENLINE onboarding controller is not assigned.");

            onboarding.AcceptGoogleIdentity(identity.subjectId, identity.email);
            StatusChanged?.Invoke("Signed in");
        }

        private void HandleSignInFailed(string failure)
        {
            StatusChanged?.Invoke(string.IsNullOrWhiteSpace(failure)
                ? "Google sign-in failed"
                : failure);
        }
    }
}
