package com.anantenergies.bms.factory

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import com.anantenergies.bms.factory.ui.FactoryApp
import com.anantenergies.bms.factory.ui.FactoryViewModel
import com.anantenergies.bms.ui.theme.AeTheme

class FactoryActivity : ComponentActivity() {
    private val vm: FactoryViewModel by viewModels()

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshStatus() }
    private val enableBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AeTheme(dark = true) {
                FactoryApp(
                    vm = vm,
                    onRequestPermissions = { permissions.launch(vm.requiredPermissions) },
                    onEnableBluetooth = { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                    onShareLogSheet = ::shareLogSheet,
                )
            }
        }
    }

    /** Hands the CSV to Android's share sheet (email, Drive, WhatsApp…). */
    private fun shareLogSheet() {
        val file = vm.logSheet.file
        if (!file.exists()) {
            Toast.makeText(this, "No packs logged yet", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "AEtools Factory log sheet")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "Share the log sheet"))
    }

    override fun onResume() {
        super.onResume()
        vm.refreshStatus()
    }
}
