# JBD write protocol (for the internal factory app)

Decoded from the JBD app "Xiaoxiang" v2.0.1 (`com.jiabaida.xiaoxiangcloud`, a uni-app; the BLE code is the
`@jiabaida/tools` module inside `assets/apps/__UNI__…/www/app-service.js`). Nothing here has been tried on our
board yet. Confirm every command with one HCI snoop log and a read-back before writing to a real pack.

**The customer app (AEtools) never uses anything in this file.** It stays read-only (see CLAUDE.md).

## Frames

Same framing and checksum as the read requests: checksum = `0x10000 − sum(bytes from register/command to last data byte)`, 16-bit big-endian.

| Step | Bytes | Notes |
|---|---|---|
| Probe chip type | `DD A5 00 00 00 00 77` | Reply byte 5 = chip type. `0` → old protocol (registers below). Non-zero → new protocol. |
| Enter settings ("factory") mode | `DD 5A 00 02 56 78 FF 30 77` | Reply status (byte 2) must be `00`. |
| Read a setting, old protocol | `DD A5 <reg> 00 <chk> 77` | Only valid inside factory mode. Reply `DD <reg> 00 <len> <data> <chk> 77`. |
| Write a setting, old protocol | `DD 5A <reg> 02 <hi> <lo> <chk> 77` | Reply `DD <reg> <status> …`; status `00` = accepted. |
| Read, new protocol | `DD A5 FA 03 <addrHi> <addrLo> <count> <chk> 77` | `count` = number of 16-bit registers. |
| Write, new protocol | `DD 5A FA <len> <addrHi> <addrLo> <count> <data…> <chk> 77` | |
| Exit **and save** | `DD 5A 01 02 28 28 FF AD 77` | Only this makes the new values permanent. |
| Exit **without saving** | `DD 5A 01 02 00 00 FF FD 77` | Use on any failure. |

The JBD app sends "exit and save" even after a failed write, so a half-applied preset gets saved. Our app must
exit **without** saving on any error, then report what failed.

## Settings on the 60V card (SP17S005 Rev 4.0)

Old-protocol register / new-protocol address. Values are unsigned 16-bit big-endian unless noted.

| Card item | Old reg | New addr | Encoding |
|---|---|---|---|
| Design capacity | 0x10 | 0 | 10 mAh |
| Cycle capacity | 0x11 | 1 | 10 mAh |
| AveFullVolt | 0x12 | 2 | mV |
| AveEmptyVolt | 0x13 | 3 | mV |
| Charge high temp protect / recover | 0x18 / 0x19 | 8 / 9 | 0.1 K (`2731 + 10 × °C`) |
| Charge low temp protect / recover | 0x1A / 0x1B | 10 / 11 | 0.1 K |
| Discharge high temp protect / recover | 0x1C / 0x1D | 12 / 13 | 0.1 K |
| Discharge low temp protect / recover | 0x1E / 0x1F | 14 / 15 | 0.1 K |
| PackVoltHigh protect / recover | 0x20 / 0x21 | 16 / 17 | 10 mV |
| PackVoltLow protect / recover | 0x22 / 0x23 | 18 / 19 | 10 mV |
| CellHighVolt protect / recover | 0x24 / 0x25 | 20 / 21 | mV |
| CellLowVolt protect / recover | 0x26 / 0x27 | 22 / 23 | mV |
| Charge over current | 0x28 | 24 | 10 mA |
| Discharge over current | 0x29 | 25 | 10 mA, stored negative: `0x10000 − A × 100` |
| Balance voltage | 0x2A | 26 | mV |
| Balance accuracy | 0x2B | 27 | mV |
| Function bits (balance on, charge balance…) | 0x2D | 29 | bit flags, read-modify-write only |
| NTC enable bits | 0x2E | 30 | bit flags, read-modify-write only |
| Cell count | 0x2F | 31 | read only for us (verify = 16) |
| SOC 80 / 60 / 40 / 20 % | 0x32–0x35 | 34–37 | mV |
| HwCellHighVolt / HwCellLowVolt | 0x36 / 0x37 | 38 / 39 | mV |
| Level2 overcurrent + delay | 0x38 | 40 | board gear, bit-packed. **Never write** |
| Short circuit + delay | 0x39 | 43 | board gear. **Never write** |
| Charge temp delays (low, high) | 0x3A | 44 / 45 | old: two 1-byte values in one register (s) |
| Discharge temp delays (low, high) | 0x3B | 46 / 47 | old: packed as above |
| Pack voltage delays (low, high) | 0x3C | 48 / 49 | old: packed as above |
| Cell voltage delays (low, high) | 0x3D | 50 / 51 | old: packed as above |
| Charge OC delay, release delay | 0x3E | 52 / 53 | old: packed as above |
| Discharge OC delay, release delay | 0x3F | 54 / 55 | old: packed as above |
| SOC 90 / 70 / 50 / 30 / 10 % | 0x42–0x46 | 106–110 | mV |
| SOC 100 % | 0x47 | 111 | mV |
| MOS high temperature | (unclear) | 202 / 203 | 0.1 K. The app's old-protocol table says 0x2E / 0x2F, which clash with NTC bits and cell count; treat as new-protocol only until confirmed |

To confirm on the board: the byte order inside the packed delay registers (the app's parser reads byte 0 as
"low/first" and byte 1 as "high/second"), and the exact bits of 0x2D.

"Battery type" is not a stored setting. The JBD app shows LFP when CellHighVolt is below 4.000 V and NMC
otherwise, so the card's CellHighVolt values make it read NMC.

## Binding and password

Both are features of JBD's Bluetooth module (commands starting `FF AA`), not of the BMS chip:

- **Binding ("app key")**: the module stores a key that JBD's app derives from its own secret and the board's
  MAC. Once it is enabled, the module needs that key before it accepts settings writes. Live data reads keep
  working (the owner tested AEtools on a bound pack). We do not set, use or work around this; staff bind
  in the JBD app as the last step.
- **Password (6 characters)**: also stored in the module (`FF AA 16 …`), and only on modules that support it.
  The SP17S005 does not.

So the factory app must write and verify every setting **before** the pack is bound.
