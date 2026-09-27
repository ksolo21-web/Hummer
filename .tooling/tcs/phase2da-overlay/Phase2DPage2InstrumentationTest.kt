package com.koenterprises.territorycardstudio

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.koenterprises.territorycardstudio.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Isolated synthetic 998/999 assignments only; never commissions a real territory. */
@RunWith(AndroidJUnit4::class)
class Phase2DPage2InstrumentationTest {
    private val stamp = "2026-09-27T01:00:00Z"
    private val authority = "a".repeat(64)

    private data class Fixture(
        val service: AndroidPdfArtifactService,
        val front: StoredCandidatePdf,
        val receipt: CandidatePdfArtifactReceipt,
        val identity: TerritoryIdentity,
        val revision: String,
        val root: File
    )

    private fun fixture(telephone: Boolean): Fixture {
        val app = ApplicationProvider.getApplicationContext<TerritoryCardStudioApplication>()
        val original = app.services.knowledgeBase
        val identity = TerritoryIdentity(if (telephone) 998 else 999,
            if (telephone) TerritoryClass.Telephone else TerritoryClass.Residential, 'a')
        val originalSlot = original.assignments.getValue("273")
        val synthetic = originalSlot.copy(
            status = "needs_new_card", displayId = identity.displayId, identity = identity,
            baseNumber = identity.baseNumber, cardClass = identity.territoryClass.token,
            suffix = "a", slot = identity.displayId, canonicalFilename = identity.canonicalFilename,
            needsNewCard = true, newCardApproved = false, fieldReleaseAllowedForExactArtifact = false,
            legacyReferenceFile = "SYNTHETIC-NOT-FOR-FIELD-USE"
        )
        val kb = original.copy(assignments = original.assignments - "273" + (identity.displayId to synthetic))
        val root = File(app.cacheDir, "phase2da-test-" + identity.displayId)
        root.deleteRecursively()
        check(root.mkdirs())
        val template = app.assets.open("territory/render-authority/Canonical-New-Designed-Template-R48.pdf").use { it.readBytes() }
        val service = AndroidPdfArtifactService(kb, root, template)
        val validation = CandidateRenderValidationReceipt(
            identity.displayId, identity.canonicalFilename, kb.revision,
            "current_authoritative_assignment", authority, true, true, true, true, true,
            LockedPdfRendererAuthorityHashes.STYLE_TOKENS_SHA256,
            LockedPdfRendererAuthorityHashes.TEMPLATE_PDF_SHA256,
            LockedPdfRendererAuthorityHashes.RENDERER_SHA256,
            LockedPdfRendererAuthorityHashes.CONTRACT_SHA256,
            LockedPdfRendererAuthorityHashes.DIRECTIONS_BADGE_SHA256
        )
        val spec = CandidatePdfRenderSpec(
            identity = identity, locality = "Synthetic test", updated = "9/27/2026",
            directionsLines = listOf("SYNTHETIC TEST ONLY", "NOT FOR FIELD USE."),
            layoutMode = "full_map", sourceMasterLabel = "SYNTHETIC-NOT-FOR-FIELD-USE",
            roads = listOf(PdfRoadStroke("test-road", "Example Rd", 225.0, 200.0, 650.0, 200.0,
                PdfRoadStatus.WORK_BOTH_SIDES)),
            labels = listOf(PdfStreetLabel("test-label", "test-road", "Example Rd", 330.0, 193.0, assignedRoadGapPx = 3.0)),
            buildings = emptyList(), nonFieldFixture = false
        )
        val front = service.renderCandidate(spec, validation)
        return Fixture(service, front, CandidatePdfArtifactReceipt(identity.displayId,
            identity.canonicalFilename, front.sha256, validation), identity, kb.revision, root)
    }

