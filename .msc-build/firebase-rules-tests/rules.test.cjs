const fs = require('node:fs');
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require('@firebase/rules-unit-testing');
const {
  Timestamp,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
} = require('firebase/firestore');

const PROJECT_ID = 'demo-my-study-companion';
const HOUSEHOLD_A = 'hh-a';
const HOUSEHOLD_B = 'hh-b';

function household(ownerUid, familyName = 'Test Family') {
  return {
    ownerUid,
    familyName,
    createdAt: Timestamp.now(),
    updatedAt: Timestamp.now(),
  };
}

function member(uid, role, displayName = uid) {
  return {
    uid,
    displayName,
    role,
    joinedAt: Timestamp.now(),
  };
}

function invite(householdId, createdBy, code = 'ABCDE-23456') {
  const expiresAtMillis = Date.now() + 60 * 60 * 1000;
  return {
    code,
    data: {
      householdId,
      createdBy,
      createdAt: Timestamp.now(),
      expiresAtEpochSeconds: Math.floor(expiresAtMillis / 1000),
      expiresAt: Timestamp.fromMillis(expiresAtMillis),
      status: 'active',
      usedBy: '',
    },
  };
}

async function seed(testEnv, callback) {
  await testEnv.withSecurityRulesDisabled(async (context) => callback(context.firestore()));
}

async function seedHousehold(testEnv, householdId, ownerUid, extraMembers = []) {
  await seed(testEnv, async (db) => {
    await setDoc(doc(db, 'households', householdId), household(ownerUid));
    await setDoc(doc(db, 'households', householdId, 'members', ownerUid), member(ownerUid, 'owner'));
    await setDoc(doc(db, 'users', ownerUid), {
      uid: ownerUid,
      displayName: ownerUid,
      householdId,
      role: 'owner',
      updatedAt: Timestamp.now(),
    });
    for (const entry of extraMembers) {
      await setDoc(
        doc(db, 'households', householdId, 'members', entry.uid),
        member(entry.uid, entry.role),
      );
      await setDoc(doc(db, 'users', entry.uid), {
        uid: entry.uid,
        displayName: entry.uid,
        householdId,
        role: entry.role,
        updatedAt: Timestamp.now(),
      });
    }
  });
}

