package com.anantenergies.bms.factory.model

/** How a board's settings are protected on the pack itself. */
enum class SettingsLock {
    /** Nothing on the pack refuses writes (binding is only on JBD's server). */
    NONE,

    /** The Bluetooth module checks a password or key before accepting writes. */
    MODULE_PASSWORD,
}

/**
 * One BMS model the factory app knows. A preset can only be applied to the model it was written
 * for, and only [writable] settings can ever be written to it.
 */
data class BmsModelProfile(
    val id: String,
    /** The board string from register 0x05 starts with this, e.g. `SP17S005P17S50A`. */
    val boardPrefix: String,
    /** The reply byte of the chip-type probe; `1` = new (`FA`) protocol. */
    val chipType: Int,
    val writable: Set<JbdSetting>,
    /** Read and shown, never written. */
    val readOnly: Set<JbdSetting>,
    val ratedDischargeAmps: Double,
    val lock: SettingsLock,
    val cellCount: Int,
    val chemistry: String,
) {
    init {
        require(writable.none { it in readOnly }) { "$id: a setting can't be both writable and read only" }
        require(writable.none { it in NEVER_WRITTEN }) { "$id: ${writable.filter { it in NEVER_WRITTEN }} must never be written" }
    }

    fun matches(boardName: String, probedChipType: Int?): Boolean =
        boardName.trim().startsWith(boardPrefix) && probedChipType == chipType

    companion object {
        /** No profile may ever make these writable (CLAUDE.md, Factory app). */
        val NEVER_WRITTEN = setOf(
            JbdSetting.CELL_COUNT,
            JbdSetting.LEVEL2_OVER_CURRENT,
            JbdSetting.SHORT_CIRCUIT,
            JbdSetting.NTC_ENABLE,
        )
    }
}

object BmsModels {
    /** JBD SP17S005 (board string `SP17S005P17S50A`), 16S NMC, 60 A. No on-pack lock. */
    val SP17S005 = BmsModelProfile(
        id = "SP17S005",
        boardPrefix = "SP17S005",
        chipType = 1,
        writable = JbdSetting.entries.toSet() - BmsModelProfile.NEVER_WRITTEN,
        readOnly = BmsModelProfile.NEVER_WRITTEN,
        ratedDischargeAmps = 60.0,
        lock = SettingsLock.NONE,
        cellCount = 16,
        chemistry = "NMC",
    )

    val all = listOf(SP17S005)

    /** The profile for a board, or null when the app doesn't know it (then nothing is written). */
    fun identify(boardName: String, chipType: Int?): BmsModelProfile? = all.firstOrNull { it.matches(boardName, chipType) }
}
