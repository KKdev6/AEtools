package com.anantenergies.bms.factory.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anantenergies.bms.factory.flow.Step
import com.anantenergies.bms.ui.components.OutlineButton
import com.anantenergies.bms.ui.components.PrimaryButton
import com.anantenergies.bms.ui.theme.AeTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

/** One screen per step of the apply flow, under a shared top bar. */
@Composable
fun FactoryApp(vm: FactoryViewModel, onRequestPermissions: () -> Unit, onEnableBluetooth: () -> Unit) {
    val step by vm.step.collectAsStateWithLifecycle()
    var showLog by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showLog) { showLog = false }
    BackHandler(enabled = !showLog && step.canGoBack()) { vm.reset() }

    Column(Modifier.fillMaxSize().background(AeTheme.colors.background).safeDrawingPadding()) {
        TopBar(subtitle = step.title(), logOpen = showLog, onLog = { showLog = !showLog })
        Box(Modifier.weight(1f)) {
            if (showLog) {
                LogScreen(vm.log)
            } else {
                StepScreen(step, vm, onRequestPermissions, onEnableBluetooth)
            }
        }
    }
}

@Composable
private fun StepScreen(step: Step, vm: FactoryViewModel, onRequestPermissions: () -> Unit, onEnableBluetooth: () -> Unit) {
    when (step) {
        Step.ChooseBoard -> ChooseBoardScreen(vm, onRequestPermissions, onEnableBluetooth)
        is Step.Connecting -> Busy("Connecting to ${step.board.name}", "Checking which BMS this is. Nothing is written yet.")
        is Step.ConnectFailed -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MessageCard("Couldn't connect", listOf(step.message), Tone.WARNING)
            PrimaryButton("Try again", vm::retry, Modifier.fillMaxWidth())
            OutlineButton("Choose another BMS", vm::reset, Modifier.fillMaxWidth())
        }
        is Step.UnknownBoard -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MessageCard(
                "Not a BMS this app knows",
                listOf(
                    "Board: ${step.identity.boardName ?: "didn't answer"} (chip type ${step.identity.chipType ?: "unknown"}).",
                    "Nothing was written. A new BMS model needs its own profile and settings card first.",
                ),
                Tone.WARNING,
            )
            OutlineButton("Choose another BMS", vm::reset, Modifier.fillMaxWidth())
        }
        is Step.EnterDetails -> DetailsScreen(step, onReview = vm::review, onCancel = vm::reset)
        is Step.Reading -> Busy("Reading the BMS", "Saving a backup of every current setting. Nothing is written yet.")
        is Step.Review -> ReviewScreen(step, onApply = vm::apply, onEdit = vm::editDetails)
        is Step.Applying -> ApplyingScreen(step)
        is Step.Applied -> AppliedScreen(step, onPowerCycle = vm::startPowerCycle, onReadBack = vm::readBack, onRetry = vm::retry, onDone = vm::reset)
        is Step.PowerCycle -> PowerCycleScreen(onVerify = vm::verify)
        is Step.Verifying -> Busy("Checking after the restart", "Reconnecting and reading every setting again.")
        is Step.Verified -> VerifiedScreen(step, onRetryVerify = vm::verify, onRetry = vm::retry, onDone = vm::reset)
    }
}
