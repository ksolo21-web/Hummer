#!/usr/bin/env python3
"""Apply the verified Phase 7 label/ancillary continuation to the exact reference-repair source.

This follow-on never changes installed territory authority assets or marks a real card approved.
The prior recovery chain establishes the exact source baseline; this script additionally verifies
every file it changes and the compressed patch transport before applying it.
"""
from __future__ import annotations
import base64, gzip, hashlib, json, subprocess, sys, tempfile
from pathlib import Path

PATCH_SHA = "56cb00a489d10d1e14df0fb861452debf0961f88a7354fb85e43c4c190263a56"
B64_SHA = "5ae0df4af366c867a8bf11a61b1545166ee22c8c9a8084166150b8d3653646dc"
CHUNK_SHA = ['8b57d823e7172ce4ef7584b5c747c994c5e540fbf0865280c31692c67da764db', '6e7c257950aebfc5062dd98b575fb885fba899cdfe5c06c920e7fe47f9559bf6', '21a1fe1aa56359860c3051a93c27259c4f5559aed2a18c88bd1ebe5ee386ddce', 'efc2ce3b2ffaaceed38f0ac6dba71a8214dbae4806369aacd78f25f08a5bee6b', 'eca6252524bff4205064a8235c37725510dea08f3ac76dc0909eb8561e2c05a5', 'c2f28242904839c3b502fed540a9f839e854a71ce2d9616f630e6b83ad5a3bc7', '7b5f7d9ef7f29376d21369aeeadda5630e5cdbb8a7c2685077d8702a43ab6341', '7deadea5eaaa4efbb2457ccde65dc03962c90fcd39d2bd6718f746b02fcefd98', '2409a138450ada587193d72ba06edc7193bdfb26598cad11e9d88f87ef523d86', '6059fdbb1215d002151f6564cae2bb506601cd568007e9bf1651eb8320663769', '429a84d59a6ecbb227de4133460af644a5b33cd8c6a5424256ed74b0b8df0994', 'cd4ff50416630b73200d7db902720bb9622b57952b6acf6ab88b89f260dc4d82', '6396006e1af8112871170b8e18c860da7aa5992b075debce798f729e3049c671', 'c1701036a6baa675fe1f6da5d7374bb90475c7135d9ddfd33128de7aeaab04c9', 'b1c5c83899e90737cd6f2986ea8052cbedc9892adcf47a1a9a302838035e6f3c', '92c3403c1f1432cbbe8e3c7c34f4df0cb19936e0008b769717605352d6bd0edd']
FILES = [('app/src/androidTest/java/com/koenterprises/territorycardstudio/Phase7AncillaryUiInstrumentationTest.kt', None, '62498998ad4b270dbec737da54f5f4c90bde319f56453669e1033acafac5a2e5'), ('app/src/main/java/com/koenterprises/territorycardstudio/AndroidNativeAuthoringService.kt', '4209a29f3a839fb40eb71b852117b73514df848224dbb057e97c3a2002bdca10', 'f4648e366ebb300bdb305e7a356bca09f083652dd225fa967fe4541c503a30af'), ('app/src/main/java/com/koenterprises/territorycardstudio/NativeAuthoringUi.kt', 'db5be2020c5c3ad680e46427ec7d02bd249e3b5659032f26570c007c684e8095', '4aa6fe6c0086577636b2b45aefa5a1c7f243446287f30f9cfa0914238cc2d86a'), ('core/src/main/kotlin/com/koenterprises/territorycardstudio/core/BuildingLabelCoordinateTransform.kt', None, '9335fbdb3dffae7d33fd9133f7c55886c952b60e5f014ae147742742eb660de6'), ('core/src/main/kotlin/com/koenterprises/territorycardstudio/core/BuildingValidationEngine.kt', 'a9e3eea17c262a302b50dbf7fe886db2668f136ebe46ef2259b408977dc1c207', '4f7531fad76a9e07fd447ed51508cb398addf4375131230f570b7cb59988de5a'), ('core/src/main/kotlin/com/koenterprises/territorycardstudio/core/CandidatePdfRenderer.kt', 'becb73831a59495390cb6df47f845d0f1b0d3972fa11f6f9022aa26077d13084', '80c8d4eebbd4622e80248438a6f2b6918aacaad82fcbbed90704b0ea7f3e1fe5'), ('core/src/main/kotlin/com/koenterprises/territorycardstudio/core/NativeBuildingLabelPlacement.kt', None, 'f0acc0dc2b25925d133365e74d7548c75c5da1662877689bcc5d8abfce0ffa5e'), ('core/src/main/kotlin/com/koenterprises/territorycardstudio/core/NativeBuildingWorkSemantics.kt', None, 'b29a00a353ca8d6a563fadbfa3704081b48ce834c72395884703a95674c0076b'), ('core/src/main/kotlin/com/koenterprises/territorycardstudio/core/NativeRoadLabelProducer.kt', '4fb5731eb41c1bd7befb617b8d139c8bccb8a7f2a84d6d0865ebb856a2d4acfe', '2cf0eb57aa4d2e6805aac8199703c59277d6e1c84f5c499fbefddddd47f676e8'), ('core/src/main/kotlin/com/koenterprises/territorycardstudio/core/ProductionRenderModelAdapter.kt', '849a211784f6a12d487b5e80fc4fa5fdbeb79f211c7ee4b1ad592bd06aa2c527', '6850789d5c440b2bdf51d0aad98f8e0991866be5c9c5e0d93a356ad6fc3f7659'), ('core/src/test/kotlin/com/koenterprises/territorycardstudio/core/Phase7AncillaryContractTest.kt', None, 'b005eb6521c94b3fbae46c9bdfc485e10eb39691b3c98546b26554821798b2f0'), ('core/src/test/kotlin/com/koenterprises/territorycardstudio/core/Phase7AncillaryRegression.kt', None, 'fbec69e6791b9c345a5bb49d18aa8cc9c20678ab4e345a820db830fe1956c891'), ('core/src/test/kotlin/com/koenterprises/territorycardstudio/core/Phase7BuildingPlacementJUnitTest.kt', None, '9f0f67b381539720adc779f97f896e5ef4d768387ab5a7f87c256d07c0fa4d73'), ('core/src/test/kotlin/com/koenterprises/territorycardstudio/core/Phase7BuildingPlacementRegression.kt', None, '7b8d5cff69ad8077e89fcae2fd5470a030341e48f23504303b734b99ae37317d'), ('core/src/test/resources/phase7/a265-current-footprint-draft.json', None, '2c7033dbc98e82e15a1109d9cfe9dab1e9897003f204e9a4e8518e410cb55da3')]

