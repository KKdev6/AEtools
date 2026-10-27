package com.anantenergies.bms.factory.protocol

import com.anantenergies.bms.ble.GattLink
import com.anantenergies.bms.dev.TrafficLog
import com.anantenergies.bms.dev.toHex
import com.anantenergies.bms.drivers.jbd.JbdFrameAssembler
import com.anantenergies.bms.drivers.jbd.JbdFrameEvent
import com.anantenergies.bms.drivers.jbd.JbdRejection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** What the board said to one frame. */
sealed interface BoardReply {
    /** Status 00; [data] is the payload. */
    class Ok(val data: ByteArray) : BoardReply

    /** The board answered with an error status. */
    class Refused(val status: Int) : BoardReply

    /** No valid reply, even after the retry. */
    data object NoReply : BoardReply
}

/** Sends one frame at a time to a board and waits for its reply. */
fun interface BoardChannel {
    /** Sends [frame] and waits for the reply whose register byte is [replyRegister]. */
    suspend fun send(frame: ByteArray, replyRegister: Int): BoardReply
}

/**
 * A [BoardChannel] over one open Bluetooth link: like the customer app's JBD queue (1 s timeout,
 * then one retry), but it can send the factory app's settings frames and reports refusals.
 */
class GattBoardChannel(private val link: GattLink, scope: CoroutineScope, private val log: TrafficLog?) : BoardChannel {
    private val assembler = JbdFrameAssembler()
    private val assemblerLock = Mutex()
    private val replies = Channel<Pair<Int, BoardReply>>(Channel.UNLIMITED)
    private val queue = Mutex()

    // Started undispatched so it is listening before the first frame goes out.
    private val reader = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        link.chunks.collect { chunk ->
            log?.rx(chunk)
            val events = assemblerLock.withLock { assembler.feed(chunk) }
            for (event in events) {
                when (event) {
                    is JbdFrameEvent.Frame -> replies.trySend(event.response.register to BoardReply.Ok(event.response.data))
                    is JbdFrameEvent.Rejected ->
                        if (event.reason == JbdRejection.ERROR_STATUS) {
                            val register = event.bytes[1].toInt() and 0xFF
                            val status = event.bytes[2].toInt() and 0xFF
                            log?.error("Refused (status %02X): %s".format(status, event.bytes.toHex()))
                            replies.trySend(register to BoardReply.Refused(status))
                        } else {
                            log?.error("Discarded (${event.reason}): ${event.bytes.toHex()}")
                        }
                }
            }
        }
    }

    override suspend fun send(frame: ByteArray, replyRegister: Int): BoardReply = queue.withLock {
        repeat(ATTEMPTS) { attempt ->
            // Forget any half-received or late reply to an earlier frame.
            assemblerLock.withLock { assembler.reset() }
            while (replies.tryReceive().isSuccess) Unit

            log?.tx(frame)
            try {
                link.write(frame)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log?.error("Write failed: ${e.message}")
                return BoardReply.NoReply
            }
            val reply = withTimeoutOrNull(TIMEOUT_MS) {
                var (register, reply) = replies.receive()
                while (register != replyRegister) {
                    val next = replies.receive()
                    register = next.first
                    reply = next.second
                }
                reply
            }
            if (reply != null) return reply
            log?.error("No reply to ${frame.toHex()}" + if (attempt == 0) ", retrying" else "")
        }
        BoardReply.NoReply
    }

    fun close() {
        reader.cancel()
    }

    companion object {
        const val TIMEOUT_MS = 1_000L
        const val ATTEMPTS = 2
    }
}
