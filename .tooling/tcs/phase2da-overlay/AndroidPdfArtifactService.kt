package com.koenterprises.territorycardstudio

import com.koenterprises.territorycardstudio.core.BundleIntegrity
import com.koenterprises.territorycardstudio.core.CandidatePdfArtifactReceipt
import com.koenterprises.territorycardstudio.core.CandidateReleaseReview
import com.koenterprises.territorycardstudio.core.CandidatePdfRenderResult
import com.koenterprises.territorycardstudio.core.CandidatePdfRenderSpec
import com.koenterprises.territorycardstudio.core.CandidatePdfRenderer
import com.koenterprises.territorycardstudio.core.CanonicalAddressBackSpec
import com.koenterprises.territorycardstudio.core.CanonicalFrontBackPdfAssembler
import com.koenterprises.territorycardstudio.core.LetterWritingAddressInventory
import com.koenterprises.territorycardstudio.core.LetterWritingAddressInventoryValidator
import com.koenterprises.territorycardstudio.core.CandidateRenderValidationReceipt
import com.koenterprises.territorycardstudio.core.ExactApprovedPdfPassthrough
import com.koenterprises.territorycardstudio.core.PdfArtifactAction
import com.koenterprises.territorycardstudio.core.PdfArtifactAuthorization
import com.koenterprises.territorycardstudio.core.PdfArtifactBoundary
import com.koenterprises.territorycardstudio.core.TerritoryKnowledgeBase
import com.koenterprises.territorycardstudio.core.TelephoneTerritoryInventory
import com.koenterprises.territorycardstudio.core.TelephoneTerritoryInventoryValidator
import com.koenterprises.territorycardstudio.core.TerritoryIdentity
import com.koenterprises.territorycardstudio.core.TerritoryClass
import com.koenterprises.territorycardstudio.core.PdfFieldReleaseState
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class StoredExactApprovedPdf(
    val displayId: String,
    val canonicalFilename: String,
    val file: File,
    val sha256: String
)

data class StoredCandidatePdf(
    val displayId: String,
    val canonicalFilename: String,
    val file: File,
    val sha256: String,
    val renderSpecSha256: String,
    val exactPdfValidationPassed: Boolean
)

data class StoredCanonicalFrontBackPdf(
    val displayId: String,
    val canonicalFilename: String,
    val file: File,
    val sha256: String,
    val frontPdfSha256: String,
    val backSpecSha256: String,
    val pageCount: Int
)

sealed class Page2Inventory {
    data class LetterWriting(val inventory: LetterWritingAddressInventory) : Page2Inventory()
    data class Telephone(val inventory: TelephoneTerritoryInventory) : Page2Inventory()
}

data class GeneratedPage2Candidate(
    val packet: StoredCanonicalFrontBackPdf,
    val inventorySha256: String,
    val fieldReleaseState: PdfFieldReleaseState = PdfFieldReleaseState.AWAITING_EXPLICIT_USER_APPROVAL
)

