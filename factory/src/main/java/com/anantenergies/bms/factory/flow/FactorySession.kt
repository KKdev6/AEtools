package com.anantenergies.bms.factory.flow

import com.anantenergies.bms.ble.GattLink
import com.anantenergies.bms.ble.GattLinkFactory
import com.anantenergies.bms.dev.TrafficLog
import com.anantenergies.bms.drivers.jbd.JbdBrand
import com.anantenergies.bms.factory.apply.ApplyResult
import com.anantenergies.bms.factory.apply.BoardIdentity
import com.anantenergies.bms.factory.apply.PresetApplier
import com.anantenergies.bms.factory.apply.WritePlan
import com.anantenergies.bms.factory.checklist.AutoCheck
import com.anantenergies.bms.factory.checklist.Checklist
import com.anantenergies.bms.factory.checklist.ChecklistInput
import com.anantenergies.bms.factory.checklist.LiveReading
import com.anantenergies.bms.factory.checklist.PackLogEntry
import com.anantenergies.bms.factory.model.BmsModelProfile
import com.anantenergies.bms.factory.model.PackModel
import com.anantenergies.bms.factory.protocol.GattBoardChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The board being worked on. */
data class Board(val name: String, val address: String)

/** Pack details typed in by the operator. */
data class PackDetails(val pack: PackModel, val packNumber: String, val testedAh: Double)

/** Where one pack is in the apply flow (CLAUDE.md, Factory app, Apply flow). */
sealed interface Step {
    data object ChooseBoard : Step

    data class Connecting(val board: Board) : Step

    /** Couldn't connect, or lost the link outside an apply. */
    data class ConnectFailed(val board: Board, val message: String) : Step

    /** A board the app doesn't know: nothing is written. */
    data class UnknownBoard(val board: Board, val identity: BoardIdentity) : Step

    /** [previous] holds what was typed before, when coming back from the review. */
    data class EnterDetails(val board: Board, val profile: BmsModelProfile, val boardName: String, val previous: PackDetails? = null) : Step

    data class Reading(val board: Board) : Step

    data class Review(val board: Board, val details: PackDetails, val plan: WritePlan) : Step

    data class Applying(val board: Board, val details: PackDetails, val plan: WritePlan, val done: Int, val total: Int) : Step

    data class Applied(val board: Board, val details: PackDetails, val plan: WritePlan, val result: ApplyResult) : Step

    /** Waiting for the operator to power-cycle the BMS. */
    data class PowerCycle(val board: Board, val details: PackDetails, val plan: WritePlan) : Step

    data class Verifying(val board: Board, val details: PackDetails, val plan: WritePlan) : Step

    /** Read back after the power-cycle. [result] is null when the board couldn't be reached again. */
    data class Verified(val board: Board, val details: PackDetails, val plan: WritePlan, val result: ApplyResult?) : Step

    /** After the pack passed: the automatic checks on a live reading, then the operator's ticks. */
    data class Checklist(
        val board: Board,
        val details: PackDetails,
        val plan: WritePlan,
        val verified: ApplyResult.Applied,
        val live: LiveReading?,
        val auto: List<AutoCheck>,
    ) : Step

    /** Written to the log sheet. */
    data class Logged(val board: Board, val details: PackDetails, val entry: PackLogEntry) : Step
}

/** Where finished packs go (the log sheet file). */
fun interface PackLogSink {
    fun append(entry: PackLogEntry)
}

/**
 * Drives one pack through connect → identify → details → review → apply → power-cycle → verify.
 * Each action is a suspend function; the screen calls one at a time and shows [step].
 */
