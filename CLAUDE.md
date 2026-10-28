# Anant Energies BMS app: build spec

This file tells Claude Code what to build. Put it in the root of the Android Studio project (next to `settings.gradle.kts`). Claude Code reads `CLAUDE.md` automatically.

## What we are building

An Android app that connects to a battery's BMS over Bluetooth Low Energy (BLE) and shows live battery data. The first supported BMS is the **JBD DB24SA08** (10–24 cells, LiFePO4, 20–80 A). Later we will add other BMS brands (e.g. JK BMS), so the code must be structured for that from day one (see Architecture).

The app is **read-only**. It must never send a command that changes BMS settings, protection values or MOSFET state. It only sends the read requests listed in this file.

A second, **internal-only** app, "AEtools Factory", writes the settings presets during pack assembly. It lives in `factory/` and is never given to customers (see "Factory app"). Everything above about read-only applies to the customer app in `app/`, which must never contain write code.

Owner: Anant Energies (tagline "Empowering the future").

## How we work

- The owner edits code in VS Code with Claude Code and builds/runs in Android Studio, in the same folder.
- Work one milestone at a time (see Milestones). At the end of each milestone, stop, make sure `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` pass, and tell the owner exactly what to run and what they should see.
- Commit to git after each working milestone with a clear message. Commit locally only; the owner pushes to GitHub.
- Do not add libraries beyond what this file names without asking.
- Ask before changing anything in "Product decisions".

## Tech stack

- Kotlin, Jetpack Compose, Material 3, single-activity app with Navigation Compose.
- Application ID and package `com.anantenergies.bms`; app name "AEtools".
- minSdk 26, compile/target SDK = the latest stable the installed Android Studio offers.
- Coroutines + Flow for all BLE and polling work. ViewModels expose `StateFlow`.
- Jetpack DataStore (Preferences) for settings.
- Google code scanner (`com.google.android.gms:play-services-code-scanner`) for QR scanning. It uses Google Play services' own scanner UI, so the app needs no camera permission.
- Android's built-in BLE APIs (`BluetoothLeScanner`, `BluetoothGatt`); no third-party BLE library.
- Unit tests: JUnit (JVM tests under `src/test`), no device needed.
- Fonts: IBM Plex Sans, IBM Plex Mono and Roboto Mono Bold (all SIL Open Font License), bundled in `res/font`.

## Architecture (built for more BMS brands later)

```
ui/            Compose screens, theme, components
domain/        BmsSnapshot (common data model), BmsDriver interface
drivers/jbd/   JbdDriver (request/response queue, polling), JbdFrame (encode/decode, chunk reassembly), JbdParser
drivers/demo/  DemoBmsDriver (simulated pack, no Bluetooth; debug source set only)
ble/           BleScanner, BleConnection (GATT wrapper: connects to the services a driver names, writes bytes, streams notification chunks; knows nothing about any brand)
data/          SettingsRepository (DataStore), BmsRepository (picks driver, runs polling)
qr/            QrScanner (Google code scanner), MacAddress (pure: finds the BMS address in a QR code's text)
```

