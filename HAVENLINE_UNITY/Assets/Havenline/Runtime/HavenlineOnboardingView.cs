using System;
using UnityEngine;
using UnityEngine.UI;

namespace Havenline
{
    /// <summary>
    /// Thin Unity-UI presenter for the approved onboarding flow:
    /// Google sign-in -> profile -> choose Character 1 or 2 -> confirm the active four-person crew.
    /// </summary>
    public sealed class HavenlineOnboardingView : MonoBehaviour
    {
        [Header("Controllers")]
        [SerializeField] private HavenlineGoogleSignInCoordinator googleSignIn;
        [SerializeField] private HavenlineOnboardingController onboarding;
        [SerializeField] private HavenlineCharacterRoster roster;

        [Header("Screens")]
        [SerializeField] private GameObject signInScreen;
        [SerializeField] private GameObject profileScreen;
        [SerializeField] private GameObject leadSelectionScreen;
        [SerializeField] private GameObject crewScreen;

        [Header("Profile")]
        [SerializeField] private InputField playerNameInput;
        [SerializeField] private Text googleAccountLabel;
        [SerializeField] private Text statusLabel;

        [Header("Lead Selection")]
        [SerializeField] private Button character1Button;
        [SerializeField] private Button character2Button;
        [SerializeField] private Button confirmLeadButton;
        [SerializeField] private Text selectedLeadLabel;
        [SerializeField] private Text companionPreviewLabel;

        [Header("Crew Confirmation")]
        [SerializeField] private Text crewSummaryLabel;
        [SerializeField] private Button enterCampButton;

        private HavenlineCharacterId? pendingLead;

        private void Awake()
        {
            if (onboarding == null)
                onboarding = GetComponent<HavenlineOnboardingController>();
            if (googleSignIn == null)
                googleSignIn = GetComponent<HavenlineGoogleSignInCoordinator>();
        }

        private void OnEnable()
        {
            if (onboarding != null)
                onboarding.StepChanged += ShowStep;
            if (googleSignIn != null)
                googleSignIn.StatusChanged += SetStatus;

            character1Button?.onClick.AddListener(SelectCharacter1);
            character2Button?.onClick.AddListener(SelectCharacter2);
            confirmLeadButton?.onClick.AddListener(ConfirmLead);
            enterCampButton?.onClick.AddListener(EnterCamp);

            ShowStep(onboarding != null
                ? onboarding.CurrentStep
                : HavenlineOnboardingStep.SignIn);
            RefreshIdentity();
            RefreshSelection();
        }

        private void OnDisable()
        {
            if (onboarding != null)
                onboarding.StepChanged -= ShowStep;
            if (googleSignIn != null)
                googleSignIn.StatusChanged -= SetStatus;

            character1Button?.onClick.RemoveListener(SelectCharacter1);
            character2Button?.onClick.RemoveListener(SelectCharacter2);
            confirmLeadButton?.onClick.RemoveListener(ConfirmLead);
            enterCampButton?.onClick.RemoveListener(EnterCamp);
        }

        public void ContinueWithGoogle()
        {
            try
            {
                googleSignIn.ContinueWithGoogle();
            }
            catch (Exception exception)
            {
                SetStatus(exception.Message);
            }
        }

        public void ContinueProfileSetup()
        {
            try
            {
                onboarding.SaveProfileName(playerNameInput != null
                    ? playerNameInput.text
                    : string.Empty);
            }
            catch (Exception exception)
            {
                SetStatus(exception.Message);
            }
        }

        public void SelectCharacter1() => SelectLead(HavenlineCharacterId.Character1);
        public void SelectCharacter2() => SelectLead(HavenlineCharacterId.Character2);

        private void SelectLead(HavenlineCharacterId lead)
        {
            pendingLead = lead;
            RefreshSelection();
        }

        private void ConfirmLead()
        {
            try
            {
                if (!pendingLead.HasValue)
                    throw new InvalidOperationException("Choose Character 1 or Character 2 first.");

                onboarding.SelectStarter(pendingLead.Value);
                onboarding.ConfirmStarterAndEnterCrew();
                RefreshCrewSummary();
            }
            catch (Exception exception)
            {
                SetStatus(exception.Message);
            }
        }

        private void EnterCamp()
        {
            try
            {
                onboarding.SpawnSelectedCrew();
            }
            catch (Exception exception)
            {
                SetStatus(exception.Message);
            }
        }

        private void ShowStep(HavenlineOnboardingStep step)
        {
            SetActive(signInScreen, step == HavenlineOnboardingStep.SignIn);
            SetActive(profileScreen, step == HavenlineOnboardingStep.ProfileSetup);
            SetActive(leadSelectionScreen, step == HavenlineOnboardingStep.ChooseStarter);
            SetActive(crewScreen, step == HavenlineOnboardingStep.Crew);

            RefreshIdentity();
            if (step == HavenlineOnboardingStep.Crew)
                RefreshCrewSummary();
        }

        private void RefreshIdentity()
        {
            if (googleAccountLabel == null || onboarding?.Profile == null)
                return;

            googleAccountLabel.text = onboarding.Profile.HasGoogleIdentity
                ? onboarding.Profile.googleEmail
                : "Not signed in";
        }

        private void RefreshSelection()
        {
            if (confirmLeadButton != null)
                confirmLeadButton.interactable = pendingLead.HasValue;

            if (!pendingLead.HasValue)
            {
                if (selectedLeadLabel != null)
                    selectedLeadLabel.text = "Choose Character 1 or Character 2";
                if (companionPreviewLabel != null)
                {
                    companionPreviewLabel.text =
                        "The lead you do not select will join Characters 3 and 4 as companions.";
                }
                return;
            }

            var selected = pendingLead.Value;
            var otherLead = selected == HavenlineCharacterId.Character1
                ? HavenlineCharacterId.Character2
                : HavenlineCharacterId.Character1;
            if (selectedLeadLabel != null)
                selectedLeadLabel.text = $"Playable lead: {DisplayName(selected)}";
            if (companionPreviewLabel != null)
            {
                companionPreviewLabel.text =
                    $"Companions: {DisplayName(otherLead)}, " +
                    $"{DisplayName(HavenlineCharacterId.Character3)}, " +
                    $"{DisplayName(HavenlineCharacterId.Character4)}";
            }
        }

        private void RefreshCrewSummary()
        {
            if (crewSummaryLabel == null || onboarding?.Profile == null ||
                !onboarding.Profile.HasLeadSelection)
            {
                return;
            }

            var profile = onboarding.Profile;
            var companions = profile.CoreCompanionIds;
            crewSummaryLabel.text =
                $"PLAYABLE LEAD\n{DisplayName(profile.SelectedLead)}\n\n" +
                "HELPERS / COMPANIONS\n" +
                $"{DisplayName(companions[0])}\n" +
                $"{DisplayName(companions[1])}\n" +
                $"{DisplayName(companions[2])}";
        }

        private string DisplayName(HavenlineCharacterId id)
        {
            if (roster != null && roster.TryGet(id, out var definition) &&
                !string.IsNullOrWhiteSpace(definition.DisplayName))
            {
                return definition.DisplayName;
            }

            return id.ToString().Replace("Character", "Character ");
        }

        private void SetStatus(string status)
        {
            if (statusLabel != null)
                statusLabel.text = status ?? string.Empty;
        }

        private static void SetActive(GameObject target, bool active)
        {
            if (target != null && target.activeSelf != active)
                target.SetActive(active);
        }
    }
}
