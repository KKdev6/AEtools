package com.anantenergies.bms.factory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anantenergies.bms.factory.checklist.AutoCheck
import com.anantenergies.bms.factory.checklist.Checklist
import com.anantenergies.bms.factory.checklist.ChecklistInput
import com.anantenergies.bms.factory.checklist.ManualCheck
import com.anantenergies.bms.factory.flow.Step
import com.anantenergies.bms.ui.components.AeCard
import com.anantenergies.bms.ui.components.CardTitle
import com.anantenergies.bms.ui.components.ChipTone
import com.anantenergies.bms.ui.components.OutlineButton
import com.anantenergies.bms.ui.components.PrimaryButton
import com.anantenergies.bms.ui.components.StatusChip
import com.anantenergies.bms.ui.theme.AeTheme

/** "Before sealing the pack": automatic checks, the operator's ticks and numbers, then save. */
@Composable
fun ChecklistScreen(step: Step.Checklist, savedOperator: String, onRefresh: () -> Unit, onSave: (ChecklistInput) -> Unit) {
    val pack = step.details.pack
    val setByHand = pack.preset.setByHand
    var operator by rememberSaveable(savedOperator) { mutableStateOf(savedOperator) }
    var resistance by rememberSaveable { mutableStateOf("") }
    var delta by rememberSaveable { mutableStateOf("") }
    var ticked by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var handDone by rememberSaveable { mutableStateOf(emptyList<String>()) }

    val input = ChecklistInput(
        operator = operator,
        resistanceMilliohm = resistance.replace(',', '.').toDoubleOrNull(),
        endOfChargeDeltaMv = delta.toIntOrNull(),
        ticked = ManualCheck.entries.filter { it.name in ticked }.toSet(),
        setByHandDone = handDone.toSet(),
    )
    val problems = Checklist.problems(step.auto, input, setByHand)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AeCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle("Checked by the app")
            step.auto.forEach { AutoRow(it) }
            Text(
                "Live cell delta: " + (step.live?.deltaMillivolts?.let { "$it mV" } ?: "not read"),
                style = AeTheme.type.number,
                color = AeTheme.colors.textSecondary,
            )
            OutlineButton("Read the BMS again", onRefresh, height = 36.dp)
        }

        AeCard(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle("Ticked by you")
            ManualCheck.entries.forEach { check ->
                Tick(check.label(pack), check.name in ticked) { on -> ticked = if (on) ticked + check.name else ticked - check.name }
            }
            setByHand.forEach { item ->
                Tick("Set in the JBD app: $item", item in handDone) { on -> handDone = if (on) handDone + item else handDone - item }
            }
        }

        OutlinedTextField(operator, { operator = it }, label = { Text("Operator") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            resistance,
            { resistance = it },
            label = { Text("Pack resistance (mΩ)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            delta,
            { delta = it.filter(Char::isDigit) },
            label = { Text("End-of-charge cell delta (mV)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        if (problems.isEmpty()) {
            MessageCard("Ready: this pack passes", listOf("Saving adds it to the log sheet."), Tone.OK)
        } else {
            MessageCard("Not passing yet", problems, Tone.WARNING)
        }
        PrimaryButton(
            if (problems.isEmpty()) "Save to the log sheet" else "Save as FAILED to the log sheet",
            { onSave(input) },
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AutoRow(check: AutoCheck) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(check.label, style = AeTheme.type.body, color = AeTheme.colors.textPrimary)
            Text(check.detail, style = AeTheme.type.small, color = AeTheme.colors.textSecondary)
        }
        StatusChip(if (check.passed) "OK" else "Check", if (check.passed) ChipTone.OK else ChipTone.ERROR)
    }
}

@Composable
private fun Tick(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = AeTheme.colors.brand, uncheckedColor = AeTheme.colors.borderStrong),
        )
        Text(label, style = AeTheme.type.body, color = AeTheme.colors.textPrimary)
    }
}
