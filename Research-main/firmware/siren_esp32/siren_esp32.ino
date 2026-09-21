#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <time.h>

#include "secrets.h"

// ---------------------------------------------------------------------------
// SIM800L — optional GSM fallback for when WiFi is down.
//
// These defaults keep the modem OFF, so a secrets.h written before the modem
// existed still compiles and behaves exactly as it did. Switch it on by adding
// SIM800L_ENABLED 1 and SIM_RECIPIENTS to secrets.h — see secrets.h.example.
//
// The modem sends SMS only. It deliberately does NOT carry the Firestore write:
// SIM800L is 2G with an onboard TLS stack that tops out around TLS 1.0, and
// Google's REST endpoints require TLS 1.2+, so the handshake fails before any
// request is made. SMS is the path that actually survives a WiFi outage.
// ---------------------------------------------------------------------------
#ifndef SIM800L_ENABLED
  #define SIM800L_ENABLED 0
#endif
#ifndef SIM_RECIPIENTS
  #define SIM_RECIPIENTS ""
#endif
#ifndef SIM_MIN_BAND
  #define SIM_MIN_BAND "yellow"
#endif
#ifndef SIM_SMS_COOLDOWN_MS
  #define SIM_SMS_COOLDOWN_MS 120000UL
#endif

const int PIN_X = 34;
const int PIN_Y = 35;
const int PIN_Z = 32;

const int PIN_LED_GREEN  = 25;
const int PIN_LED_YELLOW = 26;
const int PIN_LED_RED    = 27;
const int PIN_BUZZER     = 14;

const int PIN_SDA = 21;
const int PIN_SCL = 22;

// UART2. Wire ESP32 RX to the modem's TXD and ESP32 TX to the modem's RXD —
// crossed, not straight through. The ESP32 TX line needs a divider down to
// ~2.8 V; the modem's 3.3 V-tolerant input is the one thing here that is not.
const int PIN_SIM_RX = 16;   // ESP32 RX2  <-- SIM800L TXD
const int PIN_SIM_TX = 17;   // ESP32 TX2  --> SIM800L RXD (through divider)

LiquidCrystal_I2C lcd(0x27, 16, 2);

#if SIM800L_ENABLED
HardwareSerial simSerial(2);
#endif

#define BUZZER_IS_ACTIVE 1

const float MV_PER_G = 330.0f;

const uint16_t CAL_SAMPLES     = 1000;
const uint8_t  CAL_INTERVAL_MS = 10;
const uint8_t  MA_WINDOW       = 8;
const uint32_t SAMPLE_US       = 5000;

const float    SIGMA_MULTIPLIER  = 3.0f;
const float    MIN_TRIGGER_G     = 0.08f;
const float    ONSET_FRACTION    = 0.50f;
const uint16_t CONFIRM_MS        = 300;
const uint8_t  MIN_SAMPLES_ABOVE = 8;
const uint16_t ALERT_HOLD_MS     = 15000;
const uint16_t COOLDOWN_MS       = 30000;

const float BAND_YELLOW_G = 0.010f;
const float BAND_RED_G    = 0.120f;

float biasX = 1650, biasY = 1650, biasZ = 1650;
float sigmaResultantMv = 1.0f;
float triggerG = MIN_TRIGGER_G;
float onsetG   = MIN_TRIGGER_G * ONSET_FRACTION;

uint16_t bufX[MA_WINDOW], bufY[MA_WINDOW], bufZ[MA_WINDOW];
uint8_t  bufIdx = 0;
uint32_t sumX = 0, sumY = 0, sumZ = 0;
bool     bufPrimed = false;

enum State { IDLE, CONFIRMING, ALERTING, COOLDOWN };
State state = IDLE;

uint32_t tOnset = 0, tDetect = 0, tAlert = 0, tLed = 0, tLcd = 0, tStateEnd = 0;
float    peakG = 0;
uint8_t  samplesAbove = 0;
uint16_t seq = 0;
char     lastType[10] = "shake";

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
  digitalWrite(PIN_BUZZER, HIGH);
