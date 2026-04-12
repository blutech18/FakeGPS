<p align="center">
  <img src="app/src/main/res/drawable/logo_header.png" alt="Nodare GeoSec Logo" width="280"/>
</p>

<h1 align="center">Nodare GeoSec</h1>

<p align="center">
  <strong>GPS Security & Dispatch Management for Field Operations</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?logo=android" alt="Platform" />
  <img src="https://img.shields.io/badge/Min%20SDK-26-blue" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Target%20SDK-36-blue" alt="Target SDK" />
  <img src="https://img.shields.io/badge/Kotlin-2.0.21-purple?logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Firebase-Firestore%20%7C%20Auth%20%7C%20FCM-orange?logo=firebase" alt="Firebase" />
  <img src="https://img.shields.io/badge/License-Proprietary-red" alt="License" />
</p>

---

## Overview

Nodare GeoSec is a native Android application designed for security companies and logistics operations that need to track field employees (technicians, drivers) in real-time while detecting GPS spoofing attempts. The app provides a complete dispatch management workflow with multi-layered fake GPS detection, route deviation monitoring, and role-based access control.

---

## Features

### 🛡️ Multi-Layered Fake GPS Detection

The core of the application — a 5-layer detection engine that runs on every GPS update during an active dispatch:

| Layer | Detection Method | Severity |
|-------|-----------------|----------|
| 1 | **Mock Provider Detection** — Checks `Location.isMock` / `isFromMockProvider` | Critical |
| 2 | **Developer Options Scan** — Detects if mock location setting is enabled | High |
| 3 | **Spoofing App Detection** — Scans installed packages against a database of 20+ known spoofing apps | Critical |
| 4 | **Movement Logic Analysis** — Detects impossible speeds (>200 km/h) and teleportation (>3km in <10s) | Critical |
| 5 | **GPS Accuracy Anomaly** — Statistical analysis of accuracy variance; real GPS has natural fluctuation, spoofed GPS is unnaturally consistent | Medium |

### 📍 Real-Time GPS Tracking

- Foreground service with high-accuracy location updates every 15 seconds
- Offline-first architecture: GPS logs are stored locally (Room) when offline and synced via WorkManager when connectivity is restored
- Live map view for admins showing all active employees with color-coded markers (blue = normal, red = suspicious)

### 🗺️ Route Deviation Detection

- Expected routes are stored as encoded polylines (Google Directions API)
- Real-time comparison of employee position against the expected route using `PolyUtil`
- Alerts triggered when deviation exceeds 500 meters from the expected path

### 📋 Dispatch Management

- Full dispatch lifecycle: start → track → confirm → end
- Encrypted dispatch key generated at session start — required to end the dispatch (prevents unauthorized session closure)
- Dispatch history with status tracking: `active`, `completed`, `suspicious`
- Sessions automatically flagged as suspicious on critical security alerts

### ✅ Employee Check-In / Check-Out

- GPS-verified check-in with device ID binding
- Time-stamped attendance logs stored in Firestore
- Check-in required before starting a dispatch session

### 🔧 Equipment Inventory

- Track equipment with status management: `Repaired`, `To Be Repaired`, `Pull-Out`
- Admin-only CRUD operations
- Categorized equipment listing

### 🔔 Push Notifications (Firebase Cloud Functions)

- Real-time alerts pushed to Admin/CEO devices on:
  - Security alert creation (fake GPS, teleport, spoofing app, etc.)
  - New dispatch session started
  - Dispatch session flagged as suspicious
- Automatic FCM topic subscription for admin users
- Invalid token cleanup on delivery failure

### 👥 Role-Based Access Control

Four distinct roles with different permissions and UI views:

| Role | Capabilities |
|------|-------------|
| **CEO** | Full read/write access, delete permissions, executive overview dashboard |
| **Admin** | User management, equipment CRUD, alert resolution, monitoring dashboard |
| **Technician** | Check-in/out, start/end dispatches, GPS tracked during dispatch |
| **Car Driver** | Check-in/out, start/end deliveries, GPS tracked during delivery |

Firestore security rules enforce role-based access at the database level.

---

## Tech Stack

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Kotlin | 2.0.21 |
| Build System | Gradle (Kotlin DSL) + AGP | 9.1.0 |
| Min SDK | Android 8.0 (API 26) | — |
| Target SDK | Android 16 (API 36) | — |
| DI | Hilt (Dagger) | 2.52 |
| Database (Local) | Room | 2.8.4 |
| Database (Remote) | Cloud Firestore | BOM 34.11.0 |
| Auth | Firebase Authentication | BOM 34.11.0 |
| Push Notifications | Firebase Cloud Messaging | BOM 34.11.0 |
| Cloud Functions | Firebase Functions (TypeScript) | — |
| Maps | Google Maps SDK + Maps Utils | 20.0.0 / 4.1.1 |
| Location | Google Play Services Location | 21.3.0 |
| Networking | Retrofit 3 + OkHttp 5 | 3.0.0 / 5.3.2 |
| Navigation | Jetpack Navigation + SafeArgs | 2.9.7 |
| Background Work | WorkManager | 2.10.0 |
| Lifecycle | ViewModel + LiveData | 2.10.0 |
| Concurrency | Kotlin Coroutines | 1.10.2 |
| UI | Material Design 3 + ViewBinding | 1.13.0 |
| Symbol Processing | KSP | 2.0.21-1.0.28 |

