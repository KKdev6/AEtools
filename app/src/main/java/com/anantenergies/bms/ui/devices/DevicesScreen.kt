package com.anantenergies.bms.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.anantenergies.bms.R
import com.anantenergies.bms.data.BmsRepository
import com.anantenergies.bms.dev.DevTools
import com.anantenergies.bms.ui.appViewModel
import com.anantenergies.bms.ui.components.AeMark
import com.anantenergies.bms.ui.components.Wordmark
import com.anantenergies.bms.ui.theme.AeTheme

class DevicesViewModel(private val bms: BmsRepository) : ViewModel() {
    fun startDemo() = bms.startDemo()
}

@Composable
fun DevicesScreen(onConnected: () -> Unit) {
    val viewModel = appViewModel { DevicesViewModel(it.bms) }
    val colors = AeTheme.colors

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 20.dp),
                ) {
                    AeMark(height = 36.dp, contentDescription = null)
                    Wordmark(height = 15.dp)
                }
                Text("Connect to BMS", style = AeTheme.type.screenTitle)
                Text(
                    "Keep your phone within a few metres of the battery. The BMS sleeps after a minute with " +
                        "no current, so wake it by connecting a load or charger if it doesn't show up.",
                    style = AeTheme.type.body,
                    color = colors.textSecondary,
                )
            }
            // Bluetooth scanning arrives in milestone 3.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_bluetooth),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "Bluetooth scanning isn't built yet (milestone 3).",
                    style = AeTheme.type.small,
                    color = colors.textSecondary,
                )
            }
        }
        DevTools.DemoEntryCard(
            onStartDemo = {
                viewModel.startDemo()
                onConnected()
            },
            modifier = Modifier.padding(16.dp),
        )
    }
}
