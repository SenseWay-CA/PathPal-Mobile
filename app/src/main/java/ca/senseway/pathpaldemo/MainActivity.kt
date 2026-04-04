@file:OptIn(ExperimentalMaterial3Api::class)
package ca.senseway.pathpaldemo

import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE))
        setContent {
            SenseWayAppTheme {
                val viewModel: AppViewModel = viewModel()
                AnimatedContent(
                    targetState  = viewModel.isLoggedIn,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label        = "auth"
                ) { loggedIn ->
                    if (loggedIn) MainApp(viewModel = viewModel)
                    else          LoginScreen(
                        onLogin    = viewModel::login,
                        loginError = viewModel.loginError
                    )
                }
            }
        }
        // Must be called AFTER setContent — setContent creates the DecorView via
        // setContentView(), so window.insetsController is only safe to call here.
        hideStatusBar()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    // Immersive sticky — swipe down to peek, auto-hides again
    private fun hideStatusBar() {
        window.insetsController?.apply {
            hide(WindowInsets.Type.statusBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
fun MainApp(viewModel: AppViewModel) {
    var currentTab   by remember { mutableIntStateOf(0) }
    var showUserMenu by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        // Tab content — smooth fade between tabs
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 76.dp)
        ) {
            AnimatedContent(
                targetState  = currentTab,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
                label        = "tab_content"
            ) { tab ->
                when (tab) {
                    0    -> DashboardScreen(viewModel = viewModel)
                    1    -> StatsScreen(viewModel = viewModel)
                    2    -> SettingsScreen(viewModel = viewModel, onLogout = viewModel::logout)
                    else -> DashboardScreen(viewModel = viewModel)
                }
            }
        }

        // Notification banners
        AnimatedVisibility(
            visible  = viewModel.notifications.isNotEmpty(),
            enter    = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit     = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.notifications.take(2).forEach { notif ->
                    NotifBanner(notif) { viewModel.dismissNotification(notif.id) }
                }
            }
        }

        // Bottom nav
        AppNavBar(
            currentTab    = currentTab,
            onTabSelected = { currentTab = it },
            modifier      = Modifier.align(Alignment.BottomCenter)
        )

        // Profile avatar button (top-right)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 16.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(PanelPurple.copy(alpha = 0.92f))
                .clickable { showUserMenu = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AccountCircle, "Profile", tint = TextWhite, modifier = Modifier.size(24.dp))
        }

        // User profile dialog
        if (showUserMenu) {
            AlertDialog(
                onDismissRequest = { showUserMenu = false },
                containerColor   = PanelPurple,
                shape            = RoundedCornerShape(24.dp),
                title = {
                    Text("My Profile", fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 18.sp)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .background(
                                        Brush.linearGradient(listOf(ElectricBlue, VioletAccent)),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("A", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("admin", fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 16.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(7.dp).background(GreenOk, CircleShape))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Online", fontSize = 12.sp, color = GreenOk)
                                }
                            }
                        }

                        HorizontalDivider(color = CardBorder)

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Device ID", fontSize = 11.sp, color = TextMuted)
                            Text(
                                viewModel.userId,
                                fontSize = 11.sp, color = TextGray,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            Modifier.fillMaxWidth(),
                            shape  = RoundedCornerShape(12.dp),
                            color  = PanelPurple2,
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                ProfileStat("Battery", "${viewModel.battery}%", GreenOk)
                                ProfileStat("Heart", "${viewModel.heartRate} bpm", Color(0xFFFF6B8A))
                                ProfileStat(
                                    "Temp",
                                    if (!viewModel.temperature.isNaN()) "${viewModel.temperature.toInt()}°C" else "-- °C",
                                    temperatureColor(viewModel.temperature)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { showUserMenu = false; viewModel.logout() },
                        colors  = ButtonDefaults.textButtonColors(contentColor = RedAlert)
                    ) {
                        Text("Sign Out", fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showUserMenu = false },
                        colors  = ButtonDefaults.textButtonColors(contentColor = TextMuted)
                    ) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun ProfileStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = TextMuted)
    }
}

@Composable
fun AppNavBar(
    currentTab:    Int,
    onTabSelected: (Int) -> Unit,
    modifier:      Modifier = Modifier
) {
    val navItems = listOf(
        Triple(Icons.Default.Map,      Icons.Default.Map,      "Map"),
        Triple(Icons.Default.BarChart, Icons.Default.BarChart, "Sensors"),
        Triple(Icons.Default.Settings, Icons.Default.Settings, "Settings")
    )

    Surface(
        modifier        = modifier.fillMaxWidth(),
        color           = PanelPurple,
        shadowElevation = 20.dp,
        shape           = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        border          = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, (icon, _, label) ->
                val selected = currentTab == index
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 22.dp, vertical = 8.dp)
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .then(
                                if (selected) Modifier.background(
                                    Brush.linearGradient(
                                        listOf(ElectricBlue.copy(alpha = 0.22f), VioletAccent.copy(alpha = 0.22f))
                                    ),
                                    RoundedCornerShape(12.dp)
                                ) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon, null,
                            tint     = if (selected) ElectricBlue else TextMuted,
                            modifier = Modifier.size(if (selected) 22.dp else 20.dp)
                        )
                    }
                    if (selected) {
                        Spacer(Modifier.height(3.dp))
                        Text(label, fontSize = 10.sp, color = ElectricBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun NotifBanner(notification: AppNotification, onDismiss: () -> Unit) {
    val (color, icon) = when (notification.type) {
        NotifType.SUCCESS -> GreenOk    to Icons.Default.CheckCircle
        NotifType.ALERT   -> RedAlert   to Icons.Default.Error
        NotifType.INFO    -> ElectricBlue to Icons.Default.Info
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape           = RoundedCornerShape(16.dp),
        color           = PanelPurple,
        shadowElevation = 12.dp,
        border          = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(notification.title,   fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
                Text(notification.message, fontSize = 12.sp, color = TextMuted)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(14.dp))
            }
        }
    }
}
