#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <time.h>

#include "secrets.h"

const int PIN_LED_GREEN  = 25;
const int PIN_LED_YELLOW = 26;
const int PIN_LED_RED    = 27;
const int PIN_BUZZER     = 14;
const int PIN_BUZZER2    = 13;

// The MPU6050 and the LCD share one I2C bus. They do not clash because each
// answers on its own address: LCD 0x27, MPU6050 0x68 (0x69 if AD0 is high).
const int PIN_SDA = 21;
const int PIN_SCL = 22;

LiquidCrystal_I2C lcd(0x27, 16, 2);

#define BUZZER_IS_ACTIVE 1

// ---------------------------------------------------------------------------
// MPU6050
// ---------------------------------------------------------------------------
const uint8_t MPU_REG_SMPLRT_DIV   = 0x19;
const uint8_t MPU_REG_CONFIG       = 0x1A;
const uint8_t MPU_REG_ACCEL_CONFIG = 0x1C;
const uint8_t MPU_REG_ACCEL_XOUT_H = 0x3B;
const uint8_t MPU_REG_PWR_MGMT_1   = 0x6B;
const uint8_t MPU_REG_WHO_AM_I     = 0x75;

// +/-2 g full scale. Earthquake shaking that matters here is well under 1 g,
// and the smallest range gives the finest resolution: 16384 counts per g.
const float LSB_PER_G = 16384.0f;

uint8_t  mpuAddr  = 0;
bool     mpuReady = false;
uint32_t sensorErrors = 0;

// A still accelerometer always feels exactly 1 g of gravity, whatever way it
// is mounted. If calibration measures something far from 1 g, the sensor is
// not being read correctly and every magnitude after it would be wrong.
const float GRAVITY_MIN_G = 0.85f;
const float GRAVITY_MAX_G = 1.15f;

// The MPU6050's digital noise floor is a few thousandths of a g. Anything
// well above that during calibration means the board was moving or bumped.
const float SIGMA_WARN_G = 0.020f;

const uint16_t CAL_SAMPLES     = 1000;
const uint8_t  CAL_INTERVAL_MS = 10;
const uint8_t  MA_WINDOW       = 8;
const uint32_t SAMPLE_US       = 5000;

const float    SIGMA_MULTIPLIER  = 3.0f;

// The MPU6050 is far quieter than the ADXL335 read through the ESP32's ADC,
// so the trigger can sit lower and give the Yellow band real room. If desk
// bumps or footsteps start setting it off, raise this towards 0.050.
const float    MIN_TRIGGER_G     = 0.030f;

// Never let a noisy calibration push the trigger past the Red boundary,
// which would silently make Yellow impossible.
const float    MAX_TRIGGER_G     = 0.090f;

const float    ONSET_FRACTION    = 0.50f;
const uint16_t CONFIRM_MS        = 300;
const uint8_t  MIN_SAMPLES_ABOVE = 8;

// Alarm sounds for 3 seconds, then the system goes straight back to
// monitoring so it can detect again right away.
const uint16_t ALERT_HOLD_MS     = 3000;
const uint16_t COOLDOWN_MS       = 0;

// Must stay in lockstep with Intensity.fromMagnitude in the mobile app
// (shared/src/commonMain/kotlin/com/siren/mobile/model/Models.kt) and with the
// thresholds published in the research paper:
//   Green  0.000 - 0.010 g   Intensity I-IV    light shaking
//   Yellow 0.010 - 0.120 g   Intensity V-VI    moderate shaking
//   Red    0.120 g and above Intensity VII+    destructive shaking
const float BAND_YELLOW_G = 0.010f;
const float BAND_RED_G    = 0.120f;

float biasX = 0, biasY = 0, biasZ = 1;
float sigmaResultantG = 0.0f;
float gravityG = 1.0f;
float triggerG = MIN_TRIGGER_G;
float onsetG   = MIN_TRIGGER_G * ONSET_FRACTION;
bool  calibrationSuspect = false;

int16_t  bufX[MA_WINDOW], bufY[MA_WINDOW], bufZ[MA_WINDOW];
uint8_t  bufIdx = 0;
int32_t  sumX = 0, sumY = 0, sumZ = 0;
bool     bufPrimed = false;

enum State { IDLE, CONFIRMING, ALERTING, COOLDOWN };
State state = IDLE;