---

## Project Structure

```
├── app/
│   └── src/main/java/com/nodare/geosec/
│       ├── data/
│       │   ├── local/              # Room database, DAOs, entities
│       │   ├── model/              # Firestore data models
│       │   ├── remote/             # Retrofit API services
│       │   └── repository/         # Repository layer (Auth, GPS, Dispatch, etc.)
│       ├── di/                     # Hilt dependency injection modules
│       ├── domain/                 # Use cases (clean architecture layer)
│       ├── presentation/
│       │   ├── alerts/             # Security alerts list & management
│       │   ├── auth/               # Login screen & authentication
│       │   ├── checkin/            # Employee check-in/out
│       │   ├── common/             # Shared adapters, dialogs, views
│       │   ├── dashboard/          # Main dashboard (admin & driver views)
│       │   ├── dispatch/           # Dispatch session management
│       │   ├── equipment/          # Equipment inventory
│       │   ├── profile/            # User profile
│       │   └── tracking/           # Live map tracking (admin)
│       ├── services/
│       │   ├── detection/          # FakeGpsDetector, RouteDeviationDetector
│       │   ├── gps/                # GpsTrackingService (foreground service)
│       │   ├── notification/       # FCM service & notification helper
│       │   └── worker/             # GpsSyncWorker (offline sync)
│       └── util/                   # Constants, device utils, location math
├── firebase/
│   ├── functions/src/              # Cloud Functions (TypeScript)
│   ├── firestore.rules             # Firestore security rules
│   └── firestore.indexes.json     # Firestore indexes
└── gradle/
    └── libs.versions.toml          # Version catalog
```

---

## Prerequisites

- **Android Studio** Meerkat (2024.3.1) or newer
- **JDK 21** (required by AGP 9.1.0+)
- **Firebase Project** with the following services enabled:
  - Authentication (Email/Password)
  - Cloud Firestore
  - Cloud Functions
  - Cloud Messaging
  - Cloud Storage
- **Google Maps API Key** with Maps SDK for Android and Directions API enabled
- **Node.js 18+** (for Firebase Cloud Functions deployment)

---

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/blutech18/FakeGPS.git
cd FakeGPS
```

### 2. Firebase Setup

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Register an Android app with package name `com.nodare.geosec`
3. Download `google-services.json` and place it in the `app/` directory
4. Enable Authentication (Email/Password provider)
5. Create a Cloud Firestore database
6. Deploy Firestore security rules:
   ```bash
   cd firebase
   firebase deploy --only firestore:rules
   ```
7. Deploy Cloud Functions:
   ```bash
   cd firebase/functions
   npm install
   cd ..
   firebase deploy --only functions
   ```

### 3. Google Maps API Key

Add your Maps API key to `local.properties` (this file is gitignored):

```properties
MAPS_API_KEY=YOUR_API_KEY_HERE
```

The key is automatically loaded into `AndroidManifest.xml` via `manifestPlaceholders` at build time.

### 4. Create Initial Users

A setup script is provided to seed initial user accounts:

```bash
npm install
node create-initial-users.js
```

> Requires a `serviceAccountKey.json` file from your Firebase project (Project Settings → Service Accounts → Generate New Private Key).

### 5. Build & Run

```bash
./gradlew assembleDebug
```

Or open the project in Android Studio and run on a device/emulator with Google Play Services.

---

## Firestore Collections

| Collection | Description |
|-----------|-------------|
| `users` | User profiles with roles and FCM tokens |
| `roles` | Role definitions (CEO, Admin, Technician, Car Driver) |
| `check_in_logs` | GPS-verified attendance records |
| `equipment_inventory` | Equipment tracking with status |
| `dispatch_sessions` | Dispatch lifecycle records |
| `gps_logs` | Immutable GPS coordinate logs |
| `security_alerts` | Fake GPS and route deviation alerts |
| `routes` | Expected route polylines for deviation detection |

---

## Permissions

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | Firebase & API communication |
| `ACCESS_FINE_LOCATION` | High-accuracy GPS tracking |
| `ACCESS_COARSE_LOCATION` | Fallback location |
| `ACCESS_BACKGROUND_LOCATION` | GPS tracking while app is backgrounded |
| `FOREGROUND_SERVICE` | Persistent GPS tracking service |
| `FOREGROUND_SERVICE_LOCATION` | Location-type foreground service (Android 14+) |
| `POST_NOTIFICATIONS` | Push notification display (Android 13+) |
| `WAKE_LOCK` | Background WorkManager tasks |
| `QUERY_ALL_PACKAGES` | Scanning for installed spoofing applications |

---

## Security Considerations

- GPS logs are immutable in Firestore (update rules set to `false`)
- Firestore security rules enforce role-based access at the database level
- Dispatch keys are required to end sessions, preventing unauthorized closure
- Alert cooldown system (5 minutes per alert type) prevents notification flooding
- FCM tokens are automatically cleaned up when they become invalid
- ProGuard/R8 enabled for release builds with code shrinking and resource optimization

---

## Authors

Developed by **Nodare** — Blutech18

---

## License

This project is proprietary software. All rights reserved.
