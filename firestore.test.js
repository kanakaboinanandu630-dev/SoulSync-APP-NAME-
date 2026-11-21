const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_forever_1";
const BOB_UID = "bob_forever_2";
const EVE_UID = "eve_intruder_3";
const ADMIN_UID = "admin_mod_9";
const ADMIN_EMAIL = "kanakaboinanandu630@gmail.com";

const FAKE_PUB_KEY_A = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEAlicePublicKeyBase64Sample000001";
const FAKE_PUB_KEY_B = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEBobPublicKeyBase64Sample00000002";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastDate = (offsetMs = 5000) => new Date(Date.now() - offsetMs);
const futureDate = (offsetMs = 60000) => new Date(Date.now() + offsetMs);

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

async function seedConnectedCouple(coupleId = "couple_ab_1") {
  const created = pastDate(20000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("couples").doc(coupleId).set({
      partnerAId: ALICE_UID,
      partnerBId: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      inviteCode: "LOVE2026",
      partnerAApproved: true,
      partnerBApproved: true,
      partnerAPublicKey: FAKE_PUB_KEY_A,
      partnerBPublicKey: FAKE_PUB_KEY_B,
      partnerAName: "Alice",
      partnerBName: "Bob",
      status: "connected",
      disappearingSeconds: 0,
      createdAt: created,
      updatedAt: created,
    });
  });
  return coupleId;
}

// -----------------------------------------------------------------------------
// 1. Unauthenticated Denial (Zero-Trust Default Deny)
// -----------------------------------------------------------------------------
test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("couples").get());
  await assertFails(unauthDb.collection("encryptedMessages").get());
  await assertFails(unauthDb.collection("media").get());
  await assertFails(unauthDb.collection("calls").get());
  await assertFails(unauthDb.collection("loveNotes").get());
  await assertFails(unauthDb.collection("notifications").get());
  await assertFails(unauthDb.collection("reports").get());
  await assertFails(unauthDb.collection("inviteCodes").doc("LOVE2026").get());
});

// -----------------------------------------------------------------------------
// 2. Users & Split-Collection PII Isolation + Red Team Shadow/Timestamp Tests
// -----------------------------------------------------------------------------
test("User profile & private PII isolation enforce ownership and schema bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  // Valid Kotlin create payload (no @DocumentId 'id' key)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "Alice Rose",
      publicKeyBase64: FAKE_PUB_KEY_A,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject shadow/ghost field injection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: pastDate(1000),
    })
  );

  // Reject future tampered timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      username: "Future Alice",
      updatedAt: futureDate(),
    })
  );

  // Reject creating profile with forbidden '@DocumentId' id field
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      id: BOB_UID,
      userId: BOB_UID,
      username: "Bob",
      publicKeyBase64: FAKE_PUB_KEY_B,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Owner writes private PII account settings
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("private")
      .doc("account")
      .set({
        email: "alice@example.com",
        themePalette: "rose_quartz",
        darkMode: true,
        notificationsEnabled: true,
        readReceiptsEnabled: true,
        typingIndicatorsEnabled: true,
        disappearingSeconds: 0,
        pinEnabled: false,
        biometricEnabled: false,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Owner can list own private subcollection (empty or populated)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").get()
  );

  // PII Blanket Test: Bob cannot read or list Alice's private PII doc
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("private")
      .doc("account")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private").get()
  );
});