def sha_bytes(b: bytes) -> str: return hashlib.sha256(b).hexdigest()
def sha_file(p: Path) -> str: return sha_bytes(p.read_bytes())

def main() -> None:
    if len(sys.argv) != 2: raise SystemExit("Usage: apply.py <fully integrated source directory>")
    target=Path(sys.argv[1]).resolve(strict=True)
    here=Path(__file__).resolve().parent
    chunks=[]
    for i,expected in enumerate(CHUNK_SHA):
        p=here/'chunks'/f'c{i:02d}'
        data=p.read_bytes()
        assert sha_bytes(data)==expected, f"Continuation transport chunk changed: {p.name}"
        chunks.append(data)
    encoded=b''.join(chunks)
    assert sha_bytes(encoded)==B64_SHA, "Continuation base64 transport changed"
    patch=gzip.decompress(base64.b64decode(encoded,validate=True))
    assert sha_bytes(patch)==PATCH_SHA, "Continuation patch changed"
    for name,before,after in FILES:
        p=(target/name).resolve(); assert target in p.parents
        if before is None: assert not p.exists(), f"New continuation source already exists: {name}"
        else: assert p.is_file() and sha_file(p)==before, f"Continuation baseline changed: {name}"
    with tempfile.NamedTemporaryFile(suffix='.patch') as tmp:
        tmp.write(patch); tmp.flush()
        command=['git','apply','--recount','--whitespace=error','--directory='+target.name]
        subprocess.run(command+['--check',tmp.name],cwd=target.parent,check=True)
        subprocess.run(command+[tmp.name],cwd=target.parent,check=True)
    for name,before,after in FILES:
        p=target/name
        assert p.is_file() and sha_file(p)==after, f"Continuation output mismatch: {name}"
    print(json.dumps({'schema':1,'filesVerified':len(FILES),'patchSha256':PATCH_SHA,'phase7Complete':False,'approvedRealCards':0,'scope':'label and ancillary component repair'},indent=2))

if __name__=='__main__': main()
