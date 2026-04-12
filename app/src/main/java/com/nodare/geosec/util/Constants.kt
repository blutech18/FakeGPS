package com.nodare.geosec.util

object Constants {
    // Firestore Collections
    const val COLLECTION_USERS = "users"
    const val COLLECTION_ROLES = "roles"
    const val COLLECTION_CHECK_IN_LOGS = "check_in_logs"
    const val COLLECTION_EQUIPMENT_INVENTORY = "equipment_inventory"
    const val COLLECTION_DISPATCH_SESSIONS = "dispatch_sessions"
    const val COLLECTION_GPS_LOGS = "gps_logs"
    const val COLLECTION_SECURITY_ALERTS = "security_alerts"
    const val COLLECTION_ROUTES = "routes"

    // GPS Tracking
    const val GPS_UPDATE_INTERVAL_MS = 15_000L
    const val GPS_FASTEST_INTERVAL_MS = 10_000L
    const val GPS_MAX_WAIT_TIME_MS = 20_000L

    // Fake GPS Detection Thresholds
    const val MAX_SPEED_KMH = 200.0
    const val MAX_DISTANCE_JUMP_METERS = 3000.0
    const val TELEPORT_TIME_THRESHOLD_MS = 10_000L
    const val GPS_ACCURACY_ANOMALY_THRESHOLD = 100f

    // Route Deviation
    const val ROUTE_DEVIATION_THRESHOLD_METERS = 500.0

    // Equipment Status
    const val STATUS_REPAIRED = "Repaired"
    const val STATUS_TO_BE_REPAIRED = "To Be Repaired"
    const val STATUS_PULL_OUT = "Pull-Out"

    // User Roles
    const val ROLE_CEO = "CEO"
    const val ROLE_ADMIN = "Admin"
    const val ROLE_TECHNICIAN = "Technician"
    const val ROLE_CAR_DRIVER = "Car Driver"

    // Alert Types
    const val ALERT_FAKE_GPS = "fake_gps"
    const val ALERT_ROUTE_DEVIATION = "route_deviation"
    const val ALERT_DISPATCH_STARTED = "dispatch_started"
    const val ALERT_TECHNICIAN_OFFLINE = "technician_offline"
    const val ALERT_MOCK_PROVIDER = "mock_provider"
    const val ALERT_DEV_OPTIONS = "developer_options"
    const val ALERT_SPOOF_APP = "spoofing_app"
    const val ALERT_TELEPORT = "teleport_detected"
    const val ALERT_ACCURACY_ANOMALY = "accuracy_anomaly"

    // Alert Severity
    const val SEVERITY_LOW = "low"
    const val SEVERITY_MEDIUM = "medium"
    const val SEVERITY_HIGH = "high"
    const val SEVERITY_CRITICAL = "critical"

    // Known Spoofing App Packages
    val KNOWN_SPOOFING_PACKAGES = listOf(
        "com.lexa.fakegps",
        "com.incorporateapps.fakegps",
        "com.fakegps.mock",
        "com.gsmartstudio.fakegps",
        "com.ltp.pro.fakelocation",
        "com.marlon.floating.fake.location",
        "com.evezzon.fakegps",
        "com.theappninjas.gpsjoystick",
        "com.theappninjas.fakegpsjoystick",
        "org.hola.gpslocation",
        "com.blogspot.newlooper.fakegps",
        "ru.gavrikov.mocklocations",
        "com.divi.fakelocations",
        "com.rosteam.gpsemulator",
        "com.lkr.fakelocation",
        "location.changer.fake.gps.spoof",
        "com.fake.location",
        "com.tselofan.fakegps",
        "com.pe.fakegpsrun",
        "fake.gps.location"
    )
}
