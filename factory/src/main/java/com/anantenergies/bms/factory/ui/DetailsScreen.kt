package com.anantenergies.bms.factory.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anantenergies.bms.factory.apply.WritePlan
import com.anantenergies.bms.factory.flow.PackDetails
import com.anantenergies.bms.factory.flow.Step
import com.anantenergies.bms.factory.model.PackModel
import com.anantenergies.bms.ui.components.AeCard
import com.anantenergies.bms.ui.components.CardTitle
import com.anantenergies.bms.ui.components.OutlineButton
import com.anantenergies.bms.ui.components.PrimaryButton
import com.anantenergies.bms.ui.theme.AeShapes
import com.anantenergies.bms.ui.theme.AeTheme

/** Pack model, pack number and tested capacity. */
@Composable
fun DetailsScreen(step: Step.EnterDetails, onReview: (PackDetails) -> Unit, onCancel: () -> Unit) {
    var pack by rememberSaveable { mutableStateOf(step.previous?.pack) }
    var packNumber by rememberSaveable { mutableStateOf(step.previous?.packNumber.orEmpty()) }
    var testedText by rememberSaveable { mutableStateOf(step.previous?.testedAh?.toString().orEmpty()) }
    val tested = testedText.replace(',', '.').toDoubleOrNull()
    val plausible = tested != null && tested in WritePlan.PLAUSIBLE_AH
    val outOfRange = plausible && pack != null && tested!! !in pack!!.expectedAh
    val ready = pack != null && packNumber.isNotBlank() && plausible

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AeCard(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle("BMS")
            Text("${step.profile.id} · ${step.profile.cellCount}S ${step.profile.chemistry}", style = AeTheme.type.bodyLarge, color = AeTheme.colors.textPrimary)
            Text("${step.boardName} · ${step.board.address}", style = AeTheme.type.number, color = AeTheme.colors.textSecondary)
        }

        CardTitle("Pack model")
        step.profile.let { profile ->
            PackModel.entries.filter { it.preset.model == profile }.forEach { option ->
                val selected = option == pack
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (selected) AeTheme.colors.brand else AeTheme.colors.border, AeShapes.small)
                        .clickable { pack = option }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(option.label, style = AeTheme.type.bodyLarge, color = if (selected) AeTheme.colors.brand else AeTheme.colors.textPrimary)
                    Text(
                        "${option.preset.name} · ${option.preset.cardRevision} · expect ${option.expectedAh.start}–${option.expectedAh.endInclusive} Ah",
                        style = AeTheme.type.small,
                        color = AeTheme.colors.textSecondary,
                    )
                }
            }
        }

        OutlinedTextField(
            value = packNumber,
            onValueChange = { packNumber = it.trim() },
            label = { Text("Pack number") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = testedText,
            onValueChange = { testedText = it },
            label = { Text("Tested capacity (Ah, DSF40 discharge)") },
            singleLine = true,
            isError = testedText.isNotBlank() && !plausible,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        if (testedText.isNotBlank() && !plausible) {
            Text("Enter the Ah this pack gave on test, e.g. 28.5.", style = AeTheme.type.small, color = AeTheme.colors.error)
        }
        if (outOfRange) {
            MessageCard(
                "Outside the expected range",
                listOf("${pack!!.label} should test at ${pack!!.expectedAh.start}–${pack!!.expectedAh.endInclusive} Ah. Check the test and the pack model before going on."),
                Tone.WARNING,
            )
        }

        PrimaryButton(
            text = if (outOfRange) "Read BMS and review anyway" else "Read BMS and review",
            onClick = { if (ready) onReview(PackDetails(pack!!, packNumber, tested!!)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!ready) {
            Text("Choose the pack model and enter the pack number and tested capacity.", style = AeTheme.type.small, color = AeTheme.colors.textSecondary)
        }
        OutlineButton("Choose another BMS", onCancel, Modifier.fillMaxWidth())
    }
}
