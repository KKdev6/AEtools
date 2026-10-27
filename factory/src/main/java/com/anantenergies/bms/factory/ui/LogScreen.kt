package com.anantenergies.bms.factory.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anantenergies.bms.dev.TrafficLog
import com.anantenergies.bms.ui.components.OutlineButton
import com.anantenergies.bms.ui.theme.AeTheme

/** Raw Bluetooth traffic, newest last, for checking a first apply on a real board. */
@Composable
fun LogScreen(log: TrafficLog) {
    val entries by log.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton(
                "Copy all",
                onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("AEtools Factory Bluetooth log", log.export()))
                    Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
                },
            )
            OutlineButton("Clear", onClick = log::clear)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(entries) { entry ->
                val color = when (entry.kind) {
                    TrafficLog.Kind.TX -> AeTheme.colors.brand
                    TrafficLog.Kind.RX -> AeTheme.colors.textPrimary
                    TrafficLog.Kind.INFO -> AeTheme.colors.textSecondary
                    TrafficLog.Kind.ERROR -> AeTheme.colors.error
                }
                Text("${entry.kind} ${entry.text}", style = AeTheme.type.numberSmall, color = color)
            }
        }
    }
}
