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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anantenergies.bms.factory.checklist.LoggedPack
import com.anantenergies.bms.factory.flow.Step
import com.anantenergies.bms.ui.components.AeCard
import com.anantenergies.bms.ui.components.ChipTone
import com.anantenergies.bms.ui.components.OutlineButton
import com.anantenergies.bms.ui.components.PrimaryButton
import com.anantenergies.bms.ui.components.StatusChip
import com.anantenergies.bms.ui.theme.AeTheme

/** After saving: the pack's result and what to do next. */
@Composable
fun LoggedScreen(step: Step.Logged, onNext: () -> Unit, onShare: () -> Unit) {
    val entry = step.entry
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (entry.passed) {
            MessageCard("Pack ${entry.packNumber}: PASS", listOf("Saved to the log sheet.", "Now bind this pack in the JBD app."), Tone.OK)
        } else {
            MessageCard("Pack ${entry.packNumber}: FAIL. Do not ship.", listOf("Saved to the log sheet with these problems:") + entry.problems, Tone.ERROR)
        }
        PrimaryButton("Next pack", onNext, Modifier.fillMaxWidth())
        OutlineButton("Share the log sheet (CSV)", onShare, Modifier.fillMaxWidth())
    }
}

/** Every pack on this phone's log sheet, newest first. */
@Composable
fun LogSheetScreen(packs: List<LoggedPack>, onShare: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${packs.size} pack(s) · ${packs.count { it.result == "PASS" }} passed",
                style = AeTheme.type.body,
                color = AeTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            OutlineButton("Share CSV", onShare, height = 36.dp)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (packs.isEmpty()) {
                item { Text("No packs logged yet.", style = AeTheme.type.body, color = AeTheme.colors.textSecondary) }
            }
            items(packs) { pack ->
                AeCard(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pack.packNumber, style = AeTheme.type.bodyLarge, color = AeTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                        StatusChip(pack.result, if (pack.result == "PASS") ChipTone.OK else ChipTone.ERROR)
                    }
                    Text("${pack.packModel} · ${pack.date} · ${pack.operator}", style = AeTheme.type.small, color = AeTheme.colors.textSecondary)
                }
            }
        }
    }
}
