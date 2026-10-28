package com.anantenergies.bms.factory.checklist

import com.anantenergies.bms.factory.model.JbdSetting
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One row of the log sheet: one pack (CLAUDE.md, Factory app, Log sheet). */
data class PackLogEntry(
    val time: Long,
    val operator: String,
    val packNumber: String,
    val packModel: String,
    val bmsModel: String,
    val bmsAddress: String,
    val preset: String,
    val cardRevision: String,
    val testedAh: Double,
    val resistanceMilliohm: Double?,
    val endOfChargeDeltaMv: Int?,
    val liveDeltaMv: Int?,
    val auto: List<AutoCheck>,
    val ticked: Set<ManualCheck>,
    val setByHand: List<String>,
    val setByHandDone: Set<String>,
    val passed: Boolean,
    val problems: List<String>,
    val readBack: Map<JbdSetting, Int>,
)

/**
 * The log sheet as CSV. The columns are fixed (every setting the app knows gets one), so files from
 * different packs and boards line up in a spreadsheet.
 */
object PackLogCsv {
    private val FIXED = listOf(
        "Date", "Operator", "Pack number", "Pack model", "BMS model", "BMS MAC", "Preset", "Card revision",
        "Tested Ah", "Resistance mΩ", "End-of-charge delta mV", "Live delta mV", "Result", "Problems",
    )
    private const val AUTO_CHECKS = 4

    val header: List<String> =
        FIXED + (1..AUTO_CHECKS).map { "Auto check $it" } + ManualCheck.entries.map { it.label } + "Set by hand" +
            JbdSetting.entries.map { it.label }

    fun row(entry: PackLogEntry): List<String> {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(entry.time))
        val auto = (0 until AUTO_CHECKS).map { i ->
            entry.auto.getOrNull(i)?.let { "${it.label}: ${if (it.passed) "OK" else "FAIL"} (${it.detail})" }.orEmpty()
        }
        return listOf(
            date, entry.operator, entry.packNumber, entry.packModel, entry.bmsModel, entry.bmsAddress, entry.preset,
            entry.cardRevision, entry.testedAh.toString(), entry.resistanceMilliohm?.toString().orEmpty(),
            entry.endOfChargeDeltaMv?.toString().orEmpty(), entry.liveDeltaMv?.toString().orEmpty(),
            if (entry.passed) "PASS" else "FAIL", entry.problems.joinToString("; "),
        ) + auto +
            ManualCheck.entries.map { if (it in entry.ticked) "yes" else "no" } +
            entry.setByHand.joinToString("; ") { "$it: ${if (it in entry.setByHandDone) "done" else "not done"}" } +
            JbdSetting.entries.map { setting -> entry.readBack[setting]?.let(setting::format).orEmpty() }
    }

    fun line(cells: List<String>): String = cells.joinToString(",") { escape(it) }

    /** Splits one line written by [line] back into cells. */
    fun parse(line: String): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { cells += cell.toString(); cell.clear() }
                else -> cell.append(c)
            }
            i++
        }
        cells += cell.toString()
        return cells
    }

    private fun escape(cell: String): String {
        val flat = cell.replace('\n', ' ').replace('\r', ' ')
        return if (flat.any { it == ',' || it == '"' }) "\"" + flat.replace("\"", "\"\"") + "\"" else flat
    }
}

/** A short line per logged pack, newest first, for the log sheet screen. */
data class LoggedPack(val date: String, val packNumber: String, val packModel: String, val result: String, val operator: String)

/** The log sheet file in app storage. Appends only: nothing in the app deletes rows. */
class PackLogStore(val file: File) {
    @Synchronized
    fun append(entry: PackLogEntry) {
        // The byte-order mark makes Excel read the file as UTF-8, so °C, mΩ and – show correctly.
        if (!file.exists() || file.length() == 0L) file.writeText(BOM + PackLogCsv.line(PackLogCsv.header) + "\n")
        file.appendText(PackLogCsv.line(PackLogCsv.row(entry)) + "\n")
    }

    private companion object {
        const val BOM = "\uFEFF"
    }

    @Synchronized
    fun packs(): List<LoggedPack> {
        if (!file.exists()) return emptyList()
        return file.readLines().drop(1).filter { it.isNotBlank() }.map(PackLogCsv::parse).map {
            LoggedPack(date = it[0], operator = it[1], packNumber = it[2], packModel = it[3], result = it[12])
        }.reversed()
    }
}
