const SDK = "https://www.gstatic.com/firebasejs/12.16.0/";
let auth, db, modules, currentUser;
let initializationPromise;
let redirectHandled = false;

export function configured(){
  return Boolean(window.MSC_FIREBASE_CONFIG?.appId);
}

async function initialize(){
  if(!configured()) throw new Error("Firebase Web App registration is not configured yet.");
  if(initializationPromise) return initializationPromise;
  initializationPromise = (async()=>{
    const [appMod, authMod, fireMod] = await Promise.all([
      import(SDK + "firebase-app.js"),
      import(SDK + "firebase-auth.js"),
      import(SDK + "firebase-firestore.js")
    ]);
    modules = {...authMod, ...fireMod};
    const app = appMod.getApps().length ? appMod.getApp() : appMod.initializeApp(window.MSC_FIREBASE_CONFIG);
    auth = authMod.getAuth(app);
    await authMod.setPersistence(auth, authMod.browserLocalPersistence);
    db = fireMod.getFirestore(app);
  })();
  return initializationPromise;
}

async function finishRedirectIfNeeded(){
  await initialize();
  if(redirectHandled) return;
  redirectHandled = true;
  const result = await modules.getRedirectResult(auth);
  if(result?.user) currentUser = result.user;
}

export async function restoreSession(onState){
  await finishRedirectIfNeeded();
  return new Promise((resolve, reject)=>{
    const unsubscribe = modules.onAuthStateChanged(auth, user=>{
      currentUser = user;
      onState?.(user);
      unsubscribe();
      resolve(user);
    }, reject);
  });
}

export async function connect(onState){
  const existing = await restoreSession(onState);
  if(existing) return existing;
  const provider = new modules.GoogleAuthProvider();
  provider.setCustomParameters({prompt:"select_account"});
  try {
    const result = await modules.signInWithPopup(auth, provider);
    currentUser = result.user;
    onState?.(currentUser);
    return currentUser;
  } catch(error) {
    if(error?.code === "auth/popup-blocked") {
      await modules.signInWithRedirect(auth, provider);
      return null;
    }
    throw error;
  }
}

async function workbookPageId(profileUid, pageKey){
  const bytes = new TextEncoder().encode(`${profileUid}\u001f${pageKey}`);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map(value=>value.toString(16).padStart(2,"0")).join("").slice(0,48);
}

async function studyDocumentId(profileUid, documentId){
  const bytes = new TextEncoder().encode(`${profileUid}\u001f${documentId}`);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map(value=>value.toString(16).padStart(2,"0")).join("").slice(0,48);
}

