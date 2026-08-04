from pathlib import Path
import json
import sys

root = Path(sys.argv[1])
path = root / "MyStudyCompanionWeb/firebase.json"
config = json.loads(path.read_text(encoding="utf-8"))
config["firestore"] = {"rules": "firestore.rules"}
path.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
print("Configured Firebase Hosting and Firestore rules as one synchronized deployment.")
