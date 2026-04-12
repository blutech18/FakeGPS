// create-initial-users.js
// Run this ONCE to seed CEO/Admin/Technician/Driver accounts in Firebase Auth + Firestore.
//
// Usage:
// 1) Place serviceAccountKey.json (Firebase service account) next to this file.
// 2) Create a .env file with user passwords (see .env.example).
// 3) From this folder (D:\Clients\FakeGPS), run:
//      npm install
//      node create-initial-users.js

const admin = require("firebase-admin");
const path = require("path");
const fs = require("fs");

// Simple .env loader (no extra dependency needed)
function loadEnv() {
  const envPath = path.join(__dirname, ".env");
  if (!fs.existsSync(envPath)) {
    console.error("ERROR: .env file not found. Copy .env.example to .env and fill in passwords.");
    process.exit(1);
  }
  const lines = fs.readFileSync(envPath, "utf-8").split("\n");
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const eqIndex = trimmed.indexOf("=");
    if (eqIndex === -1) continue;
    const key = trimmed.substring(0, eqIndex).trim();
    const value = trimmed.substring(eqIndex + 1).trim();
    process.env[key] = value;
  }
}

loadEnv();

// Load service account JSON from same directory
const serviceAccount = require(path.join(__dirname, "serviceAccountKey.json"));

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const auth = admin.auth();
const db = admin.firestore();

// Initial users for Nodare GeoSec — passwords loaded from .env
const initialUsers = [
  {
    email: "ceo@nodare.com",
    password: process.env.CEO_PASSWORD,
    displayName: "Nodare CEO",
    role: "CEO",
  },
  {
    email: "admin@nodare.com",
    password: process.env.ADMIN_PASSWORD,
    displayName: "Dispatch Admin",
    role: "Admin",
  },
  {
    email: "tech@nodare.com",
    password: process.env.TECH_PASSWORD,
    displayName: "Field Technician",
    role: "Technician",
  },
  {
    email: "driver@nodare.com",
    password: process.env.DRIVER_PASSWORD,
    displayName: "Car Driver",
    role: "Car Driver",
  },
];

// Validate all passwords are set
for (const u of initialUsers) {
  if (!u.password) {
    console.error(`ERROR: Password not set for ${u.email}. Check your .env file.`);
    process.exit(1);
  }
}

async function createUserAndProfile({ email, password, displayName, role }) {
  try {
    let userRecord;

    // Create or reuse Auth user
    try {
      userRecord = await auth.getUserByEmail(email);
      console.log(`[SKIP AUTH] User already exists: ${email}`);
    } catch (err) {
      if (err.code === "auth/user-not-found") {
        userRecord = await auth.createUser({
          email,
          password,
          displayName,
          emailVerified: true,
          disabled: false,
        });
        console.log(`[CREATED AUTH] ${email} (${role}) uid=${userRecord.uid}`);
      } else {
        throw err;
      }
    }

    const uid = userRecord.uid;

    // Upsert Firestore profile document in "users" collection
    const userDocRef = db.collection("users").doc(uid);

    await userDocRef.set(
      {
        email,
        displayName,
        role,
        isActive: true,
        fcmToken: "",
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    console.log(`[UPSERT PROFILE] users/${uid} role=${role}`);
  } catch (err) {
    console.error(`[ERROR] ${email}:`, err.message);
  }
}

async function main() {
  console.log("=== Creating initial Nodare GeoSec users ===");

  for (const u of initialUsers) {
    await createUserAndProfile(u);
  }

  console.log("=== DONE ===");
  process.exit(0);
}

main().catch((err) => {
  console.error("Fatal error:", err);
  process.exit(1);
});
