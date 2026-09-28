package com.koenterprises.territorycardstudio

import com.koenterprises.territorycardstudio.core.*

/** Explicit composition dependencies. Production always supplies TerritoryCardStudioServices;
 * instrumentation can use isolated identities and transports without changing production policy. */
interface ProductionWorkflowServices {
    val knowledgeBase:TerritoryKnowledgeBase
    val activePolicy:OnlineSourcePolicy
    val endpointConfig:ProviderEndpointSwitchConfig
    val buildWorkflow:AndroidBuildWorkflowCoordinator
    val pdfPreview:AndroidPdfPreviewService
    val candidateReview:AndroidCandidateReviewService
    val candidateLifecycle:AndroidCandidateLifecycleService
    val editingDrafts:AndroidEditingDraftStore
    val extendedDrafts:AndroidExtendedDraftStore
    val editingAuthority:AndroidEditingAuthorityStore
    val editingPreparation:AndroidEditingPreparationService
    val extendedPreparation:AndroidExtendedPreparationService
    val verifiedProject:AndroidVerifiedProjectIntake
    val nativeDrafts:AndroidNativeDraftStore
    val nativeAuthoring:AndroidNativeAuthoringService
    val finalOutput:AndroidFinalOutputService
    val approvedExport:AndroidApprovedExportService
}
