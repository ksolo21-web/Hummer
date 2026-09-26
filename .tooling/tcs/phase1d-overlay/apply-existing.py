#!/usr/bin/env python3
from pathlib import Path
import sys

root=Path(sys.argv[1]).resolve()
android=root/"app/src/main/java/com/koenterprises/territorycardstudio/AndroidPdfArtifactService.kt"
smoke=root/"core/src/test/kotlin/com/koenterprises/territorycardstudio/core/CoreContractSmokeTest.kt"

def rep(path, old, new, label):
    s=path.read_text()
    n=s.count(old)
    if n != 1:
        raise SystemExit(f"{label}: expected 1 match, found {n}")
    path.write_text(s.replace(old,new,1))

rep(android,
'''import com.koenterprises.territorycardstudio.core.CandidatePdfRenderer
''',
'''import com.koenterprises.territorycardstudio.core.CandidatePdfRenderer
import com.koenterprises.territorycardstudio.core.CanonicalAddressBackSpec
import com.koenterprises.territorycardstudio.core.CanonicalFrontBackPdfAssembler
''',
"android imports")

rep(android,
'''data class StoredCandidatePdf(
    val displayId: String,
    val canonicalFilename: String,
    val file: File,
    val sha256: String,
    val renderSpecSha256: String,
    val exactPdfValidationPassed: Boolean
)

class AndroidPdfArtifactService(
''',
'''data class StoredCandidatePdf(
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

class AndroidPdfArtifactService(
''',
"stored front back model")

rep(android,
'''    private val candidateRoot = File(privateRoot, "pdf/candidates-v1").apply {
        require(exists() || mkdirs()) { "Unable to create candidate PDF store" }
    }

''',
'''    private val candidateRoot = File(privateRoot, "pdf/candidates-v1").apply {
        require(exists() || mkdirs()) { "Unable to create candidate PDF store" }
    }

    private val canonicalFrontBackRoot = File(privateRoot, "pdf/canonical-front-back-v1").apply {
        require(exists() || mkdirs()) { "Unable to create canonical front/back PDF store" }
    }

''',
"canonical storage root")

anchor='''    fun authorizeCandidateRender(receipt: CandidateRenderValidationReceipt): PdfArtifactAuthorization =
        PdfArtifactBoundary.authorizeCandidateRender(knowledgeBase, receipt)
'''
method='''    @Synchronized
    fun assembleCanonicalFrontBack(
        front: StoredCandidatePdf,
        frontArtifact: CandidatePdfArtifactReceipt,
        backSpec: CanonicalAddressBackSpec
    ): StoredCanonicalFrontBackPdf {
        require(front.displayId == frontArtifact.displayId && front.displayId == backSpec.identity.displayId) {
            "Canonical front/back identity mismatch"
        }
        require(front.canonicalFilename == frontArtifact.canonicalFilename &&
            front.canonicalFilename == backSpec.identity.canonicalFilename) {
            "Canonical front/back filename mismatch"
        }
        require(front.exactPdfValidationPassed) { "Canonical front/back requires an exact-validated front candidate" }
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

'''+anchor
rep(android,anchor,method,"android front back method")

rep(smoke,
'''    ProductionRenderModelAdapterTest.run(kb, assets)

    println("PASS core contract smoke test")
''',
'''    ProductionRenderModelAdapterTest.run(kb, assets)
    CanonicalFrontBackPdfTest.run(assets)

    println("PASS core contract smoke test")
''',
"core smoke phase1d registration")

print("PHASE1D_EXISTING_TRANSFORM=PASS")