uint32_t tOnset = 0, tDetect = 0, tAlert = 0, tLed = 0, tLcd = 0, tStateEnd = 0;
float    peakG = 0;
uint8_t  samplesAbove = 0;
uint16_t seq = 0;
char     lastType[10] = "shake";

// A knock or a dropped object produces one enormous spike and nothing after
// it, while an earthquake shakes continuously. Classifying on the mean over
// the confirmation window instead of the single highest sample separates the
// two: an impulse averages down, sustained motion does not.
float    sumG = 0;
uint16_t countG = 0;

uint32_t nextSampleUs = 0;

String   idToken;
String   refreshToken;
uint32_t tokenRefreshAtMs = 0;
bool     authed = false;

const char* bandName(float g) {
  if (g >= BAND_RED_G)    return "red";
  if (g >= BAND_YELLOW_G) return "yellow";
  return "green";
}

void buzzerOn() {
#if BUZZER_IS_ACTIVE
  digitalWrite(PIN_BUZZER,  HIGH);
  digitalWrite(PIN_BUZZER2, HIGH);
#else
  #if ESP_ARDUINO_VERSION_MAJOR >= 3
    tone(PIN_BUZZER,  2500);
    tone(PIN_BUZZER2, 2500);
  #else
    ledcSetup(0, 2500, 8);
    ledcAttachPin(PIN_BUZZER, 0);
    ledcWrite(0, 128);
    ledcSetup(1, 2500, 8);
    ledcAttachPin(PIN_BUZZER2, 1);
    ledcWrite(1, 128);
  #endif
#endif
}

void buzzerOff() {
#if BUZZER_IS_ACTIVE
  digitalWrite(PIN_BUZZER,  LOW);
  digitalWrite(PIN_BUZZER2, LOW);
#else
  #if ESP_ARDUINO_VERSION_MAJOR >= 3
    noTone(PIN_BUZZER);
    noTone(PIN_BUZZER2);
  #else
    ledcWrite(0, 0);
    ledcWrite(1, 0);
  #endif
#endif
}

void setLeds(bool g, bool y, bool r) {
  digitalWrite(PIN_LED_GREEN,  g ? HIGH : LOW);
  digitalWrite(PIN_LED_YELLOW, y ? HIGH : LOW);
  digitalWrite(PIN_LED_RED,    r ? HIGH : LOW);
}

void lcdTwoLines(const char* a, const char* b) {
  lcd.clear();
  lcd.setCursor(0, 0); lcd.print(a);
  lcd.setCursor(0, 1); lcd.print(b);
}

// ---------------------------------------------------------------------------
// MPU6050 low-level access (plain Wire, no extra library needed)
// ---------------------------------------------------------------------------
bool i2cPresent(uint8_t addr) {
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

bool mpuWrite(uint8_t reg, uint8_t val) {
  Wire.beginTransmission(mpuAddr);
  Wire.write(reg);
  Wire.write(val);
  return Wire.endTransmission() == 0;
}

int mpuReadByte(uint8_t reg) {
  Wire.beginTransmission(mpuAddr);
  Wire.write(reg);
  if (Wire.endTransmission(false) != 0) return -1;
  if (Wire.requestFrom((uint16_t)mpuAddr, (size_t)1, true) != 1) return -1;
  return Wire.read();
}

bool mpuInit() {
  mpuReady = false;

  if      (i2cPresent(0x68)) mpuAddr = 0x68;
  else if (i2cPresent(0x69)) mpuAddr = 0x69;
  else {
    Serial.println("SENSOR,ERR,MPU6050 not found on I2C (tried 0x68 and 0x69)");
    Serial.println("SENSOR,HINT,check VCC=3V3, GND, SDA=GPIO21, SCL=GPIO22; type I to scan the bus");
    return false;
  }

  mpuWrite(MPU_REG_PWR_MGMT_1, 0x80);   // reset
  delay(100);
  mpuWrite(MPU_REG_PWR_MGMT_1, 0x01);   // wake up, gyro PLL clock (more stable)
  delay(50);
  mpuWrite(MPU_REG_SMPLRT_DIV, 0x04);   // 1 kHz / (1 + 4) = 200 Hz, matches SAMPLE_US
  mpuWrite(MPU_REG_CONFIG, 0x03);       // digital low-pass ~44 Hz, cuts high-frequency noise
  bool ok = mpuWrite(MPU_REG_ACCEL_CONFIG, 0x00);   // +/-2 g

  int who = mpuReadByte(MPU_REG_WHO_AM_I);

  // Genuine MPU6050 answers 0x68. Many GY-521 boards carry a clone (0x70,
  // 0x72, 0x98...) with the same accelerometer registers, so only warn.
  Serial.printf("SENSOR,ok,addr=0x%02X,whoami=0x%02X%s\n",
                mpuAddr, who < 0 ? 0 : who,
                (who == 0x68) ? "" : ",clone-or-variant (should still work)");

  mpuReady = ok && who >= 0;
  if (!mpuReady) Serial.println("SENSOR,ERR,MPU6050 answered but would not configure");
  return mpuReady;
}

bool readAccelRaw(int16_t& ax, int16_t& ay, int16_t& az) {
  if (!mpuReady) return false;

  Wire.beginTransmission(mpuAddr);
  Wire.write(MPU_REG_ACCEL_XOUT_H);
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom((uint16_t)mpuAddr, (size_t)6, true) != 6) return false;

  // Read into separate bytes first: in "(read() << 8) | read()" C++ does not
  // guarantee which read() runs first, which can silently swap the bytes.
  uint8_t xh = Wire.read(), xl = Wire.read();
  uint8_t yh = Wire.read(), yl = Wire.read();
  uint8_t zh = Wire.read(), zl = Wire.read();
  ax = (int16_t)((xh << 8) | xl);
  ay = (int16_t)((yh << 8) | yl);
  az = (int16_t)((zh << 8) | zl);
  return true;
}