class AndroidPdfArtifactService(
    private val knowledgeBase: TerritoryKnowledgeBase,
    private val privateRoot: File,
    private val lockedTemplatePdf: ByteArray
) {
    private val exactApprovedRoot = File(privateRoot, "pdf/exact-approved-v1").apply {
        require(exists() || mkdirs()) { "Unable to create exact-approved PDF store" }
    }

    private val candidateRoot = File(privateRoot, "pdf/candidates-v1").apply {
        require(exists() || mkdirs()) { "Unable to create candidate PDF store" }
    }

    private val canonicalFrontBackRoot = File(privateRoot, "pdf/canonical-front-back-v1").apply {
        require(exists() || mkdirs()) { "Unable to create canonical front/back PDF store" }
    }

    @Synchronized
    fun importExactApproved(
        displayId: String,
        input: InputStream
    ): StoredExactApprovedPdf {
        val assignment = knowledgeBase.assignments[displayId] ?: error("Unknown territory assignment: $displayId")
        val tmp = File(exactApprovedRoot, ".${assignment.canonicalFilename}.tmp")
        tmp.delete()
        try {
            FileOutputStream(tmp).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
            val sha = tmp.inputStream().use(BundleIntegrity::sha256)
            val authorization = PdfArtifactBoundary.authorizeExactApprovedPassthrough(
                knowledgeBase,
                displayId,
                assignment.canonicalFilename,
                sha
            )
            require(authorization.action == PdfArtifactAction.EXACT_APPROVED_PASSTHROUGH) { authorization.reason }

            // Re-copy through the byte-verifier rather than trusting only the first hash pass.
            val verifiedTmp = File(exactApprovedRoot, ".${assignment.canonicalFilename}.verified.tmp")
            verifiedTmp.delete()
            try {
                tmp.inputStream().use { verifiedInput ->
                    FileOutputStream(verifiedTmp).use { output ->
                        ExactApprovedPdfPassthrough.copyVerified(authorization, verifiedInput, output)
                        output.fd.sync()
                    }
                }
                val target = File(exactApprovedRoot, assignment.canonicalFilename)
                atomicReplace(verifiedTmp, target)
                val finalSha = target.inputStream().use(BundleIntegrity::sha256)
                require(finalSha == assignment.referenceSha256) { "Stored exact-approved PDF hash drift" }
                return StoredExactApprovedPdf(displayId, assignment.canonicalFilename, target, finalSha)
            } finally {
                verifiedTmp.delete()
            }
        } finally {
            tmp.delete()
        }
    }

    @Synchronized
    fun resolveExactApproved(displayId: String): StoredExactApprovedPdf? {
        val assignment = knowledgeBase.assignments[displayId] ?: return null
        val file = File(exactApprovedRoot, assignment.canonicalFilename)
        if (!file.isFile) return null
        val sha = file.inputStream().use(BundleIntegrity::sha256)
        val authorization = PdfArtifactBoundary.authorizeExactApprovedPassthrough(
            knowledgeBase,
            displayId,
            assignment.canonicalFilename,
            sha
        )
        require(authorization.action == PdfArtifactAction.EXACT_APPROVED_PASSTHROUGH) { authorization.reason }
        return StoredExactApprovedPdf(displayId, assignment.canonicalFilename, file, sha)
    }

    @Synchronized
    fun renderCandidate(
        spec: CandidatePdfRenderSpec,
        receipt: CandidateRenderValidationReceipt
    ): StoredCandidatePdf {
        val authorization = PdfArtifactBoundary.authorizeCandidateRender(knowledgeBase, receipt)
        require(authorization.action == PdfArtifactAction.VALIDATED_CANDIDATE_RENDER && authorization.renderAllowed) {
            authorization.reason
        }
        require(spec.identity.displayId == receipt.displayId) { "Candidate render spec/receipt identity mismatch" }
        require(spec.identity.canonicalFilename == receipt.canonicalFilename) { "Candidate render spec/receipt filename mismatch" }
        require(!spec.nonFieldFixture) { "Fixture-only render specs cannot enter app candidate storage" }

        val result: CandidatePdfRenderResult = CandidatePdfRenderer.renderCandidate(lockedTemplatePdf, authorization, spec)
        require(result.exactValidation.passed) { "Generated candidate failed exact internal PDF validation" }
        val target = File(candidateRoot, receipt.canonicalFilename)
        val tmp = File(candidateRoot, ".${receipt.canonicalFilename}.tmp")
        tmp.delete()
        try {
            FileOutputStream(tmp).use { output ->
                output.write(result.pdfBytes)
                output.fd.sync()
            }
            val tmpSha = tmp.inputStream().use(BundleIntegrity::sha256)
            require(tmpSha == result.pdfSha256) { "Candidate PDF write hash mismatch" }
            atomicReplace(tmp, target)
            val finalSha = target.inputStream().use(BundleIntegrity::sha256)
            require(finalSha == result.pdfSha256) { "Stored candidate PDF hash drift" }
            return StoredCandidatePdf(
                displayId = receipt.displayId,
                canonicalFilename = receipt.canonicalFilename,
                file = target,
                sha256 = finalSha,
                renderSpecSha256 = result.renderSpecSha256,
                exactPdfValidationPassed = true
            )
        } finally {
            tmp.delete()
        }
    }

    @Synchronized
    fun assembleCanonicalFrontBack(
        front: StoredCandidatePdf,
        frontArtifact: CandidatePdfArtifactReceipt,
        backSpec: CanonicalAddressBackSpec
    ): StoredCanonicalFrontBackPdf {
        val authorization = authorizeCandidateRender(frontArtifact.renderValidation)
        require(authorization.renderAllowed) { authorization.reason }
        require(frontArtifact.renderValidation.displayId == front.displayId &&
            frontArtifact.renderValidation.canonicalFilename == front.canonicalFilename) {
            "Front artifact/render validation identity mismatch"
        }
        require(front.displayId == frontArtifact.displayId && front.displayId == backSpec.identity.displayId) {
            "Canonical front/back identity mismatch"
        }
        require(front.canonicalFilename == frontArtifact.canonicalFilename &&
            front.canonicalFilename == backSpec.identity.canonicalFilename) {
            "Canonical front/back filename mismatch"
        }
        require(front.exactPdfValidationPassed) { "Canonical front/back requires an exact-validated front candidate" }
        require(front.file.canonicalFile == File(candidateRoot, front.canonicalFilename).canonicalFile) {
            "Canonical front/back requires the app-owned candidate front"
        }
        val storedFrontSha = front.file.inputStream().use(BundleIntegrity::sha256)
        require(storedFrontSha == front.sha256 && storedFrontSha == frontArtifact.pdfSha256) {
            "Stored front PDF hash does not match canonical front artifact receipt"
        }
        val result = CanonicalFrontBackPdfAssembler.assembleCandidate(front.file.readBytes(), frontArtifact, backSpec)
        require(result.validation.passed && result.validation.pageCount == 2) {
            "Canonical front/back PDF did not pass exact two-page semantic validation"
        }
        val target = File(canonicalFrontBackRoot, front.canonicalFilename)
        val tmp = File(canonicalFrontBackRoot, ".${front.canonicalFilename}.tmp")
        tmp.delete()
        try {
            FileOutputStream(tmp).use { output ->
                output.write(result.pdfBytes)
                output.fd.sync()
            }
            val tmpSha = tmp.inputStream().use(BundleIntegrity::sha256)
            require(tmpSha == result.pdfSha256) { "Canonical front/back write hash mismatch" }
            atomicReplace(tmp, target)
            val finalSha = target.inputStream().use(BundleIntegrity::sha256)
            require(finalSha == result.pdfSha256) { "Stored canonical front/back PDF hash drift" }
            return StoredCanonicalFrontBackPdf(
                displayId = front.displayId,
                canonicalFilename = front.canonicalFilename,
                file = target,
                sha256 = finalSha,
                frontPdfSha256 = front.sha256,
                backSpecSha256 = result.backSpecSha256,
                pageCount = result.validation.pageCount
            )
        } finally {
            tmp.delete()
        }
    }

    @Synchronized
    fun assembleLetterWritingCanonicalFrontBack(
        front: StoredCandidatePdf,
        frontArtifact: CandidatePdfArtifactReceipt,
        inventory: LetterWritingAddressInventory,
        locality: String,
        updated: String
    ): StoredCanonicalFrontBackPdf {
        require(!inventory.nonFieldFixture) {
            "Android production Letter Writing Page 2 cannot use fixture-only inventory"
        }
        require(inventory.identity.territoryClass !in setOf(TerritoryClass.Telephone, TerritoryClass.TelephoneApartment)) {
            "Telephone territories require the Telephone Page 2 workflow"
        }
        validateInventoryBinding(front, frontArtifact, inventory.identity,
            inventory.knowledgeBaseRevision, inventory.assignmentAuthoritySha256)
        require(inventory.identity.displayId == front.displayId) {
            "Letter Writing inventory/front territory identity mismatch"
        }
        require(inventory.identity.canonicalFilename == front.canonicalFilename) {
            "Letter Writing inventory/front canonical filename mismatch"
        }
        val backSpec = LetterWritingAddressInventoryValidator.toCanonicalBackSpec(
            inventory = inventory,
            locality = locality,
            updated = updated
        )
        return assembleCanonicalFrontBack(front, frontArtifact, backSpec)
    }

    @Synchronized
    fun assembleTelephoneCanonicalFrontBack(
        front: StoredCandidatePdf,
        frontArtifact: CandidatePdfArtifactReceipt,
        inventory: TelephoneTerritoryInventory,
        locality: String,
        updated: String
    ): StoredCanonicalFrontBackPdf {
        require(!inventory.nonFieldFixture) {
            "Android production Telephone Page 2 cannot use fixture-only inventory"
        }
        require(inventory.identity.territoryClass in setOf(TerritoryClass.Telephone, TerritoryClass.TelephoneApartment)) {
            "Telephone Page 2 requires a Telephone territory identity"
        }
        validateInventoryBinding(front, frontArtifact, inventory.identity,
            inventory.knowledgeBaseRevision, inventory.assignmentAuthoritySha256)
        val backSpec = TelephoneTerritoryInventoryValidator.toCanonicalBackSpec(inventory, locality, updated)
        return assembleCanonicalFrontBack(front, frontArtifact, backSpec)
    }

    /** Generates a candidate packet only. Approval and field export are separate gates. */
    @Synchronized
    fun generatePage2(
        front: StoredCandidatePdf,
        frontArtifact: CandidatePdfArtifactReceipt,
        inventory: Page2Inventory,
        locality: String,
        updated: String
    ): GeneratedPage2Candidate = when (inventory) {
        is Page2Inventory.LetterWriting -> GeneratedPage2Candidate(
            assembleLetterWritingCanonicalFrontBack(front, frontArtifact, inventory.inventory, locality, updated),
            inventory.inventory.canonicalSha256()
        )
        is Page2Inventory.Telephone -> GeneratedPage2Candidate(
            assembleTelephoneCanonicalFrontBack(front, frontArtifact, inventory.inventory, locality, updated),
            inventory.inventory.canonicalSha256()
        )
    }

    private fun validateInventoryBinding(
        front: StoredCandidatePdf,
        artifact: CandidatePdfArtifactReceipt,
        identity: TerritoryIdentity,
        knowledgeBaseRevision: String,
        assignmentAuthoritySha256: String
    ) {
        require(identity.displayId == front.displayId && identity.canonicalFilename == front.canonicalFilename) {
            "Page 2 inventory/front territory identity mismatch"
        }
        require(knowledgeBaseRevision == knowledgeBase.revision &&
            knowledgeBaseRevision == artifact.renderValidation.knowledgeBaseRevision) {
            "Page 2 inventory/front Knowledge Base revision mismatch"
        }
        require(assignmentAuthoritySha256 == artifact.renderValidation.assignmentAuthoritySha256) {
            "Page 2 inventory/front assignment authority mismatch"
        }
    }

    fun authorizeCandidateRender(receipt: CandidateRenderValidationReceipt): PdfArtifactAuthorization =
        PdfArtifactBoundary.authorizeCandidateRender(knowledgeBase, receipt)

    fun authorizeCandidateRelease(
        artifact: CandidatePdfArtifactReceipt,
        review: CandidateReleaseReview
    ): PdfArtifactAuthorization = PdfArtifactBoundary.authorizeCandidateRelease(knowledgeBase, artifact, review)

    private fun atomicReplace(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
