#!/usr/bin/env python3
from pathlib import Path
import base64,hashlib,json,lzma,subprocess,sys,tempfile

PATCH_SHA="70f9376294cbcda20b409067407111cff18ba764cc5c6a409d99f8865af9d1e4"
CHUNK_SHA=[
 "283aad88671916d25219bb9688547cee3114d3f300d57d375621cd977ba62bd2",
 "635cfa9098baecc82a6bf58969adc77f5df63165fb67fd604e16f3e8a38f0b39",
 "42730f3d9655333acd02d7a33f098c8bdbf1b873153dfc7390f78ce4094e560d"
]

def main():
    if len(sys.argv)!=2:
        raise SystemExit("Usage: apply.py <exact-1.0.0-source>")
    root=Path(sys.argv[1]).resolve(strict=True)
    here=Path(__file__).resolve().parent
    chunks=[]
    for i,expected in enumerate(CHUNK_SHA):
        raw=(here/"chunks"/f"c{i:02}").read_text().strip()
        assert hashlib.sha256(raw.encode()).hexdigest()==expected, f"simple-create chunk {i} changed"
        chunks.append(raw)
    encoded="".join(chunks)
    patch=lzma.decompress(base64.b64decode(encoded))
    assert hashlib.sha256(patch).hexdigest()==PATCH_SHA, "simple-create patch changed"
    with tempfile.NamedTemporaryFile(suffix=".patch") as handle:
        handle.write(patch);handle.flush()
        cmd=["git","apply","--recount","--whitespace=error","--directory="+root.name]
        subprocess.run(cmd+["--check",handle.name],cwd=root.parent,check=True)
        subprocess.run(cmd+[handle.name],cwd=root.parent,check=True)
    evidence=root.parent/"evidence";evidence.mkdir(exist_ok=True)
    (evidence/"simple-create-101.patch").write_bytes(patch)
    print(json.dumps({
        "schema":"territory-simple-create-101",
        "patchSha256":PATCH_SHA,
        "version":"1.0.1",
        "phase7Reopened":False,
        "ux":"New Territory -> choose type -> add map -> create -> preview -> save",
        "singleCompleteUserMapCanBeAuthoritative":True
    },indent=2))
if __name__=="__main__":
    main()
