const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot read tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
});

test("Authenticated user: cannot read another user's task", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("tasks").doc("task_1").set({
      userId: BOB_UID,
      title: "Bob's Task",
      category: "Work",
      priority: "MEDIUM",
      isCompleted: false,
      hasReminder: false,
      createdAt: new Date(),
      updatedAt: new Date()
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("task_1").get());
});

test("Authenticated user: can create valid task for themselves", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      userId: ALICE_UID,
      title: "Alice's Task",
      category: "Personal",
      priority: "HIGH",
      isCompleted: false,
      hasReminder: true,
      createdAt: now,
      updatedAt: now
    })
  );
});

test("Authenticated user: cannot create task for another user", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("task_2").set({
      userId: BOB_UID,
      title: "Spoofed Task",
      category: "Work",
      priority: "LOW",
      isCompleted: false,
      hasReminder: false,
      createdAt: now,
      updatedAt: now
    })
  );
});

test("Authenticated user: can read their own tasks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
    userId: ALICE_UID,
    title: "Alice's Task",
    category: "Personal",
    priority: "HIGH",
    isCompleted: false,
    hasReminder: true,
    createdAt: now,
    updatedAt: now
  });

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get());
});
