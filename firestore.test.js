const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_scholar_01";
const BOB_UID = "bob_reader_02";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTs() {
  return new Date(Date.now() - 1000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// ---------------------------------------------------------------------------
// 1. Unauthenticated Rejections
// ---------------------------------------------------------------------------
test("Unauthenticated user: denied read/write on users, rag_books, rag_folders, rag_chunks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("rag_books").where("isShared", "==", true).get()
  );
  await assertFails(
    unauthDb.collection("rag_folders").where("isShared", "==", true).get()
  );
  await assertFails(
    unauthDb.collection("rag_chunks").where("isShared", "==", true).get()
  );
});

// ---------------------------------------------------------------------------
// 2. User Profile & Chat History Isolation
// ---------------------------------------------------------------------------
test("Authenticated owner: can create/read own profile and chat session, cross-user denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Dr. Alice",
      email: "alice@example.com",
      lastActiveAt: serverTs(),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("sess_1")
      .set({
        id: "sess_1",
        userId: ALICE_UID,
        title: "معركة قادش",
        createdAt: serverTs(),
        updatedAt: serverTs(),
      })
  );

  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("sess_1")
      .get()
  );
});

// ---------------------------------------------------------------------------
// 3. Cloud Firebase RAG: Books -> Small Folders -> Chunks
// ---------------------------------------------------------------------------
function validBook(ownerId, isShared = true) {
  return {
    bookId: "book_pharaonic_02",
    ownerId,
    isShared,
    title: "تاريخ مصر الفرعونية (2)",
    author: "د. داليا محمد سليمان",
    eraCategory: "الدولة الوسطى والحديثة",
    summary: "يتناول عصر الدولة الوسطى والهكسوس وعصر الإمبراطورية والرمسيسيين.",
    pageCount: 163,
    folderCount: 8,
    chunkCount: 48,
    keywords: ["رمسيس الثاني", "قادش", "أبو سمبل"],
    createdAt: serverTs(),
    updatedAt: serverTs(),
  };
}

function validFolder(ownerId, isShared = true) {
  return {
    folderId: "book_pharaonic_02_f05",
    bookId: "book_pharaonic_02",
    bookTitle: "تاريخ مصر الفرعونية (2)",
    ownerId,
    isShared,
    folderIndex: 5,
    folderTitle: "الأسرة التاسعة عشرة والملك رمسيس الثاني ومعركة قادش",
    category: "عصر الرمسيسيين",
    pageRange: "ص 110 - 135",
    topicSummary: "يتناول تأسيس الأسرة 19 وحروب سيتي الأول وعهد رمسيس الثاني ومعاهدة السلام.",
    chunkCount: 6,
    keywords: ["رمسيس الثاني", "معركة قادش", "خاتوسيلي"],
    createdAt: serverTs(),
    updatedAt: serverTs(),
  };
}

function validChunk(ownerId, isShared = true) {
  return {
    chunkId: "book_pharaonic_02_f05_c01",
    bookId: "book_pharaonic_02",
    bookTitle: "تاريخ مصر الفرعونية (2)",
    folderId: "book_pharaonic_02_f05",
    folderTitle: "الأسرة التاسعة عشرة والملك رمسيس الثاني ومعركة قادش",
    ownerId,
    isShared,
    chunkIndex: 1,
    pageRange: "ص 118 - 122",
    title: "بنود معاهدة السلام المصرية الحيثية",
    content: "أبرم الملك رمسيس الثاني في العام الحادي والعشرين من حكمه معاهدة سلام مع خاتوسيلي الثالث ملك الحيثيين.",
    keywords: ["معاهدة السلام", "قادش", "رمسيس الثاني"],
    createdAt: serverTs(),
    updatedAt: serverTs(),
  };
}

test("Authenticated user: can create valid RagBook, RagFolder, and RagChunk", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("rag_books").doc("book_pharaonic_02").set(validBook(ALICE_UID, true))
  );
  await assertSucceeds(
    aliceDb.collection("rag_folders").doc("book_pharaonic_02_f05").set(validFolder(ALICE_UID, true))
  );
  await assertSucceeds(
    aliceDb.collection("rag_chunks").doc("book_pharaonic_02_f05_c01").set(validChunk(ALICE_UID, true))
  );
});

