package com.koenterprises.territorycardstudio

import android.content.Context
import com.koenterprises.territorycardstudio.core.BundleIntegrity
import com.koenterprises.territorycardstudio.core.DirectoryProviderEvidenceCache
import com.koenterprises.territorycardstudio.core.HttpsUrlConnectionProviderTransport
import com.koenterprises.territorycardstudio.core.LiveGeometryVerificationRequest
import com.koenterprises.territorycardstudio.core.LiveVerificationExecutionPipeline
import com.koenterprises.territorycardstudio.core.LiveVerificationExecutionResult
import com.koenterprises.territorycardstudio.core.OnlineSourcePolicy
import com.koenterprises.territorycardstudio.core.OnlineSourcePolicyLoader
import com.koenterprises.territorycardstudio.core.ProviderCacheMode
import com.koenterprises.territorycardstudio.core.ProviderEndpointSwitchConfig
import com.koenterprises.territorycardstudio.core.ProviderEndpointSwitchConfigLoader
import com.koenterprises.territorycardstudio.core.ProviderExecutionCoordinator
import com.koenterprises.territorycardstudio.core.ProductionRenderModelAdapter
import com.koenterprises.territorycardstudio.core.ProductionRenderModelInput
import com.koenterprises.territorycardstudio.core.RenderModelAdaptationResult
import com.koenterprises.territorycardstudio.core.LockedPdfRendererContract
import com.koenterprises.territorycardstudio.core.LockedPdfRendererContractLoader
import com.koenterprises.territorycardstudio.core.TerritoryKnowledgeBase
import com.koenterprises.territorycardstudio.core.TerritoryKnowledgeBaseLoader
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class TerritoryCardStudioServices(context: Context) {
    private val appContext = context.applicationContext
    private val authorityPath = "territory/"
    private val privateRoot = File(appContext.noBackupFilesDir, "territory-card-studio").apply {
        require(exists() || mkdirs()) { "Unable to create app-private Territory Card Studio directory" }
    }
    private val cacheDirectory = File(privateRoot, "provider-evidence-v1")
    private val endpointStore = ProviderEndpointSwitchStore(
        File(privateRoot, "provider-endpoints.active.properties")
    )

    val knowledgeBase: TerritoryKnowledgeBase
    val rendererContract: LockedPdfRendererContract
    val pdfArtifacts: AndroidPdfArtifactService
    val basePolicy: OnlineSourcePolicy
    val endpointConfig: ProviderEndpointSwitchConfig
    val activePolicy: OnlineSourcePolicy
    val liveVerification: AndroidLiveVerificationService
    val renderModels: AndroidRenderModelService
    val buildWorkflow: AndroidBuildWorkflowCoordinator by lazy {
        AndroidBuildWorkflowCoordinator(knowledgeBase, renderModels, pdfArtifacts, SourceMapIntakeStore(appContext))
    }

    val pdfPreview: AndroidPdfPreviewService by lazy {
        AndroidPdfPreviewService(buildWorkflow, File(appContext.cacheDir, "candidate-preview-v1"), pdfArtifacts)
    }

    val candidateReview: AndroidCandidateReviewService by lazy {
        AndroidCandidateReviewService(buildWorkflow, File(privateRoot, "candidate-reviews-v1"))
    }

    val editingDrafts: AndroidEditingDraftStore by lazy {
        AndroidEditingDraftStore(File(privateRoot, "editing-drafts-v1"), knowledgeBase, SourceMapIntakeStore(appContext))
    }

    val extendedDrafts: AndroidExtendedDraftStore by lazy {
        AndroidExtendedDraftStore(File(privateRoot, "extended-drafts-v1"), knowledgeBase, SourceMapIntakeStore(appContext), buildWorkflow)
    }

    val editingPreparation: AndroidEditingPreparationService by lazy {
        AndroidEditingPreparationService(knowledgeBase, activePolicy, editingDrafts, SourceMapIntakeStore(appContext),
            buildWorkflow, renderModels, fetchEvidence = { request ->
            liveVerification.verify(request, ProviderCacheMode.BYPASS).evidence
        })
    }

    val approvedExport: AndroidApprovedExportService by lazy {
        AndroidApprovedExportService(knowledgeBase, pdfArtifacts)
    }

    init {
        validateEmbeddedAuthorities()
        knowledgeBase = assetReader("Territory-Knowledge-Base.json").use(TerritoryKnowledgeBaseLoader::load)
        rendererContract = assetReader("R48-Canonical-Style-Tokens.json").use(LockedPdfRendererContractLoader::load)
        val lockedTemplatePdf = appContext.assets.open(authorityPath + "render-authority/Canonical-New-Designed-Template-R48.pdf").use { it.readBytes() }
        pdfArtifacts = AndroidPdfArtifactService(knowledgeBase, privateRoot, lockedTemplatePdf)
        basePolicy = assetReader("Online-Source-Policy.json").use(OnlineSourcePolicyLoader::load)
        val embedded = assetReader("Provider-Endpoint-Switches.properties").use(ProviderEndpointSwitchConfigLoader::load)
        endpointConfig = endpointStore.loadOrEmbedded(embedded)
        activePolicy = endpointConfig.applyTo(basePolicy)
        val cache = DirectoryProviderEvidenceCache(cacheDirectory)
        val coordinator = ProviderExecutionCoordinator(
            transport = HttpsUrlConnectionProviderTransport(),
            cache = cache
        )
        liveVerification = AndroidLiveVerificationService(
            policy = activePolicy,
            pipeline = LiveVerificationExecutionPipeline(coordinator)
        )
        renderModels = AndroidRenderModelService(knowledgeBase)
    }

    fun installEndpointSwitch(candidateText: String): ProviderEndpointSwitchConfig {
        val embedded = assetReader("Provider-Endpoint-Switches.properties").use(ProviderEndpointSwitchConfigLoader::load)
        val candidate = ProviderEndpointSwitchConfigLoader.load(candidateText.reader())
        require(candidate.basePolicyRevision == embedded.basePolicyRevision)
        require(candidate.revisionSeq >= embedded.revisionSeq) { "Endpoint-switch rollback below embedded revision is forbidden" }
        val current = endpointStore.loadOrEmbedded(embedded)
        require(candidate.revisionSeq >= current.revisionSeq) { "Endpoint-switch rollback is forbidden" }
        candidate.applyTo(basePolicy)
        endpointStore.install(candidateText)
        return candidate
    }

    private fun validateEmbeddedAuthorities() {
        BundleIntegrity.requiredSha256.forEach { (name, expected) ->
            val actual = appContext.assets.open(authorityPath + name).use(BundleIntegrity::sha256)
            require(actual == expected) { "Embedded authority hash drift for $name" }
        }
    }

    private fun assetReader(name: String) = appContext.assets.open(authorityPath + name).bufferedReader(StandardCharsets.UTF_8)
}


