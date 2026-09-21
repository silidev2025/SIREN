# SIREN firmware — ESP32 + ADXL335 (+ optional SIM800L)

The sketch in `siren_esp32/` reads the accelerometer, decides the intensity band,
drives the LEDs/buzzer/LCD, and writes an `alerts` document to Firestore that the
mobile app listens for.

`SIM800L_ENABLED` adds a GSM fallback on top of that. It is **off by default** —
a `secrets.h` written before the modem existed still compiles and behaves
identically.

---

## What the SIM800L does, and what it does not

**It sends SMS. It does not carry the Firestore write.**

That is a deliberate limit, not an omission. SIM800L is a 2G module whose onboard
TLS stack tops out around TLS 1.0, while `firestore.googleapis.com` and
`identitytoolkit.googleapis.com` require TLS 1.2 or better. The handshake fails
before a request is ever sent, so routing the alert upload over GPRS does not
work regardless of how the code is written. (A few late SIM800 firmware revisions
advertise TLS 1.2; none of that is dependable enough to put an earthquake warning
behind.) If you need the cloud write to survive a WiFi outage, the module for
that job is a SIM7600 or an ESP32 with a wired uplink — not a SIM800L.

So the fallback ladder is:

| WiFi | What happens |
|---|---|
| Up | Firestore write → app shows the alert. SMS also goes if the band qualifies. |
| Down | Firestore write returns `CLOUD,ERR,offline` immediately. **SMS is the only thing that leaves the device.** |
| No modem fitted | Local siren, LEDs and LCD only. Nothing leaves the device. |

**Worth knowing before you rely on this.** Nothing in this repository sends an
FCM push when an `alerts` document is created — there is no Cloud Function here,
and `CLAUDE.md`'s test step 4 pushes to the `alerts` topic *by hand* from the
Firebase console. The app therefore learns about an ESP32 detection through its
Firestore listener, which needs the app to be running. On a phone where the app
has been swiped away, **the SMS is currently the only thing that arrives at all**,
WiFi up or down. That makes the recipient list worth choosing carefully.

---

## Bill of materials

| Part | Notes |
|---|---|
| SIM800L module | The bare red breakout, or one with an onboard regulator |
| Antenna | Required. The spring/helical one that ships with it, or an IPEX one |
| **Separate 3.4–4.4 V supply** | See below. This is not optional |
| 1000–2200 µF electrolytic | Across the module's VCC/GND, physically close to it |
| 1 kΩ and 5.6 kΩ resistors | Divider on the ESP32 → modem data line |
| 2G-capable SIM | With credit, and with the PIN lock switched off |

---

## Power — read this before wiring anything

**Almost every "SIM800L doesn't respond" report is a power problem.** The module
transmits in bursts that draw up to **2 A** for a few hundred microseconds. The
average is small, but the peak is not, and it happens exactly when the module
registers on the network — which is why the usual symptom is "it answers `AT`
fine, then resets the moment it tries to connect."

- **Do not power it from the ESP32's 3.3 V pin.** That regulator cannot source
  anywhere near 2 A, and you will brown out the ESP32 along with the modem.
- **Do not feed it 5 V.** Absolute maximum is about 4.4 V. 5 V damages it.
- **Do** use either a buck converter (LM2596 or similar) set to **4.0 V** from a
  5 V / 2 A supply, or a 3.7 V Li-ion cell.
- **Do** put a large electrolytic (1000 µF minimum) directly across the module's
  VCC and GND. This supplies the burst that the wires cannot.
- **Do** tie the modem's ground to the ESP32's ground. Separate supplies with no
  common ground means no serial link.

---

## Wiring

```
SIM800L            ESP32
--------           -----------------------------
VCC     <--------- 4.0 V from its own supply (NOT the ESP32 3.3 V pin)
GND     <--------- GND  (must be common with the ESP32)
TXD     ---------> GPIO16   (ESP32 RX2) — direct is fine
RXD     <--[div]-- GPIO17   (ESP32 TX2) — through the divider below
```

The divider on GPIO17, because the modem's inputs are 2.8 V logic and the ESP32
drives 3.3 V:

```
GPIO17 ---[ 1 kΩ ]---+--- SIM800L RXD
                     |
                  [ 5.6 kΩ ]
                     |
                    GND
```

That gives about 2.8 V. The other direction needs nothing: the modem's 2.8 V high
comfortably clears the ESP32's ~2.48 V input threshold.

Pins already in use by the rest of the board — do not reuse these: 34/35/32
(ADXL335), 25/26/27 (LEDs), 14 (buzzer), 21/22 (I²C LCD).

> **ESP32-WROVER only:** GPIO16 and GPIO17 are wired to the PSRAM on WROVER
> modules and cannot be used for UART. Move to two free pins (18 and 19 are
> unused here) and change `PIN_SIM_RX` / `PIN_SIM_TX` at the top of the sketch.
> Plain ESP32-WROOM boards, which is what this project uses, are fine as-is.

---

## SIM card

