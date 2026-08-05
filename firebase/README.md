# Shared Firebase repair for KREATIV Studio

KREATIV Studio and My Study Companion use the same Firebase project. The KREATIV
fragments in this directory are **not** complete project rules and must never be
deployed by themselves.

## Guarded permanent repair

Run the **KREATIV Firebase Permanent Repair** workflow.

The workflow and `scripts/kreativ_firebase_repair.py`:

1. authenticate to the live Firebase/Google Cloud project;
2. verify that the Firebase Android app is exactly `com.kreativstudio.app`;
3. read the current Firestore and Storage releases and their full source;
4. preserve every existing rule outside a marked KREATIV-managed block;
5. merge `firestore.rules` and `storage.rules` inside the correct root match blocks;
6. validate both merged rule sources with the Firebase Rules API;
7. in apply mode, register the permanent SHA-1 and SHA-256 certificate idempotently;
8. create both new rulesets before changing either release;
9. refuse to deploy if either live release changed after preflight;
10. roll the first release back if the second release update fails; and
11. re-read Firebase and verify the live signer and exact deployed rules.

`firebase.json` points to generated shared-rule files. Those files are produced
from the live rules during the guarded workflow; they are intentionally not
committed as static replacements.

## Required Google authentication

Use one of these workflow modes:

- **workload_identity**: environment/repository variables
  `GCP_WORKLOAD_IDENTITY_PROVIDER` and `GCP_DEPLOY_SERVICE_ACCOUNT`.
- **service_account_json**: environment secret
  `FIREBASE_SERVICE_ACCOUNT_MY_STUDY_COMPANION_ABC01`.

The identity needs permission to read/manage the Firebase Android app
certificate hashes and to read, validate, create, and release Firebase Rules.

Apply mode also requires typing the exact project ID
`my-study-companion-abc01`. Dry-run mode performs the live read and validation
without changing Firebase.

## APK release gate

The normal root-fix workflow compiles and tests source but publishes no APK.
The **KREATIV Studio Authenticated Private Alpha** workflow will publish an APK
only after it proves that:

- both permanent certificate hashes are registered on the Firebase Android app;
- the exact KREATIV Firestore and Storage blocks are already live;
- the recovered private signing key matches both permanent certificate hashes;
- package name, version, APK signature, and zip alignment all pass.

This prevents a debug-signed or backend-incomplete APK from being presented as
a repaired release.
