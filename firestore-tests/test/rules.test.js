// Firestore security rules: pairing, family privacy and sync. Runs against the local emulator.
import { after, before, beforeEach, describe, test } from "node:test";
import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { arrayUnion, doc, getDoc, getDocs, collection, setDoc, updateDoc, deleteDoc } from "firebase/firestore";

const FID = "fam1";
const CODE = "123456";
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-birthbaby",
    firestore: { rules: readFileSync(new URL("../../firestore.rules", import.meta.url), "utf8") },
  });
});
after(() => env.cleanup());

const as = (uid) => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();

/** A family the mother has just created, with its pairing code. */
async function seed(members = ["mum"]) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "families", FID), { code: CODE, members, settings: { motherName: "Mum" } });
    await setDoc(doc(db, "codes", CODE), { familyId: FID });
  });
}

beforeEach(() => env.clearFirestore());

describe("creating a family (mother's phone)", () => {
  test("mother can create a family with only herself as a member", async () => {
    await assertSucceeds(setDoc(doc(as("mum"), "families", FID), { code: CODE, members: ["mum"], settings: {} }));
    await assertSucceeds(setDoc(doc(as("mum"), "codes", CODE), { familyId: FID }));
  });

  test("cannot create a family on someone else's behalf", async () => {
    await assertFails(setDoc(doc(as("mum"), "families", FID), { code: CODE, members: ["mum", "stranger"] }));
  });

  test("must be signed in", async () => {
    await assertFails(setDoc(doc(anon(), "families", FID), { code: CODE, members: [] }));
    await assertFails(getDoc(doc(anon(), "codes", CODE)));
  });

  test("an existing pairing code cannot be overwritten to point at another family", async () => {
    await seed();
    await assertFails(setDoc(doc(as("attacker"), "codes", CODE), { familyId: "attackers-family" }));
  });
});

describe("pairing (partner's phone joins with the code)", () => {
  test("partner can look up the code and add himself", async () => {
    await seed();
    const db = as("partner");
    const code = await assertSucceeds(getDoc(doc(db, "codes", CODE)));
    await assertSucceeds(updateDoc(doc(db, "families", code.data().familyId), { members: arrayUnion("partner") }));
    await assertSucceeds(getDoc(doc(db, "families", FID)));
  });

  test("pairing codes cannot be listed", async () => {
    await seed();
    await assertFails(getDocs(collection(as("partner"), "codes")));
  });

  test("while joining, nothing else can be changed", async () => {
    await seed();
    await assertFails(updateDoc(doc(as("partner"), "families", FID), {
      members: arrayUnion("partner"),
      "settings.motherName": "Changed",
    }));
  });

  test("cannot add someone else instead of yourself", async () => {
    await seed();
    await assertFails(updateDoc(doc(as("partner"), "families", FID), { members: arrayUnion("someone-else") }));
  });

  test("cannot remove the mother while joining", async () => {
    await seed();
    await assertFails(updateDoc(doc(as("partner"), "families", FID), { members: ["partner"] }));
  });

  test("re-joining with the same phone is harmless", async () => {
    await seed(["mum", "partner"]);
    await assertSucceeds(updateDoc(doc(as("partner"), "families", FID), { members: arrayUnion("partner") }));
  });

  // Accepted for now (Sohrab, 2026-10-30): the code keeps working after both phones are
  // paired, so anyone who learns the 6 digits can join. This records that behaviour;
  // flip it to assertFails if the code is ever made single-use.
  test("ACCEPTED: the pairing code still works after both phones are paired", async () => {
    await seed(["mum", "partner"]);
    await assertSucceeds(updateDoc(doc(as("stranger"), "families", FID), { members: arrayUnion("stranger") }));
  });
});

describe("family privacy and sync", () => {
  test("a stranger cannot read the family, contractions or alerts", async () => {
    await seed(["mum", "partner"]);
    const db = as("stranger");
    await assertFails(getDoc(doc(db, "families", FID)));
    await assertFails(getDocs(collection(db, "families", FID, "contractions")));
    await assertFails(getDocs(collection(db, "families", FID, "alerts")));
    await assertFails(setDoc(doc(db, "families", FID, "alerts", "x"), { type: "WATERS", createdMs: 1, by: "MOTHER" }));
  });

  test("both phones can log and remove contractions", async () => {
    await seed(["mum", "partner"]);
    await assertSucceeds(setDoc(doc(as("mum"), "families", FID, "contractions", "c1"), { startMs: 1, by: "MOTHER" }));
    await assertSucceeds(updateDoc(doc(as("partner"), "families", FID, "contractions", "c1"), { endMs: 2 }));
    await assertSucceeds(deleteDoc(doc(as("partner"), "families", FID, "contractions", "c1")));
  });

  test("mother raises an alert, partner marks it ringing and acknowledges it", async () => {
    await seed(["mum", "partner"]);
    const ref = (db) => doc(db, "families", FID, "alerts", "a1");
    await assertSucceeds(setDoc(ref(as("mum")), { type: "WATERS", createdMs: 1, by: "MOTHER" }));
    await assertSucceeds(updateDoc(ref(as("partner")), { ringingMs: 2 }));
    await assertSucceeds(updateDoc(ref(as("partner")), { ackedMs: 3 }));
    const snap = await getDoc(ref(as("mum")));
    if (snap.data().ackedMs !== 3) throw new Error("mother's phone should see the acknowledgement");
  });

  test("partner can save Settings and send a heartbeat", async () => {
    await seed(["mum", "partner"]);
    await assertSucceeds(updateDoc(doc(as("partner"), "families", FID), { "settings.maxGapMinutes": 4 }));
    await assertSucceeds(updateDoc(doc(as("partner"), "families", FID), { partnerSeenMs: 123 }));
  });

  test("both phones can write diagnostics traces", async () => {
    await seed(["mum", "partner"]);
    await assertSucceeds(setDoc(doc(as("partner"), "families", FID, "trace", "t1"), { ts: 1, event: "x" }));
  });
});
