@file:OptIn(ExperimentalMaterial3Api::class)
package ca.senseway.pathpaldemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.asImageBitmap
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
                        onLogin        = viewModel::login,
                        loginError     = viewModel.loginError,
                        isLoginLoading = viewModel.isLoginLoading
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

    val ctx = androidx.compose.ui.platform.LocalContext.current

    // Request POST_NOTIFICATIONS permission on Android 13+
    if (Build.VERSION.SDK_INT >= 33) {
        val notifLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* silently fail-safe */ }
        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Request location permission and start GPS tracking
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(ctx, granted)
    }
    LaunchedEffect(Unit) {
        val hasFine   = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            viewModel.onLocationPermissionResult(ctx, true)
        } else {
            locationLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

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
                    DismissibleNotifBanner(notif) { viewModel.dismissNotification(notif.id) }
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
        val avatarBmp = viewModel.avatarBitmap
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
            if (avatarBmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = avatarBmp.asImageBitmap(),
                    contentDescription = "Profile",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(viewModel.displayInitial, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            }
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
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(ElectricBlue, VioletAccent))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarBmp != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = avatarBmp.asImageBitmap(),
                                        contentDescription = "Avatar",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(viewModel.displayInitial, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    viewModel.displayName,
                                    fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 16.sp
                                )
                                Text(
                                    viewModel.displayEmail,
                                    fontSize = 12.sp, color = TextMuted
                                )
                                Row(
                                    Modifier.padding(top = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(7.dp).background(GreenOk, CircleShape))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Online", fontSize = 12.sp, color = GreenOk)
                                }
                            }
                        }

                        HorizontalDivider(color = CardBorder)

                        // BT status row
                        val btColor = when (viewModel.btStatus) {
                            "Connected"     -> GreenOk
                            "Connecting..." -> Color(0xFFFFB830)
                            "Failed", "Bluetooth Off" -> RedAlert
                            else            -> TextMuted
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bluetooth, null, tint = btColor, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Bluetooth", fontSize = 12.sp, color = TextMuted)
                            }
                            Text(viewModel.btStatus, fontSize = 12.sp, color = btColor, fontWeight = FontWeight.SemiBold)
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
        Icons.Default.Map       to "Map",
        Icons.Default.BarChart  to "Sensors",
        Icons.Default.Settings  to "Settings"
    )

    Surface(
        modifier        = modifier.fillMaxWidth(),
        color           = PanelPurple,
        shadowElevation = 20.dp,
        shape           = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        border          = BorderStroke(1.dp, CardBorder)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
        ) {
            // Sliding pill — animates with spring physics when tab changes
            val itemWidth  = maxWidth / 3
            val pillWidth  = itemWidth * 0.72f
            val targetOff  = itemWidth * currentTab + (itemWidth - pillWidth) / 2
            val pillOffset by animateDpAsState(
                targetValue   = targetOff,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMediumLow
                ),
                label = "nav_pill"
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = pillOffset)
                    .width(pillWidth)
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(ElectricBlue.copy(alpha = 0.18f), VioletAccent.copy(alpha = 0.18f))
                        )
                    )
            )

            // Tab icons + labels
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                navItems.forEachIndexed { index, (icon, label) ->
                    val selected  = currentTab == index
                    val iconSize by animateDpAsState(
                        targetValue   = if (selected) 23.dp else 20.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label         = "icon_sz_$index"
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onTabSelected(index) }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            icon, null,
                            tint     = if (selected) ElectricBlue else TextMuted,
                            modifier = Modifier.size(iconSize)
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter   = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                            exit    = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    label,
                                    fontSize   = 10.sp,
                                    color      = ElectricBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
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

@Composable
fun DismissibleNotifBanner(notification: AppNotification, onDismiss: () -> Unit) {
    val offsetY = remember { Animatable(0f) }
    val scope   = rememberCoroutineScope()

    // auto-dismiss after 5 seconds
    LaunchedEffect(notification.id) {
        delay(5_000L)
        onDismiss()
    }

    val (color, icon) = when (notification.type) {
        NotifType.SUCCESS -> GreenOk      to Icons.Default.CheckCircle
        NotifType.ALERT   -> RedAlert     to Icons.Default.Error
        NotifType.INFO    -> ElectricBlue to Icons.Default.Info
    }

    Surface(
        Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, offsetY.value.toInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAmount < 0f) {
                            scope.launch { offsetY.snapTo(offsetY.value + dragAmount) }
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (offsetY.value < -60f) {
                                offsetY.animateTo(-600f, tween(220))
                                onDismiss()
                            } else {
                                offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        }
                    }
                )
            },
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