#else
  #if ESP_ARDUINO_VERSION_MAJOR >= 3
    tone(PIN_BUZZER, 2500);
  #else
    ledcSetup(0, 2500, 8);
    ledcAttachPin(PIN_BUZZER, 0);
    ledcWrite(0, 128);
  #endif
#endif
}

void buzzerOff() {
#if BUZZER_IS_ACTIVE
  digitalWrite(PIN_BUZZER, LOW);
#else
  #if ESP_ARDUINO_VERSION_MAJOR >= 3
    noTone(PIN_BUZZER);
  #else
    ledcWrite(0, 0);
  #endif
#endif
}

float readResultantG() {
  uint16_t rx = analogReadMilliVolts(PIN_X);
  uint16_t ry = analogReadMilliVolts(PIN_Y);
  uint16_t rz = analogReadMilliVolts(PIN_Z);

  if (!bufPrimed) {
    for (uint8_t i = 0; i < MA_WINDOW; i++) { bufX[i] = rx; bufY[i] = ry; bufZ[i] = rz; }
    sumX = (uint32_t)rx * MA_WINDOW;
    sumY = (uint32_t)ry * MA_WINDOW;
    sumZ = (uint32_t)rz * MA_WINDOW;
    bufPrimed = true;
  }

  sumX += rx - bufX[bufIdx]; bufX[bufIdx] = rx;
  sumY += ry - bufY[bufIdx]; bufY[bufIdx] = ry;
  sumZ += rz - bufZ[bufIdx]; bufZ[bufIdx] = rz;
  bufIdx = (bufIdx + 1) % MA_WINDOW;

  float dx = (sumX / (float)MA_WINDOW) - biasX;
  float dy = (sumY / (float)MA_WINDOW) - biasY;
  float dz = (sumZ / (float)MA_WINDOW) - biasZ;

  return sqrtf(dx * dx + dy * dy + dz * dz) / MV_PER_G;
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

void printCalibration() {
  Serial.println("CAL,done");
  Serial.printf("CAL,bias_mv,%.2f,%.2f,%.2f\n", biasX, biasY, biasZ);
  Serial.printf("CAL,sigma_mv,%.4f\n", sigmaResultantMv);
  Serial.printf("CAL,sigma_g,%.5f\n", sigmaResultantMv / MV_PER_G);
  Serial.printf("CAL,trigger_g,%.4f\n", triggerG);
  Serial.printf("CAL,onset_g,%.4f\n", onsetG);
  Serial.printf("CAL,mv_per_g,%.2f\n", MV_PER_G);
}

void calibrate() {
  state = IDLE;
  setLeds(false, false, false);
  buzzerOff();

  lcdTwoLines("Calibrating...", "Keep it still");
  Serial.println("CAL,start,keep the sensor still");

  float refX = analogReadMilliVolts(PIN_X);
  float refY = analogReadMilliVolts(PIN_Y);
  float refZ = analogReadMilliVolts(PIN_Z);

  double sX = 0, sY = 0, sZ = 0;
  double qX = 0, qY = 0, qZ = 0;

  for (uint16_t i = 0; i < CAL_SAMPLES; i++) {
    double dx = analogReadMilliVolts(PIN_X) - refX;
    double dy = analogReadMilliVolts(PIN_Y) - refY;
    double dz = analogReadMilliVolts(PIN_Z) - refZ;
    sX += dx; qX += dx * dx;
    sY += dy; qY += dy * dy;
    sZ += dz; qZ += dz * dz;

    if (i % 100 == 0) { lcd.setCursor(14, 0); lcd.print((int)(i / 100)); }
    delay(CAL_INTERVAL_MS);
  }

  const double n = CAL_SAMPLES;
  biasX = refX + sX / n;
  biasY = refY + sY / n;
  biasZ = refZ + sZ / n;

  double varX = (qX - (sX * sX) / n) / (n - 1);
  double varY = (qY - (sY * sY) / n) / (n - 1);
  double varZ = (qZ - (sZ * sZ) / n) / (n - 1);
  if (varX < 0) varX = 0;
  if (varY < 0) varY = 0;
  if (varZ < 0) varZ = 0;

  sigmaResultantMv = sqrt(varX + varY + varZ);

  float thresholdG = (SIGMA_MULTIPLIER * sigmaResultantMv) / MV_PER_G;
  triggerG = max(thresholdG, MIN_TRIGGER_G);
  onsetG   = triggerG * ONSET_FRACTION;

  bufPrimed = false;
  printCalibration();

  lcdTwoLines("SIREN ready", "Monitoring...");
  setLeds(true, false, false);
}

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
// SIM800L driver
//
// Sending an SMS takes seconds and can take half a minute on a weak cell, so
// the dispatch is a state machine pumped from loop() rather than a blocking
// call. Blocking here would freeze the 200 Hz sampler during the exact window
// an aftershock is most likely, and would overrun ALERT_HOLD_MS.
// ---------------------------------------------------------------------------
#if SIM800L_ENABLED

const uint8_t  SIM_MAX_RECIPIENTS    = 5;
const uint32_t SIM_PROMPT_TIMEOUT_MS = 8000;
const uint32_t SIM_SEND_TIMEOUT_MS   = 30000;

String   simNumbers[SIM_MAX_RECIPIENTS];
uint8_t  simNumberCount = 0;
bool     simPresent     = false;
uint32_t simLastSmsAtMs = 0;
bool     simEverSent    = false;

enum SmsState { SMS_IDLE, SMS_WAIT_PROMPT, SMS_WAIT_ACK };
SmsState smsState    = SMS_IDLE;
uint8_t  smsNext     = 0;   // recipient currently being sent
uint8_t  smsQueued   = 0;   // size of this batch; smsNext < smsQueued == work pending
String   smsBody;
uint32_t smsDeadline = 0;
String   simRx;             // rolling buffer for the non-blocking path

void simResetRx() {
  while (simSerial.available()) simSerial.read();
  simRx = "";
}

void simDrain() {
  while (simSerial.available()) {
    simRx += (char)simSerial.read();
    if (simRx.length() > 300) simRx.remove(0, simRx.length() - 300);
  }
}

// Blocking — only ever used at boot and from the serial console, never while
// an alert is being handled.
bool simWaitFor(const char* token, uint32_t timeoutMs, String* captured = nullptr) {
  uint32_t start = millis();
  String buf;
  while (millis() - start < timeoutMs) {
    while (simSerial.available()) {
      buf += (char)simSerial.read();
      if (buf.length() > 300) buf.remove(0, buf.length() - 300);
      if (buf.indexOf(token) >= 0)   { if (captured) *captured = buf; return true;  }
      if (buf.indexOf("ERROR") >= 0) { if (captured) *captured = buf; return false; }
    }
    delay(2);
  }
  if (captured) *captured = buf;
  return false;
}

bool simCmd(const char* cmd, const char* expect, uint32_t timeoutMs, String* captured = nullptr) {
  simResetRx();
  simSerial.println(cmd);
  return simWaitFor(expect, timeoutMs, captured);
}

void simParseRecipients() {
  simNumberCount = 0;
  String all = String(SIM_RECIPIENTS);
  all.trim();
  int from = 0;
  while (from <= (int)all.length() && simNumberCount < SIM_MAX_RECIPIENTS) {
    int comma = all.indexOf(',', from);
    String one = (comma < 0) ? all.substring(from) : all.substring(from, comma);
    one.trim();
    if (one.length()) simNumbers[simNumberCount++] = one;
    if (comma < 0) break;
    from = comma + 1;
  }
}

int simSignal() {                 // 0-31, or 99 when unknown
  String resp;
  if (!simCmd("AT+CSQ", "+CSQ:", 3000, &resp)) return 99;
  int i = resp.indexOf("+CSQ:");
  if (i < 0) return 99;
  int c = resp.indexOf(',', i);
  if (c < 0) return 99;
  return resp.substring(i + 5, c).toInt();
}

int simRegistration() {           // 1 = registered home, 5 = roaming
  String resp;
  if (!simCmd("AT+CREG?", "+CREG:", 3000, &resp)) return -1;
  int i = resp.indexOf("+CREG:");
  if (i < 0) return -1;
  int c = resp.indexOf(',', i);
  if (c < 0) return -1;
  return resp.substring(c + 1, c + 2).toInt();
}

bool simInit() {
  simParseRecipients();
  simSerial.begin(9600, SERIAL_8N1, PIN_SIM_RX, PIN_SIM_TX);
  delay(500);

  simPresent = false;
  for (uint8_t i = 0; i < 6 && !simPresent; i++) {
    if (simCmd("AT", "OK", 1200)) simPresent = true;
    else delay(800);
  }

  if (!simPresent) {
    Serial.println("SIM,ERR,no reply to AT");
    Serial.println("SIM,HINT,supply must hold 3.4-4.4V at 2A peak; check TX/RX are crossed; baud 9600");
    lcdTwoLines("SIM800L no reply", "check power/wire");
    delay(1500);
    return false;
  }

  simCmd("ATE0", "OK", 1500);                  // echo off, or every reply is doubled
  simCmd("AT+CMEE=2", "OK", 1500);             // verbose errors
  simCmd("AT+CNMI=0,0,0,0,0", "OK", 1500);     // do not push incoming SMS at us mid-alert
  simCmd("AT+CSCS=\"GSM\"", "OK", 1500);

  if (!simCmd("AT+CMGF=1", "OK", 3000)) {
    Serial.println("SIM,ERR,SMS text mode refused -- SIM may be missing or PIN-locked");
    return false;
  }

  int reg = -1, csq = 99;
  uint32_t start = millis();
  while (millis() - start < 20000) {
    reg = simRegistration();
    csq = simSignal();
    if ((reg == 1 || reg == 5) && csq != 99 && csq >= 5) break;
    delay(1500);
  }

  Serial.printf("SIM,READY,reg=%d,csq=%d,recipients=%u\n", reg, csq, simNumberCount);
  if (reg != 1 && reg != 5)
    Serial.println("SIM,WARN,not registered -- SIM seated? PIN disabled? 2G coverage?");
  if (csq == 99 || csq < 8)
    Serial.println("SIM,WARN,weak or unknown signal -- is the antenna attached?");
  if (simNumberCount == 0)
    Serial.println("SIM,WARN,SIM_RECIPIENTS is empty -- no SMS will ever be sent");
  return true;
}

uint8_t bandRank(const char* b) {
  if (!strcmp(b, "red"))    return 2;
  if (!strcmp(b, "yellow")) return 1;
  return 0;
}

void smsQueueAlert(const char* band, float g) {
  if (!simPresent || simNumberCount == 0) return;
  if (bandRank(band) < bandRank(SIM_MIN_BAND)) return;

  if (smsNext < smsQueued) {
    Serial.println("SIM,SKIP,previous batch still sending");
    return;
  }
  if (simEverSent && (millis() - simLastSmsAtMs) < SIM_SMS_COOLDOWN_MS) {
    Serial.printf("SIM,SKIP,cooldown,%lus left\n",
                  (unsigned long)((SIM_SMS_COOLDOWN_MS - (millis() - simLastSmsAtMs)) / 1000));
    return;
  }

  char up[8];
  strncpy(up, band, sizeof(up) - 1);
  up[sizeof(up) - 1] = '\0';
  for (char* p = up; *p; ++p) *p = toupper((unsigned char)*p);

  smsBody  = "SIREN ";
  smsBody += NODE_ID;
  smsBody += " ";
  smsBody += up;
  smsBody += ": peak ";
  smsBody += String(g, 3);
  smsBody += "g at ";
  smsBody += isoNowUtc();
  smsBody += ". Quake detected by the school sensor. Auto-message, do not reply.";

  smsNext        = 0;
  smsQueued      = simNumberCount;
  smsState       = SMS_IDLE;
  simLastSmsAtMs = millis();
  simEverSent    = true;

  Serial.printf("SIM,QUEUE,%u recipients,band=%s\n", smsQueued, band);
}

// "Sent" here means the network accepted the message, exactly as it does in the
// app. There are no delivery receipts; do not report it as "delivered".
void pumpSms() {
  if (!simPresent) return;

  switch (smsState) {

    case SMS_IDLE:
      if (smsNext >= smsQueued) return;
      simResetRx();
      simSerial.print("AT+CMGS=\"");
      simSerial.print(simNumbers[smsNext]);
      simSerial.println("\"");
      smsDeadline = millis() + SIM_PROMPT_TIMEOUT_MS;
      smsState    = SMS_WAIT_PROMPT;
      break;

    case SMS_WAIT_PROMPT:
      simDrain();
      if (simRx.indexOf('>') >= 0) {
        simSerial.print(smsBody);
        simSerial.write(26);                  // Ctrl-Z sends it
        simRx.remove(0);
        smsDeadline = millis() + SIM_SEND_TIMEOUT_MS;
        smsState    = SMS_WAIT_ACK;
      } else if (simRx.indexOf("ERROR") >= 0 || (int32_t)(millis() - smsDeadline) >= 0) {
        Serial.printf("SIM,FAIL,%s,no prompt\n", simNumbers[smsNext].c_str());
        simSerial.write(27);                  // ESC abandons a half-open CMGS
        smsNext++;
        smsState = SMS_IDLE;
      }
      break;

    case SMS_WAIT_ACK:
      simDrain();
      if (simRx.indexOf("+CMGS:") >= 0) {
        Serial.printf("SIM,SENT,%s\n", simNumbers[smsNext].c_str());
        smsNext++;
        smsState = SMS_IDLE;
      } else if (simRx.indexOf("ERROR") >= 0) {
        Serial.printf("SIM,FAIL,%s,modem error\n", simNumbers[smsNext].c_str());
        smsNext++;
        smsState = SMS_IDLE;
      } else if ((int32_t)(millis() - smsDeadline) >= 0) {
        Serial.printf("SIM,FAIL,%s,timeout\n", simNumbers[smsNext].c_str());
        smsNext++;
        smsState = SMS_IDLE;
      }
      break;
  }
}

void simStatusLine() {
  if (!simPresent) { Serial.println("SIM,STAT,present=0"); return; }
  Serial.printf("SIM,STAT,present=1,reg=%d,csq=%d,recipients=%u,sending=%d\n",
                simRegistration(), simSignal(), simNumberCount,
                smsNext < smsQueued ? 1 : 0);
}

void simTestSms() {
  if (!simPresent)            { Serial.println("SIM,ERR,modem not present");     return; }
  if (simNumberCount == 0)    { Serial.println("SIM,ERR,no recipients");         return; }
  if (smsNext < smsQueued)    { Serial.println("SIM,ERR,batch already sending"); return; }
  smsBody   = "SIREN " NODE_ID " test. If you received this, the SMS fallback works. Do not reply.";
  smsNext   = 0;
  smsQueued = simNumberCount;
  smsState  = SMS_IDLE;
  Serial.printf("SIM,TEST,queued for %u recipients\n", smsQueued);
}

#else   // SIM800L disabled — no-op stubs so the call sites stay clean

inline void pumpSms() {}
inline void smsQueueAlert(const char*, float) {}
inline void simStatusLine() { Serial.println("SIM,STAT,disabled"); }
inline void simTestSms()    { Serial.println("SIM,ERR,set SIM800L_ENABLED 1 in secrets.h"); }

#endif  // SIM800L_ENABLED

void fireAlert(float g) {
  tAlert = millis();
  const char* band = bandName(g);

  bool isRed    = (g >= BAND_RED_G);
  bool isYellow = (!isRed && g >= BAND_YELLOW_G);
  setLeds(!isRed && !isYellow, isYellow, isRed);
  tLed = millis();

  char line2[20];

  snprintf(line2, sizeof(line2), "%.3f g  %s", g, band);
  if (isRed)         lcdTwoLines("RED - TAKE COVER", line2);
  else if (isYellow) lcdTwoLines("YELLOW - ALERT",   line2);
  else               lcdTwoLines("GREEN - MINOR",    line2);
  tLcd = millis();

  if (isRed) buzzerOn();

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

  // Firestore first — it is the fast path when WiFi is up, and it returns
  // immediately when it is not, so queuing the SMS behind it costs nothing in
  // the outage case that the SMS exists for.
  uploadAlert(band, g);
  smsQueueAlert(band, g);
}

void rejectAsNoise(float g) {
  Serial.printf("REJECT,%s,%.3f,%u,below confirmation window\n", lastType, g, samplesAbove);
  state = IDLE;
}

void endAlert() {
  buzzerOff();
  setLeds(true, false, false);
  lcdTwoLines("SIREN ready", "Monitoring...");
  state = COOLDOWN;
  tStateEnd = millis() + COOLDOWN_MS;
}

void handleCommand(char c) {
  switch (c) {
    case 'C': case 'c': calibrate(); break;
    case 'Z': case 'z': printCalibration(); break;
    case 'S': case 's': endAlert(); break;
    case 'W': case 'w':
      Serial.printf("NET,wifi=%d,ip=%s,authed=%d,epoch=%lu\n",
                    WiFi.status() == WL_CONNECTED ? 1 : 0,
                    WiFi.localIP().toString().c_str(),
                    authed ? 1 : 0, (unsigned long)time(nullptr));
      break;
    case 'M': case 'm': simStatusLine(); break;
    case 'T': case 't': simTestSms(); break;
    case 'G': case 'g': strcpy(lastType, "manual"); tOnset = tDetect = millis(); fireAlert(0.22f); break;
    case 'Y': case 'y': strcpy(lastType, "manual"); tOnset = tDetect = millis(); fireAlert(0.48f); break;
    case 'R': case 'r': strcpy(lastType, "manual"); tOnset = tDetect = millis(); fireAlert(0.85f); break;
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

void setup() {
  Serial.begin(115200);
  delay(300);

  pinMode(PIN_LED_GREEN,  OUTPUT);
  pinMode(PIN_LED_YELLOW, OUTPUT);
  pinMode(PIN_LED_RED,    OUTPUT);
  pinMode(PIN_BUZZER,     OUTPUT);
  buzzerOff();

  analogReadResolution(12);
  // ADC_11db is the correct symbol on both core 2.x and 3.x — the Arduino layer
  // exports {ADC_0db, ADC_2_5db, ADC_6db, ADC_11db, ADC_ATTENDB_MAX} and nothing
  // else. ADC_ATTEN_DB_12 is an ESP-IDF name, so the version-gated branch that
  // used it failed to compile on exactly the 3.x cores it was meant for.
  analogSetAttenuation(ADC_11db);

  Wire.begin(PIN_SDA, PIN_SCL);
  lcd.init();
  lcd.backlight();
  lcdTwoLines("SIREN", "Booting...");

  setLeds(true, true, true); delay(400); setLeds(false, false, false);

  Serial.println("\nBOOT,siren-esp32,v2.0-singleboard");
  Serial.printf("node=%s project=%s\n", NODE_ID, FIREBASE_PROJECT_ID);
  Serial.println("HEADER,seq,type,peak_g,intensity,detect_ms,process_ms,led_ms,lcd_ms,total_ms");

  connectWifi();
  syncClock();
  signIn();

#if SIM800L_ENABLED
  lcdTwoLines("SIREN", "GSM starting...");
  simInit();
#else
  Serial.println("SIM,disabled,SIM800L_ENABLED=0");
#endif

  delay(1500);
  calibrate();
  nextSampleUs = micros();
}

void loop() {
  pumpConsole();
  pumpSms();

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
        strcpy(lastType, "shake");
        state = CONFIRMING;
      }
      break;

    case CONFIRMING:
      if (g > peakG) peakG = g;
      if (g >= triggerG && samplesAbove < 255) samplesAbove++;
      if (now - tDetect >= CONFIRM_MS) {
        if (samplesAbove >= MIN_SAMPLES_ABOVE) fireAlert(peakG);
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
        nextSampleUs = micros();
      }
      break;
  }
}
