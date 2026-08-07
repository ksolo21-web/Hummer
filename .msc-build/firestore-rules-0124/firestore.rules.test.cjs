const fs = require('node:fs');
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');
const {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  writeBatch,
  serverTimestamp,
  Timestamp,
} = require('firebase/firestore');

const PROJECT_ID = 'my-study-companion-rules-test';
const HOUSEHOLD_A = 'hh-family-a';
const HOUSEHOLD_B = 'hh-family-b';
const OWNER = 'owner-user';
const MEMBER = 'member-user';

let testEnv;

function memberData(uid, role, inviteCode = '') {
  return {
    uid,
    displayName: uid === OWNER ? 'Household Owner' : 'Family Member',
    ageGroup: 'ADULT',
    role,
    googleConnected: true,
    inviteCode,
    joinedAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function userData(uid, householdId, role) {
  return {
    uid,
    displayName: uid === OWNER ? 'Household Owner' : 'Family Member',
    householdId,
    role,
    updatedAt: serverTimestamp(),
  };
}

function householdData(ownerUid = OWNER, familyName = 'The Family') {
  return {
    ownerUid,
    familyName,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function boardData(uid) {
  return {
    payloadJson: '{"familyName":"The Family"}',
    revision: 1,
    updatedBy: uid,
    updatedAt: serverTimestamp(),
  };
}

function ideaData(id, createdByUid, authorUid = createdByUid) {
  return {
    id,
    createdByUid,
    authorUid,
    authorName: 'Family Member',
    topic: 'A meaningful family study topic',
    reason: 'Useful for our household.',
    scripture: 'Joshua 1:9',
    officialUrl: 'https://www.jw.org/',
    createdAtEpochMillis: 1785520800000,
    used: false,
    updatedAt: serverTimestamp(),
  };
}

async function seedHousehold(householdId, ownerUid = OWNER) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, `households/${householdId}`), {
      ownerUid,
      familyName: 'The Family',
      createdAt: Timestamp.fromMillis(1785520800000),
      updatedAt: Timestamp.fromMillis(1785520800000),
    });
    await setDoc(doc(db, `households/${householdId}/members/${ownerUid}`), {
      uid: ownerUid,
      displayName: 'Household Owner',
      ageGroup: 'ADULT',
      role: 'owner',
      googleConnected: true,
      inviteCode: '',
      joinedAt: Timestamp.fromMillis(1785520800000),
      updatedAt: Timestamp.fromMillis(1785520800000),
    });
    await setDoc(doc(db, `users/${ownerUid}`), {
      uid: ownerUid,
      displayName: 'Household Owner',
      householdId,
      role: 'owner',
      updatedAt: Timestamp.fromMillis(1785520800000),
    });
  });
}

async function seedMember(householdId, uid = MEMBER, role = 'member') {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, `households/${householdId}/members/${uid}`), {
      uid,
      displayName: 'Family Member',
      ageGroup: 'ADULT',
      role,
      googleConnected: true,
      inviteCode: 'ABCDE-23456',
      joinedAt: Timestamp.fromMillis(1785520800000),
      updatedAt: Timestamp.fromMillis(1785520800000),
    });
    await setDoc(doc(db, `users/${uid}`), {
      uid,
      displayName: 'Family Member',
      householdId,
      role,
      updatedAt: Timestamp.fromMillis(1785520800000),
    });
  });
}

async function run(name, fn) {
  await testEnv.clearFirestore();
  try {
    await fn();
    console.log(`PASS: ${name}`);
  } catch (error) {
    console.error(`FAIL: ${name}`);
    throw error;
  }
}