export async function pull(){
  if(!currentUser) return null;
  const userSnap = await modules.getDoc(modules.doc(db, "users", currentUser.uid));
  const householdId = userSnap.data()?.householdId;
  if(!householdId) return null;

  const progressRef = modules.doc(db, "households", householdId, "memberProgress", currentUser.uid);
  const snap = await modules.getDoc(progressRef);
  let payload = null;
  let revision = 0;
  if(snap.exists()) {
    revision = snap.data().revision || 0;
    try { payload = JSON.parse(snap.data().payloadJson || "null"); } catch {}
  }
  payload ||= {uid:currentUser.uid};
  const profileUid = payload.uid || currentUser.uid;
  const pagesRef = modules.collection(db, "households", householdId, "memberWorkbooks", currentUser.uid, "pages");
  const pageSnaps = await modules.getDocs(pagesRef);
  const pageProgress = {};
  pageSnaps.forEach(document => {
    const data = document.data();
    if(data.profileUid !== profileUid || !data.pageKey) return;
    try { pageProgress[data.pageKey] = JSON.parse(data.payloadJson || "null"); } catch {}
  });
  payload.interactiveWorkbooks ||= {activeBookId:"",activePageKey:"",difficultyAdjustment:0,pageProgress:{}};
  payload.interactiveWorkbooks.pageProgress = pageProgress;

  payload.studyLibrary = {activeDocumentId:"",documents:{},notesByBlockId:{},documentNotes:{},highlights:[],bookmarks:[],readingPosition:{},revisionByDocument:{}};
  payload.studyLibrary.documents ||= {};
  payload.studyLibrary.notesByBlockId ||= {};
  payload.studyLibrary.documentNotes ||= {};
  payload.studyLibrary.highlights ||= [];
  payload.studyLibrary.bookmarks ||= [];
  payload.studyLibrary.readingPosition ||= {};
  payload.studyLibrary.revisionByDocument ||= {};
  const materialsRef = modules.collection(db, "households", householdId, "memberStudyMaterials", currentUser.uid, "documents");
  const materialSnaps = await modules.getDocs(materialsRef);
  const highlightSet = new Set(payload.studyLibrary.highlights || []);
  const bookmarkSet = new Set(payload.studyLibrary.bookmarks || []);
  materialSnaps.forEach(documentSnapshot => {
    const data = documentSnapshot.data();
    if(data.profileUid !== profileUid || !data.documentId) return;
    try {
      const packet = JSON.parse(data.payloadJson || "null");
      if(!packet?.document?.id) return;
      payload.studyLibrary.documents[packet.document.id] = packet.document;
      payload.studyLibrary.revisionByDocument[packet.document.id] = Number(packet.revision ?? data.revision ?? packet.document.updatedAtEpochMillis ?? 0);
      Object.assign(payload.studyLibrary.notesByBlockId, packet.notesByBlockId || {});
      if(packet.documentNote) payload.studyLibrary.documentNotes[packet.document.id] = packet.documentNote;
      for(const blockId of packet.highlights || []) highlightSet.add(blockId);
      for(const blockId of packet.bookmarks || []) bookmarkSet.add(blockId);
      if(Number.isInteger(packet.readingPosition)) payload.studyLibrary.readingPosition[packet.document.id] = packet.readingPosition;
    } catch {}
  });
  payload.studyLibrary.highlights = [...highlightSet];
  payload.studyLibrary.bookmarks = [...bookmarkSet];
  return {householdId, payload, revision};
}

export async function push(localState){
  if(!currentUser) throw new Error("Sign in first.");
  const userSnap = await modules.getDoc(modules.doc(db, "users", currentUser.uid));
  const householdId = userSnap.data()?.householdId;
  if(!householdId) throw new Error("This account has not joined a household in the Android app.");

  const ref = modules.doc(db, "households", householdId, "memberProgress", currentUser.uid);
  let profileUid = currentUser.uid;
  await modules.runTransaction(db, async tx => {
    const snap = await tx.get(ref);
    let payload = {uid:currentUser.uid};
    let revision = 0;

    if(snap.exists()) {
      revision = snap.data().revision || 0;
      try { payload = JSON.parse(snap.data().payloadJson) || payload; } catch {}
    }

    payload.uid = payload.uid || currentUser.uid;
    profileUid = payload.uid;
    if(localState.journey) {
      payload.bibleProgress = {
        ...(payload.bibleProgress || {}),
        mode:"STORY_JOURNEYS",
        activeJourneyId:localState.journey.id,
        activeJourneyDayIndex:localState.journey.index || 0,
        canonicalPaceDays:payload.bibleProgress?.canonicalPaceDays || 365,
        canonicalDayIndex:payload.bibleProgress?.canonicalDayIndex || 0,
        completedReadingKeys:payload.bibleProgress?.completedReadingKeys || []
      };
    }
    if(localState.eventNotebooks) payload.eventNotebooks = localState.eventNotebooks;
    if(localState.familyWorkbook) payload.familyWorkbook = localState.familyWorkbook;
    if(localState.studyLibrary) {
      payload.studyLibrary = {
        activeDocumentId:localState.studyLibrary.activeDocumentId || "",
        documents:{}, notesByBlockId:{}, documentNotes:{}, highlights:[], bookmarks:[], readingPosition:{}, revisionByDocument:{}
      };
    }
    if(localState.interactiveWorkbooks) {
      payload.interactiveWorkbooks = {
        ...localState.interactiveWorkbooks,
        pageProgress:{}
      };
    }

    tx.set(ref, {
      uid:currentUser.uid,
      payloadJson:JSON.stringify(payload),
      revision:revision + 1,
      updatedAt:modules.serverTimestamp()
    }, {merge:false});
  });

  const pages = Object.entries(localState.interactiveWorkbooks?.pageProgress || {});
  for(let offset=0; offset<pages.length; offset+=400) {
    const batch = modules.writeBatch(db);
    for(const [pageKey, progress] of pages.slice(offset, offset+400)) {
      const payloadJson = JSON.stringify(progress);
      if(payloadJson.length > 700000) throw new Error("One workbook page is too large to synchronize. Clear a little ink and try again.");
      const pageId = await workbookPageId(profileUid, pageKey);
      const pageRef = modules.doc(db, "households", householdId, "memberWorkbooks", currentUser.uid, "pages", pageId);
      batch.set(pageRef, {
        accountUid:currentUser.uid,
        profileUid,
        pageKey,
        payloadJson,
        revision:Date.now(),
        updatedAt:modules.serverTimestamp()
      }, {merge:true});
    }
    await batch.commit();
  }

  const library = localState.studyLibrary || {};
  const materials = Object.values(library.documents || {});
  for(let offset=0; offset<materials.length; offset+=300) {
    const batch = modules.writeBatch(db);
    for(const document of materials.slice(offset, offset+300)) {
      if(!document?.id) continue;
      const blockIds = new Set((document.blocks || []).map(block=>block.id));
      const packet = {
        document,
        notesByBlockId:Object.fromEntries(Object.entries(library.notesByBlockId || {}).filter(([blockId])=>blockIds.has(blockId))),
        documentNote:library.documentNotes?.[document.id] || "",
        highlights:(library.highlights || []).filter(blockId=>blockIds.has(blockId)),
        bookmarks:(library.bookmarks || []).filter(blockId=>blockIds.has(blockId)),
        readingPosition:Number(library.readingPosition?.[document.id]) || 0,
        revision:Number(library.revisionByDocument?.[document.id]) || 0,
      };
      const payloadJson = JSON.stringify(packet);
      if(payloadJson.length > 700000) throw new Error("One study material is too large to synchronize. Split it into smaller sections and try again.");
      const materialId = await studyDocumentId(profileUid, document.id);
      const materialRef = modules.doc(db, "households", householdId, "memberStudyMaterials", currentUser.uid, "documents", materialId);
      batch.set(materialRef, {
        accountUid:currentUser.uid,
        profileUid,
        documentId:document.id,
        sourceUrl:document.sourceUrl || "",
        payloadJson,
        revision:packet.revision || document.updatedAtEpochMillis || Date.now(),
        updatedAt:modules.serverTimestamp()
      }, {merge:true});
    }
    await batch.commit();
  }

}



