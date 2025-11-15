package com.example.sensewayapp1.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sensewayapp1.model.Contact
import com.example.sensewayapp1.model.DeviceStatus
import com.example.sensewayapp1.model.GeoFence
import com.example.sensewayapp1.ui.components.Status
import com.example.sensewayapp1.ui.components.StatusChip

@Composable
fun HomeScreen(
    onOpenConsole: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val status = vm.status.collectAsState().value
    val contacts = vm.contacts.collectAsState(initial = emptyList()).value
    val fences = vm.fences.collectAsState(initial = emptyList()).value

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { HeaderRow(onOpenConsole, onOpenSettings) }
        item { StatusCards(status) }
        item { MapCard(status) }
        item { RecentActivityCard() }
        item { SafetyZonesCard(fences) }
        item { TrustedContactsCard(contacts) }
        item { QuickActionsCard() }
    }
}

@Composable private fun HeaderRow(onOpenConsole:()->Unit, onOpenSettings:()->Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Dashboard Overview", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = null) }
        FilledTonalButton(onClick = onOpenConsole) { Text("Device Console") }
    }
}

@Composable private fun StatusCards(s: DeviceStatus) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ElevatedCard(Modifier.weight(1f)) { Column(Modifier.padding(16.dp)) {
            Text("SOS Status"); Spacer(Modifier.height(8.dp))
            StatusChip(if (s.connected) "SAFE" else "DISCONNECTED", if (s.connected) Status.Safe else Status.Danger)
            Text("Last updated: just now", style = MaterialTheme.typography.labelSmall)
        } }
        ElevatedCard(Modifier.weight(1f)) { Column(Modifier.padding(16.dp)) {
            Text("Device Battery"); Spacer(Modifier.height(8.dp))
            Text("${s.batteryPct}%", style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(progress = s.batteryPct/100f)
            Text("~12 hours remaining", style = MaterialTheme.typography.labelSmall)
        } }
        ElevatedCard(Modifier.weight(1f)) { Column(Modifier.padding(16.dp)) {
            Text("Heart Rate"); Spacer(Modifier.height(8.dp))
            Text(s.heartRateBpm?.let { "$it BPM" } ?: "—", style = MaterialTheme.typography.titleLarge)
            Text("Normal", style = MaterialTheme.typography.labelSmall)
        } }
    }
}

@Composable private fun MapCard(s: DeviceStatus) {
    ElevatedCard { Column(Modifier.fillMaxWidth().height(220.dp).padding(16.dp)) {
        Text("Real-Time Location"); Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) { Text("Map placeholder – add Google Maps Compose") }
        Text("Last GPS update: ${s.lastGpsFix ?: "—"}", style = MaterialTheme.typography.labelSmall)
    } }
}

@Composable private fun RecentActivityCard() {
    ElevatedCard { Column(Modifier.padding(16.dp)) {
        Text("Recent Activity"); Spacer(Modifier.height(8.dp))
        ActivityRow("Device Connected","Today at 9:15 AM")
        ActivityRow("Walking session started","Today at 8:45 AM")
        ActivityRow("Arrived at safe zone","Yesterday at 6:30 PM")
    } }
}

@Composable private fun ActivityRow(title:String, time:String){
    Row(Modifier.fillMaxWidth().padding(vertical=6.dp), verticalAlignment = Alignment.CenterVertically){
        Icon(Icons.Default.Bolt, contentDescription=null)
        Column(Modifier.padding(start=12.dp).weight(1f)){
            Text(title); Text(time, style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick={}){ Text("View") }
    }
}

@Composable private fun SafetyZonesCard(fences: List<GeoFence>) {
    ElevatedCard { Column(Modifier.padding(16.dp)) {
        Text("Safety Zones")
        fences.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                Text(f.name, Modifier.weight(1f))
                AssistChip(onClick = { }, label = { Text("Edit") })
            }
        }
        OutlinedButton(onClick = { }) { Text("Add New Zone") }
    } }
}

@Composable private fun TrustedContactsCard(contacts: List<Contact>) {
    ElevatedCard { Column(Modifier.padding(16.dp)) {
        Text("Trusted Contacts")
        contacts.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                Text("${c.name}  ·  ${c.role}", Modifier.weight(1f))
                IconButton(onClick = { /* call */ }) { Icon(Icons.Default.Call, null) }
            }
        }
    } }
}

@Composable private fun QuickActionsCard() {
    ElevatedCard { Column(Modifier.padding(16.dp)) {
        Text("Quick Actions"); Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Emergency SOS")
            }
            OutlinedButton(onClick = { }) { Text("Call Guardian") }
        }
    } }
}
