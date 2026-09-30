#!/usr/bin/env python3
from pathlib import Path
import base64,zlib,hashlib,subprocess,sys,tempfile,json
PATCH_SHA='9133fad8f16ce09976ff37a2cff48b393016aa649689ee2c68c91d7c05dd3014'
BASE={'core/src/main/kotlin/com/koenterprises/territorycardstudio/core/CandidatePdfRenderer.kt': '80c8d4eebbd4622e80248438a6f2b6918aacaad82fcbbed90704b0ea7f3e1fe5', 'core/src/main/kotlin/com/koenterprises/territorycardstudio/core/PdfArtifactBoundary.kt': '6aad351f123a1d06c97c339f206ae3cafcdea4ee35bf37c70fae25c1edc6ae75', 'core/src/test/kotlin/com/koenterprises/territorycardstudio/core/CandidatePdfRendererTest.kt': 'b911527953a2f4e5e450e898d768a77f8b9cd03f1b2fcd2fb5e6c731a5a429bd', 'app/src/main/java/com/koenterprises/territorycardstudio/AndroidPdfArtifactService.kt': '0636a3215f6d098ac3cef914b9725abee8928fb9756b2cb22c12ef2f8bcb0e49', 'app/src/main/java/com/koenterprises/territorycardstudio/NativeAuthoringUi.kt': '244dade1dfc7a320104be6c22621977da45d2943aa80c7c860ddf8e6269365e0', 'app/src/main/java/com/koenterprises/territorycardstudio/TerritoryCardStudioServices.kt': '71cc4be28719826a316c62135300c484ee4fb5833a2f86e8006e1f3e92fe995c', 'app/src/main/java/com/koenterprises/territorycardstudio/WorkspaceModeUi.kt': 'da53849d67b65dfa245c53ad9c2cd4ca9ff01b058a51f056a538695f8e993f0a', 'app/src/androidTest/java/com/koenterprises/territorycardstudio/SimpleCreate101InstrumentationTest.kt': '425e51a5837e601df2a5f0a8ff9082991445c4a0d27015d103f741ecc646196a', 'app/build.gradle.kts': '63e71224cf196d3710edb89d89f3c860f444350d78cd38b3cbdfb34696a8fc70'}
FIXED={'core/src/main/kotlin/com/koenterprises/territorycardstudio/core/CandidatePdfRenderer.kt': '20747f3e116bac66caac6ea05ff4ff0a15e62af521fc0a9c15bf74fe1e612ac7', 'core/src/main/kotlin/com/koenterprises/territorycardstudio/core/PdfArtifactBoundary.kt': '8edda3afd0ccc9135a93cdca6731b39920b2497add58eab50f1dff2f2ba6cb65', 'core/src/test/kotlin/com/koenterprises/territorycardstudio/core/CandidatePdfRendererTest.kt': 'a3025a3fae3528680f76778390682e48c4b7de551f81f69ebf995ea8d5e23f5a', 'app/src/main/java/com/koenterprises/territorycardstudio/AndroidPdfArtifactService.kt': '30eb04f133eb8c8934d81b72aaada0749a383d2759525aa945aff90e999eea2a', 'app/src/main/java/com/koenterprises/territorycardstudio/NativeAuthoringUi.kt': '838c1c5eac99fa3ecfb4ad7dc7a7ddf27d598d10ac8f14f5380aa9d274f8bd5c', 'app/src/main/java/com/koenterprises/territorycardstudio/TerritoryCardStudioServices.kt': 'bd445e0e9f528b483e100100f85617b4c294160dabb2dca6ca0b6f2e2ece5cc4', 'app/src/main/java/com/koenterprises/territorycardstudio/WorkspaceModeUi.kt': 'a3b9cfd51abaa620b2b39fc4bbce47f31894ee9bc22eae33f43518d77f3bbb27', 'app/src/androidTest/java/com/koenterprises/territorycardstudio/SimpleCreate101InstrumentationTest.kt': '2a13ca9b515bf76adab06b91c79496e17449c49c5b494713739532903108b24b', 'app/build.gradle.kts': '9969616cedb747d56a7e4a014896f1f14b0d2094537dce49bf7379b01dcef99e', 'app/src/main/java/com/koenterprises/territorycardstudio/AndroidPreservedSourceDraftService.kt': '01f85d338c540a02d4a8ff9a65545554420078b58c45879141e5152a94165389'}
def sha(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def main():
    if len(sys.argv)!=2: raise SystemExit("Usage: apply.py <exact-territory-1.3.1-source>")
    root=Path(sys.argv[1]).resolve(strict=True)
    here=Path(__file__).resolve().parent
    for rel,expected in BASE.items():
        assert sha(root/rel)==expected, f"Unexpected 1.3.1 base: {rel}"
    encoded=''.join((here/'chunks'/f'c{i:02}').read_text().strip() for i in range(7))
    patch=zlib.decompress(base64.b64decode(encoded))
    assert hashlib.sha256(patch).hexdigest()==PATCH_SHA
    with tempfile.NamedTemporaryFile(prefix="tcs140-",suffix=".patch",delete=False) as f:
        f.write(patch); name=f.name
    try:
        subprocess.run(["git","apply","--check","--directory="+root.name,name],cwd=root.parent,check=True)
        subprocess.run(["git","apply","--directory="+root.name,name],cwd=root.parent,check=True)
    finally:
        Path(name).unlink(missing_ok=True)
    for rel,expected in FIXED.items():
        assert sha(root/rel)==expected, f"1.4.0 output mismatch: {rel}"
    print(json.dumps({"schema":"territory-standard-engine-140-v1","version":"1.4.0","versionCode":51,"primaryBuilder":"preserve_supplied_map","rawSkeletonizerDefault":False,"fieldReleaseFromDraft":False,"status":"APPLIED"},indent=2))
if __name__=="__main__": main()