test("Security bounds: rejects ownerId spoofing and shadow fields on RagBook create", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Spoofed ownerId
  await assertFails(
    aliceDb.collection("rag_books").doc("book_pharaonic_02").set(validBook(BOB_UID, true))
  );

  // Shadow / ghost field injection
  await assertFails(
    aliceDb.collection("rag_books").doc("book_pharaonic_02").set({
      ...validBook(ALICE_UID, true),
      isAdmin: true,
    })
  );
});

test("Client query alignment: shared books, folders, and folder-scoped chunks succeed for all signed-in users", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await aliceDb.collection("rag_books").doc("book_pharaonic_02").set(validBook(ALICE_UID, true));
  await aliceDb.collection("rag_folders").doc("book_pharaonic_02_f05").set(validFolder(ALICE_UID, true));
  await aliceDb.collection("rag_chunks").doc("book_pharaonic_02_f05_c01").set(validChunk(ALICE_UID, true));

  // Bob queries shared books, shared folders, and chunks inside a matched folder
  await assertSucceeds(
    bobDb.collection("rag_books").where("isShared", "==", true).get()
  );
  await assertSucceeds(
    bobDb.collection("rag_folders").where("isShared", "==", true).get()
  );
  await assertSucceeds(
    bobDb
      .collection("rag_chunks")
      .where("isShared", "==", true)
      .where("folderId", "==", "book_pharaonic_02_f05")
      .get()
  );
});

test("Cross-user isolation: non-owner cannot read private RAG docs or modify/delete shared RAG docs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Private book owned by Alice
  await aliceDb.collection("rag_books").doc("book_pharaonic_02").set(validBook(ALICE_UID, false));

  // Bob cannot get Alice's private book
  await assertFails(bobDb.collection("rag_books").doc("book_pharaonic_02").get());

  // Unfiltered collection list fails because rules are not filters
  await assertFails(bobDb.collection("rag_books").get());

  // Even when shared, Bob cannot update or delete Alice's book
  await aliceDb.collection("rag_books").doc("book_pharaonic_02").update({
    isShared: true,
    updatedAt: serverTs(),
  });
  await assertSucceeds(bobDb.collection("rag_books").doc("book_pharaonic_02").get());
  await assertFails(
    bobDb.collection("rag_books").doc("book_pharaonic_02").update({
      title: "Hijacked Title",
      updatedAt: serverTs(),
    })
  );
  await assertFails(bobDb.collection("rag_books").doc("book_pharaonic_02").delete());
});

test("Immutability guard: owner cannot mutate existing createdAt on user profile or chat session", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const initialCreated = new Date(Date.now() - 10000);
  const laterTs = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Dr. Alice",
      createdAt: initialCreated,
      lastActiveAt: initialCreated,
    })
  );

  // Updating displayName/lastActiveAt without mutating createdAt succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Prof. Alice",
      lastActiveAt: laterTs,
    })
  );

  // Mutating createdAt on existing profile is rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: laterTs,
      lastActiveAt: laterTs,
    })
  );

  const sessionRef = aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_imm_1");
  await assertSucceeds(
    sessionRef.set({
      id: "sess_imm_1",
      userId: ALICE_UID,
      title: "عنوان مبدئي",
      createdAt: initialCreated,
      updatedAt: initialCreated,
    })
  );

  // Updating title and updatedAt succeeds
  await assertSucceeds(
    sessionRef.update({
      title: "عنوان محدث",
      updatedAt: laterTs,
    })
  );

  // Mutating createdAt on existing session is rejected
  await assertFails(
    sessionRef.update({
      createdAt: laterTs,
      updatedAt: laterTs,
    })
  );
});

test("Private RAG query alignment: owner can list own private books/folders/chunks while non-owner cannot", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await aliceDb.collection("rag_books").doc("book_pharaonic_02").set(validBook(ALICE_UID, false));
  await aliceDb.collection("rag_folders").doc("book_pharaonic_02_f05").set(validFolder(ALICE_UID, false));
  await aliceDb.collection("rag_chunks").doc("book_pharaonic_02_f05_c01").set(validChunk(ALICE_UID, false));

  await assertSucceeds(
    aliceDb.collection("rag_books").where("ownerId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    aliceDb.collection("rag_folders").where("ownerId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    aliceDb
      .collection("rag_chunks")
      .where("ownerId", "==", ALICE_UID)
      .where("folderId", "==", "book_pharaonic_02_f05")
      .get()
  );

  await assertFails(
    bobDb.collection("rag_books").where("ownerId", "==", ALICE_UID).get()
  );
});

