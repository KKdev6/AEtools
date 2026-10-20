package com.anantenergies.bms.dev

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Raw Bluetooth traffic and connection notes, for diagnosing a real board.
 * Only debug builds create one (see DevTools); release builds log nothing.
 */
class TrafficLog(
    private val clock: () -> Long = System::currentTimeMillis,
    private val capacity: Int = 500,
) {
    enum class Kind { TX, RX, INFO, ERROR }

    data class Entry(val time: Long, val kind: Kind, val text: String)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun tx(bytes: ByteArray) = add(Kind.TX, bytes.toHex())

    fun rx(bytes: ByteArray) = add(Kind.RX, bytes.toHex())

    fun info(text: String) = add(Kind.INFO, text)

    fun error(text: String) = add(Kind.ERROR, text)

    fun clear() {
        _entries.value = emptyList()
    }

    /** Everything as plain text, oldest first, for copying out of the app. */
    fun export(): String {
        val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
        return entries.value.joinToString("\n") { "${format.format(Date(it.time))} ${it.kind} ${it.text}" }
    }

    private fun add(kind: Kind, text: String) {
        val entry = Entry(clock(), kind, text)
        _entries.update { (it + entry).takeLast(capacity) }
    }
}

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
