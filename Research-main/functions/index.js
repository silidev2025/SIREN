"use strict";

// Fans a newly created alert document out to the FCM "alerts" topic.
//
// Without this, the app learns about an earthquake exactly one way: its Firestore
// listener, which only runs while the app is running. On a phone where the app has
// been swiped away nothing arrives at all -- the alarm stack behind it (full-screen
// intent, wake lock, lock-screen overlay) never gets the chance to fire, because
// nothing wakes the process. This is the piece that wakes it.
//
// Every device that has opened the app once is subscribed to "alerts"
// (AndroidPlatformServices.subscribeToAlertsTopic, called unconditionally at start),
// so nothing else has to change on the app side.

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");
const logger = require("firebase-functions/logger");

initializeApp();

// FCM data payloads are string-to-string. A number or boolean is rejected outright,
// taking the whole send with it, so every value is stringified on the way in.
const str = (value, fallback = "") =>
  value === undefined || value === null ? fallback : String(value);

exports.fanOutAlert = onDocumentCreated("alerts/{alertId}", async (event) => {
  const snap = event.data;
  if (!snap) {
    logger.warn("fanOutAlert fired with no document snapshot; nothing to send.");
    return;
  }

  const alert = snap.data() || {};
  const alertId = event.params.alertId;

  const payload = {
    alertId,
    intensity: str(alert.intensity, "red"),
    magnitudeG: str(alert.magnitudeG, "0"),
    nodeId: str(alert.nodeId),
    // Demo Mode writes a real alerts document that fans out to every device exactly
    // like a genuine event. Carrying the source is what lets the alert screen badge a
    // drill as a simulation immediately, instead of painting it as a real earthquake
    // until the Firestore copy lands.
    source: str(alert.source, "esp32"),
  };

  try {
    const id = await getMessaging().send({
      topic: "alerts",

      // DATA ONLY -- do not add a `notification` block here.
      //
      // For a notification-payload push to a killed app, the system tray draws the
      // message itself and onMessageReceived is never called. The app would show a
      // banner and stay silent: no alarm, no full-screen alert. That is precisely the
      // case this function exists to fix, so a `notification` block would defeat it
      // while still looking like it works on a phone with the app open.
      data: payload,

      android: {
        // High priority wakes a dozing device, and is also what exempts the
        // foreground-service start from Android 12+ background restrictions.
        priority: "high",

        // An earthquake alarm that arrives hours late is worse than none -- it would
        // sound a full take-cover alert for an event long over. Five minutes keeps the
        // redelivery window useful without letting a stale alert surface.
        ttl: 5 * 60 * 1000,
      },
    });

    logger.info("Alert fanned out", { alertId, messageId: id, ...payload });
  } catch (err) {
    // Left to throw so the platform retries: a push that silently fails to send is the
    // same outcome as not having this function at all.
    logger.error("Alert fan-out FAILED", { alertId, error: err.message, ...payload });
    throw err;
  }
});
