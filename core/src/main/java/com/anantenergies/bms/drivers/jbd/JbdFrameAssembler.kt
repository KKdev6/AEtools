package com.anantenergies.bms.drivers.jbd

/** Why bytes from the BMS were thrown away. */
enum class JbdRejection {
    /** Bytes before a frame start (`DD`). */
    GARBAGE,

    /** A `DD` whose frame didn't end in `77`, so it wasn't a real start. */
    BAD_END,
    BAD_CHECKSUM,

    /** The BMS answered with a status other than 00. */
    ERROR_STATUS,
}

sealed interface JbdFrameEvent {
    class Frame(val response: JbdResponse) : JbdFrameEvent

    /** [bytes] are exactly the bytes that were discarded. */
    class Rejected(val reason: JbdRejection, val bytes: ByteArray) : JbdFrameEvent
}

/**
 * Rebuilds whole replies from BLE notifications, which arrive in pieces of about 20 bytes.
 *
 * Feed it every notification in order. It returns each frame the chunk completed, and throws away
 * anything that doesn't check out, resynchronising on the next `DD`.
 */
class JbdFrameAssembler {
    private var buffer = ByteArray(0)

    fun feed(chunk: ByteArray): List<JbdFrameEvent> {
        buffer += chunk
        val events = mutableListOf<JbdFrameEvent>()
        while (buffer.isNotEmpty()) {
            val start = buffer.indexOfFirst { (it.toInt() and 0xFF) == JbdFrame.START }
            if (start != 0) {
                val end = if (start < 0) buffer.size else start
                events += JbdFrameEvent.Rejected(JbdRejection.GARBAGE, buffer.copyOfRange(0, end))
                buffer = buffer.copyOfRange(end, buffer.size)
                continue
            }
            if (buffer.size < JbdFrame.HEADER_SIZE) break

            val length = buffer[3].toInt() and 0xFF
            val total = JbdFrame.HEADER_SIZE + length + JbdFrame.TRAILER_SIZE
            if (buffer.size < total) break

            if ((buffer[total - 1].toInt() and 0xFF) != JbdFrame.END) {
                // Not a real frame start: drop the DD and look for the next one.
                events += JbdFrameEvent.Rejected(JbdRejection.BAD_END, buffer.copyOfRange(0, 1))
                buffer = buffer.copyOfRange(1, buffer.size)
                continue
            }

            val frame = buffer.copyOfRange(0, total)
            buffer = buffer.copyOfRange(total, buffer.size)
            val dataEnd = JbdFrame.HEADER_SIZE + length
            val expected = JbdFrame.checksum(frame, from = 2, until = dataEnd)
            val actual = ((frame[dataEnd].toInt() and 0xFF) shl 8) or (frame[dataEnd + 1].toInt() and 0xFF)
            events += when {
                actual != expected -> JbdFrameEvent.Rejected(JbdRejection.BAD_CHECKSUM, frame)
                (frame[2].toInt() and 0xFF) != JbdFrame.STATUS_OK -> JbdFrameEvent.Rejected(JbdRejection.ERROR_STATUS, frame)
                else -> JbdFrameEvent.Frame(
                    JbdResponse(register = frame[1].toInt() and 0xFF, data = frame.copyOfRange(JbdFrame.HEADER_SIZE, dataEnd)),
                )
            }
        }
        return events
    }

    /** Drops any partial frame, e.g. after a request timed out. */
    fun reset() {
        buffer = ByteArray(0)
    }
}