void noteSensorError() {
  sensorErrors++;
  static uint32_t lastPrint = 0;
  if (millis() - lastPrint > 1000) {
    lastPrint = millis();
    Serial.printf("SENSOR,ERR,read failed,total=%lu\n", (unsigned long)sensorErrors);
  }
}

float readResultantG() {
  int16_t rx, ry, rz;

  // A failed read counts as "no motion". Returning garbage here is exactly
  // what caused false alarms with the old sensor, so never guess.
  if (!readAccelRaw(rx, ry, rz)) {
    noteSensorError();
    return 0.0f;
  }

  if (!bufPrimed) {
    for (uint8_t i = 0; i < MA_WINDOW; i++) { bufX[i] = rx; bufY[i] = ry; bufZ[i] = rz; }
    sumX = (int32_t)rx * MA_WINDOW;
    sumY = (int32_t)ry * MA_WINDOW;
    sumZ = (int32_t)rz * MA_WINDOW;
    bufPrimed = true;
  }

  sumX += rx - bufX[bufIdx]; bufX[bufIdx] = rx;
  sumY += ry - bufY[bufIdx]; bufY[bufIdx] = ry;
  sumZ += rz - bufZ[bufIdx]; bufZ[bufIdx] = rz;
  bufIdx = (bufIdx + 1) % MA_WINDOW;

  // Subtracting the calibrated bias removes gravity, leaving only the shaking.
  float dx = (sumX / (float)MA_WINDOW) / LSB_PER_G - biasX;
  float dy = (sumY / (float)MA_WINDOW) / LSB_PER_G - biasY;
  float dz = (sumZ / (float)MA_WINDOW) / LSB_PER_G - biasZ;

  return sqrtf(dx * dx + dy * dy + dz * dz);
}

// ---------------------------------------------------------------------------
// Calibration
// ---------------------------------------------------------------------------
void printCalibration() {
  Serial.println("CAL,done");
  Serial.printf("CAL,bias_g,%.4f,%.4f,%.4f\n", biasX, biasY, biasZ);
  Serial.printf("CAL,gravity_g,%.4f\n", gravityG);
  Serial.printf("CAL,sigma_g,%.5f\n", sigmaResultantG);
  Serial.printf("CAL,trigger_g,%.4f\n", triggerG);
  Serial.printf("CAL,onset_g,%.4f\n", onsetG);
  Serial.printf("CAL,health,%s\n", calibrationSuspect ? "SUSPECT" : "ok");
}

