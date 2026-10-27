package com.anantenergies.bms.factory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anantenergies.bms.factory.apply.PlannedWrite
import com.anantenergies.bms.factory.flow.Step
import com.anantenergies.bms.ui.components.AeCard
import com.anantenergies.bms.ui.components.CardTitle
import com.anantenergies.bms.ui.components.OutlineButton
import com.anantenergies.bms.ui.components.PrimaryButton
import com.anantenergies.bms.ui.theme.AeTheme
import java.util.Locale

/** Every setting with its current and new value, before anything is written. */
@Composable
fun ReviewScreen(step: Step.Review, onApply: () -> Unit, onEdit: () -> Unit) {
    val plan = step.plan
    val changes = plan.writes.count { it.changes }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                AeCard(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardTitle("Pack ${step.details.packNumber}")
                    Text(step.details.pack.label, style = AeTheme.type.bodyLarge, color = AeTheme.colors.textPrimary)
                    Text(
                        "${plan.pack.preset.name} · ${plan.pack.preset.cardRevision} · tested %.2f Ah".format(Locale.ROOT, plan.testedAh),
                        style = AeTheme.type.small,
                        color = AeTheme.colors.textSecondary,
                    )
                    Text("${plan.profile.id} · ${step.board.address}", style = AeTheme.type.number, color = AeTheme.colors.textSecondary)
                }
            }
            if (plan.capacityOutOfRange) {
                item { MessageCard("Tested capacity outside the expected range", listOf("Continue only if the test and pack model are right."), Tone.WARNING) }
            }
            item {
                Text(
                    "$changes of ${plan.writes.size} settings change. The current values are kept as a backup.",
                    style = AeTheme.type.body,
                    color = AeTheme.colors.textSecondary,
                )
            }
            items(plan.writes, key = { it.setting.name }) { WriteRow(it) }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Write ${plan.writes.size} settings to the BMS", onApply, Modifier.fillMaxWidth())
            OutlineButton("Change pack details", onEdit, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WriteRow(write: PlannedWrite) {
    val colors = AeTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(write.setting.label, style = AeTheme.type.small, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                write.setting.describe(write.raw),
                style = AeTheme.type.number.copy(fontWeight = if (write.changes) FontWeight.SemiBold else FontWeight.Normal),
                color = if (write.changes) colors.brand else colors.textSecondary,
            )
            if (write.changes) {
                Text("was ${write.setting.describe(write.current)}", style = AeTheme.type.numberSmall, color = colors.textSecondary)
            }
        }
    }
}