1. **2G must still exist on that network.** Many countries have shut 2G down; in
   the Philippines Globe and Smart still run it, which is why this module is a
   reasonable choice here and would not be in, say, Singapore.
2. **Turn the PIN lock off.** Put the SIM in a phone, disable the SIM PIN, then
   move it over. A PIN-locked SIM registers with no error the firmware can see —
   `AT+CMGF=1` just fails and no SMS ever sends.
3. **Load it.** Each text costs. `SIM_MIN_BAND "green"` on a twitchy sensor will
   drain a prepaid SIM quickly, which is why the default is `"yellow"` and there
   is a two-minute cooldown.
4. Insert it with the board **unpowered**, contacts down, notch matching the
   holder.

---

## Configuring `secrets.h`

Copy `siren_esp32/secrets.h.example` to `siren_esp32/secrets.h` (it is gitignored)
and set:

```c
#define SIM800L_ENABLED  1
#define SIM_RECIPIENTS   "+639171234567,+639281234567"
#define SIM_MIN_BAND     "yellow"
#define SIM_SMS_COOLDOWN_MS  120000UL
```

- **Numbers must be E.164** — leading `+`, country code, no spaces or dashes.
  `09171234567` will not send.
- Maximum five recipients. These should be the people who must know when the
  internet is down — the adviser, the school's emergency contact — not the class.
- `SIM_MIN_BAND` is the *lowest* band that texts. `"yellow"` covers Yellow and Red.

---

## Building and flashing

JDK is irrelevant here; this needs the Arduino ESP32 core.

```bash
arduino-cli core install esp32:esp32
arduino-cli lib install "ArduinoJson"
arduino-cli lib install "LiquidCrystal I2C"

arduino-cli compile --fqbn esp32:esp32:esp32 firmware/siren_esp32
arduino-cli upload  --fqbn esp32:esp32:esp32 -p COM5 firmware/siren_esp32
```

---

## Testing it, in this order

Open the serial monitor at **115200**. Single-letter commands, no Enter needed:

| Key | Does |
|---|---|
| `M` | Modem status — presence, registration, signal, recipient count |
| `T` | Send a test SMS to every configured recipient |
| `W` | WiFi / auth / clock status |
| `C` | Recalibrate (keep the sensor still) |
| `G` `Y` `R` | Fire a fake Green / Yellow / Red alert |
| `S` | Stop the current alert |

1. **Boot.** Watch for `SIM,READY,reg=…,csq=…`. `reg` must be `1` (home) or `5`
   (roaming). `csq` is 0–31; below 8 is marginal, 99 means it has no idea.
2. **`M`** — confirm the recipient count matches what you configured. Zero means
   `SIM_RECIPIENTS` did not parse.
3. **`T`** — a real text to real phones. Confirm it arrives before trusting any
   of the rest.
4. **`R`** — a simulated Red. You should see the local siren, then `CLOUD,OK`,
   then `SIM,QUEUE` and one `SIM,SENT` line per recipient.
5. **Pull the WiFi.** Fire `R` again. Now you should see `CLOUD,ERR,offline`
   immediately, followed by the SMS going out anyway. **This is the whole point
   of the module — if you test nothing else, test this.**
6. **Fire `R` twice inside two minutes.** The second should log `SIM,SKIP,cooldown`
   rather than sending again.

`SIM,SENT` means the network accepted the message, not that it was delivered.
There are no delivery receipts, and the firmware does not pretend otherwise —
the same honesty the app applies to its own SMS path.

---

## Troubleshooting

| Symptom | Cause |
|---|---|
| `SIM,ERR,no reply to AT` | Power. Then TX/RX swapped. Then baud — some modules ship at 115200; send `AT+IPR=9600` from a USB-serial adapter to pin it |
| Answers `AT`, then resets when registering | Supply cannot deliver the 2 A burst. Add the capacitor; use a real supply |
| `reg=0` forever | No 2G coverage, SIM not seated, or PIN lock still on |
| `reg=3` | Registration denied — SIM not activated, or barred |
| `csq=99` | No antenna, or it is not seated |
| Blinks fast, never registers | Same as above — the status LED blinks ~1 Hz when searching, ~3 s when registered |
| `SIM,ERR,SMS text mode refused` | PIN-locked SIM, or no SIM detected |
| `SIM,FAIL,…,no prompt` | The modem never returned `>`. Usually a dropped link mid-command |
| `SIM,WARN,SIM_RECIPIENTS is empty` | Missing `+`/country code, or the define is still `""` |

---

## Deliberately not solved here

- **Cloud → phone push.** See the note at the top: no Cloud Function exists, so a
  detection does not wake a killed app. A Firestore-triggered function publishing
  to the `alerts` topic is the fix; the SMS fallback is not a substitute for it.
- **Delivery confirmation.** GSM gives none without an SMSC receipt request.
- **Inbound SMS.** `AT+CNMI=0,0,0,0,0` tells the modem not to interrupt with
  incoming messages. The node only sends.
