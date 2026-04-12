/**
 * Nodare GeoSec - Firebase Cloud Functions
 *
 * These functions handle server-side push notifications triggered by
 * Firestore document events (security alerts, dispatch sessions).
 */

import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

// ============================================================
// FUNCTION: onSecurityAlertCreated
// Triggered when a new security alert is written to Firestore.
// Sends push notification to all Admin and CEO users.
// ============================================================
export const onSecurityAlertCreated = functions.firestore
  .document("security_alerts/{alertId}")
  .onCreate(async (snapshot, context) => {
    const alert = snapshot.data();
    if (!alert) return;

    const alertType = alert.alertType || "unknown";
    const description = alert.description || "A security event occurred";
    const severity = alert.severity || "medium";
    const userName = alert.userName || "Unknown User";

    // Build notification title based on alert type
    let title = "Security Alert";
    switch (alertType) {
      case "mock_provider":
        title = "Fake GPS Detected!";
        break;
      case "developer_options":
        title = "Developer Options Alert";
        break;
      case "spoofing_app":
        title = "Spoofing App Detected!";
        break;
      case "teleport_detected":
        title = "Teleport Detected!";
        break;
      case "accuracy_anomaly":
        title = "GPS Accuracy Anomaly";
        break;
      case "route_deviation":
        title = "Route Deviation Detected";
        break;
      default:
        title = `Security Alert: ${alertType}`;
    }

    const body = `${userName}: ${description} [${severity.toUpperCase()}]`;

    // Send to admin topic
    try {
      await messaging.send({
        topic: "admin_alerts",
        notification: { title, body },
        data: {
          alertId: context.params.alertId,
          alertType: alertType,
          severity: severity,
          channel: "security_alerts_channel",
        },
        android: {
          priority: "high",
          notification: {
            channelId: "security_alerts_channel",
            priority: "max",
            sound: "default",
          },
        },
      });
      functions.logger.info(
        `Sent alert notification: ${title} to admin_alerts topic`
      );
    } catch (error) {
      functions.logger.error("Error sending alert notification:", error);
    }

    // Also send individually to each Admin/CEO device token
    try {
      const adminsSnapshot = await db
        .collection("users")
        .where("role", "in", ["CEO", "Admin"])
        .get();

      const tokens: string[] = [];
      adminsSnapshot.forEach((doc) => {
        const userData = doc.data();
        if (userData.fcmToken) {
          tokens.push(userData.fcmToken);
        }
      });

      if (tokens.length > 0) {
        const response = await messaging.sendEachForMulticast({
          tokens: tokens,
          notification: { title, body },
          data: {
            alertId: context.params.alertId,
            alertType: alertType,
            severity: severity,
            channel: "security_alerts_channel",
          },
          android: {
            priority: "high",
            notification: {
              channelId: "security_alerts_channel",
              priority: "max",
              sound: "default",
            },
          },
        });

        functions.logger.info(
          `Sent to ${response.successCount}/${tokens.length} admin devices`
        );

        // Clean up invalid tokens
        response.responses.forEach((resp, idx) => {
          if (
            !resp.success &&
            (resp.error?.code === "messaging/invalid-registration-token" ||
              resp.error?.code ===
                "messaging/registration-token-not-registered")
          ) {
            functions.logger.warn(`Removing invalid token: ${tokens[idx]}`);
            // Find and clear the invalid token
            db.collection("users")
              .where("fcmToken", "==", tokens[idx])
              .get()
              .then((snap) => {
                snap.forEach((doc) => {
                  doc.ref.update({ fcmToken: "" });
                });
              });
          }
        });
      }
    } catch (error) {
      functions.logger.error("Error sending individual notifications:", error);
    }
  });

// ============================================================
// FUNCTION: onDispatchStarted
// Triggered when a new dispatch session is created.
// Sends notification to admin users.
// ============================================================
export const onDispatchStarted = functions.firestore
  .document("dispatch_sessions/{sessionId}")
  .onCreate(async (snapshot, context) => {
    const session = snapshot.data();
    if (!session) return;

    const userName = session.userName || "Unknown";
    const role = session.role || "Unknown";

    try {
      await messaging.send({
        topic: "admin_alerts",
        notification: {
          title: "Dispatch Started",
          body: `${userName} (${role}) has started a dispatch session`,
        },
        data: {
          sessionId: context.params.sessionId,
          type: "dispatch_started",
          channel: "dispatch_channel",
        },
        android: {
          notification: {
            channelId: "dispatch_channel",
            sound: "default",
          },
        },
      });
      functions.logger.info(`Dispatch start notification sent for ${userName}`);
    } catch (error) {
      functions.logger.error("Error sending dispatch notification:", error);
    }
  });

// ============================================================
// FUNCTION: onDispatchMarkedSuspicious
// Triggered when a dispatch session is updated to suspicious.
// Sends high-priority notification to admin users.
// ============================================================
export const onDispatchUpdated = functions.firestore
  .document("dispatch_sessions/{sessionId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();

    // Only trigger when newly marked suspicious
    if (!before.isSuspicious && after.isSuspicious) {
      const userName = after.userName || "Unknown";

      try {
        await messaging.send({
          topic: "admin_alerts",
          notification: {
            title: "Suspicious Dispatch!",
            body: `${userName}'s dispatch session has been flagged as suspicious`,
          },
          data: {
            sessionId: context.params.sessionId,
            type: "dispatch_suspicious",
            channel: "security_alerts_channel",
          },
          android: {
            priority: "high",
            notification: {
              channelId: "security_alerts_channel",
              priority: "max",
              sound: "default",
            },
          },
        });
        functions.logger.info(
          `Suspicious dispatch notification sent for ${userName}`
        );
      } catch (error) {
        functions.logger.error(
          "Error sending suspicious dispatch notification:",
          error
        );
      }
    }
  });

// ============================================================
// FUNCTION: onUserCreated
// Triggered when a user document is created.
// Auto-subscribes admin users to the admin_alerts topic.
// ============================================================
export const onUserCreated = functions.firestore
  .document("users/{userId}")
  .onCreate(async (snapshot) => {
    const user = snapshot.data();
    if (!user) return;

    const role = user.role;
    const fcmToken = user.fcmToken;

    if (fcmToken && (role === "CEO" || role === "Admin")) {
      try {
        await messaging.subscribeToTopic([fcmToken], "admin_alerts");
        functions.logger.info(
          `Subscribed ${role} user to admin_alerts topic`
        );
      } catch (error) {
        functions.logger.error("Error subscribing to topic:", error);
      }
    }
  });
