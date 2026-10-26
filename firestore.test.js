const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
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

function validBook(ownerId, isShared = true, bookId = "book_pharaonic_02") {
  return {
    bookId,
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

function validFolder(
  ownerId,
  isShared = true,
  folderId = "book_pharaonic_02_f05",
  bookId = "book_pharaonic_02"
) {
  return {
    folderId,
    bookId,
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

function validChunk(
  ownerId,
  isShared = true,
  chunkId = "book_pharaonic_02_f05_c01",
  folderId = "book_pharaonic_02_f05",
  bookId = "book_pharaonic_02"
) {
  return {
    chunkId,
    bookId,
    bookTitle: "تاريخ مصر الفرعونية (2)",
    folderId,
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

// ---------------------------------------------------------------------------
// 1. Unauthenticated Rejections
// ---------------------------------------------------------------------------
test("Unauthenticated user: denied get, list, and write on users, rag_books, rag_folders, rag_chunks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("rag_books").doc("book_pharaonic_02").get());
  await assertFails(unauthDb.collection("rag_folders").doc("book_pharaonic_02_f05").get());
  await assertFails(unauthDb.collection("rag_chunks").doc("book_pharaonic_02_f05_c01").get());
  await assertFails(
    unauthDb.collection("rag_books").where("isShared", "==", true).get()
  );
  await assertFails(
    unauthDb.collection("rag_folders").where("isShared", "==", true).get()
  );
  await assertFails(
    unauthDb.collection("rag_chunks").where("isShared", "==", true).get()
  );
  await assertFails(
    unauthDb.collection("rag_books").doc("book_pharaonic_02").set(validBook(ALICE_UID, true))
  );
});

// ---------------------------------------------------------------------------
// 2. Cross-User Access Isolation Across Profiles, Sessions, Messages & RAG
// ---------------------------------------------------------------------------
test("Cross-user access: blocks get, list, create, update, and delete on another user's profile, sessions, messages, and RAG docs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Alice creates profile, session, and message
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Dr. Alice",
      email: "alice@example.com",
      createdAt: serverTs(),
      lastActiveAt: serverTs(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").set({
      id: "sess_1",
      userId: ALICE_UID,
      title: "معركة قادش",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("sess_1")
      .collection("messages")
      .doc("msg_1")
      .set({
        id: "msg_1",
        sessionId: "sess_1",
        userId: ALICE_UID,
        sender: "USER",
        text: "ما هي بنود معاهدة قادش؟",
        timestamp: serverTs(),
        isVoiceResponse: false,
        isError: false,
      })
  );

  // Bob is denied get, list, create, update, delete across Alice's user hierarchy
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hijacked" })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).delete());

  const aliceSessionsAsBob = bobDb.collection("users").doc(ALICE_UID).collection("sessions");
  await assertFails(aliceSessionsAsBob.doc("sess_1").get());
  await assertFails(aliceSessionsAsBob.get());
  await assertFails(
    aliceSessionsAsBob.doc("sess_2").set({
      id: "sess_2",
      userId: ALICE_UID,
      title: "Injected",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertFails(aliceSessionsAsBob.doc("sess_1").delete());

  const aliceMessagesAsBob = aliceSessionsAsBob.doc("sess_1").collection("messages");
  await assertFails(aliceMessagesAsBob.doc("msg_1").get());
  await assertFails(aliceMessagesAsBob.get());
  await assertFails(
    aliceMessagesAsBob.doc("msg_1").update({ text: "Tampered" })
  );
  await assertFails(aliceMessagesAsBob.doc("msg_1").delete());

  // Private RAG hierarchy owned by Alice
  await assertSucceeds(
    aliceDb.collection("rag_books").doc("book_priv").set(validBook(ALICE_UID, false, "book_priv"))
  );
  await assertSucceeds(
    aliceDb
      .collection("rag_folders")
      .doc("book_priv_f01")
      .set(validFolder(ALICE_UID, false, "book_priv_f01", "book_priv"))
  );
  await assertSucceeds(
    aliceDb
      .collection("rag_chunks")
      .doc("book_priv_f01_c01")
      .set(validChunk(ALICE_UID, false, "book_priv_f01_c01", "book_priv_f01", "book_priv"))
  );

  // Bob cannot get or list Alice's private RAG book, folder, or chunk
  await assertFails(bobDb.collection("rag_books").doc("book_priv").get());
  await assertFails(bobDb.collection("rag_folders").doc("book_priv_f01").get());
  await assertFails(bobDb.collection("rag_chunks").doc("book_priv_f01_c01").get());
  await assertFails(bobDb.collection("rag_books").where("ownerId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("rag_folders").where("ownerId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("rag_chunks").where("ownerId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("rag_books").get());
  await assertFails(bobDb.collection("rag_folders").get());
  await assertFails(bobDb.collection("rag_chunks").get());

  // Even on shared RAG docs, Bob cannot update, delete, spoof ownerId, or attach child docs
  await assertSucceeds(
    aliceDb.collection("rag_books").doc("book_shared").set(validBook(ALICE_UID, true, "book_shared"))
  );
  await assertSucceeds(
    aliceDb
      .collection("rag_folders")
      .doc("book_shared_f01")
      .set(validFolder(ALICE_UID, true, "book_shared_f01", "book_shared"))
  );
  await assertSucceeds(
    aliceDb
      .collection("rag_chunks")
      .doc("book_shared_f01_c01")
      .set(validChunk(ALICE_UID, true, "book_shared_f01_c01", "book_shared_f01", "book_shared"))
  );

  await assertFails(
    bobDb.collection("rag_books").doc("book_shared").update({ title: "Hijacked", updatedAt: serverTs() })
  );
  await assertFails(
    bobDb.collection("rag_folders").doc("book_shared_f01").update({ folderTitle: "Hijacked", updatedAt: serverTs() })
  );
  await assertFails(
    bobDb.collection("rag_chunks").doc("book_shared_f01_c01").update({ content: "Hijacked", updatedAt: serverTs() })
  );
  await assertFails(bobDb.collection("rag_books").doc("book_shared").delete());
  await assertFails(bobDb.collection("rag_folders").doc("book_shared_f01").delete());
  await assertFails(bobDb.collection("rag_chunks").doc("book_shared_f01_c01").delete());

  // Cross-owner child attachment denied
  await assertFails(
    bobDb
      .collection("rag_folders")
      .doc("bob_folder_under_alice")
      .set(validFolder(BOB_UID, true, "bob_folder_under_alice", "book_shared"))
  );
  await assertFails(
    bobDb
      .collection("rag_chunks")
      .doc("bob_chunk_under_alice")
      .set(validChunk(BOB_UID, true, "bob_chunk_under_alice", "book_shared_f01", "book_shared"))
  );
});

// ---------------------------------------------------------------------------
// 3. Security Bounds: Spoofing, Ghost Fields & Timestamp Immutability
// ---------------------------------------------------------------------------
test("Security bounds: rejects ownerId spoofing, ghost fields, ID mismatch, and createdAt mutation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const initialCreated = new Date(Date.now() - 10000);
  const laterTs = new Date(Date.now() - 1000);

  // Spoofed ownerId and ghost field on RagBook create
  await assertFails(
    aliceDb.collection("rag_books").doc("book_pharaonic_02").set(validBook(BOB_UID, true))
  );
  await assertFails(
    aliceDb.collection("rag_books").doc("book_pharaonic_02").set({
      ...validBook(ALICE_UID, true),
      isAdmin: true,
    })
  );

  // Path vs payload ID mismatch on session and message
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Dr. Alice",
      createdAt: initialCreated,
      lastActiveAt: initialCreated,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_real").set({
      id: "sess_spoofed",
      userId: ALICE_UID,
      title: "عنوان",
      createdAt: initialCreated,
      updatedAt: initialCreated,
    })
  );

  // createdAt immutability on user profile and chat session
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
  await assertFails(
    sessionRef.update({
      createdAt: laterTs,
      updatedAt: laterTs,
    })
  );
});

// ---------------------------------------------------------------------------
// 4. Client Query Alignment & Multi-Chunk Quota Verification (get + list)
// ---------------------------------------------------------------------------
test("Client query alignment: shared books, book-scoped folders, and 12-chunk folder list queries succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("rag_books").doc("book_pharaonic_02").set(validBook(ALICE_UID, true))
  );
  await assertSucceeds(
    aliceDb.collection("rag_folders").doc("book_pharaonic_02_f05").set(validFolder(ALICE_UID, true))
  );

  // Create 12 chunks inside the same folder (>10 docs) to verify parent get() caching on list queries
  for (let i = 1; i <= 12; i++) {
    const cid = `book_pharaonic_02_f05_c${String(i).padStart(2, "0")}`;
    await assertSucceeds(
      aliceDb.collection("rag_chunks").doc(cid).set({
        ...validChunk(ALICE_UID, true, cid),
        chunkIndex: i,
      })
    );
  }

  const booksSnap = await assertSucceeds(
    bobDb.collection("rag_books").where("isShared", "==", true).get()
  );
  assert.equal(booksSnap.size, 1);

  // Unscoped rag_folders list (without bookId filter) is rejected because bookId could reference a private parent book
  await assertFails(
    bobDb.collection("rag_folders").where("isShared", "==", true).get()
  );

  // Book-scoped rag_folders list (matching FirestoreRagRepository's whereIn("bookId", slice)) succeeds
  const foldersScopedSnap = await assertSucceeds(
    bobDb
      .collection("rag_folders")
      .where("isShared", "==", true)
      .where("bookId", "in", ["book_pharaonic_02"])
      .get()
  );
  assert.equal(foldersScopedSnap.size, 1);

  const chunksSnap = await assertSucceeds(
    bobDb
      .collection("rag_chunks")
      .where("isShared", "==", true)
      .where("folderId", "==", "book_pharaonic_02_f05")
      .get()
  );
  assert.equal(chunksSnap.size, 12);
});

test("Multi-book whereIn folder list: 8 shared books succeed within get() quota, while mixing in 1 private book is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const eightBookIds = Array.from({ length: 8 }, (_, i) => `book_batch_${i + 1}`);
  for (const bid of eightBookIds) {
    await assertSucceeds(
      aliceDb.collection("rag_books").doc(bid).set(validBook(ALICE_UID, true, bid))
    );
    await assertSucceeds(
      aliceDb
        .collection("rag_folders")
        .doc(`${bid}_f01`)
        .set(validFolder(ALICE_UID, true, `${bid}_f01`, bid))
    );
  }

  // Add 1 private book owned by Alice
  await assertSucceeds(
    aliceDb.collection("rag_books").doc("book_priv_extra").set(validBook(ALICE_UID, false, "book_priv_extra"))
  );

  // Querying all 8 shared books in a single whereIn succeeds (8 get() calls <= 10 limit)
  const eightFoldersSnap = await assertSucceeds(
    bobDb
      .collection("rag_folders")
      .where("isShared", "==", true)
      .where("bookId", "in", eightBookIds)
      .get()
  );
  assert.equal(eightFoldersSnap.size, 8);

  // Including a private book in whereIn is rejected by canReadRagFolder()
  await assertFails(
    bobDb
      .collection("rag_folders")
      .where("isShared", "==", true)
      .where("bookId", "in", [...eightBookIds.slice(0, 7), "book_priv_extra"])
      .get()
  );
});

// ---------------------------------------------------------------------------
// 5. Private Parent Documents: Prevent Child Access on BOTH get AND list
// ---------------------------------------------------------------------------
test("Private parent documents: blocks child get AND list when parent book or parent folder is private or missing", async () => {
  // Seed inconsistent parent/child sharing states directly via admin context to verify rules defense-in-depth
  await testEnv.withSecurityRulesDisabled(async (adminCtx) => {
    const adminDb = adminCtx.firestore();

    // Case A: Private parent book (isShared: false), but child folder & chunk have isShared: true
    await adminDb
      .collection("rag_books")
      .doc("priv_book")
      .set(validBook(ALICE_UID, false, "priv_book"));
    await adminDb
      .collection("rag_folders")
      .doc("priv_book_f01")
      .set(validFolder(ALICE_UID, true, "priv_book_f01", "priv_book"));
    await adminDb
      .collection("rag_chunks")
      .doc("priv_book_f01_c01")
      .set(validChunk(ALICE_UID, true, "priv_book_f01_c01", "priv_book_f01", "priv_book"));

    // Case B: Shared parent book (isShared: true), PRIVATE parent folder (isShared: false), child chunk has isShared: true
    await adminDb
      .collection("rag_books")
      .doc("shared_book_priv_folder")
      .set(validBook(ALICE_UID, true, "shared_book_priv_folder"));
    await adminDb
      .collection("rag_folders")
      .doc("priv_folder_only")
      .set(validFolder(ALICE_UID, false, "priv_folder_only", "shared_book_priv_folder"));
    await adminDb
      .collection("rag_chunks")
      .doc("priv_folder_only_c01")
      .set(
        validChunk(
          ALICE_UID,
          true,
          "priv_folder_only_c01",
          "priv_folder_only",
          "shared_book_priv_folder"
        )
      );

    // Case C: Missing/deleted parent book with dangling shared child folder & chunk
    await adminDb
      .collection("rag_folders")
      .doc("dangling_f01")
      .set(validFolder(ALICE_UID, true, "dangling_f01", "deleted_book_id"));
    await adminDb
      .collection("rag_chunks")
      .doc("dangling_f01_c01")
      .set(validChunk(ALICE_UID, true, "dangling_f01_c01", "dangling_f01", "deleted_book_id"));

    // Case D: Fully valid shared book + folder + chunk (to verify scoped queries for valid books still work)
    await adminDb
      .collection("rag_books")
      .doc("clean_shared_book")
      .set(validBook(ALICE_UID, true, "clean_shared_book"));
    await adminDb
      .collection("rag_folders")
      .doc("clean_shared_f01")
      .set(validFolder(ALICE_UID, true, "clean_shared_f01", "clean_shared_book"));
    await adminDb
      .collection("rag_chunks")
      .doc("clean_shared_f01_c01")
      .set(validChunk(ALICE_UID, true, "clean_shared_f01_c01", "clean_shared_f01", "clean_shared_book"));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // --- Case A assertions (Private parent book) ---
  await assertFails(bobDb.collection("rag_books").doc("priv_book").get());
  await assertFails(bobDb.collection("rag_folders").doc("priv_book_f01").get());
  await assertFails(bobDb.collection("rag_chunks").doc("priv_book_f01_c01").get());

  // List queries touching children of private parent book MUST ALSO FAIL (previously leaked via allow list!)
  await assertFails(
    bobDb
      .collection("rag_folders")
      .where("isShared", "==", true)
      .where("bookId", "==", "priv_book")
      .get()
  );
  await assertFails(
    bobDb.collection("rag_folders").where("isShared", "==", true).get()
  );
  await assertFails(
    bobDb
      .collection("rag_chunks")
      .where("isShared", "==", true)
      .where("folderId", "==", "priv_book_f01")
      .get()
  );
  await assertFails(
    bobDb
      .collection("rag_chunks")
      .where("isShared", "==", true)
      .where("bookId", "==", "priv_book")
      .get()
  );

  // --- Case B assertions (Shared book, private parent folder) ---
  await assertSucceeds(bobDb.collection("rag_books").doc("shared_book_priv_folder").get());
  await assertFails(bobDb.collection("rag_folders").doc("priv_folder_only").get());
  await assertFails(bobDb.collection("rag_chunks").doc("priv_folder_only_c01").get());
  await assertFails(
    bobDb
      .collection("rag_chunks")
      .where("isShared", "==", true)
      .where("folderId", "==", "priv_folder_only")
      .get()
  );

  // --- Case C assertions (Missing/deleted parent book) ---
  await assertFails(bobDb.collection("rag_folders").doc("dangling_f01").get());
  await assertFails(bobDb.collection("rag_chunks").doc("dangling_f01_c01").get());
  await assertFails(
    bobDb
      .collection("rag_folders")
      .where("isShared", "==", true)
      .where("bookId", "==", "deleted_book_id")
      .get()
  );
  await assertFails(
    bobDb
      .collection("rag_chunks")
      .where("isShared", "==", true)
      .where("folderId", "==", "dangling_f01")
      .get()
  );

  // --- Case D assertions: Scoped queries to clean_shared_book still succeed for Bob! ---
  const cleanFolders = await assertSucceeds(
    bobDb
      .collection("rag_folders")
      .where("isShared", "==", true)
      .where("bookId", "in", ["clean_shared_book"])
      .get()
  );
  assert.equal(cleanFolders.size, 1);

  const cleanChunks = await assertSucceeds(
    bobDb
      .collection("rag_chunks")
      .where("isShared", "==", true)
      .where("folderId", "==", "clean_shared_f01")
      .get()
  );
  assert.equal(cleanChunks.size, 1);

  // --- Owner Alice can still get and list all her own documents via ownerId ---
  await assertSucceeds(aliceDb.collection("rag_folders").doc("priv_book_f01").get());
  await assertSucceeds(aliceDb.collection("rag_chunks").doc("priv_folder_only_c01").get());
  const aliceFolders = await assertSucceeds(
    aliceDb.collection("rag_folders").where("ownerId", "==", ALICE_UID).get()
  );
  assert.equal(aliceFolders.size, 4);
  const aliceChunks = await assertSucceeds(
    aliceDb.collection("rag_chunks").where("ownerId", "==", ALICE_UID).get()
  );
  assert.equal(aliceChunks.size, 4);
});

// ---------------------------------------------------------------------------
// 6. Revoked Sharing: Immediate Denial on BOTH get AND list + Full Cascade
// ---------------------------------------------------------------------------
test("Revoked sharing: revoking parent book immediately blocks both get and list on folders and chunks before and after cascade", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // 1. Alice creates shared book + folder + chunk
  await assertSucceeds(
    aliceDb.collection("rag_books").doc("book_pharaonic_02").set(validBook(ALICE_UID, true))
  );
  await assertSucceeds(
    aliceDb.collection("rag_folders").doc("book_pharaonic_02_f05").set(validFolder(ALICE_UID, true))
  );
  await assertSucceeds(
    aliceDb.collection("rag_chunks").doc("book_pharaonic_02_f05_c01").set(validChunk(ALICE_UID, true))
  );

  // 2. While shared, Bob succeeds on BOTH get AND list across all 3 collections
  await assertSucceeds(bobDb.collection("rag_books").doc("book_pharaonic_02").get());
  await assertSucceeds(bobDb.collection("rag_folders").doc("book_pharaonic_02_f05").get());
  await assertSucceeds(bobDb.collection("rag_chunks").doc("book_pharaonic_02_f05_c01").get());
  await assertSucceeds(bobDb.collection("rag_books").where("isShared", "==", true).get());
  await assertSucceeds(
    bobDb
      .collection("rag_folders")
      .where("isShared", "==", true)
      .where("bookId", "==", "book_pharaonic_02")
      .get()
  );
  await assertSucceeds(
    bobDb
      .collection("rag_chunks")
      .where("isShared", "==", true)
      .where("folderId", "==", "book_pharaonic_02_f05")
      .get()
  );

  // 3. Alice revokes sharing on parent book ONLY (simulating mid-cascade window before child docs update)
  await assertSucceeds(
    aliceDb.collection("rag_books").doc("book_pharaonic_02").update({
      isShared: false,
      updatedAt: serverTs(),
    })
  );

  // Bob is IMMEDIATELY denied on BOTH get AND list for book, child folder, and child chunk!
  await assertFails(bobDb.collection("rag_books").doc("book_pharaonic_02").get());
  await assertFails(bobDb.collection("rag_folders").doc("book_pharaonic_02_f05").get());
  await assertFails(bobDb.collection("rag_chunks").doc("book_pharaonic_02_f05_c01").get());

  await assertFails(
    bobDb.collection("rag_books").where("bookId", "==", "book_pharaonic_02").get()
  );
  await assertFails(
    bobDb
      .collection("rag_folders")
      .where("isShared", "==", true)
      .where("bookId", "==", "book_pharaonic_02")
      .get()
  );
  await assertFails(
    bobDb.collection("rag_folders").where("isShared", "==", true).get()
  );
  await assertFails(
    bobDb
      .collection("rag_chunks")
      .where("isShared", "==", true)
      .where("folderId", "==", "book_pharaonic_02_f05")
      .get()
  );

  // 4. Owner Alice cascades isShared = false to child folder and chunk
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

  // After full cascade, Bob's general shared-books query returns 0 docs (not Alice's revoked book),
  // and direct get/list on Alice's revoked hierarchy remains denied
  const sharedBooksAfterCascade = await assertSucceeds(
    bobDb.collection("rag_books").where("isShared", "==", true).get()
  );
  assert.equal(sharedBooksAfterCascade.size, 0);

  // Probing rag_chunks on Alice's private/revoked folderId is strictly denied on list as well as get
  await assertFails(
    bobDb
      .collection("rag_chunks")
      .where("isShared", "==", true)
      .where("folderId", "==", "book_pharaonic_02_f05")
      .get()
  );

  await assertFails(bobDb.collection("rag_folders").doc("book_pharaonic_02_f05").get());
  await assertFails(bobDb.collection("rag_chunks").doc("book_pharaonic_02_f05_c01").get());
  await assertFails(
    bobDb.collection("rag_chunks").where("folderId", "==", "book_pharaonic_02_f05").get()
  );

  // 5. Write-time parent consistency: Alice cannot create a shared child under a private parent or orphan parent, nor mutate ownerId
  await assertFails(
    aliceDb.collection("rag_folders").doc("orphan_f01").set({
      ...validFolder(ALICE_UID, true, "orphan_f01", "nonexistent_book"),
    })
  );
  await assertFails(
    aliceDb.collection("rag_folders").doc("mismatched_f01").set({
      ...validFolder(ALICE_UID, true, "mismatched_f01", "book_pharaonic_02"),
    })
  );
  await assertFails(
    aliceDb.collection("rag_books").doc("book_pharaonic_02").update({
      ownerId: BOB_UID,
      updatedAt: serverTs(),
    })
  );
});

// ---------------------------------------------------------------------------
// 7. Invalid Keyword Lists Across Create & Update (0..49 Element Validation)
// ---------------------------------------------------------------------------
test("Invalid keyword lists: rejects non-lists, non-strings at any index, empty/overlong strings, delimiter injection, and oversized arrays on create and update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Valid empty list and valid max-length lists (40 on book/chunk, 50 on folder) succeed
  const fortyValid = Array.from({ length: 40 }, (_, i) => `كلمة_مفتاحية_${i + 1}`);
  const fiftyValid = Array.from({ length: 50 }, (_, i) => `موضوع_${i + 1}`);

  await assertSucceeds(
    aliceDb.collection("rag_books").doc("book_empty_kw").set({
      ...validBook(ALICE_UID, true, "book_empty_kw"),
      keywords: [],
    })
  );
  await assertSucceeds(
    aliceDb.collection("rag_books").doc("book_max_kw").set({
      ...validBook(ALICE_UID, true, "book_max_kw"),
      keywords: fortyValid,
    })
  );
  await assertSucceeds(
    aliceDb.collection("rag_folders").doc("book_max_kw_f01").set({
      ...validFolder(ALICE_UID, true, "book_max_kw_f01", "book_max_kw"),
      keywords: fiftyValid,
    })
  );
  await assertSucceeds(
    aliceDb.collection("rag_chunks").doc("book_max_kw_f01_c01").set({
      ...validChunk(ALICE_UID, true, "book_max_kw_f01_c01", "book_max_kw_f01", "book_max_kw"),
      keywords: fortyValid,
    })
  );

  // Reject non-list keywords field
  await assertFails(
    aliceDb.collection("rag_books").doc("book_not_list").set({
      ...validBook(ALICE_UID, true, "book_not_list"),
      keywords: "رمسيس الثاني",
    })
  );

  // Reject non-string at index 0, index 1, boundary index 10, middle index 25, and tail index 39/49
  for (const [idx, badVal] of [
    [0, 999],
    [1, 12345],
    [10, null],
    [25, { nested: "invalid" }],
    [39, false],
  ]) {
    const mutated = [...fortyValid];
    mutated[idx] = badVal;
    await assertFails(
      aliceDb.collection("rag_books").doc(`book_bad_idx_${idx}`).set({
        ...validBook(ALICE_UID, true, `book_bad_idx_${idx}`),
        keywords: mutated,
      })
    );
  }

  const badFolder49 = [...fiftyValid];
  badFolder49[49] = 987654;
  await assertFails(
    aliceDb.collection("rag_folders").doc("book_max_kw_f_bad49").set({
      ...validFolder(ALICE_UID, true, "book_max_kw_f_bad49", "book_max_kw"),
      keywords: badFolder49,
    })
  );

  // Reject empty string "" at index 0 and tail index, and >100-char string on book, folder, and chunk
  await assertFails(
    aliceDb.collection("rag_books").doc("book_empty_str0").set({
      ...validBook(ALICE_UID, true, "book_empty_str0"),
      keywords: [""],
    })
  );
  await assertFails(
    aliceDb.collection("rag_books").doc("book_empty_elem").set({
      ...validBook(ALICE_UID, true, "book_empty_elem"),
      keywords: ["رمسيس", "قادش", ""],
    })
  );
  await assertFails(
    aliceDb.collection("rag_folders").doc("book_max_kw_f_long").set({
      ...validFolder(ALICE_UID, true, "book_max_kw_f_long", "book_max_kw"),
      keywords: [...fiftyValid.slice(0, 49), "أ".repeat(101)],
    })
  );
  await assertFails(
    aliceDb.collection("rag_chunks").doc("book_max_kw_c_long").set({
      ...validChunk(ALICE_UID, true, "book_max_kw_c_long", "book_max_kw_f01", "book_max_kw"),
      keywords: ["رمسيس", "ب".repeat(101)],
    })
  );

  // Reject oversized keyword lists (41 on book, 51 on folder, 41 on chunk)
  await assertFails(
    aliceDb.collection("rag_books").doc("book_over_max").set({
      ...validBook(ALICE_UID, true, "book_over_max"),
      keywords: [...fortyValid, "زيادة"],
    })
  );
  await assertFails(
    aliceDb.collection("rag_folders").doc("book_max_kw_f_over").set({
      ...validFolder(ALICE_UID, true, "book_max_kw_f_over", "book_max_kw"),
      keywords: [...fiftyValid, "زيادة"],
    })
  );
  await assertFails(
    aliceDb.collection("rag_chunks").doc("book_max_kw_c_over").set({
      ...validChunk(ALICE_UID, true, "book_max_kw_c_over", "book_max_kw_f01", "book_max_kw"),
      keywords: [...fortyValid, "زيادة"],
    })
  );

  // Reject delimiter-injection attempt trying to split an over-100-char string
  await assertFails(
    aliceDb.collection("rag_books").doc("book_delim_inject").set({
      ...validBook(ALICE_UID, true, "book_delim_inject"),
      keywords: ["أ".repeat(60) + "\u001F" + "ب".repeat(60)],
    })
  );

  // Reject invalid keyword list on UPDATE as well as CREATE
  await assertFails(
    aliceDb.collection("rag_books").doc("book_max_kw").update({
      keywords: ["صحيح", 42],
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    aliceDb.collection("rag_chunks").doc("book_max_kw_f01_c01").update({
      keywords: ["صحيح", ""],
      updatedAt: serverTs(),
    })
  );
});
