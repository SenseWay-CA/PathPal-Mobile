package com.example.sensewayapp1.ui.console

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sensewayapp1.model.Contact
import com.example.sensewayapp1.model.DeviceStatus
import com.example.sensewayapp1.ui.components.Status
import com.example.sensewayapp1.ui.components.StatusChip

@Composable
fun DeviceConsoleScreen(vm: ConsoleViewModel = hiltViewModel()) {
    val s = vm.status.collectAsState(initial = DeviceStatus("", false, 0, null, null, null)).value
    val people = vm.contacts.collectAsState(initial = emptyList<Contact>()).value
    val ok = vm.txHealthy.collectAsState().value

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("PathPal Console", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            ElevatedCard { Column(Modifier.padding(16.dp)) {
                Text("Connection"); Spacer(Modifier.height(8.dp))
                StatusChip(if (s.connected) "Bluetooth Connected" else "Not Connected", if (s.connected) Status.Safe else Status.Danger)
                Spacer(Modifier.height(8.dp))
                //Text("RSSI: ${s.rssi ?: 0} dBm")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = vm::scanAndPair) { Text("Pair / Reconnect") }
                    TextButton(onClick = vm::troubleshoot) { Text("Troubleshoot") }
                }
            } }
        }
        item {
            ElevatedCard { Column(Modifier.padding(16.dp)) {
                Text("Device Health"); Spacer(Modifier.height(8.dp))
                Text("Battery: ${s.batteryPct}%"); LinearProgressIndicator(progress = s.batteryPct/100f)
                Text("Heart Rate: ${s.heartRateBpm ?: "—"}")
                Text("Last GPS: ${s.lastGpsFix ?: "—"}")
            } }
        }
        item {
            ElevatedCard { Column(Modifier.padding(16.dp)) {
                Text("Data Pipeline"); Spacer(Modifier.height(8.dp))
                StatusChip(if (ok) "Transmitting normally" else "Packet loss detected", if (ok) Status.Safe else Status.Warn)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { }) { Text("Run diagnostic") }
                    OutlinedButton(onClick = { }) { Text("Export logs") }
                }
            } }
        }
        item {
            ElevatedCard { Column(Modifier.padding(16.dp)) {
                Text("People with Access")
                people.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical=6.dp)) {
                        Text(p.name, Modifier.weight(1f))
                        Text(p.role, style = MaterialTheme.typography.labelSmall)
                    }
                }
                OutlinedButton(onClick = { }) { Text("Manage access") }
            } }
        }
        item {
            ElevatedCard { Column(Modifier.padding(16.dp)) {
                Text("Device Settings")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Haptic strength"); TextButton(onClick = { }) { Text("Configure") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Heart-rate monitor"); Switch(checked = true, onCheckedChange = { })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Firmware"); TextButton(onClick = { }) { Text("Check updates") }
                }
            } }
        }
    }
}
