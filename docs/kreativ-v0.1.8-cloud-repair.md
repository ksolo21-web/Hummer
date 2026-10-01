# KREATIV Studio v0.1.8 cloud repair

- Removes the forced App Check token preflight from sideloaded builds.
- Initializes Play Integrity App Check only for the future Play-distributed release build.
- Verifies Firestore write/read access and Cloud Storage upload/read/delete access before enabling backup.
- Preserves local artwork as the authoritative copy.
- Reports the exact Firebase service rejection when cloud verification fails.
