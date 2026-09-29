#!/usr/bin/env python3
"""Apply the locally verified Phase 7 label/ancillary repair to the exact successful
reference-repair source. Refuse mixed revisions, altered patches, or partial results."""
from pathlib import Path
import hashlib, json, shutil, subprocess, sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: apply.py <reference-repair-source>")
target=Path(sys.argv[1]).resolve(strict=True)
here=Path(__file__).resolve().parent

def sha(path:Path)->str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

replacements=[
 ("AndroidNativeAuthoringService.kt","app/src/main/java/com/koenterprises/territorycardstudio/AndroidNativeAuthoringService.kt",
  "4209a29f3a839fb40eb71b852117b73514df848224dbb057e97c3a2002bdca10","f4648e366ebb300bdb305e7a356bca09f083652dd225fa967fe4541c503a30af"),
 ("NativeRoadLabelProducer.kt","core/src/main/kotlin/com/koenterprises/territorycardstudio/core/NativeRoadLabelProducer.kt",
  "4fb5731eb41c1bd7befb617b8d139c8bccb8a7f2a84d6d0865ebb856a2d4acfe","2cf0eb57aa4d2e6805aac8199703c59277d6e1c84f5c499fbefddddd47f676e8"),
]
new_files=[
 ("BuildingLabelCoordinateTransform.kt","core/src/main/kotlin/com/koenterprises/territorycardstudio/core/BuildingLabelCoordinateTransform.kt","9335fbdb3dffae7d33fd9133f7c55886c952b60e5f014ae147742742eb660de6"),
 ("NativeBuildingLabelPlacement.kt","core/src/main/kotlin/com/koenterprises/territorycardstudio/core/NativeBuildingLabelPlacement.kt","f0acc0dc2b25925d133365e74d7548c75c5da1662877689bcc5d8abfce0ffa5e"),
 ("NativeBuildingWorkSemantics.kt","core/src/main/kotlin/com/koenterprises/territorycardstudio/core/NativeBuildingWorkSemantics.kt","b29a00a353ca8d6a563fadbfa3704081b48ce834c72395884703a95674c0076b"),
]
patches=[
 ("NativeAuthoringUi.patch","app/src/main/java/com/koenterprises/territorycardstudio/NativeAuthoringUi.kt",
  "db5be2020c5c3ad680e46427ec7d02bd249e3b5659032f26570c007c684e8095","4aa6fe6c0086577636b2b45aefa5a1c7f243446287f30f9cfa0914238cc2d86a","3a90d7907d1596aafdb41a575dbf1c16da201b39332f1654a914a25886ff60bf"),
 ("BuildingValidationEngine.patch","core/src/main/kotlin/com/koenterprises/territorycardstudio/core/BuildingValidationEngine.kt",
  "a9e3eea17c262a302b50dbf7fe886db2668f136ebe46ef2259b408977dc1c207","4f7531fad76a9e07fd447ed51508cb398addf4375131230f570b7cb59988de5a","3f29f5ae8a6e55b500d5afb2ea18e67696d73a86d17e80c1923bbc13fd35cc94"),
 ("CandidatePdfRenderer.patch","core/src/main/kotlin/com/koenterprises/territorycardstudio/core/CandidatePdfRenderer.kt",
  "becb73831a59495390cb6df47f845d0f1b0d3972fa11f6f9022aa26077d13084","80c8d4eebbd4622e80248438a6f2b6918aacaad82fcbbed90704b0ea7f3e1fe5","2e656fb2d6fde5daff00f80ebc440e542d80c0935faecfd3fb1af7b979b4f5fb"),
 ("ProductionRenderModelAdapter.patch","core/src/main/kotlin/com/koenterprises/territorycardstudio/core/ProductionRenderModelAdapter.kt",
  "849a211784f6a12d487b5e80fc4fa5fdbeb79f211c7ee4b1ad592bd06aa2c527","6850789d5c440b2bdf51d0aad98f8e0991866be5c9c5e0d93a356ad6fc3f7659","e8bb0ee80c02724ed9093760920e0f1d80b56f0b73b1757a68ce5dd992201801"),
]

# Verify exact reference-repair baseline before mutation.
for _,rel,before,_ in replacements:
    p=target/rel
    assert p.is_file() and sha(p)==before, f"Replacement baseline changed: {rel}"
for name,rel,before,_,patch_sha in patches:
    p=target/rel; patch=here/name
    assert p.is_file() and sha(p)==before, f"Patch baseline changed: {rel}"
    assert patch.is_file() and sha(patch)==patch_sha, f"Patch bytes changed: {name}"
for name,rel,expected in new_files:
    source=here/name
    assert source.is_file() and sha(source)==expected, f"Overlay source changed: {name}"
    assert not (target/rel).exists(), f"New source unexpectedly exists: {rel}"
for name,rel,_,after in replacements:
    source=here/name
    assert source.is_file() and sha(source)==after, f"Replacement overlay changed: {name}"

# Apply readable patches to four existing files.
for name,_,_,_,_ in patches:
    patch=here/name
    cmd=["git","apply","--recount","--whitespace=error","--directory="+target.name,str(patch)]
    subprocess.run(cmd[:4]+["--check"]+cmd[4:],cwd=target.parent,check=True)
    subprocess.run(cmd,cwd=target.parent,check=True)

# Copy exact replacements/new files.
for name,rel,_,_ in replacements:
    dst=target/rel; dst.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(here/name,dst)
for name,rel,_ in new_files:
    dst=target/rel; dst.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(here/name,dst)

# Verify exact tested result hashes.
verified=0
for _,rel,_,after in replacements:
    assert sha(target/rel)==after, f"Replacement result changed: {rel}"; verified+=1
for _,rel,_,after,_ in patches:
    assert sha(target/rel)==after, f"Patched result changed: {rel}"; verified+=1
for _,rel,after in new_files:
    assert sha(target/rel)==after, f"New result changed: {rel}"; verified+=1
print(json.dumps({"schema":1,"filesVerified":verified,"phase7Complete":False,"approvedRealCards":0,
  "scope":"label placement and explicit ancillary/context semantics software repair"},indent=2))