// ---------------------------------------------------------------------------
// 4. Bounded String List Validation Across All Elements (0..49)
// ---------------------------------------------------------------------------
test("isBoundedStringList: validates type and 1..100 length for every element, not just index 0", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Valid empty list and valid 40-element list on RagBook succeed
  const fortyValid = Array.from({ length: 40 }, (_, i) => `كلمة_مفتاحية_${i + 1}`);
  await assertSucceeds(
    aliceDb.collection("rag_books").doc("book_empty_kw").set({
      ...validBook(ALICE_UID, true),
      bookId: "book_empty_kw",
      keywords: [],
    })
  );
  await assertSucceeds(
    aliceDb.collection("rag_books").doc("book_max_kw").set({
      ...validBook(ALICE_UID, true),
      bookId: "book_max_kw",
      keywords: fortyValid,
    })
  );

  // Valid 50-element list on RagFolder succeeds
  const fiftyValid = Array.from({ length: 50 }, (_, i) => `موضوع_${i + 1}`);
  await assertSucceeds(
    aliceDb.collection("rag_folders").doc("book_max_kw_f01").set({
      ...validFolder(ALICE_UID, true),
      folderId: "book_max_kw_f01",
      bookId: "book_max_kw",
      keywords: fiftyValid,
    })
  );

  // Reject non-string at index 1 (previously bypassed when only arr[0] was checked)
  await assertFails(
    aliceDb.collection("rag_books").doc("book_bad_idx1").set({
      ...validBook(ALICE_UID, true),
      bookId: "book_bad_idx1",
      keywords: ["رمسيس الثاني", 12345],
    })
  );

  // Reject non-string at middle index 15 and tail index 39
  const badIdx15 = [...fortyValid];
  badIdx15[15] = { nested: "invalid" };
  await assertFails(
    aliceDb.collection("rag_books").doc("book_bad_idx15").set({
      ...validBook(ALICE_UID, true),
      bookId: "book_bad_idx15",
      keywords: badIdx15,
    })
  );

  const badIdx39 = [...fortyValid];
  badIdx39[39] = false;
  await assertFails(
    aliceDb.collection("rag_books").doc("book_bad_idx39").set({
      ...validBook(ALICE_UID, true),
      bookId: "book_bad_idx39",
      keywords: badIdx39,
    })
  );

  // Reject empty string "" at tail index and >100-char string at index 49 on RagFolder
  await assertFails(
    aliceDb.collection("rag_books").doc("book_empty_elem").set({
      ...validBook(ALICE_UID, true),
      bookId: "book_empty_elem",
      keywords: ["رمسيس", "قادش", ""],
    })
  );

  const badFolderIdx49 = [...fiftyValid];
  badFolderIdx49[49] = "أ".repeat(101);
  await assertFails(
    aliceDb.collection("rag_folders").doc("book_max_kw_f02").set({
      ...validFolder(ALICE_UID, true),
      folderId: "book_max_kw_f02",
      bookId: "book_max_kw",
      keywords: badFolderIdx49,
    })
  );

  // Reject list exceeding maxLen (41 on RagBook, 51 on RagFolder)
  await assertFails(
    aliceDb.collection("rag_books").doc("book_over_max").set({
      ...validBook(ALICE_UID, true),
      bookId: "book_over_max",
      keywords: [...fortyValid, "زيادة"],
    })
  );
  await assertFails(
    aliceDb.collection("rag_folders").doc("book_max_kw_f03").set({
      ...validFolder(ALICE_UID, true),
      folderId: "book_max_kw_f03",
      bookId: "book_max_kw",
      keywords: [...fiftyValid, "زيادة"],
    })
  );

  // Reject delimiter-injection attempt trying to split an over-100-char string
  await assertFails(
    aliceDb.collection("rag_books").doc("book_delim_inject").set({
      ...validBook(ALICE_UID, true),
      bookId: "book_delim_inject",
      keywords: ["أ".repeat(60) + "\u001F" + "ب".repeat(60)],
    })
  );
});

