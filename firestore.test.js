const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DEPOT_ID = "ppio_main";
const ADMIN_UID = "vinod_admin_uid";
const SUP_UID = "sse_supervisor_uid";
const STOREMATE_UID = "storemate_uid";
const TECH_UID = "tech_uid";
const SHIFT_INCHARGE_UID = "shift_incharge_uid";

const serverTs = () => new Date(Date.now() - 1000);

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: "chinthawarvinod@gmail.com",
    email_verified: true,
  });
}

function supervisorContext() {
  return testEnv.authenticatedContext(SUP_UID, {
    email: "sse.depot@example.com",
    email_verified: true,
  });
}

function storemateContext() {
  return testEnv.authenticatedContext(STOREMATE_UID, {
    email: "storemate@example.com",
    email_verified: true,
  });
}

function techContext() {
  return testEnv.authenticatedContext(TECH_UID, {
    email: "tech@example.com",
    email_verified: true,
  });
}

function shiftInchargeContext() {
  return testEnv.authenticatedContext(SHIFT_INCHARGE_UID, {
    email: "shift.incharge@example.com",
    email_verified: true,
  });
}

async function seedSharedDepotBase() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const depot = db.collection("depots").doc(DEPOT_ID);
    const ts = new Date(Date.now() - 5000);

    await depot.collection("userRoles").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      email: "chinthawarvinod@gmail.com",
      displayName: "Vinod Chinthawar",
      role: "ADMIN",
      staffId: "staff_4",
      updatedByUid: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    });
    await depot.collection("userRoles").doc(SUP_UID).set({
      uid: SUP_UID,
      email: "sse.depot@example.com",
      displayName: "VENKATA KRISHNA",
      role: "SUPERVISOR",
      staffId: "staff_1",
      updatedByUid: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    });
    await depot.collection("userRoles").doc(STOREMATE_UID).set({
      uid: STOREMATE_UID,
      email: "storemate@example.com",
      displayName: "Depot Storemate",
      role: "STOREMATE",
      staffId: "",
      updatedByUid: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    });
    await depot.collection("userRoles").doc(TECH_UID).set({
      uid: TECH_UID,
      email: "tech@example.com",
      displayName: "P. RAM KUMAR",
      role: "TECHNICIAN",
      staffId: "staff_7",
      updatedByUid: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    });
    await depot.collection("userRoles").doc(SHIFT_INCHARGE_UID).set({
      uid: SHIFT_INCHARGE_UID,
      email: "shift.incharge@example.com",
      displayName: "Shift Incharge",
      role: "SHIFT_INCHARGE",
      staffId: "",
      updatedByUid: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    });

    await depot.collection("staff").doc("staff_1").set({
      userId: DEPOT_ID,
      serialNo: 1,
      name: "VENKATA KRISHNA",
      designation: "SSE",
      competency: "NONE",
      stationDuty: false,
      shedDuty: false,
      weeklyRestDay: "NONE",
      createdAt: ts,
      updatedAt: ts,
    });
    await depot.collection("staff").doc("staff_3").set({
      userId: DEPOT_ID,
      serialNo: 3,
      name: "Y. SAI KRISHNA",
      designation: "JE",
      competency: "NONE",
      stationDuty: false,
      shedDuty: false,
      weeklyRestDay: "NONE",
      createdAt: ts,
      updatedAt: ts,
    });
    await depot.collection("staff").doc("staff_7").set({
      userId: DEPOT_ID,
      serialNo: 7,
      name: "P. RAM KUMAR",
      designation: "SR_TECH",
      competency: "NONE",
      stationDuty: true,
      shedDuty: false,
      weeklyRestDay: "SUNDAY",
      createdAt: ts,
      updatedAt: ts,
    });
    await depot.collection("staff").doc("staff_29").set({
      userId: DEPOT_ID,
      serialNo: 29,
      name: "G. RAJESH",
      designation: "KHP",
      competency: "POINTSMAN",
      stationDuty: true,
      shedDuty: true,
      weeklyRestDay: "MONDAY",
      createdAt: ts,
      updatedAt: ts,
    });
  });
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

test("Unauthenticated access is denied across shared depot collections", async () => {
  await seedSharedDepotBase();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const depot = unauthDb.collection("depots").doc(DEPOT_ID);
  await assertFails(depot.collection("staff").get());
  await assertFails(depot.collection("assignments").get());
  await assertFails(depot.collection("dutyDrafts").get());
  await assertFails(depot.collection("leaves").get());
});

