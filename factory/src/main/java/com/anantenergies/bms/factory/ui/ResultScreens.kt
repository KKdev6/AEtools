package com.anantenergies.bms.factory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anantenergies.bms.factory.apply.ApplyResult
import com.anantenergies.bms.factory.apply.Mismatch
import com.anantenergies.bms.factory.flow.Step
import com.anantenergies.bms.ui.components.OutlineButton
import com.anantenergies.bms.ui.components.PrimaryButton
import com.anantenergies.bms.ui.theme.AeTheme
import java.util.Locale

@Composable
fun ApplyingScreen(step: Step.Applying) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Text("Writing ${step.done + 1} of ${step.total}", style = AeTheme.type.headerTitle, color = AeTheme.colors.textPrimary)
        LinearProgressIndicator(
            progress = { (step.done + 1).toFloat() / step.total },
            color = AeTheme.colors.brand,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Keep the phone close and don't switch off the BMS. Nothing is saved until every setting is written.",
            style = AeTheme.type.body,
            color = AeTheme.colors.textSecondary,
        )
    }
}

/** Straight after writing: the first read-back, or why it stopped. */
@Composable
fun AppliedScreen(step: Step.Applied, onPowerCycle: () -> Unit, onReadBack: () -> Unit, onRetry: () -> Unit, onDone: () -> Unit) {
    ResultColumn {
        when (val result = step.result) {
            is ApplyResult.Applied -> {
                ReadBackSummary(result, total = step.plan.writes.size, afterRestart = false)
                if (result.passed) {
                    PrimaryButton("Next: power-cycle the BMS", onPowerCycle, Modifier.fillMaxWidth())
                } else {
                    OutlineButton("Read the BMS again and retry", onRetry, Modifier.fillMaxWidth())
                }
            }
            is ApplyResult.Failed -> {
                FailureCard(result)
                if (result.stage == ApplyResult.Stage.SAVE) {
                    PrimaryButton("Read the settings back", onReadBack, Modifier.fillMaxWidth())
                } else {
                    PrimaryButton("Try again", onRetry, Modifier.fillMaxWidth())
                }
            }
        }
        OutlineButton("Start over with another BMS", onDone, Modifier.fillMaxWidth())
    }
}

@Composable
fun PowerCycleScreen(onVerify: () -> Unit) {
    ResultColumn {
        MessageCard(
            "Power-cycle the BMS",
            listOf(
                "1. Disconnect the BMS from the battery (or unplug J1) for 10 seconds.",
                "2. Connect it again and wake it (load or charger).",
                "3. Tap the button below. The app reconnects and reads every setting again.",
            ),
            Tone.NEUTRAL,
        )
        PrimaryButton("BMS restarted: check the settings", onVerify, Modifier.fillMaxWidth())
    }
}

/** After the power-cycle: the final verdict for this pack. */
@Composable
fun VerifiedScreen(step: Step.Verified, onRetryVerify: () -> Unit, onRetry: () -> Unit, onChecklist: () -> Unit, onDone: () -> Unit) {
    ResultColumn {
        val result = step.result
        when {
            result == null -> {
                MessageCard(
                    "Couldn't reach the BMS",
                    listOf("Make sure it is connected and awake, then try again."),
                    Tone.WARNING,
                )
                PrimaryButton("Try again", onRetryVerify, Modifier.fillMaxWidth())
            }
            result is ApplyResult.Applied -> {
                ReadBackSummary(result, total = step.plan.writes.size, afterRestart = true)
                if (result.passed) {
                    MessageCard(
                        "Pack ${step.details.packNumber}: settings verified",
                        listOf("Next: the checks before sealing the pack."),
                        Tone.OK,
                    )
                    PrimaryButton("Next: checklist", onChecklist, Modifier.fillMaxWidth())
                } else {
                    OutlineButton("Read the BMS again and retry", onRetry, Modifier.fillMaxWidth())
                }
            }
            result is ApplyResult.Failed -> {
                FailureCard(result)
                PrimaryButton("Try again", onRetryVerify, Modifier.fillMaxWidth())
            }
        }
        if (result !is ApplyResult.Applied || !result.passed) {
            OutlineButton("Start over with another BMS", onDone, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ReadBackSummary(result: ApplyResult.Applied, total: Int, afterRestart: Boolean) {
    val rating = result.wrongBoardRating
    if (rating != null) {
        MessageCard(
            "This is a %.0f A board. Do not ship.".format(Locale.ROOT, rating),
            listOf("Discharge over current reads back as %.0f A instead of 60 A.".format(Locale.ROOT, rating)),
            Tone.ERROR,
        )
    }
    if (result.mismatches.isEmpty()) {
        MessageCard(
            if (afterRestart) "All $total settings still correct after the restart" else "All $total settings written and read back",
            emptyList(),
            Tone.OK,
        )
    } else {
        MessageCard(
            "${result.mismatches.size} setting(s) didn't stick. Do not ship.",
            result.mismatches.map { it.line() },
            Tone.ERROR,
        )
    }
}

@Composable
private fun FailureCard(result: ApplyResult.Failed) {
    val lines = buildList {
        add(result.reason + ".")
        if (!result.saved) {
            add("Nothing was saved: the BMS kept its old settings.")
            add("If the link dropped, power-cycle the BMS before trying again.")
        }
    }
    MessageCard("Stopped", lines, Tone.ERROR)
}

private fun Mismatch.line() = "${setting.label}: should be ${setting.describe(expected)}, reads ${setting.describe(actual)}"

@Composable
private fun ResultColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}
