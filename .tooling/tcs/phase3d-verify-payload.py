import sys,json,zipfile,hashlib,re
from pathlib import Path
def payload(path):
 with zipfile.ZipFile(path) as z:
  names=z.namelist();assert len(names)==len(set(names)),"Duplicate ZIP entries"
  return {n:hashlib.sha256(z.read(n)).hexdigest() for n in names if not re.fullmatch(r"META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))",n,re.IGNORECASE)}
a,b=map(payload,sys.argv[1:3]);assert a==b,"APK payload changed"
Path(sys.argv[3]).write_text(json.dumps(dict(passed=True,entries=len(a),payload=a,accepted_sha256=hashlib.sha256(Path(sys.argv[1]).read_bytes()).hexdigest(),emulator_sha256=hashlib.sha256(Path(sys.argv[2]).read_bytes()).hexdigest()),indent=2)+"\n")