void checkCalibrationHealth(uint16_t failedReads) {
  calibrationSuspect = false;

  if (!mpuReady) {
    calibrationSuspect = true;
    Serial.println("CAL,WARN,no MPU6050, detection is off until it is connected (then type C)");
    return;
  }

  if (failedReads > 0) {
    calibrationSuspect = true;
    Serial.printf("CAL,WARN,%u of %u reads failed, check SDA/SCL wires\n",
                  failedReads, CAL_SAMPLES);
  }

  if (gravityG < GRAVITY_MIN_G || gravityG > GRAVITY_MAX_G) {
    calibrationSuspect = true;
    Serial.printf("CAL,WARN,sensor feels %.3f g of gravity, expected about 1.000 g\n", gravityG);
    Serial.println("CAL,WARN,the sensor is not being read correctly; check wiring");
  }

  if (sigmaResultantG > SIGMA_WARN_G) {
    calibrationSuspect = true;
    Serial.printf("CAL,WARN,noise %.4f g is high, expected under %.3f g\n",
                  sigmaResultantG, SIGMA_WARN_G);
    Serial.println("CAL,WARN,recalibrate with C while nothing touches the sensor");
  }
}

void calibrate() {
  state = IDLE;
  setLeds(false, false, false);
  buzzerOff();

  if (!mpuReady) mpuInit();

  lcdTwoLines("Calibrating...", "Keep it still");
  Serial.println("CAL,start,keep the sensor still");

  double sX = 0, sY = 0, sZ = 0;
  double qX = 0, qY = 0, qZ = 0;
  uint16_t good = 0, failed = 0;

  for (uint16_t i = 0; i < CAL_SAMPLES; i++) {
    int16_t rx, ry, rz;
    if (readAccelRaw(rx, ry, rz)) {
      double gx = rx / LSB_PER_G, gy = ry / LSB_PER_G, gz = rz / LSB_PER_G;
      sX += gx; qX += gx * gx;
      sY += gy; qY += gy * gy;
      sZ += gz; qZ += gz * gz;
      good++;
    } else {
      failed++;
    }

    if (i % 100 == 0) { lcd.setCursor(14, 0); lcd.print((int)(i / 100)); }
    delay(CAL_INTERVAL_MS);
  }

  if (good >= 2) {
    const double n = good;
    biasX = sX / n;
    biasY = sY / n;
    biasZ = sZ / n;

    double varX = (qX - (sX * sX) / n) / (n - 1);
    double varY = (qY - (sY * sY) / n) / (n - 1);
    double varZ = (qZ - (sZ * sZ) / n) / (n - 1);
    if (varX < 0) varX = 0;
    if (varY < 0) varY = 0;
    if (varZ < 0) varZ = 0;

    sigmaResultantG = sqrt(varX + varY + varZ);
    gravityG = sqrtf(biasX * biasX + biasY * biasY + biasZ * biasZ);
  } else {
    sigmaResultantG = 0;
    gravityG = 0;
  }

  float thresholdG = SIGMA_MULTIPLIER * sigmaResultantG;
  triggerG = max(thresholdG, MIN_TRIGGER_G);
  triggerG = min(triggerG, MAX_TRIGGER_G);
  onsetG   = triggerG * ONSET_FRACTION;

  bufPrimed = false;
  checkCalibrationHealth(failed);
  printCalibration();

  if (!mpuReady)               lcdTwoLines("MPU6050 missing", "Check SDA/SCL");
  else if (calibrationSuspect) lcdTwoLines("Check sensor", "See serial log");
  else                         lcdTwoLines("S.I.R.E.N. ready", "Monitoring...");
  setLeds(true, false, false);
  nextSampleUs = micros();
}

// ---------------------------------------------------------------------------
// Network and Firebase
// ---------------------------------------------------------------------------
void connectWifi() {
  Serial.printf("WiFi: connecting to %s\n", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  uint32_t start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 30000) {
    delay(500);
    Serial.print('.');
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("WiFi: connected, ip=");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println("WiFi: FAILED -- check SSID/password and that it is 2.4 GHz");
  }
}

void syncClock() {
  configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  Serial.print("NTP: syncing");
  uint32_t start = millis();
  while (time(nullptr) < 1700000000 && millis() - start < 20000) {
    delay(300);
    Serial.print('.');
  }
  Serial.println();
  Serial.printf("NTP: epoch=%lu\n", (unsigned long)time(nullptr));
}