    private fun letter(f: Fixture) = LetterWritingAddressInventory(
        identity = f.identity, assignmentAuthoritySha256 = authority,
        knowledgeBaseRevision = f.revision, sourceTruthPassed = true,
        provenance = listOf(LetterWritingAddressProvenance("test", "Synthetic authorized source",
            "USER_AUTHORIZED_FIXTURE", stamp, "b".repeat(64), true)),
        records = listOf(LetterWritingAddressRecord("address-1", f.identity.displayId, "100 Example Way",
            verificationStatus = LetterWritingVerificationStatus.VERIFIED, verifiedAtUtc = stamp,
            boundaryStatus = LetterWritingBoundaryStatus.INSIDE_LOCKED_WORKING_AREA,
            boundaryEvidenceSha256 = "c".repeat(64), provenanceIds = listOf("test")))
    )

    private fun telephone(f: Fixture): TelephoneTerritoryInventory {
        val record = TelephoneTerritoryRecord("phone-1", f.identity.displayId, "100 Example Way",
            addressVerificationStatus = TelephoneRecordVerificationStatus.VERIFIED,
            addressVerifiedAtUtc = stamp, boundaryStatus = TelephoneBoundaryStatus.INSIDE_LOCKED_WORKING_AREA,
            boundaryEvidenceSha256 = "c".repeat(64), addressProvenanceIds = listOf("test"),
            phoneState = TelephoneNumberState.VERIFIED_NUMBER, phoneNumber = "2025550101",
            phoneVerifiedAtUtc = stamp, phoneRecordBindingVerified = true, phoneProvenanceIds = listOf("test"))
        return TelephoneTerritoryInventory(f.identity, authority, f.revision, true,
            listOf(TelephoneProvenance("test", "Synthetic authorized source", "USER_AUTHORIZED_FIXTURE",
                stamp, "b".repeat(64), TelephoneSourceAuthorization.USER_PROVIDED, true, true)),
            listOf(record, record.copy(recordId = "phone-2", streetAddress = "101 Example Way",
                phoneState = TelephoneNumberState.UNAVAILABLE, phoneNumber = null)))
    }

    private fun build(f: Fixture, inventory: Page2Inventory,
        front: StoredCandidatePdf = f.front, receipt: CandidatePdfArtifactReceipt = f.receipt) =
        f.service.generatePage2(front, receipt, inventory, "Synthetic test", "9/27/2026")

    private fun expectBlocked(f: Fixture, existing: ByteArray, action: () -> Unit) {
        val error = runCatching(action).exceptionOrNull()
        assertTrue("Expected fail-closed validation, got $error", error is IllegalArgumentException)
        val packet = File(f.root, "pdf/canonical-front-back-v1/" + f.identity.canonicalFilename)
        assertArrayEquals("Rejected request replaced prior packet", existing, packet.readBytes())
    }

