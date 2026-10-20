# Anant Energies BMS app: build spec

This file tells Claude Code what to build. Put it in the root of the Android Studio project (next to `settings.gradle.kts`). Claude Code reads `CLAUDE.md` automatically.

## What we are building

An Android app that connects to a battery's BMS over Bluetooth Low Energy (BLE) and shows live battery data. The first supported BMS is the **JBD DB24SA08** (10–24 cells, LiFePO4, 20–80 A). Later we will add other BMS brands (e.g. JK BMS), so the code must be structured for that from day one (see Architecture).

The app is **read-only**. It must never send a command that changes BMS settings, protection values or MOSFET state. It only sends the read requests listed in this file.

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

`DemoBmsDriver` produces a realistic 16S LiFePO4 pack with no Bluetooth: cell voltages around 3.30 V with small noise, current drifting between −20 A and +15 A, SOC that slowly follows the current, temperatures 25–35 °C, balancing on the highest cells when they are above 3.30 V and the spread is over 15 mV. Add a hidden "scenario" control (long-press the demo banner) that switches between: normal, charging, high spread (one weak cell), over-temperature protection, cell overvoltage protection, and Bluetooth dropped. This lets us test every warning state without hardware. Show a small "Demo" banner on every screen while demo mode is on.

**Demo mode is for development only.** It (the "Start demo mode" button, the demo driver and the fault-scenario control) must exist only in **debug** builds. Gate it with `BuildConfig.DEBUG` (or put the demo code in the `src/debug` source set) so release builds sent to customers contain no demo button and no fake data. The raw TX/RX debug screen from milestone 3 follows the same rule.

## Product decisions (ask before changing)

- Show the SOC **percentage only**. Never display Ah capacity or a "time left" estimate anywhere.
- Read-only: no writing of BMS parameters, ever.
- Cell count comes from the BMS (10–24), never hard-coded.
- Colour limits from the DB24SA08 datasheet: cell range 2.30–3.75 V; balancing starts at 3.30 V with a spread over 15 mV.
- Temperature colours (owner decision): above 45 °C orange, above 50 °C red, judged in °C whatever unit is displayed.

## Screens

Reference mockup (owner can open it): https://claude.ai/artifact/KTUwenqcppsFq8ow4rVpDz

Bottom navigation with three tabs: **Dashboard** (gauge icon), **Cells** (bar-chart icon), **Devices** (Bluetooth icon). Settings opens from a gear button at the top right of the Dashboard.

1. **Connect / Devices.** Title "Connect to BMS", a short hint ("Keep your phone within a few metres of the battery. The BMS sleeps after a minute with no current, so wake it by connecting a load or charger if it doesn't show up."), Anant Energies logo (AE mark + wordmark) at the top. Live scan list: device name, MAC (partly masked), signal strength in dBm; a BMS the app recognises (advertises the JBD service or has a known JBD-style name) gets a highlighted border and "JBD service found". Other devices are labelled "Not recognised" but can still be tapped, with a confirmation, so an unexpected board can be diagnosed; devices with no name are hidden behind a toggle. Stop/scan button. At the bottom, in debug builds only, a "No BMS nearby?" card with a **Start demo mode** button.
2. **Dashboard.** Header: AE mark, "DB24SA08 · 16S" (model and cell count), status line "Connected · −62 dBm · updated 1 s ago", gear button. Then:
   - **Battery card:** header row "Battery" with a status pill on the right (Charging / Discharging / Idle). Body: SOC ring (brand cyan) with the big percentage and "State of charge" inside; beside it a list of Voltage, Current (orange when discharging), Power.
   - Below that, three cards that share the remaining height equally: **Cells** (highest cell #n in orange, lowest #n in blue, spread in mV, "16 cells · 3 balancing ›", tapping opens Cells), **Temperatures** (each sensor), **Status** ("No protections active" or the active protection messages, chips: Charge MOS on/off, Discharge MOS on/off, Heater on/off when known, cycle count). Values in the Cells and Temperatures cards are vertically centred under the title.
3. **Cells.** Back arrow, title "Cells", "16 in series · LiFePO4", AE mark at the right. Four small tiles: Highest, Lowest, Average, Spread. A bar chart of every cell (window around the average, highest bar orange, lowest blue, others grey) and a two-column list of cells with voltage to 3 decimals and a "BAL" tag on balancing cells.
4. **Settings.** Back arrow, title, AE mark. Sections:
   - Connection: Auto-connect on launch (switch), Refresh rate 1 s / 2 s / 5 s (default 2 s).
   - Display: Theme System / Dark / Light (default Dark), Temperature unit °C / °F.
   - This BMS: name, MAC, model, connected state; Disconnect and Forget this BMS buttons.
   - About: App version, then the ANANT ENERGIES wordmark with "Empowering the future" directly underneath (4 dp gap), white in the dark theme and black in the light theme.

Behaviour: poll 0x03 then 0x04 every refresh interval while a screen is visible; auto-reconnect with backoff when the link drops and show "Reconnecting…"; remember the last device and auto-connect if enabled. Ask for permissions properly: Android 12+ `BLUETOOTH_SCAN` (with `neverForLocation`) and `BLUETOOTH_CONNECT`; Android 8–11 `ACCESS_FINE_LOCATION`. Handle "Bluetooth is off" with a clear prompt.

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
| warning / protections / cell outside 2.30–3.75 V | #FF8A80 (bg #3D1C1C, border #6B2E2E) | #B3261E (bg #FCE4E2, border #F1B5AF) |

A MOS that is off is shown as a neutral grey chip, not red. The full token set, including the few extra colours the mockup needs, is in `ui/theme/Color.kt`.

## Milestones

1. **Skeleton + demo.** Theme (both palettes, fonts, shapes), navigation, all four screens fed by `DemoBmsDriver`, settings saved in DataStore. Done when the app runs on a phone or emulator in demo mode and every screen matches the description.
2. **JBD parser.** `JbdFrame` encode/decode, chunk reassembly, `JbdParser` to `BmsSnapshot`, with all unit tests above passing.
3. **Bluetooth.** Permissions, scanning, recognising JBD, connecting, notifications, the request/response queue, polling, reconnect. Add a debug screen (hidden behind 5 taps on the app version) that shows raw TX/RX hex and discovered UUIDs, so we can diagnose the real board.
   - Status: built and tested without hardware. Debug builds list a **simulated JBD board** in the scan (`drivers/sim/`, debug source set) that answers with real JBD frames over a fake link; long-press its banner for faults (dropped/corrupt replies, link drop, silent BMS, protections).
   - Still to confirm on the real DB24SA08: GATT UUIDs and advertised name, byte layout of 0x03/0x04, what 0x05 returns (shown as the model), which temperature index is the board/MOS sensor, heater state.
4. **Polish.** Auto-connect, Bluetooth-off and permission-denied states, empty and error states, app icon from the AE mark.
   - Done early: app icon, Bluetooth-off / Location-off / permission prompts, status line that shortens on narrow phones, release signing setup.

Future (not now): JK BMS driver (JK BD series boards), data logging/export, multiple saved packs.

## Release signing

- `app/build.gradle.kts` signs release builds when `keystore.properties` exists next to `settings.gradle.kts` (format in `keystore.properties.example`). It and any `*.jks` / `*.keystore` are git-ignored; never commit them or paste passwords into chat.
- The owner creates the key once (Android Studio: Build → Generate Signed App Bundle or APK → Create new…), keeps the `.jks` outside the project, and backs up the file and both passwords. Losing them means no more updates to the same Play listing (Play App Signing reduces the risk).
- Without `keystore.properties`, release builds come out unsigned.