String isoNowUtc() {
  time_t now = time(nullptr);
  struct tm t;
  gmtime_r(&now, &t);
  char buf[25];
  strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", &t);
  return String(buf);
}

int postJson(const String& url, const String& body, String& out, const char* contentType = "application/json") {
  WiFiClientSecure client;

  client.setInsecure();

  HTTPClient http;
  http.setTimeout(12000);
  if (!http.begin(client, url)) {
    out = "begin() failed";
    return -1;
  }
  http.addHeader("Content-Type", contentType);
  if (idToken.length() && url.indexOf("firestore.googleapis.com") >= 0) {
    http.addHeader("Authorization", "Bearer " + idToken);
  }

  int code = http.POST(body);
  out = http.getString();
  http.end();
  return code;
}

bool signIn() {
  String url = String("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=") + FIREBASE_API_KEY;

  JsonDocument req;
  req["email"] = ESP32_ACCOUNT_EMAIL;
  req["password"] = ESP32_ACCOUNT_PASSWORD;
  req["returnSecureToken"] = true;
  String body;
  serializeJson(req, body);

  String resp;
  int code = postJson(url, body, resp);
  if (code != 200) {
    Serial.printf("AUTH: sign-in failed http=%d %s\n", code, resp.c_str());
    authed = false;
    return false;
  }

  JsonDocument doc;
  if (deserializeJson(doc, resp)) {
    Serial.println("AUTH: could not parse sign-in response");
    return false;
  }

  idToken      = doc["idToken"].as<String>();
  refreshToken = doc["refreshToken"].as<String>();
  uint32_t expiresIn = doc["expiresIn"].as<String>().toInt();
  if (expiresIn < 60) expiresIn = 3600;
  tokenRefreshAtMs = millis() + (expiresIn - 300) * 1000UL;
  authed = true;

  Serial.printf("AUTH: signed in as %s, token good for %lus\n",
                ESP32_ACCOUNT_EMAIL, (unsigned long)expiresIn);
  return true;
}

bool refreshIdToken() {
  if (!refreshToken.length()) return signIn();

  String url = String("https://securetoken.googleapis.com/v1/token?key=") + FIREBASE_API_KEY;
  String body = "grant_type=refresh_token&refresh_token=" + refreshToken;

  String resp;
  int code = postJson(url, body, resp, "application/x-www-form-urlencoded");
  if (code != 200) {
    Serial.printf("AUTH: refresh failed http=%d, falling back to sign-in\n", code);
    return signIn();
  }

  JsonDocument doc;
  if (deserializeJson(doc, resp)) return signIn();

  idToken      = doc["id_token"].as<String>();
  refreshToken = doc["refresh_token"].as<String>();
  uint32_t expiresIn = doc["expires_in"].as<String>().toInt();
  if (expiresIn < 60) expiresIn = 3600;
  tokenRefreshAtMs = millis() + (expiresIn - 300) * 1000UL;
  authed = true;

  Serial.println("AUTH: token refreshed");
  return true;
}

void keepAuthFresh() {
  if (!authed) { signIn(); return; }
  if ((int32_t)(millis() - tokenRefreshAtMs) >= 0) refreshIdToken();
}

String makeAlertId(uint16_t s) {
  return String(NODE_ID) + "-" + String((unsigned long)time(nullptr)) + "-" + String(s);
}

bool createAlert(const char* intensity, float magnitudeG, uint16_t s, String& alertIdOut) {
  alertIdOut = makeAlertId(s);

  String url = String("https://firestore.googleapis.com/v1/projects/") + FIREBASE_PROJECT_ID +
               "/databases/(default)/documents/alerts?documentId=" + alertIdOut;

  JsonDocument doc;
  JsonObject f = doc["fields"].to<JsonObject>();
  f["intensity"]["stringValue"]     = intensity;
  f["magnitudeG"]["doubleValue"]    = magnitudeG;
  f["detectedAt"]["timestampValue"] = isoNowUtc();
  f["source"]["stringValue"]        = "esp32";
  f["nodeId"]["stringValue"]        = NODE_ID;
  f["closed"]["booleanValue"]       = false;

  String body;
  serializeJson(doc, body);

  String resp;
  int code = postJson(url, body, resp);

  if (code == 401 || code == 403) {
    Serial.println("FS: token rejected, refreshing and retrying once");
    if (refreshIdToken()) code = postJson(url, body, resp);
  }

  if (code == 200) return true;

  Serial.printf("FS: create failed http=%d %s\n", code, resp.c_str());
  return false;
}