class AndroidRenderModelService(private val knowledgeBase: TerritoryKnowledgeBase) {
    fun adaptProduction(input: ProductionRenderModelInput): RenderModelAdaptationResult =
        ProductionRenderModelAdapter.adaptProduction(knowledgeBase, input)

    fun adaptNonFieldFixture(input: ProductionRenderModelInput): RenderModelAdaptationResult =
        ProductionRenderModelAdapter.adaptNonFieldFixture(knowledgeBase, input)
}

class AndroidLiveVerificationService(
    private val policy: OnlineSourcePolicy,
    private val pipeline: LiveVerificationExecutionPipeline
) {
    val policyRevision: String get() = policy.revision

    @Synchronized
    fun verify(
        request: LiveGeometryVerificationRequest,
        cacheMode: ProviderCacheMode = ProviderCacheMode.READ_WRITE
    ): LiveVerificationExecutionResult = pipeline.verify(request, policy, cacheMode)
}

private class ProviderEndpointSwitchStore(private val file: File) {
    fun loadOrEmbedded(embedded: ProviderEndpointSwitchConfig): ProviderEndpointSwitchConfig {
        if (!file.isFile) return embedded
        val stored = file.reader(StandardCharsets.UTF_8).use(ProviderEndpointSwitchConfigLoader::load)
        require(stored.basePolicyRevision == embedded.basePolicyRevision) { "Stored endpoint-switch policy mismatch" }
        require(stored.revisionSeq >= embedded.revisionSeq) { "Stored endpoint-switch is older than embedded baseline" }
        return stored
    }

    fun install(text: String) {
        require(text.toByteArray(StandardCharsets.UTF_8).size <= 64 * 1024) { "Endpoint-switch file too large" }
        val parent = file.parentFile ?: error("Endpoint-switch store has no parent directory")
        require(parent.exists() || parent.mkdirs()) { "Unable to create endpoint-switch directory" }
        val tmp = File(parent, file.name + ".tmp")
        try {
            FileOutputStream(tmp).use { output ->
                output.write(text.toByteArray(StandardCharsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            tmp.delete()
        }
    }
}
