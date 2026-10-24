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