class FactorySession(
    private val scope: CoroutineScope,
    private val links: GattLinkFactory,
    private val log: TrafficLog?,
    /** Called when the operator is asked to power-cycle [address] (the simulated board restarts). */
    private val onPowerCycle: (address: String) -> Unit = {},
    private val logSheet: PackLogSink = PackLogSink {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _step = MutableStateFlow<Step>(Step.ChooseBoard)
    val step: StateFlow<Step> = _step.asStateFlow()

    private var link: GattLink? = null
    private var channel: GattBoardChannel? = null
    private var applier: PresetApplier? = null
    private var boardName = ""

    suspend fun connect(board: Board) {
        _step.value = Step.Connecting(board)
        val identity = open(board.address, attempts = 1)
        _step.value = when {
            identity == null -> Step.ConnectFailed(board, "Couldn't connect. Wake the BMS (connect a load or charger) and try again.")
            identity.profile == null -> {
                close()
                Step.UnknownBoard(board, identity)
            }
            else -> {
                boardName = identity.boardName.orEmpty()
                Step.EnterDetails(board, identity.profile, boardName)
            }
        }
    }

    /** Reads every current value (the backup) and builds the plan for [details]. */
    suspend fun review(details: PackDetails) {
        val current = _step.value as? Step.EnterDetails ?: return
        _step.value = Step.Reading(current.board)
        val backup = applier?.readAll(current.profile)
        if (backup == null) {
            close()
            _step.value = Step.ConnectFailed(current.board, "Couldn't read the BMS settings. Nothing was written. Tap Log to see which read failed.")
            return
        }
        val plan = WritePlan.create(current.profile, details.pack, details.testedAh, backup)
        log?.info("Plan: ${details.pack.label}, ${plan.writes.count { it.changes }} of ${plan.writes.size} settings change")
        _step.value = Step.Review(current.board, details, plan)
    }

    /** Back from the review to the details, keeping the connection. */
    fun editDetails() {
        val current = _step.value as? Step.Review ?: return
        _step.value = Step.EnterDetails(current.board, current.plan.profile, boardName, previous = current.details)
    }

    suspend fun apply() {
        val current = _step.value as? Step.Review ?: return
        val applier = applier ?: return
        val result = applier.apply(current.plan) { done, total ->
            _step.value = Step.Applying(current.board, current.details, current.plan, done, total)
        }
        log?.info("Apply result: $result")
        _step.value = Step.Applied(current.board, current.details, current.plan, result)
    }

    /** After an unconfirmed save: reads everything back on the same connection to see what stuck. */
    suspend fun readBack() {
        val current = _step.value as? Step.Applied ?: return
        val result = applier?.verify(current.plan) ?: return
        _step.value = current.copy(result = result)
    }

    /** Disconnects and asks the operator to power-cycle the BMS. */
    fun startPowerCycle() {
        val current = _step.value as? Step.Applied ?: return
        close()
        onPowerCycle(current.board.address)
        _step.value = Step.PowerCycle(current.board, current.details, current.plan)
    }

    /** Reconnects after the power-cycle (the BMS may still be starting) and reads everything back. */
    suspend fun verifyAfterPowerCycle() {
        val current = (_step.value as? Step.PowerCycle)?.let { Triple(it.board, it.details, it.plan) }
            ?: (_step.value as? Step.Verified)?.takeIf { it.result !is ApplyResult.Applied }?.let { Triple(it.board, it.details, it.plan) }
            ?: return
        val (board, details, plan) = current
        _step.value = Step.Verifying(board, details, plan)
        val identity = open(board.address, attempts = RECONNECT_ATTEMPTS)
        val result = if (identity?.profile == plan.profile) applier?.verify(plan) else null
        log?.info("Verify after power-cycle: $result")
        _step.value = Step.Verified(board, details, plan, result)
    }

    /** After a pass: reads live cells and temperatures and runs the automatic checks. */
    suspend fun startChecklist() {
        val current = _step.value as? Step.Verified ?: return
        val verified = (current.result as? ApplyResult.Applied)?.takeIf { it.passed } ?: return
        _step.value = checklist(current.board, current.details, current.plan, verified)
    }

    /** Reads the live values again, e.g. after the operator fixed a sensor. */
    suspend fun refreshChecklist() {
        val current = _step.value as? Step.Checklist ?: return
        _step.value = checklist(current.board, current.details, current.plan, current.verified)
    }

    /** Saves the pack to the log sheet, passed or not. */
    fun saveChecklist(input: ChecklistInput) {
        val current = _step.value as? Step.Checklist ?: return
        val preset = current.plan.pack.preset
        val problems = Checklist.problems(current.auto, input, preset.setByHand)
        val entry = PackLogEntry(
            time = clock(),
            operator = input.operator.trim(),
            packNumber = current.details.packNumber,
            packModel = current.details.pack.label,
            bmsModel = boardName.ifBlank { current.plan.profile.id },
            bmsAddress = current.board.address,
            preset = preset.name,
            cardRevision = preset.cardRevision,
            testedAh = current.details.testedAh,
            resistanceMilliohm = input.resistanceMilliohm,
            endOfChargeDeltaMv = input.endOfChargeDeltaMv,
            liveDeltaMv = current.live?.deltaMillivolts,
            auto = current.auto,
            ticked = input.ticked,
            setByHand = preset.setByHand,
            setByHandDone = input.setByHandDone,
            passed = problems.isEmpty(),
            problems = problems,
            readBack = current.verified.readBack,
        )
        logSheet.append(entry)
        log?.info("Logged pack ${entry.packNumber}: ${if (entry.passed) "PASS" else "FAIL"} $problems")
        _step.value = Step.Logged(current.board, current.details, entry)
    }

    private suspend fun checklist(board: Board, details: PackDetails, plan: WritePlan, verified: ApplyResult.Applied): Step.Checklist {
        val live = applier?.readLive()
        return Step.Checklist(board, details, plan, verified, live, Checklist.automatic(plan.profile, verified, live))
    }

    /** Starts again from a fresh read of the same board, e.g. after a failed apply. */
    suspend fun retry() {
        val board = when (val current = _step.value) {
            is Step.Applied -> current.board
            is Step.Verified -> current.board
            is Step.ConnectFailed -> current.board
            else -> return
        }
        close()
        connect(board)
    }

    /** Disconnects and goes back to the board list. */
    fun reset() {
        close()
        _step.value = Step.ChooseBoard
    }

    private suspend fun open(address: String, attempts: Int): BoardIdentity? {
        close()
        repeat(attempts) { attempt ->
            if (attempt > 0) delay(RECONNECT_GAP_MS)
            val link = links.create(address, JbdBrand.PROFILE)
            try {
                link.connect()
                val channel = GattBoardChannel(link, scope, log)
                val applier = PresetApplier(channel)
                val identity = applier.identify()
                log?.info("Board: ${identity.boardName}, chip type ${identity.chipType}, profile ${identity.profile?.id}")
                this.link = link
                this.channel = channel
                this.applier = applier
                if (identity.boardName != null) return identity
                close()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log?.error("Connect failed: ${e.message}")
                link.close()
            }
        }
        return null
    }

    private fun close() {
        channel?.close()
        link?.close()
        channel = null
        link = null
        applier = null
    }

    private companion object {
        const val RECONNECT_ATTEMPTS = 6
        const val RECONNECT_GAP_MS = 2_000L
    }
}
