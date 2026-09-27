package com.koenterprises.territorycardstudio

import com.koenterprises.territorycardstudio.core.*

data class BuildWorkflowState(
    val sourceReady: Boolean,
    val inputReady: Boolean,
    val inventorySummary: String,
    val buildBlockers: List<String>,
    val page2Blockers: List<String>,
    val front: StoredCandidatePdf? = null,
    val packet: StoredCanonicalFrontBackPdf? = null,
    val error: String? = null
) {
    val canBuild: Boolean get() = buildBlockers.isEmpty()
    val canGeneratePage2: Boolean get() = page2Blockers.isEmpty()
}

/** Session preparation is supplied by the verified-input pipeline, never inferred from a map. */
class AndroidBuildWorkflowCoordinator(
    private val knowledgeBase: TerritoryKnowledgeBase,
    private val renderModels: AndroidRenderModelService,
    private val artifacts: AndroidPdfArtifactService,
    private val sources: SourceMapIntakeStore
) {
    private data class Prepared(
        val source: SourceMapIntakeRecord,
        val input: ProductionRenderModelInput,
        val inputHash: String,
        val inventory: Page2Inventory?,
        val inventoryHash: String?,
        var candidateVersion: String = java.util.UUID.randomUUID().toString(),
        var front: StoredCandidatePdf? = null,
        var receipt: CandidatePdfArtifactReceipt? = null,
        var packet: StoredCanonicalFrontBackPdf? = null
    )
    private val prepared = mutableMapOf<String, Prepared>()
    private fun key(id: String, mode: WorkspaceMode) = id + ":" + mode.name

    @Synchronized
    fun prepare(id: String, mode: WorkspaceMode, sourceSha256: String,
        input: ProductionRenderModelInput, inventory: Page2Inventory? = null) {
        val slot = knowledgeBase.assignments[id] ?: error("Unknown territory")
        require(mode in WorkspaceModePolicy.allowedModes(slot)) { "Workspace mode does not match territory" }
        val source = requireNotNull(sources.verifiedRecord(id)) { "Import a source map first" }
        require(source.sha256 == sourceSha256 && source.canonicalFilename == slot.canonicalFilename) {
            "Prepared input does not match the current source map"
        }
        require(input.assignment.displayId == id) { "Prepared territory identity mismatch" }
        val adapted = renderModels.adaptProduction(input)
        require(adapted is RenderModelAdaptationResult.Renderable) {
            (adapted as RenderModelAdaptationResult.Blocked).reasons.joinToString("; ")
        }
        require(!adapted.renderSpec.nonFieldFixture) { "Fixture inputs are not production candidates" }
        val failures = inventoryFailures(mode, inventory, input)
        require(failures.isEmpty()) { failures.joinToString("; ") }
        prepared[key(id, mode)] = Prepared(source, input, input.canonicalSha256(), inventory, inventoryHash(inventory))
    }

    @Synchronized
    fun state(id: String, mode: WorkspaceMode): BuildWorkflowState {
        val slot = knowledgeBase.assignments[id]
        val blockers = mutableListOf<String>()
        if (slot == null || mode !in WorkspaceModePolicy.allowedModes(slot)) blockers += "Territory or workspace mode is not available."
        val read = runCatching { sources.verifiedRecord(id) }
        val source = read.getOrNull()
        if (source == null) blockers += read.exceptionOrNull()?.message ?: "Import a source map before preparing a build."
        var p = prepared[key(id, mode)]
        if (p != null && (source != p.source || p.input.canonicalSha256() != p.inputHash || inventoryHash(p.inventory) != p.inventoryHash)) {
            prepared.remove(key(id, mode))
            p = null
            blockers += "Source or prepared data changed. Verify and prepare the build again."
        }
        if (slot != null && !slot.needsNewCard) blockers += "The approved card is preserved. Creating a replacement requires the commissioning workflow."
        if (p == null) blockers += "Verified build inputs are not prepared for this territory."
        val entry = p
        val adapted = entry?.let { renderModels.adaptProduction(it.input) }
        if (adapted is RenderModelAdaptationResult.Blocked) blockers += adapted.reasons
        if (entry != null && entry.front != null) {
            val valid = runCatching { entry.front!!.file.inputStream().use(BundleIntegrity::sha256) == entry.front!!.sha256 }.getOrDefault(false)
            if (!valid) { entry.front = null; entry.receipt = null; entry.packet = null }
        }
        if (entry != null && entry.packet != null) {
            val valid = runCatching { entry.packet!!.file.inputStream().use(BundleIntegrity::sha256) == entry.packet!!.sha256 }.getOrDefault(false)
            if (!valid) entry.packet = null
        }
        val pageBlockers = blockers.toMutableList()
        if (mode == WorkspaceMode.REGULAR) pageBlockers += "This workspace does not generate an address or phone page."
        if (mode != WorkspaceMode.REGULAR && entry?.inventory == null) pageBlockers += "Attach a verified territory-specific inventory."
        if (entry?.front == null) pageBlockers += "Build the front candidate first."
        val summary = when (val inventory = entry?.inventory) {
            is Page2Inventory.LetterWriting -> "${inventory.inventory.records.size} verified " + if (inventory.inventory.records.size == 1) "address" else "addresses"
            is Page2Inventory.Telephone -> "${inventory.inventory.records.size} verified address records; unavailable numbers remain explicit"
            null -> if (mode == WorkspaceMode.REGULAR) "Not required for the front candidate" else "No verified inventory attached"
        }
        return BuildWorkflowState(source != null, adapted is RenderModelAdaptationResult.Renderable, summary,
            blockers.distinct(), pageBlockers.distinct(), entry?.front, entry?.packet)
    }

    @Synchronized
    fun buildFront(id: String, mode: WorkspaceMode): BuildWorkflowState = execute(id, mode) {
        val current = state(id, mode)
        require(current.canBuild) { current.buildBlockers.joinToString("; ") }
        val p = requireNotNull(prepared[key(id, mode)])
        val adapted = renderModels.adaptProduction(p.input)
        require(adapted is RenderModelAdaptationResult.Renderable) { "Prepared input no longer passes validation" }
        val front = artifacts.renderCandidate(adapted.renderSpec, adapted.validationReceipt)
        p.candidateVersion = java.util.UUID.randomUUID().toString()
        p.front = front
        p.receipt = CandidatePdfArtifactReceipt(id, front.canonicalFilename, front.sha256, adapted.validationReceipt)
        p.packet = null
    }

    @Synchronized
    fun generatePage2(id: String, mode: WorkspaceMode): BuildWorkflowState = execute(id, mode) {
        val current = state(id, mode)
        require(current.canGeneratePage2) { current.page2Blockers.joinToString("; ") }
        val p = requireNotNull(prepared[key(id, mode)])
        val generated = artifacts.generatePage2(requireNotNull(p.front), requireNotNull(p.receipt),
            requireNotNull(p.inventory), p.input.assignment.locality, p.input.assignment.updated)
        check(generated.fieldReleaseState == PdfFieldReleaseState.AWAITING_EXPLICIT_USER_APPROVAL)
        p.candidateVersion = java.util.UUID.randomUUID().toString()
        p.packet = generated.packet
    }

    @Synchronized
    internal fun resolvePreview(id: String, mode: WorkspaceMode, kind: PdfPreviewKind): CurrentPreviewArtifact {
        val current = state(id, mode)
        require(current.canBuild) { "Preview unavailable. " + current.buildBlockers.joinToString("; ") }
        val p = requireNotNull(prepared[key(id, mode)])
        val front = requireNotNull(current.front) { "Build a current front candidate before previewing it." }
        require(front.displayId == id && front.canonicalFilename == p.input.assignment.canonicalFilename &&
            front.sha256 == p.receipt?.pdfSha256 && front.exactPdfValidationPassed) { "Candidate identity or validation changed" }
        return when (kind) {
            PdfPreviewKind.FRONT -> CurrentPreviewArtifact(PdfPreviewDocument(id, mode, kind,
                front.canonicalFilename, front.sha256, 1), front.file)
            PdfPreviewKind.PACKET -> {
                require(mode != WorkspaceMode.REGULAR && current.canGeneratePage2) { "This workspace has no current two-page candidate" }
                val packet = requireNotNull(current.packet) { "Generate the current two-page candidate before previewing it." }
                require(packet.displayId == id && packet.canonicalFilename == front.canonicalFilename &&
                    packet.frontPdfSha256 == front.sha256 && packet.pageCount == 2) { "Packet identity or front binding changed" }
                CurrentPreviewArtifact(PdfPreviewDocument(id, mode, kind, packet.canonicalFilename, packet.sha256, 2), packet.file)
            }
        }
    }

    /** The review uses the final packet in letter/telephone mode; a front alone is incomplete. */
    @Synchronized
    internal fun resolveReview(id: String, mode: WorkspaceMode): CandidateReviewTicket {
        val kind = if (mode == WorkspaceMode.REGULAR) PdfPreviewKind.FRONT else PdfPreviewKind.PACKET
        val artifact = resolvePreview(id, mode, kind)
        val p = requireNotNull(prepared[key(id, mode)])
        val front = requireNotNull(p.front)
        val receipt = requireNotNull(p.receipt)
        require(artifact.file.length() in 1..(300L * 1024 - 1)) { "Candidate must be under 300 KiB" }
        val frontBytes = front.file.readBytes()
        val result = when (mode) {
            WorkspaceMode.REGULAR -> CrossModePacketValidator.validateRegular(frontBytes, receipt, p.candidateVersion)
            WorkspaceMode.LETTER_WRITING -> CrossModePacketValidator.validateLetterWriting(frontBytes, receipt,
                (p.inventory as Page2Inventory.LetterWriting).inventory, p.input.assignment.locality,
                p.input.assignment.updated, p.candidateVersion)
            WorkspaceMode.TELEPHONE -> CrossModePacketValidator.validateTelephone(frontBytes, receipt,
                (p.inventory as Page2Inventory.Telephone).inventory, p.input.assignment.locality,
                p.input.assignment.updated, p.candidateVersion)
        }
        require(result.passed) { result.errors.joinToString("; ") }
        val manifest = requireNotNull(result.manifest)
        require(manifest.packetPdfSha256 == artifact.document.sha256 &&
            manifest.pageCount == artifact.document.pageCount &&
            artifact.file.inputStream().use(BundleIntegrity::sha256) == manifest.packetPdfSha256) {
            "Stored PDF does not match the exact validated packet"
        }
        return CandidateReviewTicket(manifest, p.source.sha256, p.inputHash)
    }

    /** Prevent build/preparation changes between final revalidation and durable decision write. */
    @Synchronized
    internal fun <T> withCurrentReview(ticket: CandidateReviewTicket, action: () -> T): T {
        require(resolveReview(ticket.manifest.displayId, WorkspaceMode.valueOf(ticket.manifest.mode.name)) == ticket) {
            "Candidate changed. Reopen review before recording a decision."
        }
        return action()
    }

    private fun execute(id: String, mode: WorkspaceMode, action: () -> Unit): BuildWorkflowState {
        val failure = runCatching(action).exceptionOrNull()
        return state(id, mode).copy(error = failure?.message)
    }

    private fun inventoryHash(inventory: Page2Inventory?): String? = when (inventory) {
        is Page2Inventory.LetterWriting -> inventory.inventory.canonicalSha256()
        is Page2Inventory.Telephone -> inventory.inventory.canonicalSha256()
        null -> null
    }

    private fun inventoryFailures(mode: WorkspaceMode, inventory: Page2Inventory?, input: ProductionRenderModelInput): List<String> {
        if (inventory == null) return emptyList() // Front build is independent; Page 2 remains blocked.
        val identity: TerritoryIdentity
        val revision: String
        val authority: String
        val fixture: Boolean
        val failures: List<String>
        when (inventory) {
            is Page2Inventory.LetterWriting -> {
                require(mode == WorkspaceMode.LETTER_WRITING) { "Letter Writing inventory requires Letter Writing mode" }
                val v = inventory.inventory
                identity = v.identity; revision = v.knowledgeBaseRevision; authority = v.assignmentAuthoritySha256; fixture = v.nonFieldFixture
                failures = LetterWritingAddressInventoryValidator.validateForPage2(v).errors
            }
            is Page2Inventory.Telephone -> {
                require(mode == WorkspaceMode.TELEPHONE) { "Telephone inventory requires Telephone mode" }
                val v = inventory.inventory
                identity = v.identity; revision = v.knowledgeBaseRevision; authority = v.assignmentAuthoritySha256; fixture = v.nonFieldFixture
                failures = TelephoneTerritoryInventoryValidator.validateForPage2(v).errors
            }
        }
        return failures + buildList {
            if (fixture) add("Fixture inventories cannot enter a production build")
            if (identity != input.assignment.identity) add("Inventory territory does not match the prepared front")
            if (revision != knowledgeBase.revision) add("Inventory Knowledge Base revision changed")
            if (authority != input.assignment.authoritySha256) add("Inventory assignment authority does not match the prepared front")
        }
    }
}
