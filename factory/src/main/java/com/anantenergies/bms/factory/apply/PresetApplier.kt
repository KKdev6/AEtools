package com.anantenergies.bms.factory.apply

import com.anantenergies.bms.drivers.jbd.JbdParser
import com.anantenergies.bms.drivers.jbd.JbdRequest
import com.anantenergies.bms.factory.model.BmsModelProfile
import com.anantenergies.bms.factory.model.BmsModels
import com.anantenergies.bms.factory.model.JbdSetting
import com.anantenergies.bms.factory.protocol.BoardChannel
import com.anantenergies.bms.factory.protocol.BoardReply
import com.anantenergies.bms.factory.protocol.JbdSettingsFrames

/** What the board says it is. [profile] is null for a board the app doesn't know. */
data class BoardIdentity(val boardName: String?, val chipType: Int?, val profile: BmsModelProfile?)

/** A setting whose read-back value isn't the one that was written. */
data class Mismatch(val setting: JbdSetting, val expected: Int, val actual: Int?)

sealed interface ApplyResult {
    /**
     * Everything was written and saved. [mismatches] lists settings that didn't stick;
     * [wrongBoardRating] is set when discharge over current read back below the board's rating.
     */
    data class Applied(
        val readBack: Map<JbdSetting, Int>,
        val mismatches: List<Mismatch>,
        val wrongBoardRating: Double?,
    ) : ApplyResult {
        val passed: Boolean get() = mismatches.isEmpty() && wrongBoardRating == null
    }

    /**
     * Stopped before saving. [saved] is false whenever the app left settings mode without saving
     * (or never entered it), so the board kept its old values.
     */
    data class Failed(val stage: Stage, val setting: JbdSetting?, val reason: String, val saved: Boolean) : ApplyResult

    enum class Stage { ENTER, WRITE, SAVE, READ_BACK }
}

/**
 * Applies a [WritePlan] to one board: settings mode, every write, then save. Any refused or
 * unanswered write leaves settings mode without saving, so a pack never gets half a preset.
 */
class PresetApplier(private val board: BoardChannel) {

    /** Probes the chip type and reads the board string (register 0x05) to find its profile. */
    suspend fun identify(): BoardIdentity {
        val chipType = (board.send(JbdSettingsFrames.probeChipType, PROBE_REPLY) as? BoardReply.Ok)
            ?.data?.takeIf { it.size >= 2 }?.let { it[1].toInt() and 0xFF }
        val name = (board.send(JbdRequest.HARDWARE.encode(), JbdRequest.HARDWARE.register) as? BoardReply.Ok)
            ?.let { JbdParser.hardwareName(it.data) }
        return BoardIdentity(name, chipType, name?.let { BmsModels.identify(it, chipType) })
    }

    /** Every writable and read-only setting of [profile], or null if any read went unanswered or was refused. */
    suspend fun readAll(profile: BmsModelProfile): Map<JbdSetting, Int>? {
        val wanted = (profile.writable + profile.readOnly).associateBy { it.address }
        val values = mutableMapOf<JbdSetting, Int>()
        for (block in profile.readBlocks) {
            val count = block.last - block.first + 1
            val reply = board.send(JbdSettingsFrames.read(block.first, count), JbdSettingsFrames.FA) as? BoardReply.Ok ?: return null
            val raw = JbdSettingsFrames.readValues(reply.data, block.first, count) ?: return null
            block.zip(raw).forEach { (address, value) -> wanted[address]?.let { values[it] = value } }
        }
        return values
    }

    /** [onProgress] gets the number of settings written so far and the total. */
    suspend fun apply(plan: WritePlan, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ApplyResult {
        if (board.send(JbdSettingsFrames.enterSettings, JbdSettingsFrames.ENTER_REPLY) !is BoardReply.Ok) {
            return ApplyResult.Failed(ApplyResult.Stage.ENTER, null, "The BMS didn't enter settings mode", saved = false)
        }
        for ((index, write) in plan.writes.withIndex()) {
            onProgress(index, plan.writes.size)
            val reply = board.send(JbdSettingsFrames.write(plan.profile, write.setting, write.raw), JbdSettingsFrames.FA)
            if (reply !is BoardReply.Ok) {
                board.send(JbdSettingsFrames.exitWithoutSaving, JbdSettingsFrames.EXIT_REPLY)
                val reason = if (reply is BoardReply.Refused) "refused (status %02X)".format(reply.status) else "not answered"
                return ApplyResult.Failed(ApplyResult.Stage.WRITE, write.setting, "${write.setting.label} was $reason", saved = false)
            }
        }
        if (board.send(JbdSettingsFrames.exitAndSave, JbdSettingsFrames.EXIT_REPLY) !is BoardReply.Ok) {
            // Every write went in, so the save may still have happened: the read-back decides.
            return ApplyResult.Failed(ApplyResult.Stage.SAVE, null, "The BMS didn't confirm the save. Read the settings back.", saved = true)
        }
        return verify(plan)
    }

    /** Reads everything back and compares it with [plan]; also used after the power-cycle. */
    suspend fun verify(plan: WritePlan): ApplyResult {
        val readBack = readAll(plan.profile)
            ?: return ApplyResult.Failed(ApplyResult.Stage.READ_BACK, null, "Couldn't read the settings back", saved = true)
        val mismatches = plan.writes.filter { readBack[it.setting] != it.raw }.map { Mismatch(it.setting, it.raw, readBack[it.setting]) }
        val discharge = readBack[JbdSetting.DISCHARGE_OVER_CURRENT]?.let { JbdSetting.DISCHARGE_OVER_CURRENT.encoding.fromRaw(it) }
        val wrongRating = discharge?.takeIf { it < plan.profile.ratedDischargeAmps - RATING_TOLERANCE_A }
        return ApplyResult.Applied(readBack, mismatches, wrongRating)
    }

    private companion object {
        /** The chip-type probe's reply has register byte 00, like the enter reply. */
        const val PROBE_REPLY = 0x00
        const val RATING_TOLERANCE_A = 0.5
    }
}