test("User role registration defaults to TECHNICIAN, blocks self-escalation & email spoofing, allows Admin assignment", async () => {
  const depotAdmin = adminContext().firestore().collection("depots").doc(DEPOT_ID);

  // Spoofed unverified admin email cannot claim ADMIN
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: "chinthawarvinod@gmail.com",
    email_verified: false,
  }).firestore();
  await assertFails(
    spoofDb.collection("depots").doc(DEPOT_ID).collection("userRoles").doc("spoof_uid").set({
      uid: "spoof_uid",
      email: "chinthawarvinod@gmail.com",
      displayName: "Spoofer",
      role: "ADMIN",
      staffId: "",
      updatedByUid: "spoof_uid",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Verified bootstrap Admin can register himself as ADMIN
  await assertSucceeds(
    depotAdmin.collection("userRoles").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      email: "chinthawarvinod@gmail.com",
      displayName: "Vinod Chinthawar",
      role: "ADMIN",
      staffId: "staff_4",
      updatedByUid: ADMIN_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // New user registering must start as TECHNICIAN and cannot self-assign STOREMATE or SUPERVISOR
  const newcomerDb = testEnv.authenticatedContext("new_user", {
    email: "new@example.com",
    email_verified: true,
  }).firestore();
  const newcomerRoles = newcomerDb.collection("depots").doc(DEPOT_ID).collection("userRoles");

  await assertFails(
    newcomerRoles.doc("new_user").set({
      uid: "new_user",
      email: "new@example.com",
      displayName: "New User",
      role: "SUPERVISOR",
      staffId: "",
      updatedByUid: "new_user",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    newcomerRoles.doc("new_user").set({
      uid: "new_user",
      email: "new@example.com",
      displayName: "New User",
      role: "TECHNICIAN",
      staffId: "",
      updatedByUid: "new_user",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Non-admin cannot list other users' PII email profiles or escalate their own role
  await assertFails(newcomerRoles.get());
  await assertFails(
    newcomerRoles.doc("new_user").update({
      role: "STOREMATE",
      updatedAt: serverTs(),
    })
  );

  // Admin can list users, promote new_user to STOREMATE, and create a user with username
  await assertSucceeds(depotAdmin.collection("userRoles").get());
  await assertSucceeds(
    depotAdmin.collection("userRoles").doc("new_user").update({
      role: "STOREMATE",
      staffId: "staff_29",
      updatedByUid: ADMIN_UID,
      updatedAt: serverTs(),
    })
  );
  await assertSucceeds(
    depotAdmin.collection("userRoles").doc("user_created_1").set({
      uid: "user_created_1",
      username: "ravi_store",
      email: "ravi@example.com",
      displayName: "Ravi Kumar",
      role: "STOREMATE",
      staffId: "",
      updatedByUid: ADMIN_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
});

test("Technician has read access, cannot write duties/leaves directly, and can apply PENDING leave forwarded to SSE only", async () => {
  await seedSharedDepotBase();
  const techDepot = techContext().firestore().collection("depots").doc(DEPOT_ID);
  const supDepot = supervisorContext().firestore().collection("depots").doc(DEPOT_ID);

  // Can read shared staff & assignments
  await assertSucceeds(techDepot.collection("staff").get());
  await assertSucceeds(techDepot.collection("assignments").get());

  // Cannot assign duties or submit duty drafts
  await assertFails(
    techDepot.collection("assignments").doc("2026-11-18_SHIFT_06_14_SHED_0").set({
      userId: DEPOT_ID,
      date: "2026-11-18",
      shiftType: "SHIFT_06_14",
      dutyArea: "SHED",
      roleType: "POINTSMAN",
      slotIndex: 0,
      staffId: "staff_29",
      staffName: "G. RAJESH",
      isEmergencyOverride: false,
      overrideReason: "",
      approvedLocked: false,
      approvedByName: "",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    techDepot.collection("dutyDrafts").doc("draft_1").set({
      depotId: DEPOT_ID,
      dutyDate: "2026-11-18",
      status: "PENDING",
      submittedByUid: TECH_UID,
      submittedByName: "P. RAM KUMAR",
      reviewedByUid: "",
      reviewedByName: "",
      reviewNotes: "",
      proposedSlots: [{ slotId: "2026-11-18_SHIFT_06_14_SHED_0", staffId: "staff_29" }],
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Cannot create directly APPROVED leave
  await assertFails(
    techDepot.collection("leaves").doc("leave_bad_approved").set({
      userId: DEPOT_ID,
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      leaveType: "CL",
      startDate: "2026-11-19",
      endDate: "2026-11-20",
      status: "APPROVED",
      appliedByUid: TECH_UID,
      appliedByName: "P. RAM KUMAR",
      forwardedToStaffId: "staff_1",
      forwardedToName: "VENKATA KRISHNA",
      reviewedByName: "",
      notes: "Self approve attempt",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Cannot forward leave to JE (staff_3); must be SSE Only
  await assertFails(
    techDepot.collection("leaves").doc("leave_bad_je").set({
      userId: DEPOT_ID,
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      leaveType: "CL",
      startDate: "2026-11-19",
      endDate: "2026-11-20",
      status: "PENDING",
      appliedByUid: TECH_UID,
      appliedByName: "P. RAM KUMAR",
      forwardedToStaffId: "staff_3",
      forwardedToName: "Y. SAI KRISHNA",
      reviewedByName: "",
      notes: "JE target invalid",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Valid PENDING leave forwarded to SSE (staff_1 VENKATA KRISHNA) succeeds
  await assertSucceeds(
    techDepot.collection("leaves").doc("leave_ok_sse").set({
      userId: DEPOT_ID,
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      leaveType: "CL",
      startDate: "2026-11-19",
      endDate: "2026-11-20",
      status: "PENDING",
      appliedByUid: TECH_UID,
      appliedByName: "P. RAM KUMAR",
      forwardedToStaffId: "staff_1",
      forwardedToName: "VENKATA KRISHNA",
      reviewedByName: "",
      notes: "Family emergency",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Technician cannot approve their own leave; Supervisor can approve it
  await assertFails(
    techDepot.collection("leaves").doc("leave_ok_sse").update({
      status: "APPROVED",
      updatedAt: serverTs(),
    })
  );
  await assertSucceeds(
    supDepot.collection("leaves").doc("leave_ok_sse").update({
      status: "APPROVED",
      reviewedByName: "VENKATA KRISHNA",
      updatedAt: serverTs(),
    })
  );
});

test("Storemate submits dutyDraft for Supervisor approval; after approval & lock, Storemate cannot alter but Supervisor can", async () => {
  await seedSharedDepotBase();
  const storeDepot = storemateContext().firestore().collection("depots").doc(DEPOT_ID);
  const supDepot = supervisorContext().firestore().collection("depots").doc(DEPOT_ID);
  const techDepot = techContext().firestore().collection("depots").doc(DEPOT_ID);

  // Storemate cannot write directly to shared assignments DB
  await assertFails(
    storeDepot.collection("assignments").doc("2026-11-18_SHIFT_06_14_SHED_0").set({
      userId: DEPOT_ID,
      date: "2026-11-18",
      shiftType: "SHIFT_06_14",
      dutyArea: "SHED",
      roleType: "POINTSMAN",
      slotIndex: 0,
      staffId: "staff_29",
      staffName: "G. RAJESH",
      isEmergencyOverride: false,
      overrideReason: "",
      approvedLocked: false,
      approvedByName: "",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Storemate submits a PENDING dutyDraft for 2026-11-18
  await assertSucceeds(
    storeDepot.collection("dutyDrafts").doc("draft_2026_11_18").set({
      depotId: DEPOT_ID,
      dutyDate: "2026-11-18",
      status: "PENDING",
      submittedByUid: STOREMATE_UID,
      submittedByName: "Depot Storemate",
      reviewedByUid: "",
      reviewedByName: "",
      reviewNotes: "",
      proposedSlots: [
        {
          slotId: "2026-11-18_SHIFT_06_14_SHED_0",
          date: "2026-11-18",
          shiftType: "SHIFT_06_14",
          dutyArea: "SHED",
          roleType: "POINTSMAN",
          slotIndex: 0,
          staffId: "staff_29",
          staffName: "G. RAJESH",
        },
      ],
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Supervisor approves draft, publishes assignment to DB (visible to everyone), and locks date
  await assertSucceeds(
    supDepot.collection("assignments").doc("2026-11-18_SHIFT_06_14_SHED_0").set({
      userId: DEPOT_ID,
      date: "2026-11-18",
      shiftType: "SHIFT_06_14",
      dutyArea: "SHED",
      roleType: "POINTSMAN",
      slotIndex: 0,
      staffId: "staff_29",
      staffName: "G. RAJESH",
      isEmergencyOverride: false,
      overrideReason: "",
      approvedLocked: true,
      approvedByName: "VENKATA KRISHNA",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertSucceeds(
    supDepot.collection("dutyDrafts").doc("draft_2026_11_18").update({
      status: "APPROVED",
      reviewedByUid: SUP_UID,
      reviewedByName: "VENKATA KRISHNA",
      reviewNotes: "Approved",
      updatedAt: serverTs(),
    })
  );
  await assertSucceeds(
    supDepot.collection("dutyLocks").doc("2026-11-18").set({
      dutyDate: "2026-11-18",
      lockedByUid: SUP_UID,
      lockedByName: "VENKATA KRISHNA",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Technician sees the shared assignment
  const techSnap = await assertSucceeds(techDepot.collection("assignments").get());
  if (techSnap.size !== 1) throw new Error("Expected Technician to read published assignment");

  // Once approved & locked, Storemate CANNOT alter the approved draft OR submit a new draft for 2026-11-18
  await assertFails(
    storeDepot.collection("dutyDrafts").doc("draft_2026_11_18").update({
      proposedSlots: [{ slotId: "x", staffId: "staff_7" }],
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    storeDepot.collection("dutyDrafts").doc("draft_2026_11_18_v2").set({
      depotId: DEPOT_ID,
      dutyDate: "2026-11-18",
      status: "PENDING",
      submittedByUid: STOREMATE_UID,
      submittedByName: "Depot Storemate",
      reviewedByUid: "",
      reviewedByName: "",
      reviewNotes: "",
      proposedSlots: [{ slotId: "2026-11-18_SHIFT_06_14_SHED_0", staffId: "staff_7" }],
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Supervisor CAN still directly change existing assigned duties even when locked
  await assertSucceeds(
    supDepot.collection("assignments").doc("2026-11-18_SHIFT_06_14_SHED_0").update({
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      updatedAt: serverTs(),
    })
  );
});

test("Supervisor cannot manage staff profiles or user roles; Admin can add/edit/delete staff", async () => {
  await seedSharedDepotBase();
  const supDepot = supervisorContext().firestore().collection("depots").doc(DEPOT_ID);
  const adminDepot = adminContext().firestore().collection("depots").doc(DEPOT_ID);

  // Supervisor cannot add staff or edit user roles
  await assertFails(
    supDepot.collection("staff").doc("staff_40").set({
      userId: DEPOT_ID,
      serialNo: 40,
      name: "UNAUTHORIZED",
      designation: "KHP",
      competency: "NONE",
      stationDuty: false,
      shedDuty: true,
      weeklyRestDay: "NONE",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    supDepot.collection("userRoles").doc(TECH_UID).update({
      role: "SUPERVISOR",
      updatedAt: serverTs(),
    })
  );

  // Supervisor CAN update weeklyRestDay
  await assertSucceeds(
    supDepot.collection("staff").doc("staff_7").update({
      weeklyRestDay: "FRIDAY",
      updatedAt: serverTs(),
    })
  );

  // Admin can add, edit, and remove staff
  await assertSucceeds(
    adminDepot.collection("staff").doc("staff_40").set({
      userId: DEPOT_ID,
      serialNo: 40,
      name: "S. ARJUN",
      designation: "TECH_II",
      competency: "NONE",
      stationDuty: true,
      shedDuty: true,
      weeklyRestDay: "WEDNESDAY",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertSucceeds(
    adminDepot.collection("staff").doc("staff_40").update({
      name: "S. ARJUN RAO",
      updatedAt: serverTs(),
    })
  );
  await assertSucceeds(adminDepot.collection("staff").doc("staff_40").delete());
});

test("Admin roleInvites allow cross-device Google Sign-In role claiming while blocking spoofing and cross-user reads", async () => {
  await seedSharedDepotBase();
  const adminDepot = adminContext().firestore().collection("depots").doc(DEPOT_ID);
  const invitedEmail = "rahul.sse@gmail.com";

  // Unauthenticated cannot read or write roleInvites
  const unauthDepot = testEnv.unauthenticatedContext().firestore().collection("depots").doc(DEPOT_ID);
  await assertFails(unauthDepot.collection("roleInvites").doc(invitedEmail).get());

  // Non-admin cannot create or list roleInvites
  const supDepot = supervisorContext().firestore().collection("depots").doc(DEPOT_ID);
  await assertFails(supDepot.collection("roleInvites").get());
  await assertFails(
    supDepot.collection("roleInvites").doc(invitedEmail).set({
      email: invitedEmail,
      username: "rahul",
      displayName: "RAHUL SSE",
      role: "SUPERVISOR",
      staffId: "staff_1",
      updatedByUid: SUP_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Admin creates role invite for Phone 2's Google email
  await assertSucceeds(
    adminDepot.collection("roleInvites").doc(invitedEmail).set({
      email: invitedEmail,
      username: "rahul",
      displayName: "RAHUL SSE",
      role: "SUPERVISOR",
      staffId: "staff_1",
      updatedByUid: ADMIN_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Other user cannot read Rahul's invite
  const techDepot = techContext().firestore().collection("depots").doc(DEPOT_ID);
  await assertFails(techDepot.collection("roleInvites").doc(invitedEmail).get());

  // Unverified email spoofing cannot read invite or claim SUPERVISOR
  const unverifiedDepot = testEnv.authenticatedContext("spoof_rahul", {
    email: invitedEmail,
    email_verified: false,
  }).firestore().collection("depots").doc(DEPOT_ID);
  await assertFails(unverifiedDepot.collection("roleInvites").doc(invitedEmail).get());
  await assertFails(
    unverifiedDepot.collection("userRoles").doc("spoof_rahul").set({
      uid: "spoof_rahul",
      username: "rahul",
      email: invitedEmail,
      displayName: "RAHUL SSE",
      role: "SUPERVISOR",
      staffId: "staff_1",
      updatedByUid: "spoof_rahul",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Verified Phone 2 user can read their own invite, CANNOT escalate to ADMIN, and CAN claim exact SUPERVISOR + staffId
  const rahulDepot = testEnv.authenticatedContext("rahul_google_uid", {
    email: "Rahul.SSE@gmail.com",
    email_verified: true,
  }).firestore().collection("depots").doc(DEPOT_ID);

  await assertSucceeds(rahulDepot.collection("roleInvites").doc(invitedEmail).get());

  await assertFails(
    rahulDepot.collection("userRoles").doc("rahul_google_uid").set({
      uid: "rahul_google_uid",
      username: "rahul",
      email: invitedEmail,
      displayName: "RAHUL SSE",
      role: "ADMIN",
      staffId: "staff_1",
      updatedByUid: "rahul_google_uid",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    rahulDepot.collection("userRoles").doc("rahul_google_uid").set({
      uid: "rahul_google_uid",
      username: "rahul",
      email: invitedEmail,
      displayName: "RAHUL SSE",
      role: "SUPERVISOR",
      staffId: "staff_1",
      updatedByUid: "rahul_google_uid",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Admin later updates invite to STOREMATE; user can sync their own userRoles doc to match verified invite
  await assertSucceeds(
    adminDepot.collection("roleInvites").doc(invitedEmail).update({
      role: "STOREMATE",
      updatedByUid: ADMIN_UID,
      updatedAt: serverTs(),
    })
  );

  await assertSucceeds(
    rahulDepot.collection("userRoles").doc("rahul_google_uid").update({
      role: "STOREMATE",
      staffId: "staff_1",
      displayName: "RAHUL SSE",
      username: "rahul",
      email: invitedEmail,
      updatedAt: serverTs(),
    })
  );
});

test("Depot settings config is Admin-writable only and readable by signed-in depot users", async () => {
  await seedSharedDepotBase();
  const unauthDepot = testEnv.unauthenticatedContext().firestore().collection("depots").doc(DEPOT_ID);
  const adminDepot = adminContext().firestore().collection("depots").doc(DEPOT_ID);
  const supDepot = supervisorContext().firestore().collection("depots").doc(DEPOT_ID);
  const techDepot = techContext().firestore().collection("depots").doc(DEPOT_ID);

  // Unauthenticated cannot read or write settings
  await assertFails(unauthDepot.collection("settings").doc("config").get());

  // Non-admin cannot create settings
  await assertFails(
    supDepot.collection("settings").doc("config").set({
      allowTechnicianShare: false,
      updatedByUid: SUP_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    techDepot.collection("settings").doc("config").set({
      allowTechnicianShare: true,
      updatedByUid: TECH_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Admin can create settings config
  await assertSucceeds(
    adminDepot.collection("settings").doc("config").set({
      allowTechnicianShare: false,
      updatedByUid: ADMIN_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Signed-in Technician & Supervisor can read config, cannot list or update or delete it
  await assertSucceeds(techDepot.collection("settings").doc("config").get());
  await assertSucceeds(supDepot.collection("settings").doc("config").get());
  await assertFails(techDepot.collection("settings").get());
  await assertFails(
    techDepot.collection("settings").doc("config").update({
      allowTechnicianShare: true,
      updatedByUid: TECH_UID,
      updatedAt: serverTs(),
    })
  );

  // Supervisor can update Shunter visibility/export toggles, but CANNOT change allowTechnicianShare
  await assertSucceeds(
    supDepot.collection("settings").doc("config").update({
      showShunterForTechnicians: true,
      includeShunterInExport: true,
      updatedByUid: SUP_UID,
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    supDepot.collection("settings").doc("config").update({
      allowTechnicianShare: true,
      updatedByUid: SUP_UID,
      updatedAt: serverTs(),
    })
  );

  // Admin can toggle allowTechnicianShare and Shunter flags, cannot delete
  await assertSucceeds(
    adminDepot.collection("settings").doc("config").update({
      allowTechnicianShare: true,
      showShunterForTechnicians: false,
      includeShunterInExport: false,
      updatedByUid: ADMIN_UID,
      updatedAt: serverTs(),
    })
  );
  await assertFails(adminDepot.collection("settings").doc("config").delete());
});

test("LAP/LHAP leave types work; Technician can self-update LAP once, then requires Supervisor/Admin approval", async () => {
  await seedSharedDepotBase();
  const techDepot = techContext().firestore().collection("depots").doc(DEPOT_ID);
  const supDepot = supervisorContext().firestore().collection("depots").doc(DEPOT_ID);

  // Technician can apply LAP and LHAP pending leaves forwarded to SSE
  await assertSucceeds(
    techDepot.collection("leaves").doc("leave_lap_1").set({
      userId: DEPOT_ID,
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      leaveType: "LAP",
      startDate: "2026-11-22",
      endDate: "2026-11-24",
      status: "PENDING",
      appliedByUid: TECH_UID,
      appliedByName: "P. RAM KUMAR",
      forwardedToStaffId: "staff_1",
      forwardedToName: "VENKATA KRISHNA",
      reviewedByName: "",
      notes: "LAP request",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertSucceeds(
    techDepot.collection("leaves").doc("leave_lhap_1").set({
      userId: DEPOT_ID,
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      leaveType: "LHAP",
      startDate: "2026-11-26",
      endDate: "2026-11-27",
      status: "PENDING",
      appliedByUid: TECH_UID,
      appliedByName: "P. RAM KUMAR",
      forwardedToStaffId: "staff_1",
      forwardedToName: "VENKATA KRISHNA",
      reviewedByName: "",
      notes: "Medical LHAP",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Technician CANNOT create a leaveAccount for another staff member (staff_29)
  await assertFails(
    techDepot.collection("leaveAccounts").doc("staff_29").set({
      staffId: "staff_29",
      staffName: "G. RAJESH",
      baseYear: 2026,
      lapOpeningBalance: 120,
      lhapOpeningBalance: 20,
      lapSelfUpdated: true,
      pendingLapBalance: 0,
      pendingLapYear: 0,
      pendingLapReason: "",
      pendingLapStatus: "NONE",
      pendingLapRequestedByUid: "",
      pendingLapReviewedByName: "",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Technician CAN perform their ONE-TIME direct LAP self-update on their own linked staff_7
  await assertSucceeds(
    techDepot.collection("leaveAccounts").doc("staff_7").set({
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      baseYear: 2026,
      lapOpeningBalance: 145,
      lhapOpeningBalance: 20,
      lapSelfUpdated: true,
      pendingLapBalance: 0,
      pendingLapYear: 0,
      pendingLapReason: "",
      pendingLapStatus: "NONE",
      pendingLapRequestedByUid: "",
      pendingLapReviewedByName: "",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Technician CANNOT directly overwrite lapOpeningBalance a 2nd time!
  await assertFails(
    techDepot.collection("leaveAccounts").doc("staff_7").update({
      lapOpeningBalance: 160,
      updatedAt: serverTs(),
    })
  );

  // Technician CAN submit a PENDING LAP correction request with a reason
  await assertSucceeds(
    techDepot.collection("leaveAccounts").doc("staff_7").update({
      staffName: "P. RAM KUMAR",
      pendingLapBalance: 155,
      pendingLapYear: 2026,
      pendingLapReason: "Typo on first entry, service register shows 155",
      pendingLapStatus: "PENDING",
      pendingLapRequestedByUid: TECH_UID,
      updatedAt: serverTs(),
    })
  );

  // Supervisor CAN approve the pending LAP correction and update lapOpeningBalance
  await assertSucceeds(
    supDepot.collection("leaveAccounts").doc("staff_7").update({
      baseYear: 2026,
      lapOpeningBalance: 155,
      pendingLapStatus: "APPROVED",
      pendingLapReviewedByName: "VENKATA KRISHNA",
      updatedAt: serverTs(),
    })
  );
});

test("Duty relief permissions are strictly visible and writable ONLY to Supervisor and Admin", async () => {
  await seedSharedDepotBase();
  const unauthDepot = testEnv.unauthenticatedContext().firestore().collection("depots").doc(DEPOT_ID);
  const techDepot = techContext().firestore().collection("depots").doc(DEPOT_ID);
  const storeDepot = storemateContext().firestore().collection("depots").doc(DEPOT_ID);
  const supDepot = supervisorContext().firestore().collection("depots").doc(DEPOT_ID);
  const adminDepot = adminContext().firestore().collection("depots").doc(DEPOT_ID);

  const validPayload = {
    userId: DEPOT_ID,
    staffId: "staff_7",
    staffName: "P. RAM KUMAR",
    date: "2026-11-22",
    shiftType: "SHIFT_06_14",
    hours: 2.5,
    reliefType: "EARLY_RELIEF",
    reason: "Medical appointment",
    grantedByUid: SUP_UID,
    grantedByName: "VENKATA KRISHNA",
    createdAt: serverTs(),
    updatedAt: serverTs(),
  };

  // Unauthenticated, Technician, and Storemate CANNOT read or create dutyPermissions
  await assertFails(unauthDepot.collection("dutyPermissions").get());
  await assertFails(techDepot.collection("dutyPermissions").get());
  await assertFails(storeDepot.collection("dutyPermissions").get());
  await assertFails(techDepot.collection("dutyPermissions").doc("perm_tech").set(validPayload));
  await assertFails(storeDepot.collection("dutyPermissions").doc("perm_store").set(validPayload));

  // Invalid hours (0 or >12) are rejected even for Supervisor
  await assertFails(
    supDepot.collection("dutyPermissions").doc("perm_bad_zero").set({
      ...validPayload,
      hours: 0,
    })
  );
  await assertFails(
    supDepot.collection("dutyPermissions").doc("perm_bad_high").set({
      ...validPayload,
      hours: 15,
    })
  );

  // Supervisor CAN create, read, and update dutyPermissions
  await assertSucceeds(supDepot.collection("dutyPermissions").doc("perm_1").set(validPayload));
  await assertSucceeds(supDepot.collection("dutyPermissions").get());
  await assertSucceeds(
    supDepot.collection("dutyPermissions").doc("perm_1").update({
      hours: 3,
      reason: "Extended medical relief",
      updatedAt: serverTs(),
    })
  );

  // Technician & Storemate still CANNOT read or delete the created permission doc
  await assertFails(techDepot.collection("dutyPermissions").doc("perm_1").get());
  await assertFails(storeDepot.collection("dutyPermissions").doc("perm_1").get());
  await assertFails(techDepot.collection("dutyPermissions").doc("perm_1").delete());

  // Admin CAN read and delete dutyPermissions
  await assertSucceeds(adminDepot.collection("dutyPermissions").doc("perm_1").get());
  await assertSucceeds(adminDepot.collection("dutyPermissions").doc("perm_1").delete());
});

test("TRAINING leave type and staff trainingDueDate updates enforce role and schema validation", async () => {
  await seedSharedDepotBase();
  const techDepot = techContext().firestore().collection("depots").doc(DEPOT_ID);
  const storeDepot = storemateContext().firestore().collection("depots").doc(DEPOT_ID);
  const supDepot = supervisorContext().firestore().collection("depots").doc(DEPOT_ID);
  const adminDepot = adminContext().firestore().collection("depots").doc(DEPOT_ID);

  // Technician can apply PENDING TRAINING leave forwarded to SSE
  await assertSucceeds(
    techDepot.collection("leaves").doc("leave_trg_tech").set({
      userId: DEPOT_ID,
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      leaveType: "TRAINING",
      startDate: "2026-12-01",
      endDate: "2026-12-05",
      status: "PENDING",
      appliedByUid: TECH_UID,
      appliedByName: "P. RAM KUMAR",
      forwardedToStaffId: "staff_1",
      forwardedToName: "VENKATA KRISHNA",
      reviewedByName: "",
      notes: "Periodic Technical Refresher",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Supervisor can approve the TRAINING leave
  await assertSucceeds(
    supDepot.collection("leaves").doc("leave_trg_tech").update({
      status: "APPROVED",
      reviewedByName: "VENKATA KRISHNA",
      updatedAt: serverTs(),
    })
  );

  // Technician & Storemate CANNOT set trainingDueDate on staff records
  await assertFails(
    techDepot.collection("staff").doc("staff_7").update({
      trainingDueDate: "2026-12-15",
      trainingCourse: "Safety / PME",
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    storeDepot.collection("staff").doc("staff_7").update({
      trainingDueDate: "2026-12-15",
      trainingCourse: "Safety / PME",
      updatedAt: serverTs(),
    })
  );

  // Invalid date format is rejected even for Supervisor
  await assertFails(
    supDepot.collection("staff").doc("staff_7").update({
      trainingDueDate: "15-12-2026",
      trainingCourse: "Safety / PME",
      updatedAt: serverTs(),
    })
  );

  // Supervisor CAN set and update trainingDueDate & trainingCourse
  await assertSucceeds(
    supDepot.collection("staff").doc("staff_7").update({
      trainingDueDate: "2026-12-15",
      trainingCourse: "Technical Refresher",
      updatedAt: serverTs(),
    })
  );

  // Admin CAN clear trainingDueDate & trainingCourse (empty strings allowed)
  await assertSucceeds(
    adminDepot.collection("staff").doc("staff_7").update({
      trainingDueDate: "",
      trainingCourse: "",
      updatedAt: serverTs(),
    })
  );
});

test("SHIFT_INCHARGE has ONLY Permission Manager write access and staff optional phoneNumber is validated", async () => {
  await seedSharedDepotBase();
  const siDepot = shiftInchargeContext().firestore().collection("depots").doc(DEPOT_ID);
  const adminDepot = adminContext().firestore().collection("depots").doc(DEPOT_ID);

  // ShiftIncharge CAN read staff & assignments (needed for Live Dashboard)
  await assertSucceeds(siDepot.collection("staff").get());
  await assertSucceeds(siDepot.collection("assignments").get());

  // ShiftIncharge CAN create, read, update, and delete dutyPermissions
  const permPayload = {
    userId: DEPOT_ID,
    staffId: "staff_7",
    staffName: "P. RAM KUMAR",
    date: "2026-11-22",
    shiftType: "SHIFT_06_14",
    hours: 2,
    reliefType: "EARLY_RELIEF",
    reason: "Shift relief approved",
    grantedByUid: SHIFT_INCHARGE_UID,
    grantedByName: "Shift Incharge",
    createdAt: serverTs(),
    updatedAt: serverTs(),
  };
  await assertSucceeds(siDepot.collection("dutyPermissions").doc("perm_si").set(permPayload));
  await assertSucceeds(siDepot.collection("dutyPermissions").get());
  await assertSucceeds(
    siDepot.collection("dutyPermissions").doc("perm_si").update({
      hours: 1.5,
      updatedAt: serverTs(),
    })
  );
  await assertSucceeds(siDepot.collection("dutyPermissions").doc("perm_si").delete());

  // ShiftIncharge CANNOT assign duties, create drafts, apply/approve leaves, or edit staff
  await assertFails(
    siDepot.collection("assignments").doc("asgn_si").set({
      userId: DEPOT_ID,
      date: "2026-11-22",
      shiftType: "SHIFT_06_14",
      dutyArea: "SHED",
      roleType: "GENERAL_DUTY",
      slotIndex: 0,
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    siDepot.collection("dutyDrafts").doc("draft_si").set({
      userId: DEPOT_ID,
      date: "2026-11-22",
      shiftType: "SHIFT_06_14",
      dutyArea: "SHED",
      roleType: "GENERAL_DUTY",
      slotIndex: 0,
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      submittedByUid: SHIFT_INCHARGE_UID,
      submittedByName: "Shift Incharge",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    siDepot.collection("leaves").doc("leave_si").set({
      userId: DEPOT_ID,
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      leaveType: "CL",
      startDate: "2026-11-23",
      endDate: "2026-11-23",
      status: "PENDING",
      appliedByUid: SHIFT_INCHARGE_UID,
      appliedByName: "Shift Incharge",
      forwardedToStaffId: "staff_1",
      forwardedToName: "VENKATA KRISHNA",
      reviewedByName: "",
      notes: "",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    siDepot.collection("staff").doc("staff_7").update({
      phoneNumber: "9848012345",
      updatedAt: serverTs(),
    })
  );

  // Admin CAN save optional phoneNumber and secondaryPhoneNumber on any staff (overlong values rejected)
  await assertSucceeds(
    adminDepot.collection("staff").doc("staff_7").update({
      phoneNumber: "9848012345",
      secondaryPhoneNumber: "9123456789",
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    adminDepot.collection("staff").doc("staff_7").update({
      phoneNumber: "1234567890123456789012345",
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    adminDepot.collection("staff").doc("staff_7").update({
      secondaryPhoneNumber: "1234567890123456789012345",
      updatedAt: serverTs(),
    })
  );
});

test("SHUNTER designation and roleType are allowed for regular and General Shift assignments by Supervisor/Admin only", async () => {
  await seedSharedDepotBase();
  const adminDepot = adminContext().firestore().collection("depots").doc(DEPOT_ID);
  const supDepot = supervisorContext().firestore().collection("depots").doc(DEPOT_ID);
  const siDepot = shiftInchargeContext().firestore().collection("depots").doc(DEPOT_ID);
  const techDepot = techContext().firestore().collection("depots").doc(DEPOT_ID);

  // Admin can create a staff member with SHUNTER designation
  await assertSucceeds(
    adminDepot.collection("staff").doc("staff_shunter_1").set({
      userId: DEPOT_ID,
      serialNo: 39,
      name: "K. SHUNTER TEST",
      designation: "SHUNTER",
      competency: "NONE",
      stationDuty: false,
      shedDuty: true,
      weeklyRestDay: "SUNDAY",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Supervisor can assign Shunter in regular shift (slotIndex 6)
  await assertSucceeds(
    supDepot.collection("assignments").doc("2026-11-22_SHIFT_06_14_SHED_6").set({
      userId: DEPOT_ID,
      date: "2026-11-22",
      shiftType: "SHIFT_06_14",
      dutyArea: "SHED",
      roleType: "SHUNTER",
      slotIndex: 6,
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      isEmergencyOverride: false,
      overrideReason: "",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Supervisor can assign Shunter in General Shift 08/17 (slotIndex 6)
  await assertSucceeds(
    supDepot.collection("assignments").doc("2026-11-22_SHIFT_08_17_SHED_6").set({
      userId: DEPOT_ID,
      date: "2026-11-22",
      shiftType: "SHIFT_08_17",
      dutyArea: "SHED",
      roleType: "SHUNTER",
      slotIndex: 6,
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      isEmergencyOverride: false,
      overrideReason: "",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // ShiftIncharge & Technician cannot assign Shunter or toggle settings
  await assertFails(
    siDepot.collection("assignments").doc("2026-11-23_SHIFT_06_14_SHED_6").set({
      userId: DEPOT_ID,
      date: "2026-11-23",
      shiftType: "SHIFT_06_14",
      dutyArea: "SHED",
      roleType: "SHUNTER",
      slotIndex: 6,
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      isEmergencyOverride: false,
      overrideReason: "",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    siDepot.collection("settings").doc("config").set({
      allowTechnicianShare: true,
      showShunterForTechnicians: true,
      includeShunterInExport: true,
      updatedByUid: SHIFT_INCHARGE_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    techDepot.collection("settings").doc("config").set({
      allowTechnicianShare: true,
      showShunterForTechnicians: true,
      includeShunterInExport: false,
      updatedByUid: TECH_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Supervisor can bootstrap settings/config when toggling Shunter (with allowTechnicianShare == true)
  await assertSucceeds(
    supDepot.collection("settings").doc("config").set({
      allowTechnicianShare: true,
      showShunterForTechnicians: false,
      includeShunterInExport: true,
      updatedByUid: SUP_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
});

test("importantContacts: Admin-only create/update/delete, ShiftIncharge/Supervisor/Admin read-only, Technician/Storemate denied", async () => {
  await seedSharedDepotBase();
  const adminDepot = adminContext().firestore().collection("depots").doc(DEPOT_ID);
  const supDepot = supervisorContext().firestore().collection("depots").doc(DEPOT_ID);
  const siDepot = shiftInchargeContext().firestore().collection("depots").doc(DEPOT_ID);
  const storeDepot = storemateContext().firestore().collection("depots").doc(DEPOT_ID);
  const techDepot = techContext().firestore().collection("depots").doc(DEPOT_ID);

  const validContact = {
    userId: DEPOT_ID,
    label: "RRI KZJ",
    phoneNumber: "7815913694",
    category: "Signal / RRI",
    sortOrder: 1,
    createdAt: serverTs(),
    updatedAt: serverTs(),
  };

  // Non-admin roles cannot create important contacts
  await assertFails(supDepot.collection("importantContacts").doc("rri_kzj").set(validContact));
  await assertFails(siDepot.collection("importantContacts").doc("rri_kzj").set(validContact));
  await assertFails(storeDepot.collection("importantContacts").doc("rri_kzj").set(validContact));
  await assertFails(techDepot.collection("importantContacts").doc("rri_kzj").set(validContact));

  // Admin cannot create invalid contact payloads (empty label, too-short/too-long phone)
  await assertFails(
    adminDepot.collection("importantContacts").doc("bad_label").set({
      ...validContact,
      label: "",
    })
  );
  await assertFails(
    adminDepot.collection("importantContacts").doc("bad_phone").set({
      ...validContact,
      phoneNumber: "123456789012345678901",
    })
  );

  // Admin can create an important contact and mark settings/config importantContactsInitialized
  await assertSucceeds(adminDepot.collection("importantContacts").doc("rri_kzj").set(validContact));
  await assertSucceeds(
    adminDepot.collection("settings").doc("config").set({
      allowTechnicianShare: true,
      showShunterForTechnicians: false,
      includeShunterInExport: false,
      importantContactsInitialized: true,
      updatedByUid: ADMIN_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Supervisor & ShiftIncharge CAN read (list & get) importantContacts
  await assertSucceeds(supDepot.collection("importantContacts").get());
  await assertSucceeds(supDepot.collection("importantContacts").doc("rri_kzj").get());
  await assertSucceeds(siDepot.collection("importantContacts").get());
  await assertSucceeds(siDepot.collection("importantContacts").doc("rri_kzj").get());

  // Technician & Storemate CANNOT read importantContacts
  await assertFails(techDepot.collection("importantContacts").get());
  await assertFails(techDepot.collection("importantContacts").doc("rri_kzj").get());
  await assertFails(storeDepot.collection("importantContacts").get());
  await assertFails(storeDepot.collection("importantContacts").doc("rri_kzj").get());

  // Supervisor & ShiftIncharge CANNOT update or delete importantContacts, nor alter importantContactsInitialized
  await assertFails(
    supDepot.collection("importantContacts").doc("rri_kzj").update({
      label: "HACKED",
      updatedAt: serverTs(),
    })
  );
  await assertFails(supDepot.collection("importantContacts").doc("rri_kzj").delete());
  await assertFails(siDepot.collection("importantContacts").doc("rri_kzj").delete());
  await assertFails(
    supDepot.collection("settings").doc("config").update({
      importantContactsInitialized: false,
      updatedByUid: SUP_UID,
      updatedAt: serverTs(),
    })
  );

  // Admin CAN update and delete importantContacts
  await assertSucceeds(
    adminDepot.collection("importantContacts").doc("rri_kzj").update({
      label: "RRI KZJ CONTROL",
      updatedAt: serverTs(),
    })
  );
  await assertSucceeds(adminDepot.collection("importantContacts").doc("rri_kzj").delete());
});

test("Online registration enforces unique usernames, strict owner-only userCredentials, and linked staff phone sync", async () => {
  await seedSharedDepotBase();
  const adminDepot = adminContext().firestore().collection("depots").doc(DEPOT_ID);
  const newUserA = testEnv.authenticatedContext("reg_user_a", {
    email: "user.a@gmail.com",
    email_verified: true,
  }).firestore().collection("depots").doc(DEPOT_ID);
  const newUserB = testEnv.authenticatedContext("reg_user_b", {
    email: "user.b@gmail.com",
    email_verified: true,
  }).firestore().collection("depots").doc(DEPOT_ID);

  // User A reserves username 'ram_kumar'
  await assertSucceeds(
    newUserA.collection("usernames").doc("ram_kumar").set({
      username: "ram_kumar",
      uid: "reg_user_a",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Listing usernames is denied (anti-enumeration), but single-doc availability lookup succeeds
  await assertFails(newUserB.collection("usernames").get());
  await assertSucceeds(newUserB.collection("usernames").doc("ram_kumar").get());

  // User B CANNOT claim or overwrite already-existing username 'ram_kumar' or delete User A's claim
  await assertFails(
    newUserB.collection("usernames").doc("ram_kumar").set({
      username: "ram_kumar",
      uid: "reg_user_b",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertFails(newUserB.collection("usernames").doc("ram_kumar").delete());

  // User A completes registered profile with staff link and phone numbers (role locked to TECHNICIAN)
  await assertSucceeds(
    newUserA.collection("userRoles").doc("reg_user_a").set({
      uid: "reg_user_a",
      username: "ram_kumar",
      email: "user.a@gmail.com",
      displayName: "P. RAM KUMAR",
      role: "TECHNICIAN",
      staffId: "staff_7",
      phoneNumber: "9848011223",
      secondaryPhoneNumber: "9123450099",
      registered: true,
      updatedByUid: "reg_user_a",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // User A saves own PBKDF2 password credential
  await assertSucceeds(
    newUserA.collection("userCredentials").doc("reg_user_a").set({
      uid: "reg_user_a",
      username: "ram_kumar",
      email: "user.a@gmail.com",
      passwordSalt: "c2FsdF8xMjM0NTY3ODkwMTIzNDU2",
      passwordHash: "aGFzaF8xMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM=",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Owner can read and update their own password credential
  await assertSucceeds(newUserA.collection("userCredentials").doc("reg_user_a").get());
  await assertSucceeds(
    newUserA.collection("userCredentials").doc("reg_user_a").update({
      passwordSalt: "bmV3X3NhbHRfMTIzNDU2Nzg5MDEyMw==",
      passwordHash: "bmV3X2hhc2hfMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODk=",
      updatedAt: serverTs(),
    })
  );

  // Neither another user NOR Admin can list, read, or overwrite User A's password credential
  await assertFails(newUserB.collection("userCredentials").doc("reg_user_a").get());
  await assertFails(adminDepot.collection("userCredentials").doc("reg_user_a").get());
  await assertFails(adminDepot.collection("userCredentials").get());
  await assertFails(
    adminDepot.collection("userCredentials").doc("reg_user_a").update({
      passwordHash: "dGFtcGVyZWRfaGFzaF8xMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ=",
      updatedAt: serverTs(),
    })
  );

  // User A can sync phone numbers to their linked staff record (staff_7), but NOT alter designation or another staff member
  await assertSucceeds(
    newUserA.collection("staff").doc("staff_7").update({
      phoneNumber: "9848011223",
      secondaryPhoneNumber: "9123450099",
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    newUserA.collection("staff").doc("staff_7").update({
      designation: "SSE",
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    newUserA.collection("staff").doc("staff_29").update({
      phoneNumber: "9848011223",
      updatedAt: serverTs(),
    })
  );

  // Admin can clean up username and userCredential docs when deleting a user account
  await assertSucceeds(adminDepot.collection("usernames").doc("ram_kumar").delete());
  await assertSucceeds(adminDepot.collection("userCredentials").doc("reg_user_a").delete());
});

test("Leave balance update allows one-time self-update of BOTH LAP and LHAP, then requires Supervisor/Admin approval", async () => {
  await seedSharedDepotBase();
  const techDepot = techContext().firestore().collection("depots").doc(DEPOT_ID);
  const supDepot = supervisorContext().firestore().collection("depots").doc(DEPOT_ID);

  // First-time self-update sets BOTH lapOpeningBalance (140) and lhapOpeningBalance (65) directly
  await assertSucceeds(
    techDepot.collection("leaveAccounts").doc("staff_7").set({
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      baseYear: 2026,
      lapOpeningBalance: 140,
      lhapOpeningBalance: 65,
      lapSelfUpdated: true,
      pendingLapBalance: 0,
      pendingLhapBalance: 0,
      pendingLapYear: 0,
      pendingLapReason: "",
      pendingLapStatus: "NONE",
      pendingLapRequestedByUid: "",
      pendingLapReviewedByName: "",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Second direct update to LHAP or LAP is denied
  await assertFails(
    techDepot.collection("leaveAccounts").doc("staff_7").update({
      lhapOpeningBalance: 80,
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    techDepot.collection("leaveAccounts").doc("staff_7").update({
      lapOpeningBalance: 150,
      updatedAt: serverTs(),
    })
  );

  // Subsequent change submits PENDING request with BOTH pendingLapBalance and pendingLhapBalance
  await assertSucceeds(
    techDepot.collection("leaveAccounts").doc("staff_7").update({
      staffName: "P. RAM KUMAR",
      pendingLapBalance: 150,
      pendingLhapBalance: 72,
      pendingLapYear: 2026,
      pendingLapReason: "Verified against service sheet",
      pendingLapStatus: "PENDING",
      pendingLapRequestedByUid: TECH_UID,
      updatedAt: serverTs(),
    })
  );

  // Supervisor approves both LAP and LHAP balances
  await assertSucceeds(
    supDepot.collection("leaveAccounts").doc("staff_7").update({
      baseYear: 2026,
      lapOpeningBalance: 150,
      lhapOpeningBalance: 72,
      pendingLapStatus: "APPROVED",
      pendingLapReviewedByName: "VENKATA KRISHNA",
      updatedAt: serverTs(),
    })
  );
});

test("Rake Manager: all signed-in roles can read rakes; only Admin, Supervisor, and ShiftIncharge can create/edit/delete", async () => {
  await seedSharedDepotBase();
  const unauthDepot = testEnv.unauthenticatedContext().firestore().collection("depots").doc(DEPOT_ID);
  const adminDepot = adminContext().firestore().collection("depots").doc(DEPOT_ID);
  const supDepot = supervisorContext().firestore().collection("depots").doc(DEPOT_ID);
  const shiftInchargeDepot = shiftInchargeContext().firestore().collection("depots").doc(DEPOT_ID);
  const storemateDepot = storemateContext().firestore().collection("depots").doc(DEPOT_ID);
  const techDepot = techContext().firestore().collection("depots").doc(DEPOT_ID);

  const validRakePayload = {
    userId: DEPOT_ID,
    rakeName: "3-Ph EMU Rake 1",
    category: "EMU_3PHASE_12CAR",
    make: "ICF",
    tcasFitted: false,
    startEnd: "KZJ",
    finishEnd: "SC",
    remarks: "Standard 12-car formation",
    sortOrder: 101,
    coaches: [
      { position: 1, coachNumber: "188396", coachType: "DTC", altNumber: "", endTag: "KZJ" },
      { position: 2, coachNumber: "388", coachType: "MC", altNumber: "", endTag: "" },
    ],
    updatedByUid: ADMIN_UID,
    updatedByName: "Vinod Chinthawar",
    createdAt: serverTs(),
    updatedAt: serverTs(),
  };

  // Unauthenticated cannot read or write rakes
  await assertFails(unauthDepot.collection("rakes").get());
  await assertFails(unauthDepot.collection("rakes").doc("rake_emu3p_1").set(validRakePayload));

  // Admin creates a rake formation
  await assertSucceeds(adminDepot.collection("rakes").doc("rake_emu3p_1").set(validRakePayload));

  // ShiftIncharge can create a Vande Bharat rake and set rakesInitialized flag
  await assertSucceeds(
    shiftInchargeDepot.collection("rakes").doc("rake_vb_83").set({
      ...validRakePayload,
      rakeName: "Vande Bharat R-83",
      category: "VANDE_BHARAT",
      sortOrder: 601,
      updatedByUid: SHIFT_INCHARGE_UID,
      updatedByName: "Shift Incharge",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertSucceeds(
    shiftInchargeDepot.collection("settings").doc("config").set({
      allowTechnicianShare: true,
      showShunterForTechnicians: false,
      includeShunterInExport: false,
      rakesInitialized: true,
      rakesSchemaVersion: 2,
      updatedByUid: SHIFT_INCHARGE_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // All signed-in roles (including Technician & Storemate) can list and read rakes
  await assertSucceeds(techDepot.collection("rakes").get());
  await assertSucceeds(techDepot.collection("rakes").doc("rake_emu3p_1").get());
  await assertSucceeds(storemateDepot.collection("rakes").get());
  await assertSucceeds(shiftInchargeDepot.collection("rakes").get());

  // Technician and Storemate cannot create, update, or delete rakes
  await assertFails(
    techDepot.collection("rakes").doc("rake_tech_bad").set({
      ...validRakePayload,
      updatedByUid: TECH_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    storemateDepot.collection("rakes").doc("rake_emu3p_1").update({
      remarks: "Unauthorized edit",
      updatedAt: serverTs(),
    })
  );
  await assertFails(techDepot.collection("rakes").doc("rake_emu3p_1").delete());

  // Supervisor can update mutable fields, but cannot alter immutable userId or createdAt or send invalid category/empty coaches
  await assertSucceeds(
    supDepot.collection("rakes").doc("rake_emu3p_1").update({
      rakeName: "3-Ph EMU Rake 1 (Updated)",
      tcasFitted: true,
      remarks: "TCAS commissioned",
      updatedByUid: SUP_UID,
      updatedByName: "VENKATA KRISHNA",
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    supDepot.collection("rakes").doc("rake_emu3p_1").update({
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    supDepot.collection("rakes").doc("rake_emu3p_1").update({
      category: "INVALID_TYPE",
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    supDepot.collection("rakes").doc("rake_emu3p_1").update({
      coaches: [],
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    adminDepot.collection("rakes").doc("rake_future_ts").set({
      ...validRakePayload,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Supervisor and Admin can delete rakes
  await assertSucceeds(supDepot.collection("rakes").doc("rake_vb_83").delete());
  await assertSucceeds(adminDepot.collection("rakes").doc("rake_emu3p_1").delete());
});

test("SSE and JE roles work with defaults and Admin Roles Manager dynamically enforces 3-level menu access", async () => {
  await seedSharedDepotBase();
  const adminDepot = adminContext().firestore().collection("depots").doc(DEPOT_ID);
  const supDepot = supervisorContext().firestore().collection("depots").doc(DEPOT_ID);

  const SSE_UID = "sse_role_uid";
  const JE_UID = "je_role_uid";

  const sseDepot = testEnv.authenticatedContext(SSE_UID, {
    email: "sse.officer@example.com",
    email_verified: true,
  }).firestore().collection("depots").doc(DEPOT_ID);

  const jeDepot = testEnv.authenticatedContext(JE_UID, {
    email: "je.officer@example.com",
    email_verified: true,
  }).firestore().collection("depots").doc(DEPOT_ID);

  // Admin creates SSE and JE user accounts
  await assertSucceeds(
    adminDepot.collection("userRoles").doc(SSE_UID).set({
      uid: SSE_UID,
      email: "sse.officer@example.com",
      displayName: "SSE Officer",
      role: "SSE",
      staffId: "staff_1",
      updatedByUid: ADMIN_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertSucceeds(
    adminDepot.collection("userRoles").doc(JE_UID).set({
      uid: JE_UID,
      email: "je.officer@example.com",
      displayName: "JE Officer",
      role: "JE",
      staffId: "staff_3",
      updatedByUid: ADMIN_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Default SSE: can directly assign duties, approve leaves, grant & delete permissions
  await assertSucceeds(
    sseDepot.collection("assignments").doc("2026-11-28_SHIFT_06_14_SHED_0").set({
      userId: DEPOT_ID,
      date: "2026-11-28",
      shiftType: "SHIFT_06_14",
      dutyArea: "SHED",
      roleType: "POINTSMAN",
      slotIndex: 0,
      staffId: "staff_29",
      staffName: "G. RAJESH",
      isEmergencyOverride: false,
      overrideReason: "",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Default JE: cannot directly assign duties (EDIT_WITH_APPROVAL), CAN submit dutyDrafts
  await assertFails(
    jeDepot.collection("assignments").doc("2026-11-29_SHIFT_06_14_SHED_0").set({
      userId: DEPOT_ID,
      date: "2026-11-29",
      shiftType: "SHIFT_06_14",
      dutyArea: "SHED",
      roleType: "POINTSMAN",
      slotIndex: 0,
      staffId: "staff_29",
      staffName: "G. RAJESH",
      isEmergencyOverride: false,
      overrideReason: "",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertSucceeds(
    jeDepot.collection("dutyDrafts").doc("draft_je_1").set({
      depotId: DEPOT_ID,
      dutyDate: "2026-11-29",
      status: "PENDING",
      submittedByUid: JE_UID,
      submittedByName: "JE Officer",
      reviewedByUid: "",
      reviewedByName: "",
      reviewNotes: "",
      proposedSlots: [{ slotId: "2026-11-29_SHIFT_06_14_SHED_0", staffId: "staff_29" }],
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Default JE has EDIT_WITH_APPROVAL on PERMISSIONS -> can grant, CANNOT delete
  await assertSucceeds(
    jeDepot.collection("dutyPermissions").doc("perm_je_1").set({
      userId: DEPOT_ID,
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      date: "2026-11-29",
      shiftType: "SHIFT_06_14",
      hours: 2,
      reliefType: "EARLY_RELIEF",
      reason: "JE granted relief",
      grantedByUid: JE_UID,
      grantedByName: "JE Officer",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertFails(jeDepot.collection("dutyPermissions").doc("perm_je_1").delete());

  // Non-Admin (SSE, Supervisor, JE) CANNOT write roleMenuAccess in settings/config
  await assertFails(
    sseDepot.collection("settings").doc("config").set({
      allowTechnicianShare: true,
      roleMenuAccess: { JE: { SCHEDULE: "FULL_CONTROL" } },
      updatedByUid: SSE_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    supDepot.collection("settings").doc("config").set({
      allowTechnicianShare: true,
      roleMenuAccess: { JE: { SCHEDULE: "FULL_CONTROL" } },
      updatedByUid: SUP_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Admin CANNOT write an override for 'ADMIN' key (Admin is permanently locked to Full Control)
  await assertFails(
    adminDepot.collection("settings").doc("config").set({
      allowTechnicianShare: true,
      roleMenuAccess: { ADMIN: { SCHEDULE: "READ_ONLY" } },
      updatedByUid: ADMIN_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Admin configures dynamic roleMenuAccess:
  // - Promote JE SCHEDULE to FULL_CONTROL
  // - Downgrade SSE LEAVE to READ_ONLY
  // - Grant TECHNICIAN RAKES = EDIT_WITH_APPROVAL (can create/update, cannot delete)
  // - Grant SSE USERS = FULL_CONTROL (can manage non-Admin users, still cannot escalate to ADMIN)
  await assertSucceeds(
    adminDepot.collection("settings").doc("config").set({
      allowTechnicianShare: true,
      roleMenuAccess: {
        JE: { SCHEDULE: "FULL_CONTROL" },
        SSE: { LEAVE: "READ_ONLY", USERS: "FULL_CONTROL" },
        TECHNICIAN: { RAKES: "EDIT_WITH_APPROVAL" },
      },
      updatedByUid: ADMIN_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Now JE CAN directly assign duties because SCHEDULE == FULL_CONTROL
  await assertSucceeds(
    jeDepot.collection("assignments").doc("2026-11-29_SHIFT_06_14_SHED_0").set({
      userId: DEPOT_ID,
      date: "2026-11-29",
      shiftType: "SHIFT_06_14",
      dutyArea: "SHED",
      roleType: "POINTSMAN",
      slotIndex: 0,
      staffId: "staff_29",
      staffName: "G. RAJESH",
      isEmergencyOverride: false,
      overrideReason: "",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Now SSE CANNOT write leaves because LEAVE == READ_ONLY
  await assertFails(
    sseDepot.collection("leaves").doc("leave_sse_blocked").set({
      userId: DEPOT_ID,
      staffId: "staff_7",
      staffName: "P. RAM KUMAR",
      leaveType: "CL",
      startDate: "2026-11-30",
      endDate: "2026-11-30",
      status: "APPROVED",
      appliedByUid: SSE_UID,
      appliedByName: "SSE Officer",
      forwardedToStaffId: "staff_1",
      forwardedToName: "VENKATA KRISHNA",
      reviewedByName: "SSE Officer",
      notes: "Should fail under READ_ONLY",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );

  // Technician with RAKES == EDIT_WITH_APPROVAL can create & update rake, CANNOT delete
  const techDepot = techContext().firestore().collection("depots").doc(DEPOT_ID);
  await assertSucceeds(
    techDepot.collection("rakes").doc("rake_tech_edit_ok").set({
      userId: DEPOT_ID,
      rakeName: "Tech Editable Rake",
      category: "EMU_3PHASE_12CAR",
      make: "ICF",
      tcasFitted: false,
      startEnd: "KZJ",
      finishEnd: "SC",
      remarks: "Allowed by EDIT_WITH_APPROVAL",
      sortOrder: 201,
      coaches: [{ position: 1, coachNumber: "188396", coachType: "DTC", altNumber: "", endTag: "KZJ" }],
      updatedByUid: TECH_UID,
      updatedByName: "P. RAM KUMAR",
      createdAt: serverTs(),
      updatedAt: serverTs(),
    })
  );
  await assertFails(techDepot.collection("rakes").doc("rake_tech_edit_ok").delete());

  // SSE with USERS == FULL_CONTROL can update a Technician's role to JE,
  // but CANNOT promote anyone to ADMIN or modify/delete the ADMIN account
  await assertSucceeds(
    sseDepot.collection("userRoles").doc(TECH_UID).update({
      role: "JE",
      updatedByUid: SSE_UID,
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    sseDepot.collection("userRoles").doc(TECH_UID).update({
      role: "ADMIN",
      updatedByUid: SSE_UID,
      updatedAt: serverTs(),
    })
  );
  await assertFails(
    sseDepot.collection("userRoles").doc(ADMIN_UID).update({
      role: "TECHNICIAN",
      updatedByUid: SSE_UID,
      updatedAt: serverTs(),
    })
  );
  await assertFails(sseDepot.collection("userRoles").doc(ADMIN_UID).delete());
});

test("OTA appRelease/latest is readable by all signed-in users, writable only by Admin with strict HTTPS & SHA-256 validation", async () => {
  const SSE_UID = "sse_ota_uid";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("depots").doc(DEPOT_ID).collection("userRoles").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      email: "admin@rail.in",
      displayName: "Vinod Chinthawar",
      role: "ADMIN",
      staffId: "",
      updatedByUid: ADMIN_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    });
    await db.collection("depots").doc(DEPOT_ID).collection("userRoles").doc(SUP_UID).set({
      uid: SUP_UID,
      email: "sup@rail.in",
      displayName: "Supervisor",
      role: "SUPERVISOR",
      staffId: "staff_1",
      updatedByUid: ADMIN_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    });
    await db.collection("depots").doc(DEPOT_ID).collection("userRoles").doc(SSE_UID).set({
      uid: SSE_UID,
      email: "sse@rail.in",
      displayName: "SSE Officer",
      role: "SSE",
      staffId: "staff_1",
      updatedByUid: ADMIN_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    });
    await db.collection("depots").doc(DEPOT_ID).collection("userRoles").doc(TECH_UID).set({
      uid: TECH_UID,
      email: "tech@rail.in",
      displayName: "Tech",
      role: "TECHNICIAN",
      staffId: "staff_7",
      updatedByUid: ADMIN_UID,
      createdAt: serverTs(),
      updatedAt: serverTs(),
    });
  });

  const adminDepot = adminContext().firestore().collection("depots").doc(DEPOT_ID);
  const supDepot = supervisorContext().firestore().collection("depots").doc(DEPOT_ID);
  const sseDepot = testEnv.authenticatedContext(SSE_UID, { email: "sse@rail.in", email_verified: true })
    .firestore().collection("depots").doc(DEPOT_ID);
  const techDepot = techContext().firestore().collection("depots").doc(DEPOT_ID);
  const unauthDepot = testEnv.unauthenticatedContext().firestore().collection("depots").doc(DEPOT_ID);

  const validPayload = {
    latestVersionCode: 2,
    latestVersionName: "1.1.0",
    minSupportedVersionCode: 1,
    mandatory: false,
    downloadUrl: "https://github.com/ppio-depot/ppio-roster/releases/download/v1.1.0/ppio-roster.apk",
    releaseNotes: "Added SSE/JE roles and Roles Manager.",
    sha256Checksum: "a".repeat(64),
    publishedByUid: ADMIN_UID,
    publishedByName: "Vinod Chinthawar",
    createdAt: serverTs(),
    updatedAt: serverTs(),
  };

  // Non-Admins cannot create appRelease/latest
  await assertFails(supDepot.collection("appRelease").doc("latest").set(validPayload));
  await assertFails(sseDepot.collection("appRelease").doc("latest").set(validPayload));
  await assertFails(techDepot.collection("appRelease").doc("latest").set(validPayload));
  await assertFails(unauthDepot.collection("appRelease").doc("latest").set(validPayload));

  // Admin cannot publish insecure http:// URL, invalid SHA-256, or wrong doc id
  await assertFails(
    adminDepot.collection("appRelease").doc("latest").set({
      ...validPayload,
      downloadUrl: "http://github.com/ppio-depot/ppio-roster/releases/download/v1.1.0/app.apk",
    })
  );
  await assertFails(
    adminDepot.collection("appRelease").doc("latest").set({
      ...validPayload,
      sha256Checksum: "invalid-hash",
    })
  );
  await assertFails(adminDepot.collection("appRelease").doc("other").set(validPayload));

  // Admin CAN publish valid HTTPS GitHub Releases config
  await assertSucceeds(adminDepot.collection("appRelease").doc("latest").set(validPayload));

  // Signed-in users can get 'latest' (but cannot list or delete); unauthenticated cannot read
  await assertSucceeds(techDepot.collection("appRelease").doc("latest").get());
  await assertSucceeds(sseDepot.collection("appRelease").doc("latest").get());
  await assertSucceeds(supDepot.collection("appRelease").doc("latest").get());
  await assertFails(unauthDepot.collection("appRelease").doc("latest").get());
  await assertFails(techDepot.collection("appRelease").get());
  await assertFails(adminDepot.collection("appRelease").doc("latest").delete());

  // Non-Admin cannot hijack or modify the update URL
  await assertFails(
    sseDepot.collection("appRelease").doc("latest").update({
      downloadUrl: "https://evil.example.com/fake.apk",
      updatedAt: serverTs(),
    })
  );

  // Admin can update to mandatory and also pause/clear cleanly
  await assertSucceeds(
    adminDepot.collection("appRelease").doc("latest").update({
      mandatory: true,
      minSupportedVersionCode: 2,
      updatedAt: serverTs(),
    })
  );
  await assertSucceeds(
    adminDepot.collection("appRelease").doc("latest").update({
      latestVersionCode: 0,
      latestVersionName: "",
      minSupportedVersionCode: 0,
      mandatory: false,
      downloadUrl: "",
      releaseNotes: "",
      sha256Checksum: "",
      updatedAt: serverTs(),
    })
  );
});
