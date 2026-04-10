@file:OptIn(ExperimentalMaterial3Api::class)
package ca.senseway.pathpaldemo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap

@Composable
fun SettingsScreen(viewModel: AppViewModel, onLogout: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(BgDeep)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Black, color = TextWhite)
            Text("App configuration & info", fontSize = 13.sp, color = TextMuted)
        }

        // User profile card
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(22.dp),
            color = PanelPurple,
            border = BorderStroke(1.dp, CardBorder),
            shadowElevation = 8.dp
        ) {
            Row(
                Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val avatarBmp = viewModel.avatarBitmap
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ElectricBlue, VioletAccent))),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarBmp != null) {
                        Image(
                            bitmap = avatarBmp.asImageBitmap(),
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(viewModel.displayInitial, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(viewModel.displayName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    Text(viewModel.displayEmail, fontSize = 12.sp, color = TextMuted)
                    Text(viewModel.displayType,  fontSize = 12.sp, color = TextMuted)
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GreenOk.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, GreenOk.copy(alpha = 0.25f))
                ) {
                    Text(
                        "Active",
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 12.sp,
                        color = GreenOk,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // Device section
        SettingsSection("Device") {
            SettingsRow(Icons.Default.Fingerprint, "Device ID", viewModel.userId.take(20) + "…", ElectricBlue)
            HorizontalDivider(Modifier.padding(start = 52.dp), color = CardBorder, thickness = 0.5.dp)
            SettingsRow(Icons.Default.Sync, "Poll Interval", "3 seconds", TextMuted)
            HorizontalDivider(Modifier.padding(start = 52.dp), color = CardBorder, thickness = 0.5.dp)
            SettingsRow(Icons.Default.Code, "API Endpoint", "api.senseway.ca", TextMuted)
        }

        Spacer(Modifier.height(16.dp))

        // Bluetooth section
        BluetoothSettingsSection(viewModel)

        Spacer(Modifier.height(16.dp))

        // Notifications section
        SettingsSection("Notifications") {
            SettingsToggleRow(Icons.Default.Notifications, "Push Notifications", ElectricBlue)
            HorizontalDivider(Modifier.padding(start = 52.dp), color = CardBorder, thickness = 0.5.dp)
            SettingsToggleRow(Icons.Default.Warning, "Alert Events", YellowWarn)
        }

        Spacer(Modifier.height(16.dp))

        // About section
        SettingsSection("About") {
            SettingsRow(Icons.Default.Info, "Version", "1.0.0 (Demo)", TextMuted)
            HorizontalDivider(Modifier.padding(start = 52.dp), color = CardBorder, thickness = 0.5.dp)
            SettingsRow(Icons.Default.Business, "Developer", "Senseway Inc.", TextMuted)
            HorizontalDivider(Modifier.padding(start = 52.dp), color = CardBorder, thickness = 0.5.dp)
            SettingsRow(Icons.Default.Language, "Website", "senseway.ca", ElectricBlue)
        }

        Spacer(Modifier.height(24.dp))

        // Logout button
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(RedAlert.copy(alpha = 0.1f))
                .border(1.dp, RedAlert.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .clickable { onLogout() },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ExitToApp,
                    null,
                    tint = RedAlert,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Sign Out",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RedAlert
                )
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
fun BluetoothSettingsSection(viewModel: AppViewModel) {
    val statusColor = when (viewModel.btStatus) {
        "Connected"    -> GreenOk
        "Connecting..."-> YellowWarn
        "Failed", "Bluetooth Off" -> RedAlert
        else           -> TextMuted
    }
    var macInput by remember { mutableStateOf(viewModel.btMacAddress) }

    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "BLUETOOTH",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = PanelPurple,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column {
                // status row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bluetooth, null, tint = statusColor, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("PathPal Pi", fontSize = 14.sp, color = TextWhite)
                        Text(viewModel.btStatus, fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                }

                HorizontalDivider(Modifier.padding(start = 52.dp), color = CardBorder, thickness = 0.5.dp)

                // mac address input
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Device MAC Address", fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(bottom = 6.dp))
                    OutlinedTextField(
                        value = macInput,
                        onValueChange = { macInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("XX:XX:XX:XX:XX:XX", color = TextMuted, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = ElectricBlue,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor     = TextWhite,
                            unfocusedTextColor   = TextGray,
                            cursorColor          = ElectricBlue
                        ),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                }

                HorizontalDivider(Modifier.padding(start = 52.dp), color = CardBorder, thickness = 0.5.dp)

                // connect / disconnect buttons
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val connected = viewModel.btStatus == "Connected"
                    Button(
                        onClick = {
                            viewModel.saveBtMac(macInput.trim())
                            viewModel.connectBluetooth()
                        },
                        enabled = !connected,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            disabledContainerColor = ElectricBlue.copy(alpha = 0.3f)
                        )
                    ) {
                        Icon(Icons.Default.BluetoothSearching, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Connect", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { viewModel.disconnectBluetooth() },
                        enabled = connected,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (connected) RedAlert.copy(alpha = 0.5f) else CardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (connected) RedAlert else TextMuted)
                    ) {
                        Icon(Icons.Default.BluetoothDisabled, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Disconnect", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = PanelPurple,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column { content() }
        }
    }
}

@Composable
fun SettingsRow(icon: ImageVector, label: String, value: String, iconTint: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = TextWhite, modifier = Modifier.weight(1f))
        Text(
            value,
            fontSize = 13.sp,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SettingsToggleRow(icon: ImageVector, label: String, iconTint: Color) {
    var checked by remember { mutableStateOf(true) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = TextWhite, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { checked = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ElectricBlue,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = PanelPurple2
            )
        )
    }
}