void uploadAlert(const char* band, float g) {
  uint32_t t0 = millis();

  if (WiFi.status() != WL_CONNECTED) {
    Serial.printf("CLOUD,ERR,offline,%lu\n", (unsigned long)(millis() - t0));
    return;
  }
  keepAuthFresh();
  if (!authed) {
    Serial.printf("CLOUD,ERR,auth,%lu\n", (unsigned long)(millis() - t0));
    return;
  }

  String alertId;
  if (createAlert(band, g, seq, alertId)) {
    Serial.printf("CLOUD,OK,%s,%lu\n", alertId.c_str(), (unsigned long)(millis() - t0));
  } else {
    Serial.printf("CLOUD,ERR,write,%lu\n", (unsigned long)(millis() - t0));
  }
}

// ---------------------------------------------------------------------------
// Alerting
// ---------------------------------------------------------------------------
void fireAlert(float g) {
  tAlert = millis();
  const char* band = bandName(g);

  bool isRed    = (g >= BAND_RED_G);
  bool isYellow = (!isRed && g >= BAND_YELLOW_G);
  setLeds(!isRed && !isYellow, isYellow, isRed);
  tLed = millis();

  char line2[20];
  // 3 decimals, not 2: the Green band is only 0.010 g wide, so "%.2f" would
  // round a 0.005 g reading to "0.01 g" -- exactly the Yellow boundary.
  snprintf(line2, sizeof(line2), "%.3f g  %s", g, band);
  if (isRed)         lcdTwoLines("RED - TAKE COVER", line2);
  else if (isYellow) lcdTwoLines("YELLOW - ALERT",   line2);
  else               lcdTwoLines("GREEN - MINOR",    line2);
  tLcd = millis();

  // Both Yellow and Red sound the alarm. Green is informational only, so it
  // stays silent -- otherwise the buzzer would fire on every passing footstep.
  if (isRed || isYellow) buzzerOn();

  seq++;

  Serial.printf("TRIAL,%u,%s,%.3f,%s,%lu,%lu,%lu,%lu,%lu\n",
                seq, lastType, g, band,
                (unsigned long)(tDetect - tOnset),
                (unsigned long)(tAlert - tDetect),
                (unsigned long)(tLed - tAlert),
                (unsigned long)(tLcd - tAlert),
                (unsigned long)(tLcd - tOnset));

  state = ALERTING;
  tStateEnd = millis() + ALERT_HOLD_MS;

  uploadAlert(band, g);

  // The upload blocks for a couple of seconds. Restart the sample clock so
  // the loop does not race through a backlog of missed samples afterwards.
  nextSampleUs = micros();
}

void rejectAsNoise(float g) {
  Serial.printf("REJECT,%s,%.3f,%u,below confirmation window\n", lastType, g, samplesAbove);
  state = IDLE;
}

void endAlert() {
  buzzerOff();
  setLeds(true, false, false);
  lcdTwoLines("S.I.R.E.N. ready", "Monitoring...");
  state = COOLDOWN;
  tStateEnd = millis() + COOLDOWN_MS;
}

// ---------------------------------------------------------------------------
// Serial console
// ---------------------------------------------------------------------------
void printLive() {
  int16_t rx, ry, rz;
  if (!readAccelRaw(rx, ry, rz)) {
    Serial.println("LIVE,ERR,could not read the MPU6050");
    return;
  }
  float gx = rx / LSB_PER_G, gy = ry / LSB_PER_G, gz = rz / LSB_PER_G;
  // At rest: one axis near +/-1.000 (the one facing up or down), the other
  // two near 0.000, total near 1.000, and shake_g close to zero.
  Serial.printf("LIVE,g,%.3f,%.3f,%.3f,total,%.3f,shake_g,%.4f\n",
                gx, gy, gz, sqrtf(gx * gx + gy * gy + gz * gz), readResultantG());
}

void scanI2C() {
  Serial.println("I2C,scan,start");
  uint8_t found = 0;
  for (uint8_t a = 1; a < 127; a++) {
    if (i2cPresent(a)) {
      const char* what = (a == 0x27 || a == 0x3F) ? "LCD"
                       : (a == 0x68 || a == 0x69) ? "MPU6050" : "?";
      Serial.printf("I2C,found,0x%02X,%s\n", a, what);
      found++;
    }
  }
  Serial.printf("I2C,scan,done,%u device(s)\n", found);
}

