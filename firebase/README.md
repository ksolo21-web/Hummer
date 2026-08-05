# Shared Firebase rules deployment

KREATIV Studio and My Study Companion use the same Firebase project. The files
`firestore.rules` and `storage.rules` in this directory are **merge fragments**,
not deployable standalone rulesets.

Before deployment:

1. Export the current live Firestore and Storage rules from the shared Firebase project.
2. Merge the KREATIV match blocks into the existing service-level match blocks.
3. Save the reviewed complete rulesets as:
   - `firebase/generated/shared-firestore.rules`
   - `firebase/generated/shared-storage.rules`
4. Run Firebase Rules unit tests for both applications.
5. Deploy only after authenticated review with an owner-approved Firebase credential.

`firebase.json` deliberately references the generated merged files. Because those
files are not committed, an accidental `firebase deploy` from this branch fails
instead of replacing My Study Companion's live permissions.

KREATIV owns only these namespaces:

- Firestore: `/users/{uid}/kreativProjects/**`
- Firestore: `/users/{uid}/kreativPrivate/**`
- Storage: `/users/{uid}/kreativStudio/**`

Every rule requires an authenticated Firebase user whose UID equals the path UID.
