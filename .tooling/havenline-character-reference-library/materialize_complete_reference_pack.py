#!/usr/bin/env python3
"""Materialize HAVENLINE's complete approved character-reference pack.

Binary images are stored as checksum-pinned Base64 payloads because the repository
connector writes text files. This script reconstructs the visible WebP references
that Codex and Unity-side tooling should inspect.
"""
from __future__ import annotations
import argparse, base64, hashlib, json
from pathlib import Path

SPECS = [
    {"key":"asset01","path":"Character1/Expressions.webp","parts":1,"bytes":64776,"sha256":"66ce78787130f539beb862a52d62cca1ab5bd1bd612919e8f1ce6ef4808f85e9","width":1000,"height":708},
    {"key":"asset02","path":"Character1/Turnaround.webp","parts":1,"bytes":48474,"sha256":"ad6abd12a49810832610c4f7bacd4d7a49a1eb006f3fd1263fdce16d11a964ba","width":1000,"height":708},
    {"key":"asset03","path":"Character2/Expressions.webp","parts":1,"bytes":63554,"sha256":"79f913a4d54529af1bed38fc547bd069bf8b43b2a8edb3007ee8a8131f876caa","width":1000,"height":708},
    {"key":"asset04","path":"Character2/Turnaround.webp","parts":1,"bytes":43050,"sha256":"044dfed9e1a86575ab86b41c2bec817725aa754aa1c070ce884a432d982a5422","width":1000,"height":708},
    {"key":"asset05","path":"Character3/Expressions.webp","parts":1,"bytes":65836,"sha256":"096d34e70f2f2d85530abd936f0e02e008e1a2d6c3202734d6b73ebc97646b14","width":1000,"height":708},
    {"key":"asset06","path":"Character3/Turnaround.webp","parts":1,"bytes":50846,"sha256":"34b21d93f2a205eedf3e4ed99f7a7285f3f52b1774436c901b960c831e390d05","width":1000,"height":708},
    {"key":"asset07","path":"Character4/Expressions.webp","parts":1,"bytes":64662,"sha256":"0e167968f17cad284bd1fccc74f283a2e8271dd1ae11f9c639b30e3568db864f","width":1000,"height":708},
    {"key":"asset08","path":"Character4/Turnaround.webp","parts":1,"bytes":51496,"sha256":"76eac51ae6df232e731bc836c107683e9ea8f59a51d581f62e384b883ee70324","width":1000,"height":708},
    {"key":"asset09","path":"Shared/Character_Select_UI.webp","parts":2,"bytes":95944,"sha256":"2269f2375baf9e3d44dc88c43c6d47294dee5f43d20f7fcb652ef620d539d7f8","width":800,"height":1421},
    {"key":"asset10","path":"Shared/Gameplay_Camera_Readability.webp","parts":2,"bytes":125850,"sha256":"be847a313cbbe3ca2652cf5904bfd4b2744f8e41e9ea1931cd309afcc748b04f","width":1000,"height":708},
    {"key":"asset11","path":"Shared/Gear_Outfit_Material_Callouts.webp","parts":2,"bytes":84184,"sha256":"361e6b7571869feef35bf090943b4df175ccb8a42a4d74b51cf7649c6177e175","width":1000,"height":708},
    {"key":"asset12","path":"Shared/Onboarding_Crew_UI.webp","parts":1,"bytes":64974,"sha256":"a97209b879da56b1238ba2b11666fa6d2ca07a23ac1230f100555184208c7179","width":1000,"height":708},
]

def main() -> int:
    ap=argparse.ArgumentParser()
    ap.add_argument('--payload-root', default=str(Path(__file__).resolve().parent))
    ap.add_argument('--output-root', default='HAVENLINE_UNITY/Reference/Characters/Complete')
    args=ap.parse_args()
    payload_root=Path(args.payload_root); output_root=Path(args.output_root)
    results=[]
    for spec in SPECS:
        encoded=[]; part_paths=[]
        for n in range(1, spec['parts']+1):
            part=payload_root / f"{spec['key']}.part{n:02d}.b64"
            if not part.is_file(): raise SystemExit(f"missing payload part: {part}")
            encoded.append(''.join(part.read_text(encoding='ascii').split())); part_paths.append(str(part))
        data=base64.b64decode(''.join(encoded), validate=True)
        digest=hashlib.sha256(data).hexdigest()
        if len(data)!=spec['bytes']: raise SystemExit(f"byte mismatch for {spec['path']}: {len(data)} != {spec['bytes']}")
        if digest!=spec['sha256']: raise SystemExit(f"checksum mismatch for {spec['path']}")
        if not (data.startswith(b'RIFF') and data[8:12]==b'WEBP'): raise SystemExit(f"not WEBP: {spec['path']}")
        dest=output_root/spec['path']; dest.parent.mkdir(parents=True, exist_ok=True); dest.write_bytes(data)
        results.append({**spec,'payloadParts':part_paths,'verified':True})
    report={'schemaVersion':2,'referenceAssetCount':len(results),'allVerified':all(x['verified'] for x in results),'assets':results}
    (output_root/'materialization-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,indent=2))
    return 0
if __name__=='__main__': raise SystemExit(main())