- `BmsSnapshot` is brand-neutral: pack voltage (V), current (A, + = charging, − = discharging), power (W, computed), SOC (%), cycle count, cell voltages (list, V), balancing cells (set of indices), temperatures (list of labelled readings, °C), charge MOS on/off, discharge MOS on/off, heater state (nullable, unknown if the BMS doesn't report it), active protections (list of enum values), cell count, timestamp.
- `BmsBrand` (one per brand, listed in the registry in `AppContainer`): `id`, `name`, `serviceUuid`, `fun matches(advertisement): Boolean` (recognise this brand from the advertisement or service UUIDs), `fun create(): BmsDriver`.
- `BmsDriver` (one per connection): `linkState`, `info`, `suspend fun connect(device)` (throws `BmsConnectException.NotSupported` / `Unreachable`), `fun snapshots(interval): Flow<BmsSnapshot>`, `suspend fun disconnect()`. `ble/LinkKeeper` gives any driver the first-connection retries and reconnect backoff.
- The UI only ever sees `BmsSnapshot`. Adding JK BMS later means adding `drivers/jk/` with its own frame parser and one line in the driver registry. No screen code changes.
- Keep protocol parsing pure (bytes in, data out, no Android types) so it is fully unit-testable.
- Planned second brand: **JK BD series boards** (no others for now). JK streams large frames over one shared FFE0 channel instead of answering polls, so each driver owns its framing, reassembly and polling; the BLE layer stays a plain byte pipe.
- Recognising a brand: first from the advertisement (service UUIDs, name), then, for a device that wasn't recognised, from the GATT services found after connecting. There is no manual brand picker; unrecognised devices are listed with whatever the board advertises.
- The remembered device stores which driver it uses, so auto-connect goes straight to the right one.
- Read-only guard: each driver can only produce a fixed set of read requests (for JBD the `JbdRequest` values), and a unit test fails if it could send anything else.
- When JK is added, the cell colour limits (now the DB24SA08 values) become per driver/chemistry. Ask the owner first, as that touches Product decisions.

## JBD protocol (BLE)

Source: the public JBD/Xiaoxiang protocol. The datasheet we have does not include it, so treat UUIDs and byte layout as "expected" and confirm on the real board.

**GATT:** service `0000ff00-0000-1000-8000-00805f9b34fb`, notify characteristic `0000ff01-…` (enable notifications via CCCD `00002902-…`), write characteristic `0000ff02-…`. If the service is not found, log every discovered service and characteristic UUID so we can adapt. Some JBD boards also expose a UART-style service; don't assume, log it.

**Request frame:** `DD A5 <register> 00 <checksum hi> <checksum lo> 77`. Checksum = `0x10000 − (register + length + data bytes)`, as 16-bit big-endian.
- Basic info: `DD A5 03 00 FF FD 77`
- Cell voltages: `DD A5 04 00 FF FC 77`
- Hardware/name (optional): register `0x05`

These three read requests are the only bytes the app may write.

**Response frame:** `DD <register> <status> <length> <data…> <checksum hi> <checksum lo> 77`. Status `00` = OK, anything else = error. Checksum = `0x10000 − (status + length + sum of data)`.

Responses arrive as BLE notifications split into ~20-byte chunks. Reassemble: start at `DD`, read `length` at byte 3, wait until `4 + length + 3` bytes have arrived, verify the trailing `77` and the checksum, then parse. Discard and resync on garbage. Send one request at a time and wait for its response (1 s timeout, then retry once) before sending the next.

**Register 0x03, basic info** (all multi-byte values big-endian):

| Offset | Size | Field | Unit / notes |
|---|---|---|---|
| 0 | 2 | Pack voltage | 10 mV |
| 2 | 2 | Current | signed, 10 mA; + charge, − discharge |
| 4 | 2 | Remaining capacity | 10 mAh (parse, but do not display) |
| 6 | 2 | Nominal capacity | 10 mAh (parse, but do not display) |
| 8 | 2 | Cycle count | |
| 10 | 2 | Production date | packed bits, ignore for now |
| 12 | 2 | Balance status, cells 1–16 | bit n = cell n+1 balancing |
| 14 | 2 | Balance status, cells 17–32 | |
| 16 | 2 | Protection status | bit flags, see below |
| 18 | 1 | Software version | |
| 19 | 1 | SOC (RSOC) | % |
| 20 | 1 | MOS status | bit0 = charge MOS on, bit1 = discharge MOS on |
| 21 | 1 | Cell count | |
| 22 | 1 | NTC count (N) | |
| 23 | 2×N | Temperatures | 0.1 K; °C = (value − 2731) / 10 |
| … | | Extra bytes | newer firmware appends more fields; ignore anything past the NTCs unless confirmed |

Protection bits (offset 16): 0 cell overvoltage, 1 cell undervoltage, 2 pack overvoltage, 3 pack undervoltage, 4 charge over-temperature, 5 charge under-temperature, 6 discharge over-temperature, 7 discharge under-temperature, 8 charge overcurrent, 9 discharge overcurrent, 10 short circuit, 11 front-end IC error, 12 MOS locked by software. Show each as a plain-English message.

**Register 0x04, cell voltages:** `length / 2` cells, 2 bytes each, mV.

**Temperatures:** the DB24SA08 has 1 board sensor and 2 external probes. Which index is the board/MOS sensor is not confirmed. Show them as "T1, T2, T3" until confirmed on hardware, then relabel.

**Heater:** the board has a heating-film output, but whether its state appears in the 0x03 reply is not confirmed. Make `heater` nullable and hide the Heater chip when it's unknown.

### Test vectors (synthetic, built to the format above, not captured from hardware)

Basic info, 16S pack, 52.82 V, −12.40 A, SOC 78 %, 42 cycles, cells 3/7/14 balancing, no protections, both MOS on, 3 NTCs at 31.0 / 27.4 / 28.1 °C:
```
DD 03 00 1D 14 A2 FB 28 1E 78 27 10 00 2A 00 00 20 44 00 00 00 00 10 4E 03 10 03 0B E1 0B BD 0B C4 F9 B8 77
```
Cell voltages (mV) 3301 3298 3305 3300 3296 3303 3311 3299 3302 3297 3300 3292 3304 3309 3301 3298:
```
DD 04 00 20 0C E5 0C E2 0C E9 0C E4 0C E0 0C E7 0C EF 0C E3 0C E6 0C E1 0C E4 0C DC 0C E8 0C ED 0C E5 0C E2 F0 D0 77
```
Basic info with cell overvoltage protection active, charge MOS off, discharge MOS on, SOC 100 %, 56.00 V, 0 A:
```
DD 03 00 1D 15 E0 00 00 27 10 27 10 00 2A 00 00 00 00 00 00 00 01 10 64 02 10 03 0B E1 0B BD 0B C4 FB 49 77
```
Unit tests must cover: these three frames, the same frames split into 20-byte chunks, a corrupted checksum (rejected), a status byte ≠ 00 (rejected), and request encoding (`DD A5 03 00 FF FD 77`, `DD A5 04 00 FF FC 77`).

## Demo mode

`DemoBmsDriver` produces a realistic 16S Li-ion (NMC) pack with no Bluetooth (matching the real board, cells near 3.83 V): cell voltages that follow a Li-ion OCV curve with small noise, current drifting between −20 A and +15 A, SOC that slowly follows the current, temperatures 25–35 °C, balancing on the highest cells when they are above 3.90 V and the spread is over 15 mV. A scenario control switches between: normal, charging, high spread (one weak cell), over-temperature protection, cell overvoltage protection, and Bluetooth dropped. This lets us test every warning state without hardware.

**Demo mode is for development only.** The demo driver, the simulated JBD board and their scenario controls exist only in **debug** builds (the `src/debug` source set), so release builds sent to customers contain no fake data. The raw TX/RX log from milestone 3 follows the same rule.

**Debug builds look like release builds (owner decision).** No demo card on the Connect screen, no simulated board in the scan list, no banner on the screens. The only visible difference is "debug build" next to the app version in Settings. Everything for testing without hardware is on the **Developer tools** screen, opened with 5 taps on the app version: connect the simulated board or start the demo pack, change their scenario, and the Bluetooth log (Copy all / Clear).

## Product decisions (ask before changing)

- Show the SOC **percentage only**. Never display Ah capacity or a "time left" estimate anywhere.
- Read-only: the customer app never writes BMS parameters. Only the internal factory app does (owner decision, 2026-10-27).
- Cell count comes from the BMS (10–24), never hard-coded.
- Phones only for now. Don't build tablet-specific layouts yet, but avoid fixed pixel widths so a tablet layout can be added later.
- Internet (owner decision): keep the INTERNET permission that Google's code scanner library adds for its usage statistics. The app must work fully offline: scanning the list and connecting over Bluetooth never need the internet. Only the QR scanner's one-time download by Google Play services does, and offline the app says so straight away and points to the list.
- Colour limits (owner decision, 2026-10-23): the connected packs are Li-ion (NMC) — confirmed on the real board, whose cells rest near 3.83 V — so cell range is **2.80–4.25 V** (red outside it). Balancing is read from the BMS, not computed. When a LiFePO4 board is added later, limits become per driver/chemistry (ask the owner).
- Temperature colours (owner decision): above 45 °C orange, above 50 °C red, judged in °C whatever unit is displayed.

## Screens

Reference mockup (owner can open it): https://claude.ai/artifact/KTUwenqcppsFq8ow4rVpDz

Bottom navigation with three tabs: **Dashboard** (gauge icon), **Cells** (bar-chart icon), **Devices** (Bluetooth icon). Settings opens from a gear button at the top right of the Dashboard.

1. **Connect / Devices.** Title "Connect to BMS", a short hint ("Keep your phone within a few metres of the battery. The BMS sleeps after a minute with no current, so wake it by connecting a load or charger if it doesn't show up."), Anant Energies logo (AE mark + wordmark) at the top. Live scan list: device name, MAC (partly masked), signal strength in dBm; a BMS the app recognises (advertises the JBD service or has a known JBD-style name) gets a highlighted border and "JBD service found". Other devices are labelled "Not recognised" but can still be tapped, with a confirmation, so an unexpected board can be diagnosed; devices with no name are hidden behind a toggle. Stop/scan button. A prominent **Scan QR code on the battery pack** button above the list (see "QR connect" below).
2. **Dashboard.** Header: AE mark, "DB24SA08 · 16S" (model and cell count), status line "Connected · −62 dBm · updated 1 s ago", gear button. Then:
   - **Battery card:** header row "Battery" with a status pill on the right (Charging / Discharging / Idle). Body: SOC ring (brand cyan) with the big percentage and "State of charge" inside; beside it a list of Voltage, Current (orange when discharging), Power.
   - Below that, three cards that share the remaining height equally: **Cells** (highest cell #n in orange, lowest #n in blue, spread in mV, "16 cells · 3 balancing ›", tapping opens Cells), **Temperatures** (each sensor), **Status** ("No protections active" or the active protection messages, chips: Charge MOS on/off, Discharge MOS on/off, Heater on/off when known, cycle count). Values in the Cells and Temperatures cards are vertically centred under the title.
3. **Cells.** Back arrow, title "Cells", "16 in series · Li-ion" (the chemistry label comes from the driver), AE mark at the right. Four small tiles: Highest, Lowest, Average, Spread. A bar chart of every cell (window around the average, highest bar orange, lowest blue, others grey) and a two-column list of cells with voltage to 3 decimals and a "BAL" tag on balancing cells.
4. **Settings.** Back arrow, title, AE mark. Sections:
   - Connection: Auto-connect on launch (switch), Refresh rate 1 s / 2 s / 5 s (default 2 s).
   - Display: Theme System / Dark / Light (default Dark), Temperature unit °C / °F.
   - This BMS: name, MAC, model, connected state; Disconnect and Forget this BMS buttons.
   - About: App version, then the ANANT ENERGIES wordmark with "Empowering the future" directly underneath (4 dp gap), white in the dark theme and black in the light theme.

Behaviour: poll 0x03 then 0x04 every refresh interval while a screen is visible; auto-reconnect with backoff when the link drops and show "Reconnecting…"; remember the last device and auto-connect if enabled. Auto-connect runs only on a fresh launch, not when the app returns from the background (owner decision). While it works, the Dashboard shows the approved states from mockup row 5: connecting, couldn't reach (Try again), and Bluetooth off or not allowed (it continues by itself once Bluetooth is ready), each with Choose another BMS. Ask for permissions properly: Android 12+ `BLUETOOTH_SCAN` (with `neverForLocation`) and `BLUETOOTH_CONNECT`; Android 8–11 `ACCESS_FINE_LOCATION`. Handle "Bluetooth is off" with a clear prompt.

## QR connect

Each battery pack has a QR label stuck on it that contains its BMS's Bluetooth MAC address. The labels come with the BMS; no sample has been captured yet. The **Scan QR code on the battery pack** button opens the Google code scanner. When a code is read:

1. Extract the MAC: accept `A4:C1:38:12:34:56`, `A4-C1-38-12-34-56`, `a4c138123456`, or a URL or text that contains one of those. Normalise it to upper case with colons. If no valid MAC is found, show "This QR code doesn't contain a BMS address" and stay on the Connect screen.
2. Run a short BLE scan (up to 10 s) filtered to that exact address, so we know the BMS is awake and in range. If it isn't found, show "BMS not found. Make sure it's nearby." with a Retry button.
3. When found, connect straight away with the matching driver, save it as the remembered device (so auto-connect uses it next time), and open the Dashboard.

Put the MAC parsing in a pure function with unit tests for every accepted format and for invalid input. Bluetooth permissions and "Bluetooth is off" handling are the same as for a normal scan.

In debug builds, a QR code containing the simulated board's address (`SI:MU:LA:TE:D0:01`) connects to the simulated JBD board, so the whole QR flow can be tested without hardware.

## Visual design

- Fonts: IBM Plex Sans for text, IBM Plex Mono for all numbers. Owner decision: the labels of readings in cards (Voltage, Current, Power, Highest / Lowest / Average / Spread, T1–T3) use Roboto Mono Bold, tracking −0.02 em. Card titles and "State of charge" stay IBM Plex Sans.
- Shapes: cards 12 dp corners; small tiles and buttons 4 dp; status chips fully rounded (pills).
- Brand assets are in `app/src/main/res/drawable/` (copy from the `assets/` folder delivered with this file): `ae_mark.png` (cyan AE mark, for dark theme), `ae_mark_dark.png` (deeper cyan, for light theme), `anant_wordmark.png` (cyan wordmark). Never redraw the logo. The only recolouring allowed (owner decision): the wordmark is tinted black in the light theme, because the cyan is unreadable on white.

Colour tokens:

| Token | Dark | Light |
|---|---|---|
| background | #0E1315 | #F4F6F7 |
| surface (cards) | #172024 | #FFFFFF |
| border | #26323A | #DCE2E5 |
| nav bar | #121A1D | #FFFFFF |
| text primary | #E8EEF0 | #11181B |
| text secondary | #9AA8AE | #5A676D |
| brand / SOC ring / selected | #3CBED0 | #138A9C |
| discharging / highest cell | #FFC27A (pill bg #3A2A14) | #A0560B (pill bg #FCEBD5) |
| lowest cell / links | #8EC5FF | #1F64B5 |
| OK / MOS on | #6FE0C4 (chip border #2F5A50) | #0E7A62 (chip border #9FD6C6) |
| connected dot | #3CC8A8 | #12A37F |
| balancing tag | bg #1E3550, text #9FD0FF | bg #DCE8F7, text #1F64B5 |
| warning / protections / cell outside 2.80–4.25 V | #FF8A80 (bg #3D1C1C, border #6B2E2E) | #B3261E (bg #FCE4E2, border #F1B5AF) |

A MOS that is off is shown as a neutral grey chip, not red. The full token set, including the few extra colours the mockup needs, is in `ui/theme/Color.kt`.

## Milestones

1. **Skeleton + demo.** Theme (both palettes, fonts, shapes), navigation, all four screens fed by `DemoBmsDriver`, settings saved in DataStore. Done when the app runs on a phone or emulator in demo mode and every screen matches the description.
2. **JBD parser.** `JbdFrame` encode/decode, chunk reassembly, `JbdParser` to `BmsSnapshot`, with all unit tests above passing.
3. **Bluetooth.** Permissions, scanning, recognising JBD, connecting, notifications, the request/response queue, polling, reconnect, and QR connect. Add a debug screen (hidden behind 5 taps on the app version) that shows raw TX/RX hex and discovered UUIDs, so we can diagnose the real board. It is now the Developer tools screen (see Demo mode).
   - Status: built and tested without hardware. Debug builds have a **simulated JBD board** (`drivers/sim/`, debug source set) that answers with real JBD frames over a fake link, started from Developer tools, with fault scenarios there (dropped/corrupt replies, link drop, silent BMS, protections). The Bluetooth log also records each board's advertisement on connect.
   - Confirmed on the real board (2026-10-23, model `SP17S005P17S50A`, advertised name `512-260514-150213`, recognised by the `ff00` service): GATT UUIDs `ff00`/`ff01`/`ff02` as expected; the advertised name is not JBD-style, so recognition is by service UUID. The 0x03 reply is 38 data bytes (length `0x26`) with nine fields after the 3 NTCs, which we ignore; 0x03/0x04 byte layout parses correctly. 0x05 returns the raw board string `SP17S005P17S50A`, shown verbatim as the model (owner decision). A real-board capture is locked in as a parser regression test (`JbdTestVectors.HW_*`).
   - Still to confirm on the real board: which temperature index is the board/MOS sensor; heater state (the nine trailing 0x03 fields are undecoded); the current sign convention (the capture was idle at 0 A — needs a load/charge test).
   - QR connect: built (added to the spec after the first Milestone 3 commit). The manifest asks Google Play services to download its scanner at install time. Tried on the emulator up to the scanner opening and closing; what happens after a real scan is still to try on a phone. Debug builds also accept a QR code with the simulated board's address.
4. **Polish.** Auto-connect, Bluetooth-off and permission-denied states, empty and error states, app icon from the AE mark.
   - Done early: app icon, Bluetooth-off / Location-off / permission prompts, status line that shortens on narrow phones, release signing setup.
   - Auto-connect: built and tried on the emulator with the simulated board (debug builds remember it; its "Out of range" scenario tests the couldn't-reach state). Not yet tried with the real board.

Future (not now): JK BMS driver (JK BD series boards), data logging/export, multiple saved packs.

## Factory app (internal only)

Owner decision, 2026-10-27. **AEtools Factory** applies a whole settings preset to a pack's BMS in one go, verifies it, and keeps a log sheet, so staff no longer type each setting into the JBD app. Staff phones only: never on the Play Store for customers (sideload the APK, or a Play internal testing track at most).

### Layout (same repository)

```
app/       customer app "AEtools" (com.anantenergies.bms), read-only; depends on core/ only
factory/   "AEtools Factory" (com.anantenergies.bms.factory); all write code, profiles, presets, apply/verify, checklist, log sheet
core/      shared, read-only: ble/, domain/, the JBD read frames and parser, theme, fonts, brand assets
docs/      jbd-write-protocol.md (decoded commands, confirmed against a real-board capture)
```

- `app` must never depend on `factory`. A build check fails if it does, and the customer app's read-only test stays.
- Moving the shared code from `app/` into `core/` is a mechanical move with no behaviour change. All of the customer app's tests must still pass afterwards.
- The factory app uses the same theme, fonts and logo, and the same tech stack. No new libraries without asking. The log sheet is a CSV file in app storage, shared with Android's share sheet.

### Write protocol

Exact bytes, addresses and scaling are in `docs/jbd-write-protocol.md`. Rules:

- Enter settings mode once, write every setting of the preset, then **exit and save** once. If any write is refused or times out, **exit without saving** (`DD 5A 01 02 00 00 FF FD 77`) and report which setting failed. Never save a half-applied preset (the JBD app does).
- Only the addresses in the BMS model's profile can be written. A unit test fails if the factory code could write anything else.
- Never write: cell count, Level2 overcurrent, short circuit (fixed by the board), or production data. Function bits (address 29) are read-modify-write and only the balance bits may change.
- Reads use the new-protocol read (`DD A5 FA …`), which needs no settings mode.

### BMS model profiles

A preset can only be applied to the BMS model it was written for. Each profile has:

- id and how to recognise it: the board string from register 0x05 (e.g. `SP17S005P17S50A` starts with `SP17S005`) plus the chip-type probe (`DD A5 00 00 00 00 77` → `01` = new protocol);
- protocol (old registers or new `FA` addresses), the writable addresses, the read-only ones;
- rated discharge current (SP17S005: 60 A);
- lock: none, or a module password/key. The SP17S005 has none, so its flow has no password step. A model with a password gets its own profile, presets (from its own settings card) and a password step;
- the cell-count and chemistry the presets assume (SP17S005: 16 cells, NMC).

On an unknown board the app writes nothing and says so.

### Presets: 60V family on SP17S005 (settings card Rev 4.0)

Two presets. Each records its card revision, and the log sheet stores it with every pack.

| Setting | 60V 23Ah (16S5P) | 60V 27 / 32 / 36 / 41Ah (16S6P–9P) |
|---|---|---|
| Cell high protect / recover | 4.18 / 4.10 V | 4.20 / 4.15 V |
| Cell low protect / recover | 2.70 / 2.90 V | same |
| Pack high protect / recover | 66.8 / 65.6 V | 67.0 / 66.4 V |
| Pack low protect / recover | 43.2 / 46.4 V | same |
| Hardware cell high / low | 4.25 / 2.50 V | same |
| Voltage delays (pack high, pack low, cell high, cell low) | 2 s each | same |
| Charge over current, delay, release | 20 A, 10 s, 32 s | same |
| Discharge over current, delay, release | 60 A, 30 s, 32 s | same |
| Charge high temp protect / recover | 54 / 48 °C | same |
| Charge low temp protect / recover | 3 / 8 °C | same |
| Discharge high temp protect / recover | 60 / 50 °C | same |
| Discharge low temp protect / recover | −15 / −10 °C | same |
| MOS high temp protect | 90 °C, set by hand in the JBD app: the SP17S005 refuses its address (202) over Bluetooth, so the review and pass screens remind staff | same |
| Temperature delays | 5 s each | same |
| Balance | on, charge balance (never static) | same |
| Balance voltage / accuracy | 4.09 V / 15 mV | 4.14 V / 15 mV |
| AveFullVolt / AveEmptyVolt | 4.08 / 2.80 V | 4.13 / 2.80 V |
| SOC 100…10 % (V per cell) | 4.09 4.03 3.96 3.89 3.82 3.75 3.68 3.58 3.45 3.30 | 4.14 4.06 3.99 3.92 3.84 3.77 3.69 3.60 3.46 3.31 |

Per pack, typed in by the operator: **design capacity and cycle capacity = the Ah this pack gave on test** (DSF40 discharge). The app checks it against the expected range for the model and warns outside it: 23Ah 18.9–19.7, 27Ah 23.9–24.9, 32Ah 28.0–29.0, 36Ah 32.0–33.2, 41Ah 36.0–37.4 Ah (at 15 A). Staff choose the pack model (23 / 27 / 32 / 36 / 41Ah); the model picks the preset and the expected range.

Preset values live in code, are reviewed like code, and have unit tests that encode them to the exact bytes. Staff cannot edit them in the app.

### Apply flow

Connect → identify the BMS model → choose pack model → enter pack number and tested Ah → screen listing every setting, current value → new value → read and keep a backup of all current values → apply → read everything back and compare → ask the operator to power-cycle the BMS and wait → reconnect and read everything again → checklist → save the log entry → reminder: "Now bind this pack in the JBD app."

- Discharge over current must read back as 60 A. If it reads 40 A: **"This is a 40 A board. Do not ship."**
- Any mismatch on read-back fails the pack and lists the settings that didn't stick.

### Checklist (automatic where the app can measure it)

Automatic: 16 cells; every enabled temperature sensor at room temperature ±3 °C; all values read back after the power-cycle; balance set to charge balance. Live cell delta is shown; at end of charge it must be 15 mV or less (over 30 mV: top-balance with the NEEY).

Ticked by the operator: J2 then J1 plugged within 5 s; protection counts reset; 80 A DC fuse fitted; charger output in range (23Ah 65.5–65.7 V, 27–41Ah 66.3–66.5 V); balance leads and sensors secured.

### Log sheet

One row per pack: date and time, operator, pack number, pack model, BMS model and MAC, preset and card revision, tested Ah, resistance (typed in), end-of-charge delta, each checklist result, pass/fail, and the settings read back. Kept on the phone and exported as CSV.

### Binding

JBD's binding is an account record on JBD's server (see `docs/jbd-write-protocol.md`). It sends nothing to the pack, so it cannot reset settings, and it only stops other **JBD app** users from changing them. Staff bind in the JBD app after the factory app passes the pack. The SP17S005 has no on-pack lock.

### Testing without hardware

The simulated JBD board (debug builds) gains write support: register store, settings mode, save versus exit-without-save, and fault injection (refused write, timeout, value that doesn't stick, 40 A board, link drop mid-apply). Encoding tests use the frames from the real-board capture in `docs/jbd-write-protocol.md`.

### Factory milestones

- **F1. `core/` module.** Move the shared code out of `app/`, with no behaviour change, and add the build check that `app` doesn't depend on `factory`.
  - Done: `core/` holds `ble/`, `domain/`, `drivers/jbd/`, `qr/`, `dev/TrafficLog`, `ui/theme/`, `ui/components/`, the fonts and drawables, and their tests (Kotlin packages unchanged; resources are `com.anantenergies.bms.core.R`). `app/` keeps the screens, view models, `data/`, `ui/Format`, the demo driver, the simulated board and Developer tools. All 85 unit tests still pass; the guard in `app/build.gradle.kts` was tried with a dummy `:factory` dependency and fails the build as intended.
- **F2. Write protocol, profiles, presets.** Pure Kotlin, fully unit-tested, and the simulated board with write support.
  - Done, in `factory/`: `model/` (`JbdSetting` addresses and encodings, `BmsModelProfile` + `BmsModels.SP17S005`, `Presets` and `PackModel` from card Rev 4.0), `protocol/` (`JbdSettingsFrames`, the only code that builds write frames, and `GattBoardChannel`, the one-frame-at-a-time queue over a `GattLink`), `apply/` (`WritePlan`, `PresetApplier`: identify, read all, apply, verify). Debug only: `sim/SimulatedSettingsBoard` with the faults from "Testing without hardware"; it starts with the values read from the real test pack. 34 tests: every frame from the real-board capture, the card's values encoded to exact register values, the write guard, and apply/verify against the simulated board for each fault. The app screen is a placeholder that lists the presets.
  - Not yet confirmed on hardware: the encodings of the settings the capture didn't touch (temperatures, currents, SOC table, capacities). F3's first real apply checks them by read-back and in the JBD app.
  - After a link drop mid-apply the real board may still be in settings mode with unsaved writes: F3 tells the operator to power-cycle before trying again.
- **F3. Factory app: apply and verify.** Connect, identify, choose a preset, enter tested Ah, review, apply, read back, power-cycle and verify again. Tried against the simulated board first, then on one real pack.
  - Built: `flow/FactorySession` (the steps), `ui/` (board list with Bluetooth checks, pack details, review with current → new values, progress, results, power-cycle, final check, Bluetooth log with Copy all). Debug builds list "Simulated SP17S005" at the top of the board list with a fault picker under it. 125 unit tests pass, including the whole flow against the simulated board.
  - Owner tried the whole flow on the emulator with the simulated board, including every fault (2026-10-28).
  - First real pack (2026-10-28): connected and identified; the backup read failed because 8-register reads crossed the JBD app's groups, and then because the board refuses address 202 (MOS high temp). Fixed: profiles list their read blocks (from the JBD app), and MOS high temp is a set-by-hand item for the SP17S005.
  - **Confirmed on the real pack (2026-10-28):** the owner applied the 60V 27–41Ah preset to the test pack and reports a pass after the power-cycle. The JBD app's Voltage, Current and Temp pages then showed exactly the card's values (cell 4.2 / 4.15 / 2.7 / 2.9 V, pack 67 / 66.4 / 43.2 / 46.4 V, delays 2 s, charge OC 20 A / 10 s / 32 s, discharge OC −60 A / 30 s / 32 s, temperatures 54 / 48, 3 / 8, 60 / 50, −15 / −10 °C, delays 5 s), with L2 OC and SCP untouched. The Balance page (on, 4.14 V, 15 mV, charge balance) and Static Cap page (4.13 / 2.8 V, SOC 100…10 % 4.14 … 3.31 V) match too, so every setting the app writes is confirmed on hardware. The JBD app's Temp page has no MOS temperature item on this board.
- **F4. Checklist and log sheet.** Checklist screen, log storage, CSV export.
  - Built: after the pass, **Next: checklist** reads 0x03/0x04 live (the same read requests as the customer app) and checks 16 cells, temperature sensors at room temperature (each within ±3 °C of the sensors' average), all settings correct after the power-cycle, and charge balance on; it shows the live cell delta. The operator ticks the card's manual checks (charger range per pack model) and any set-by-hand items, and types operator (remembered in DataStore), resistance and end-of-charge delta (≤ 15 mV passes; over 30 mV says to top-balance with the NEEY). Saving appends one row to `files/pack-log.csv` (fixed columns, one per setting, so files line up), pass or fail, then says to bind in the JBD app. **Log sheet** on the first screen lists packs and shares the CSV through Android's share sheet (FileProvider). 137 unit tests pass.
  - Tried by the owner on the real test pack (2026-10-28): PASS, all four automatic checks OK (16 cells; 28.5 / 26.6 / 26.5 °C; settings correct after the power-cycle; function bits 0x000E), live delta 8 mV, and the shared CSV holds every read-back value matching the card. After that: cell count and balance accuracy are written as `16` and `15 mV` in the CSV and on screen, and a new log file starts with a UTF-8 byte-order mark so Excel shows °C, mΩ and – correctly (version 0.2).

### Getting the app to staff (owner decision, 2026-10-28)

- Staff phones get the **release** APK as a WhatsApp file. Never send the debug APK: it lists the simulated board and its fault picker.
- Build it with `./gradlew :factory:assembleRelease` with `keystore.properties` in place; it comes out as `factory/build/outputs/apk/release/factory-release.apk`. It is signed with the same key as the customer app. A phone only accepts an update signed with the same key, so never build a staff APK without it.
- Before every APK sent to staff, raise `versionCode` (by 1) and `versionName` in `factory/build.gradle.kts`; Android refuses an update with the same or a lower `versionCode`. The first screen shows "AEtools Factory version …" so staff can check they have the latest.
- On the phone: open the file in WhatsApp → allow "Install unknown apps" for WhatsApp once → Install (if Play Protect warns, "More details → Install anyway"). Updating keeps the log sheet; uninstalling deletes it, so share the CSV first.

### Adding a new settings card (owner decision, 2026-10-28)

The owner sends the new card as a PDF. Then:

1. **Same BMS model (SP17S005)**, e.g. a new pack family or a new revision: add or update the presets in `factory/model/Presets.kt` (one `Preset` per column of the card, with its `cardRevision`) and the `PackModel` entries (label, preset, expected capacity range), then the charger range in `ManualCheck.chargerRange` and any card checks that differ. A new revision replaces the old preset values; the log sheet keeps the revision each pack was set with.
2. **New BMS model** (a different board string from register 0x05): also add a `BmsModelProfile` in `BmsModels`. Before writing to it, get from the owner: the board string (the factory app's Log shows it), and one Bluetooth HCI snoop log of the JBD app reading the parameter pages and changing one setting, to confirm the protocol, read groups and addresses. Ask whether it has a password/key lock.
3. Card items the board can't take over Bluetooth go in `setByHand`. Never add Level2 OC, short circuit, cell count or NTC bits to `writable`.
4. Unit tests: each new preset's values encoded to exact register values (like `PresetsTest`), and every new card item covered. Build, run all tests, raise `versionCode`/`versionName`, and tell the owner to send the new release APK to staff.
5. First real pack of a new model: owner applies it, power-cycles, and compares the JBD app's parameter pages, as was done for the SP17S005.

### 23Ah (owner decision, 2026-10-28)

The 60V 23Ah preset uses exactly the same flow and checks as 27–41Ah; it needs no separate real-pack trial.

## Release signing

- `app/build.gradle.kts` and `factory/build.gradle.kts` sign release builds when `keystore.properties` exists next to `settings.gradle.kts` (format in `keystore.properties.example`). It and any `*.jks` / `*.keystore` are git-ignored; never commit them or paste passwords into chat.
- The owner creates the key once (Android Studio: Build → Generate Signed App Bundle or APK → Create new…), keeps the `.jks` outside the project, and backs up the file and both passwords. Losing them means no more updates to the same Play listing (Play App Signing reduces the risk).
- Without `keystore.properties`, release builds come out unsigned.