async function createHouseholdAs(testEnv, uid, householdId) {
  const db = testEnv.authenticatedContext(uid).firestore();
  const batch = writeBatch(db);
  batch.set(doc(db, 'households', householdId), {
    ownerUid: uid,
    familyName: 'Created Family',
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  batch.set(doc(db, 'households', householdId, 'members', uid), {
    uid,
    displayName: uid,
    role: 'owner',
    joinedAt: serverTimestamp(),
  });
  batch.set(doc(db, 'users', uid), {
    uid,
    displayName: uid,
    householdId,
    role: 'owner',
    updatedAt: serverTimestamp(),
  });
  batch.set(doc(db, 'households', householdId, 'shared', 'familyBoard'), {
    payloadJson: '{"familyName":"Created Family"}',
    revision: Date.now(),
    updatedBy: uid,
    updatedAt: serverTimestamp(),
  });
  return batch.commit();
}

async function joinWithInvite(testEnv, uid, code, householdId) {
  const db = testEnv.authenticatedContext(uid).firestore();
  const batch = writeBatch(db);
  batch.update(doc(db, 'householdInvites', code), {
    status: 'used',
    usedBy: uid,
    usedAt: serverTimestamp(),
  });
  batch.set(doc(db, 'households', householdId, 'members', uid), {
    uid,
    displayName: uid,
    role: 'member',
    inviteCode: code,
    joinedAt: serverTimestamp(),
  });
  batch.set(doc(db, 'users', uid), {
    uid,
    displayName: uid,
    householdId,
    role: 'member',
    updatedAt: serverTimestamp(),
  });
  return batch.commit();
}

async function main() {
  const testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync('firestore.rules', 'utf8'),
    },
  });

  const tests = [];
  function test(name, fn) {
    tests.push({ name, fn });
  }

  test('unauthenticated household creation is denied', async () => {
    const db = testEnv.unauthenticatedContext().firestore();
    await assertFails(setDoc(doc(db, 'households', HOUSEHOLD_A), {
      ownerUid: 'anonymous',
      familyName: 'No',
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }));
  });

  test('signed-in owner can atomically create household, member, user link, and board', async () => {
    await assertSucceeds(createHouseholdAs(testEnv, 'owner-a', HOUSEHOLD_A));
  });

  test('owner can create a correctly shaped active invitation', async () => {
    await seedHousehold(testEnv, HOUSEHOLD_A, 'owner-a');
    const db = testEnv.authenticatedContext('owner-a').firestore();
    const created = invite(HOUSEHOLD_A, 'owner-a');
    await assertSucceeds(setDoc(doc(db, 'householdInvites', created.code), {
      ...created.data,
      createdAt: serverTimestamp(),
    }));
  });

  test('member can consume an active invite once and join atomically', async () => {
    await seedHousehold(testEnv, HOUSEHOLD_A, 'owner-a');
    const created = invite(HOUSEHOLD_A, 'owner-a');
    await seed(testEnv, (db) => setDoc(doc(db, 'householdInvites', created.code), created.data));
    await assertSucceeds(joinWithInvite(testEnv, 'member-a', created.code, HOUSEHOLD_A));
    await assertFails(joinWithInvite(testEnv, 'member-b', created.code, HOUSEHOLD_A));
  });

  test('organizer cannot retarget an invite to a different household', async () => {
    await seedHousehold(testEnv, HOUSEHOLD_A, 'owner-a', [{ uid: 'organizer-a', role: 'organizer' }]);
    await seedHousehold(testEnv, HOUSEHOLD_B, 'owner-b');
    const created = invite(HOUSEHOLD_A, 'owner-a');
    await seed(testEnv, (db) => setDoc(doc(db, 'householdInvites', created.code), created.data));
    const db = testEnv.authenticatedContext('organizer-a').firestore();
    await assertFails(updateDoc(doc(db, 'householdInvites', created.code), {
      householdId: HOUSEHOLD_B,
    }));
  });

  test('member cannot switch the immutable user link to another household', async () => {
    await seedHousehold(testEnv, HOUSEHOLD_A, 'owner-a', [{ uid: 'member-a', role: 'member' }]);
    await seedHousehold(testEnv, HOUSEHOLD_B, 'owner-b');
    await seed(testEnv, (db) => setDoc(
      doc(db, 'households', HOUSEHOLD_B, 'members', 'member-a'),
      member('member-a', 'member'),
    ));
    const db = testEnv.authenticatedContext('member-a').firestore();
    await assertFails(updateDoc(doc(db, 'users', 'member-a'), {
      householdId: HOUSEHOLD_B,
      updatedAt: serverTimestamp(),
    }));
  });

  test('member can write only their own progress document', async () => {
    await seedHousehold(testEnv, HOUSEHOLD_A, 'owner-a', [
      { uid: 'member-a', role: 'member' },
      { uid: 'member-b', role: 'member' },
    ]);
    const db = testEnv.authenticatedContext('member-a').firestore();
    const own = {
      uid: 'member-a',
      payloadJson: '{"completed":1}',
      revision: Date.now(),
      updatedAt: serverTimestamp(),
    };
    await assertSucceeds(setDoc(doc(db, 'households', HOUSEHOLD_A, 'memberProgress', 'member-a'), own));
    await assertFails(setDoc(doc(db, 'households', HOUSEHOLD_A, 'memberProgress', 'member-b'), {
      ...own,
      uid: 'member-b',
    }));
  });

  test('ordinary member cannot publish Family Worship but owner can', async () => {
    await seedHousehold(testEnv, HOUSEHOLD_A, 'owner-a', [{ uid: 'member-a', role: 'member' }]);
    const payload = {
      payloadJson: '{"title":"Family Worship"}',
      revision: Date.now(),
      updatedBy: 'member-a',
      updatedAt: serverTimestamp(),
    };
    const memberDb = testEnv.authenticatedContext('member-a').firestore();
    await assertFails(setDoc(doc(memberDb, 'households', HOUSEHOLD_A, 'familyWorship', 'current'), payload));
    const ownerDb = testEnv.authenticatedContext('owner-a').firestore();
    await assertSucceeds(setDoc(doc(ownerDb, 'households', HOUSEHOLD_A, 'familyWorship', 'current'), {
      ...payload,
      updatedBy: 'owner-a',
    }));
  });

  test('unknown shared documents and extra synchronized fields are denied', async () => {
    await seedHousehold(testEnv, HOUSEHOLD_A, 'owner-a');
    const db = testEnv.authenticatedContext('owner-a').firestore();
    await assertFails(setDoc(doc(db, 'households', HOUSEHOLD_A, 'shared', 'arbitrary'), {
      payloadJson: '{}',
      revision: 1,
      updatedBy: 'owner-a',
      updatedAt: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(db, 'households', HOUSEHOLD_A, 'shared', 'familyBoard'), {
      payloadJson: '{}',
      revision: 1,
      updatedBy: 'owner-a',
      updatedAt: serverTimestamp(),
      injectedField: true,
    }));
  });

  test('signed-in users cannot list invitation documents', async () => {
    const db = testEnv.authenticatedContext('reader').firestore();
    await assertFails(getDocs(collection(db, 'householdInvites')));
  });

  test('deleting the household document makes orphaned subcollection data inaccessible', async () => {
    await seedHousehold(testEnv, HOUSEHOLD_A, 'owner-a', [{ uid: 'member-a', role: 'member' }]);
    await seed(testEnv, (db) => setDoc(doc(db, 'households', HOUSEHOLD_A, 'shared', 'familyBoard'), {
      payloadJson: '{}',
      revision: 1,
      updatedBy: 'owner-a',
      updatedAt: Timestamp.now(),
    }));
    const ownerDb = testEnv.authenticatedContext('owner-a').firestore();
    await assertSucceeds(deleteDoc(doc(ownerDb, 'households', HOUSEHOLD_A)));
    const memberDb = testEnv.authenticatedContext('member-a').firestore();
    await assertFails(getDoc(doc(memberDb, 'households', HOUSEHOLD_A, 'shared', 'familyBoard')));
  });

  let failures = 0;
  for (const { name, fn } of tests) {
    await testEnv.clearFirestore();
    try {
      await fn();
      console.log(`PASS: ${name}`);
    } catch (error) {
      failures += 1;
      console.error(`FAIL: ${name}`);
      console.error(error);
    }
  }

  await testEnv.cleanup();
  if (failures > 0) {
    throw new Error(`${failures} Firestore rules test(s) failed.`);
  }
  console.log(`PASS: ${tests.length} Firestore authorization and abuse tests completed.`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
