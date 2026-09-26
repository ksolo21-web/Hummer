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
'''import com.koenterprises.territorycardstudio.core.CanonicalFrontBackPdfAssembler
''',
'''import com.koenterprises.territorycardstudio.core.CanonicalFrontBackPdfAssembler
import com.koenterprises.territorycardstudio.core.LetterWritingAddressInventory
import com.koenterprises.territorycardstudio.core.LetterWritingAddressInventoryValidator
''',
"letter-writing android imports")

anchor='''    fun authorizeCandidateRender(receipt: CandidateRenderValidationReceipt): PdfArtifactAuthorization =
        PdfArtifactBoundary.authorizeCandidateRender(knowledgeBase, receipt)
'''
method='''    @Synchronized
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

'''+anchor
rep(android,anchor,method,"letter-writing android assembly method")

rep(smoke,
'''    CanonicalFrontBackPdfTest.run(assets)

    println("PASS core contract smoke test")
''',
'''    CanonicalFrontBackPdfTest.run(assets)
    LetterWritingAddressInventoryRegression.run()

    println("PASS core contract smoke test")
''',
"letter-writing core smoke registration")

print("PHASE1D_B_EXISTING_TRANSFORM=PASS")
