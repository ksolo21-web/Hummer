import sys,json,zipfile,hashlib
from pathlib import Path
def payload(path):
 with zipfile.ZipFile(path) as z:
  return {n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if not n.startswith("META-INF/")}
a,b=map(payload,sys.argv[1:3]);assert a==b,"APK payload changed"
Path(sys.argv[3]).write_text(json.dumps(dict(passed=True,entries=len(a),payload=a,accepted_sha256=hashlib.sha256(Path(sys.argv[1]).read_bytes()).hexdigest(),emulator_sha256=hashlib.sha256(Path(sys.argv[2]).read_bytes()).hexdigest()),indent=2)+"\n")
