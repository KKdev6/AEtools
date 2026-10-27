package com.anantenergies.bms.factory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anantenergies.bms.factory.model.PackModel
import com.anantenergies.bms.ui.components.AeMark
import com.anantenergies.bms.ui.theme.AeTheme

/** Placeholder until the apply and verify screens (milestone F3): lists the built-in presets. */
class FactoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AeTheme(dark = true) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AeTheme.colors.background)
                        .safeDrawingPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AeMark(height = 32.dp)
                    Text("AEtools Factory", style = AeTheme.type.screenTitle, color = AeTheme.colors.textPrimary)
                    Text(
                        "Internal use only. Apply and verify screens come next.",
                        style = AeTheme.type.body,
                        color = AeTheme.colors.textSecondary,
                    )
                    PackModel.entries.forEach { pack ->
                        Text(
                            "${pack.label} → ${pack.preset.name} (${pack.preset.cardRevision})",
                            style = AeTheme.type.body,
                            color = AeTheme.colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}