// ---------------------------------------------------------------------------
// 5. Parent Consistency, Ownership Immutability & Sharing Revocation
// ---------------------------------------------------------------------------
test("Sharing & parent consistency: blocks orphan/cross-owner/mismatched-share writes and revokes child get immediately", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // 1. Cannot create folder when parent book does not exist
  await assertFails(
    aliceDb.collection("rag_folders").doc("orphan_f01").set({
      ...validFolder(ALICE_UID, true),
      folderId: "orphan_f01",
      bookId: "nonexistent_book",
    })
  );

  // 2. Alice creates a shared book + folder + chunk
  await assertSucceeds(
    aliceDb.collection("rag_books").doc("book_pharaonic_02").set(validBook(ALICE_UID, true))
  );
  await assertSucceeds(
    aliceDb.collection("rag_folders").doc("book_pharaonic_02_f05").set(validFolder(ALICE_UID, true))
  );
  await assertSucceeds(
    aliceDb.collection("rag_chunks").doc("book_pharaonic_02_f05_c01").set(validChunk(ALICE_UID, true))
  );

  // 3. Bob cannot attach his own folder or chunk under Alice's book
  await assertFails(
    bobDb.collection("rag_folders").doc("bob_hijack_f01").set({
      ...validFolder(BOB_UID, true),
      folderId: "bob_hijack_f01",
      bookId: "book_pharaonic_02",
    })
  );
  await assertFails(
    bobDb.collection("rag_chunks").doc("bob_hijack_c01").set({
      ...validChunk(BOB_UID, true),
      chunkId: "bob_hijack_c01",
      bookId: "book_pharaonic_02",
      folderId: "book_pharaonic_02_f05",
    })
  );

  // 4. Alice cannot create a shared folder/chunk under a private book (mismatched isShared)
  await assertSucceeds(
    aliceDb.collection("rag_books").doc("book_private_only").set({
      ...validBook(ALICE_UID, false),
      bookId: "book_private_only",
    })
  );
  await assertFails(
    aliceDb.collection("rag_folders").doc("book_private_only_f01").set({
      ...validFolder(ALICE_UID, true),
      folderId: "book_private_only_f01",
      bookId: "book_private_only",
    })
  );

  // 5. Owner cannot mutate ownerId on update
  await assertFails(
    aliceDb.collection("rag_books").doc("book_pharaonic_02").update({
      ownerId: BOB_UID,
      updatedAt: serverTs(),
    })
  );

  // 6. While shared, Bob can get book, folder, and chunk
  await assertSucceeds(bobDb.collection("rag_books").doc("book_pharaonic_02").get());
  await assertSucceeds(bobDb.collection("rag_folders").doc("book_pharaonic_02_f05").get());
  await assertSucceeds(bobDb.collection("rag_chunks").doc("book_pharaonic_02_f05_c01").get());

  // 7. Instant revocation on parent book: even before child docs are updated, Bob's get() on child folder & chunk is denied!
  await assertSucceeds(
    aliceDb.collection("rag_books").doc("book_pharaonic_02").update({
      isShared: false,
      updatedAt: serverTs(),
    })
  );
  await assertFails(bobDb.collection("rag_books").doc("book_pharaonic_02").get());
  await assertFails(bobDb.collection("rag_folders").doc("book_pharaonic_02_f05").get());
  await assertFails(bobDb.collection("rag_chunks").doc("book_pharaonic_02_f05_c01").get());

  // Owner Alice can still read her own revoked book, folder, and chunk, and cascade isShared = false to children
  await assertSucceeds(aliceDb.collection("rag_folders").doc("book_pharaonic_02_f05").get());
  await assertSucceeds(aliceDb.collection("rag_chunks").doc("book_pharaonic_02_f05_c01").get());
  await assertSucceeds(
    aliceDb.collection("rag_folders").doc("book_pharaonic_02_f05").update({
      isShared: false,
      updatedAt: serverTs(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("rag_chunks").doc("book_pharaonic_02_f05_c01").update({
      isShared: false,
      updatedAt: serverTs(),
    })
  );
});