void handleCommand(char c) {
  switch (c) {
    case 'C': case 'c': calibrate(); break;
    case 'Z': case 'z': printCalibration(); break;
    case 'S': case 's': endAlert(); break;
    case 'L': case 'l': printLive(); break;
    case 'I': case 'i': scanI2C(); break;
    case 'W': case 'w':
      Serial.printf("NET,wifi=%d,ip=%s,authed=%d,epoch=%lu\n",
                    WiFi.status() == WL_CONNECTED ? 1 : 0,
                    WiFi.localIP().toString().c_str(),
                    authed ? 1 : 0, (unsigned long)time(nullptr));
      break;
    case 'G': case 'g': strcpy(lastType, "manual"); tOnset = tDetect = millis(); fireAlert(0.005f); break;
    case 'Y': case 'y': strcpy(lastType, "manual"); tOnset = tDetect = millis(); fireAlert(0.050f); break;
    case 'R': case 'r': strcpy(lastType, "manual"); tOnset = tDetect = millis(); fireAlert(0.300f); break;
    default: break;
  }
}

void pumpConsole() {
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\r' || c == '\n') continue;
    handleCommand(c);
  }
}

// ---------------------------------------------------------------------------
// Setup and main loop
// ---------------------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  delay(300);

  pinMode(PIN_LED_GREEN,  OUTPUT);
  pinMode(PIN_LED_YELLOW, OUTPUT);
  pinMode(PIN_LED_RED,    OUTPUT);
  pinMode(PIN_BUZZER,     OUTPUT);
  pinMode(PIN_BUZZER2,    OUTPUT);
  buzzerOff();

  Wire.begin(PIN_SDA, PIN_SCL);
  lcd.init();
  lcd.backlight();
  lcdTwoLines("S.I.R.E.N.", "Booting...");

  setLeds(true, true, true); delay(400); setLeds(false, false, false);

  Serial.println("\nBOOT,siren-esp32,v3.0-mpu6050");
  Serial.printf("node=%s project=%s\n", NODE_ID, FIREBASE_PROJECT_ID);
  Serial.println("HEADER,seq,type,peak_g,intensity,detect_ms,process_ms,led_ms,lcd_ms,total_ms");

  mpuInit();

  connectWifi();
  syncClock();
  signIn();

  delay(1500);
  calibrate();
}

void loop() {
  pumpConsole();

  static uint32_t lastCheck = 0;
  if (millis() - lastCheck > 30000) {
    lastCheck = millis();
    if (WiFi.status() != WL_CONNECTED) connectWifi();
    else keepAuthFresh();
  }

  if ((int32_t)(micros() - nextSampleUs) < 0) return;
  nextSampleUs += SAMPLE_US;

  float g = readResultantG();
  uint32_t now = millis();

  switch (state) {

    case IDLE:
      if (g >= onsetG && tOnset == 0) tOnset = now;
      if (g < onsetG) tOnset = 0;
      if (g >= triggerG) {
        if (tOnset == 0) tOnset = now;
        tDetect      = now;
        peakG        = g;
        samplesAbove = 1;
        sumG         = g;
        countG       = 1;
        strcpy(lastType, "shake");
        state = CONFIRMING;
      }
      break;

    case CONFIRMING:
      if (g > peakG) peakG = g;
      sumG += g;
      countG++;
      if (g >= triggerG && samplesAbove < 255) samplesAbove++;
      if (now - tDetect >= CONFIRM_MS) {
        if (samplesAbove >= MIN_SAMPLES_ABOVE) fireAlert(sumG / countG);
        else { rejectAsNoise(peakG); tOnset = 0; }
      }
      break;

    case ALERTING:
      if (g > peakG) peakG = g;
      if ((int32_t)(now - tStateEnd) >= 0) endAlert();
      break;

    case COOLDOWN:
      if ((int32_t)(now - tStateEnd) >= 0) {
        state  = IDLE;
        tOnset = 0;
        peakG  = 0;
        sumG   = 0;
        countG = 0;
        nextSampleUs = micros();
      }
      break;
  }
}