async function main() {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync('firestore.rules', 'utf8'),
    },
  });

  await run('unauthenticated clients cannot read household data', async () => {
    await seedHousehold(HOUSEHOLD_A);
    const db = testEnv.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, `households/${HOUSEHOLD_A}`)));
  });

  await run('standalone household creation is rejected without atomic owner membership', async () => {
    const db = testEnv.authenticatedContext(OWNER).firestore();
    await assertFails(setDoc(doc(db, `households/${HOUSEHOLD_A}`), householdData()));
  });

  await run('owner can atomically create household, membership, user link and board', async () => {
    const db = testEnv.authenticatedContext(OWNER).firestore();
    const batch = writeBatch(db);
    batch.set(doc(db, `households/${HOUSEHOLD_A}`), householdData());
    batch.set(doc(db, `households/${HOUSEHOLD_A}/members/${OWNER}`), memberData(OWNER, 'owner'));
    batch.set(doc(db, `users/${OWNER}`), userData(OWNER, HOUSEHOLD_A, 'owner'));
    batch.set(doc(db, `households/${HOUSEHOLD_A}/shared/familyBoard`), boardData(OWNER));
    await assertSucceeds(batch.commit());
    await assertSucceeds(getDoc(doc(db, `households/${HOUSEHOLD_A}`)));
  });

  await run('valid one-time invitation can atomically create membership without a household pre-read', async () => {
    await seedHousehold(HOUSEHOLD_A);
    const code = 'ABCDE-23456';
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      await setDoc(doc(db, `householdInvites/${code}`), {
        householdId: HOUSEHOLD_A,
        createdBy: OWNER,
        createdAt: Timestamp.fromMillis(1785520800000),
        expiresAtEpochSeconds: 4102444800,
        expiresAt: Timestamp.fromMillis(4102444800000),
        status: 'active',
        usedBy: '',
      });
    });
    const db = testEnv.authenticatedContext(MEMBER).firestore();
    const batch = writeBatch(db);
    batch.set(doc(db, `households/${HOUSEHOLD_A}/members/${MEMBER}`), memberData(MEMBER, 'member', code));
    batch.set(doc(db, `users/${MEMBER}`), userData(MEMBER, HOUSEHOLD_A, 'member'));
    batch.update(doc(db, `householdInvites/${code}`), {
      status: 'used',
      usedBy: MEMBER,
      usedAt: serverTimestamp(),
    });
    await assertSucceeds(batch.commit());
    await assertSucceeds(getDoc(doc(db, `households/${HOUSEHOLD_A}`)));
  });

  await run('self-joining without a valid invitation is rejected', async () => {
    await seedHousehold(HOUSEHOLD_A);
    const db = testEnv.authenticatedContext(MEMBER).firestore();
    const batch = writeBatch(db);
    batch.set(doc(db, `households/${HOUSEHOLD_A}/members/${MEMBER}`), memberData(MEMBER, 'member', 'FAKE-23456'));
    batch.set(doc(db, `users/${MEMBER}`), userData(MEMBER, HOUSEHOLD_A, 'member'));
    await assertFails(batch.commit());
  });

  await run('ordinary member cannot overwrite organizer-only board configuration', async () => {
    await seedHousehold(HOUSEHOLD_A);
    await seedMember(HOUSEHOLD_A);
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), `households/${HOUSEHOLD_A}/shared/familyBoard`), {
        payloadJson: '{}', revision: 1, updatedBy: OWNER,
        updatedAt: Timestamp.fromMillis(1785520800000),
      });
    });
    const db = testEnv.authenticatedContext(MEMBER).firestore();
    await assertFails(updateDoc(doc(db, `households/${HOUSEHOLD_A}/shared/familyBoard`), {
      payloadJson: '{"familyName":"Hijacked"}',
      revision: 2,
      updatedBy: MEMBER,
      updatedAt: serverTimestamp(),
    }));
  });

  await run('member can create and edit only an idea owned by that account', async () => {
    await seedHousehold(HOUSEHOLD_A);
    await seedMember(HOUSEHOLD_A);
    const db = testEnv.authenticatedContext(MEMBER).firestore();
    const ideaRef = doc(db, `households/${HOUSEHOLD_A}/ideas/idea-member-1`);
    await assertSucceeds(setDoc(ideaRef, ideaData('idea-member-1', MEMBER, 'local-child-1')));
    await assertSucceeds(updateDoc(ideaRef, {
      topic: 'An updated meaningful study topic',
      updatedAt: serverTimestamp(),
    }));

    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), `households/${HOUSEHOLD_A}/ideas/idea-owner-1`), {
        ...ideaData('idea-owner-1', OWNER, OWNER),
        updatedAt: Timestamp.fromMillis(1785520800000),
      });
    });
    await assertFails(updateDoc(doc(db, `households/${HOUSEHOLD_A}/ideas/idea-owner-1`), {
      topic: 'Unauthorized edit',
      updatedAt: serverTimestamp(),
    }));
  });

  await run('vote document identity prevents duplicate vote stuffing', async () => {
    await seedHousehold(HOUSEHOLD_A);
    await seedMember(HOUSEHOLD_A);
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), `households/${HOUSEHOLD_A}/ideas/idea-1`), {
        ...ideaData('idea-1', OWNER, OWNER),
        updatedAt: Timestamp.fromMillis(1785520800000),
      });
    });
    const db = testEnv.authenticatedContext(MEMBER).firestore();
    const correctId = `vote-${MEMBER}~idea-1~local-child-1`;
    const vote = {
      ideaId: 'idea-1',
      voterUid: 'local-child-1',
      createdByUid: MEMBER,
      createdAt: serverTimestamp(),
    };
    await assertSucceeds(setDoc(doc(db, `households/${HOUSEHOLD_A}/ideaVotes/${correctId}`), vote));
    await assertFails(setDoc(doc(db, `households/${HOUSEHOLD_A}/ideaVotes/another-id`), vote));
    await assertFails(setDoc(doc(db, `households/${HOUSEHOLD_A}/ideaVotes/${correctId}`), vote));
    await assertSucceeds(deleteDoc(doc(db, `households/${HOUSEHOLD_A}/ideaVotes/${correctId}`)));
  });

  await run('owner can publish Family Worship while ordinary member cannot', async () => {
    await seedHousehold(HOUSEHOLD_A);
    await seedMember(HOUSEHOLD_A);
    const ownerDb = testEnv.authenticatedContext(OWNER).firestore();
    const memberDb = testEnv.authenticatedContext(MEMBER).firestore();
    const refPath = `households/${HOUSEHOLD_A}/familyWorship/current`;
    await assertSucceeds(setDoc(doc(ownerDb, refPath), {
      payloadJson: '{"title":"Family Worship"}',
      revision: 1,
      updatedBy: OWNER,
      updatedAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(memberDb, refPath), {
      payloadJson: '{"title":"Unauthorized"}',
      revision: 2,
      updatedBy: MEMBER,
      updatedAt: serverTimestamp(),
    }));
  });

  await run('membership in one household does not expose another household', async () => {
    await seedHousehold(HOUSEHOLD_A);
    await seedMember(HOUSEHOLD_A);
    await seedHousehold(HOUSEHOLD_B, 'other-owner');
    const db = testEnv.authenticatedContext(MEMBER).firestore();
    await assertFails(getDoc(doc(db, `households/${HOUSEHOLD_B}`)));
  });

  console.log('PASS: all hardened Firestore authorization scenarios completed.');
}

main()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(async () => {
    if (testEnv) await testEnv.cleanup();
  });