// -----------------------------------------------------------------------------
// 3. InviteCode & Dual-Approval Couple Connection Lifecycle
// -----------------------------------------------------------------------------
test("Dual-approval pairing flow prevents state shortcutting and invite scraping", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const created = pastDate(10000);
  const coupleId = "couple_pair_1";
  const code = "ROSE8821";

  // Step 1: Alice creates pending couple
  await assertSucceeds(
    aliceDb.collection("couples").doc(coupleId).set({
      partnerAId: ALICE_UID,
      memberUids: [ALICE_UID],
      inviteCode: code,
      partnerAApproved: false,
      partnerBApproved: false,
      partnerAPublicKey: FAKE_PUB_KEY_A,
      partnerAName: "Alice",
      status: "pending_partner",
      disappearingSeconds: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Step 2: Alice publishes secret invite code
  await assertSucceeds(
    aliceDb.collection("inviteCodes").doc(code).set({
      code,
      coupleId,
      creatorId: ALICE_UID,
      creatorName: "Alice",
      creatorPublicKey: FAKE_PUB_KEY_A,
      redeemed: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Invite codes MUST NOT be listable/scrapable
  await assertFails(bobDb.collection("inviteCodes").get());

  // Bob can look up exact code
  await assertSucceeds(bobDb.collection("inviteCodes").doc(code).get());

  // State Shortcutting Attack: Bob cannot jump straight to 'connected'
  await assertFails(
    bobDb.collection("couples").doc(coupleId).update({
      partnerBId: BOB_UID,
      partnerBName: "Bob",
      partnerBPublicKey: FAKE_PUB_KEY_B,
      memberUids: [ALICE_UID, BOB_UID],
      partnerAApproved: true,
      partnerBApproved: true,
      status: "connected",
      updatedAt: pastDate(2000),
    })
  );

  // Step 3: Bob redeems code & joins -> pending_approval
  await assertSucceeds(
    bobDb.collection("inviteCodes").doc(code).update({
      redeemed: true,
      redeemedByUid: BOB_UID,
      updatedAt: pastDate(2000),
    })
  );

  await assertSucceeds(
    bobDb.collection("couples").doc(coupleId).update({
      partnerBId: BOB_UID,
      partnerBName: "Bob",
      partnerBPublicKey: FAKE_PUB_KEY_B,
      memberUids: [ALICE_UID, BOB_UID],
      partnerBApproved: true,
      status: "pending_approval",
      updatedAt: pastDate(2000),
    })
  );

  // Premature Messaging Attack: Cannot message before Partner A approves
  await assertFails(
    bobDb.collection("encryptedMessages").doc("msg_early").set({
      conversationId: coupleId,
      coupleId,
      memberUids: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      ciphertextBase64: "ZW5jcnlwdGVkX2RhdGE=",
      ivBase64: "MTIzNDU2Nzg5MDEy",
      messageType: "text",
      forwarded: false,
      deliveryStatus: "sent",
      deleted: false,
      createdAt: pastDate(1500),
      updatedAt: pastDate(1500),
    })
  );

  // Step 4: Alice approves -> connected!
  await assertSucceeds(
    aliceDb.collection("couples").doc(coupleId).update({
      partnerAApproved: true,
      status: "connected",
      updatedAt: pastDate(1000),
    })
  );

  // Filtered Kotlin query succeeds for Alice & Bob; Eve is denied
  await assertSucceeds(
    aliceDb.collection("couples").where("memberUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(eveDb.collection("couples").doc(coupleId).get());
  await assertFails(eveDb.collection("couples").get());
});

// -----------------------------------------------------------------------------
// 4. EncryptedMessages, Conversations, Media & LoveNotes Security
// -----------------------------------------------------------------------------
test("Connected couple can exchange E2EE messages, media, and love notes; outsider blocked", async () => {
  const coupleId = await seedConnectedCouple();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const now = pastDate(3000);

  // Conversation creation
  await assertSucceeds(
    aliceDb.collection("conversations").doc(coupleId).set({
      coupleId,
      memberUids: [ALICE_UID, BOB_UID],
      disappearingSeconds: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Encrypted message send by Alice
  await assertSucceeds(
    aliceDb.collection("encryptedMessages").doc("msg_1").set({
      conversationId: coupleId,
      coupleId,
      memberUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      ciphertextBase64: "Y2lwaGVydGV4dF9hZXNfZ2Nt",
      ivBase64: "MTIzNDU2Nzg5MDEy",
      messageType: "text",
      forwarded: false,
      deliveryStatus: "sent",
      deleted: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can query couple messages with Kotlin-aligned filter
  await assertSucceeds(
    bobDb
      .collection("encryptedMessages")
      .where("coupleId", "==", coupleId)
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  // Unfiltered list query MUST fail
  await assertFails(bobDb.collection("encryptedMessages").get());

  // Bob marks read receipt (allowed), cannot alter ciphertext (denied)
  await assertSucceeds(
    bobDb.collection("encryptedMessages").doc("msg_1").update({
      deliveryStatus: "read",
      readAtEpochMs: Date.now() - 1000,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    bobDb.collection("encryptedMessages").doc("msg_1").update({
      ciphertextBase64: "dGFtcGVyZWQ=",
      updatedAt: pastDate(500),
    })
  );

  // Alice can delete-for-everyone
  await assertSucceeds(
    aliceDb.collection("encryptedMessages").doc("msg_1").update({
      deleted: true,
      ciphertextBase64: "REVMRVRFRA==",
      ivBase64: "MDAwMDAwMDAwMDAw",
      updatedAt: pastDate(500),
    })
  );

  // Eve cannot read or inject messages
  await assertFails(eveDb.collection("encryptedMessages").doc("msg_1").get());
  await assertFails(
    eveDb.collection("encryptedMessages").doc("msg_eve").set({
      conversationId: coupleId,
      coupleId,
      memberUids: [ALICE_UID, BOB_UID],
      senderId: EVE_UID,
      ciphertextBase64: "ZXZl",
      ivBase64: "MTIzNDU2Nzg5MDEy",
      messageType: "text",
      forwarded: false,
      deliveryStatus: "sent",
      deleted: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Encrypted media & love notes
  await assertSucceeds(
    aliceDb.collection("media").doc("media_1").set({
      coupleId,
      memberUids: [ALICE_UID, BOB_UID],
      uploaderId: ALICE_UID,
      mediaType: "photo",
      ciphertextBase64: "ZW5jcnlwdGVkX2ltYWdl",
      ivBase64: "MTIzNDU2Nzg5MDEy",
      mimeType: "image/jpeg",
      sizeBytes: 4096,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("media").where("coupleId", "==", coupleId).where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertFails(eveDb.collection("media").doc("media_1").get());

  await assertSucceeds(
    aliceDb.collection("loveNotes").doc("note_1").set({
      coupleId,
      memberUids: [ALICE_UID, BOB_UID],
      authorId: ALICE_UID,
      ciphertextBase64: "bG92ZV9ub3RlX2NpcGhlcg==",
      ivBase64: "MTIzNDU2Nzg5MDEy",
      mood: "adore",
      pinned: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("loveNotes").doc("note_1").update({
      pinned: true,
      updatedAt: pastDate(500),
    })
  );
});

// -----------------------------------------------------------------------------
// 5. Calls Signaling Terminal Lock, Notifications & Abuse Reports (Admin RBAC)
// -----------------------------------------------------------------------------
test("Call terminal state lock, notifications, disconnect lock, and admin email verification", async () => {
  const coupleId = await seedConnectedCouple();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const spoofedAdminDb = testEnv
    .authenticatedContext("spoof_uid", { email: ADMIN_EMAIL, email_verified: false })
    .firestore();
  const realAdminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const now = pastDate(5000);

  // Call lifecycle: ringing -> active -> ended (terminal)
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      coupleId,
      memberUids: [ALICE_UID, BOB_UID],
      callerId: ALICE_UID,
      calleeId: BOB_UID,
      callType: "video",
      status: "ringing",
      offerSdp: "v=0\r\no=alice 123 2 IN IP4 127.0.0.1",
      mutedByCaller: false,
      mutedByCallee: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("calls").doc("call_1").update({
      status: "active",
      answerSdp: "v=0\r\no=bob 456 2 IN IP4 127.0.0.1",
      startedAtEpochMs: Date.now() - 2000,
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").update({
      status: "ended",
      endedAtEpochMs: Date.now() - 1000,
      updatedAt: pastDate(1000),
    })
  );
  // Terminal State Lock: cannot mutate ended call
  await assertFails(
    bobDb.collection("calls").doc("call_1").update({
      mutedByCallee: true,
      updatedAt: pastDate(500),
    })
  );

  // Notifications: Alice sends to Bob; Bob reads & marks read; Alice cannot read Bob's inbox
  await assertSucceeds(
    aliceDb.collection("notifications").doc("notif_1").set({
      userId: BOB_UID,
      senderId: ALICE_UID,
      coupleId,
      memberUids: [ALICE_UID, BOB_UID],
      type: "love_note",
      title: "New Love Note",
      body: "Alice left you an encrypted love note.",
      read: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("notifications").where("userId", "==", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("notifications").doc("notif_1").update({
      read: true,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(aliceDb.collection("notifications").doc("notif_1").get());

  // Abuse report: Alice submits; unverified spoofed admin rejected; verified admin succeeds
  await assertSucceeds(
    aliceDb.collection("reports").doc("rep_1").set({
      reporterId: ALICE_UID,
      reportedUserId: BOB_UID,
      coupleId,
      category: "safety",
      details: "Test abuse report submission.",
      status: "submitted",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    spoofedAdminDb.collection("reports").doc("rep_1").update({
      status: "resolved",
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    realAdminDb.collection("reports").doc("rep_1").update({
      status: "resolved",
      updatedAt: pastDate(500),
    })
  );

  // Secure Disconnection: locks couple & blocks subsequent messages
  await assertSucceeds(
    aliceDb.collection("couples").doc(coupleId).update({
      status: "disconnected",
      disconnectedByUid: ALICE_UID,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    bobDb.collection("encryptedMessages").doc("msg_after_dc").set({
      conversationId: coupleId,
      coupleId,
      memberUids: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      ciphertextBase64: "YmxvY2tlZA==",
      ivBase64: "MTIzNDU2Nzg5MDEy",
      messageType: "text",
      forwarded: false,
      deliveryStatus: "sent",
      deleted: false,
      createdAt: pastDate(200),
      updatedAt: pastDate(200),
    })
  );
});
