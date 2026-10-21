package com.anantenergies.bms.qr

/** Finds a BMS's Bluetooth address in the text of the QR label on its battery pack. Pure, so it runs in JVM tests. */
object MacAddress {
    private const val HEX = "[0-9A-Fa-f]"

    /**
     * `A4:C1:38:12:34:56` or `A4-C1-38-12-34-56`: one separator throughout. In a longer run the last
     * six groups win, because in `MAC:A4:C1:…` the "AC" of "MAC" is hex too.
     */
    private val separated = Regex("(?<!$HEX)$HEX{2}([:-])(?:$HEX{2}\\1){4}$HEX{2}(?!$HEX)(?!\\1$HEX)")

    /**
     * `a4c138123456`: exactly 12 hex digits. Not when joined to more hex by a dash, so the last
     * group of a UUID in a URL isn't taken for an address.
     */
    private val bare = Regex("(?<!$HEX)(?<!$HEX-)$HEX{12}(?!$HEX)(?!-$HEX)")

    /** A URL may carry the address percent-encoded, e.g. `A4%3AC1%3A…`. */
    private val percentEncoded = Regex("%($HEX{2})")

    private const val NONE = "00:00:00:00:00:00"
    private const val BROADCAST = "FF:FF:FF:FF:FF:FF"

    /**
     * The first address in [text], upper case with colons (`A4:C1:38:12:34:56`), or null if there
     * is none. An address written with separators wins over a bare 12-digit one, which could
     * also be a serial number.
     */
    fun findIn(text: String): String? {
        val decoded = percentEncoded.replace(text) { it.groupValues[1].toInt(16).toChar().toString() }
        return (separated.findAll(decoded) + bare.findAll(decoded))
            .map { normalise(it.value) }
            .firstOrNull { it != NONE && it != BROADCAST }
    }

    private fun normalise(raw: String): String =
        raw.filter { it != ':' && it != '-' }.uppercase().chunked(2).joinToString(":")
}