export function normalizeHouseholdInvitationCode(value){
  const compact=String(value||"").trim().toUpperCase().replace(/[^A-Z0-9]/g,"");
  if(compact.length<6||compact.length>32)throw new Error("Enter a valid household invitation code.");
  if(compact.length===8)return `${compact.slice(0,4)}-${compact.slice(4)}`;
  if(compact.length===10)return `${compact.slice(0,5)}-${compact.slice(5)}`;
  return compact;
}
export async function householdStatus(){
  if(!currentUser)return {signedIn:false,householdId:"",role:"",displayName:""};
  const snapshot=await modules.getDoc(modules.doc(db,"users",currentUser.uid));
  const data=snapshot.data()||{};return {signedIn:true,householdId:data.householdId||"",role:data.role||"",displayName:data.displayName||currentUser.displayName||""};
}
export async function validateHouseholdInvitation(value){
  if(!currentUser)throw new Error("Sign in with Google first.");
  const canonical=normalizeHouseholdInvitationCode(value),compact=canonical.replace(/-/g,"");
  const candidates=[...new Set([canonical,compact,compact.length===8?`${compact.slice(0,4)}-${compact.slice(4)}`:null,compact.length===10?`${compact.slice(0,5)}-${compact.slice(5)}`:null].filter(Boolean))];
  for(const code of candidates){const snapshot=await modules.getDoc(modules.doc(db,"householdInvites",code));if(snapshot.exists()){const data=snapshot.data(),seconds=Number(data.expiresAtEpochSeconds||0);if(data.status!=="active")throw new Error("That invitation has already been used or cancelled.");if(seconds&&seconds<=Math.floor(Date.now()/1000))throw new Error("That invitation has expired. Ask the organizer for a new one.");return {code,householdId:data.householdId||""};}}
  throw new Error("That invitation code was not found. Check every character or ask the organizer for a fresh code.");
}
