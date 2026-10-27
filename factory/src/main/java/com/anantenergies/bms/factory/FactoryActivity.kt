package com.anantenergies.bms.factory

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshStatus()
    }
}