    @Test fun letterWritingUsesFrozenAssemblerAndRejectsStaleOrMismatchedInputs() {
        val f = fixture(false)
        try {
            val inventory = letter(f)
            val first = build(f, Page2Inventory.LetterWriting(inventory))
            val bytes = first.packet.file.readBytes()
            val second = build(f, Page2Inventory.LetterWriting(inventory))
            assertEquals(first.packet.sha256, second.packet.sha256)
            assertEquals(2, first.packet.pageCount)
            assertEquals(inventory.canonicalSha256(), first.inventorySha256)
            assertEquals(PdfFieldReleaseState.AWAITING_EXPLICIT_USER_APPROVAL, first.fieldReleaseState)
            assertArrayEquals(f.front.file.readBytes(), bytes.copyOfRange(0, f.front.file.length().toInt()))
            val expected = CanonicalFrontBackPdfAssembler.assembleCandidate(f.front.file.readBytes(), f.receipt,
                LetterWritingAddressInventoryValidator.toCanonicalBackSpec(inventory, "Synthetic test", "9/27/2026"))
            assertArrayEquals(expected.pdfBytes, bytes)

            val bad = listOf(inventory.copy(nonFieldFixture = true),
                inventory.copy(knowledgeBaseRevision = "stale"),
                inventory.copy(assignmentAuthoritySha256 = "d".repeat(64)),
                inventory.copy(identity = TerritoryIdentity(997)),
                inventory.copy(sourceTruthPassed = false),
                inventory.copy(records = inventory.records + inventory.records.first()),
                inventory.copy(records = (1..241).map { inventory.records.first().copy(recordId = "overflow-$it", streetAddress = "$it Overflow Rd") }))
            bad.forEach { candidate -> expectBlocked(f, bytes) { build(f, Page2Inventory.LetterWriting(candidate)) } }
            expectBlocked(f, bytes) { build(f, Page2Inventory.Telephone(telephone(f))) }
            expectBlocked(f, bytes) { build(f, Page2Inventory.LetterWriting(inventory), receipt = f.receipt.copy(pdfSha256 = "e".repeat(64))) }
            expectBlocked(f, bytes) { build(f, Page2Inventory.LetterWriting(inventory), receipt = f.receipt.copy(
                renderValidation = f.receipt.renderValidation.copy(sourceTruthPassed = false))) }
            val external = File(f.root, "external.pdf").apply { writeBytes(f.front.file.readBytes()) }
            expectBlocked(f, bytes) { build(f, Page2Inventory.LetterWriting(inventory), front = f.front.copy(file = external)) }
            f.front.file.appendText("tamper")
            expectBlocked(f, bytes) { build(f, Page2Inventory.LetterWriting(inventory)) }
        } finally { f.root.deleteRecursively() }
    }

    @Test fun telephoneUsesFrozenPhonePageAndKeepsUnavailableNumbersExplicit() {
        val f = fixture(true)
        try {
            val inventory = telephone(f)
            val result = build(f, Page2Inventory.Telephone(inventory))
            val bytes = result.packet.file.readBytes()
            val expected = CanonicalFrontBackPdfAssembler.assembleCandidate(f.front.file.readBytes(), f.receipt,
                TelephoneTerritoryInventoryValidator.toCanonicalBackSpec(inventory, "Synthetic test", "9/27/2026"))
            assertArrayEquals(expected.pdfBytes, bytes)
            assertEquals(listOf(TerritoryPdfPageRole.FRONT_MAP, TerritoryPdfPageRole.BACK_PHONE_LIST), expected.validation.pageRoles)
            assertTrue(bytes.toString(Charsets.ISO_8859_1).contains("UNAVAILABLE"))
            assertEquals(result.packet.sha256, build(f, Page2Inventory.Telephone(inventory)).packet.sha256)
            assertEquals(inventory.canonicalSha256(), result.inventorySha256)
            assertEquals(PdfFieldReleaseState.AWAITING_EXPLICIT_USER_APPROVAL, result.fieldReleaseState)
            val bad = listOf(inventory.copy(nonFieldFixture = true),
                inventory.copy(knowledgeBaseRevision = "stale"),
                inventory.copy(assignmentAuthoritySha256 = "d".repeat(64)),
                inventory.copy(identity = TerritoryIdentity(997, TerritoryClass.Telephone)),
                inventory.copy(provenance = inventory.provenance.map { it.copy(telephoneUseAuthorized = false) }),
                inventory.copy(records = inventory.records.map { it.copy(phoneRecordBindingVerified = false) }),
                inventory.copy(records = inventory.records + inventory.records.first().copy(recordId = "duplicate")),
                inventory.copy(records = inventory.records.map { it.copy(boundaryStatus = TelephoneBoundaryStatus.AMBIGUOUS) }))
            bad.forEach { candidate -> expectBlocked(f, bytes) { build(f, Page2Inventory.Telephone(candidate)) } }
            expectBlocked(f, bytes) { build(f, Page2Inventory.LetterWriting(letter(f))) }
        } finally { f.root.deleteRecursively() }
    }
}
