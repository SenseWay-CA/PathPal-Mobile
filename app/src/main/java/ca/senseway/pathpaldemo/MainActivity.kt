@file:OptIn(ExperimentalMaterial3Api::class)

package ca.senseway.pathpaldemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


/* ADded extra imports */
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import android.util.Log
import kotlinx.coroutines.delay
import retrofit2.converter.gson.GsonConverterFactory
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SenseWayAppTheme {
                SenseWayApp()
            }
        }
    }
}



object ApiClient {
    private const val BASE_URL = "https://api.senseway.ca/"

    val api: SenseWayApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SenseWayApi::class.java)
    }
}



class DashboardViewModel : ViewModel() {
    var battery by mutableStateOf(0)
        private set

    var heartRate by mutableStateOf(0)
        private set

    private val userId = "c1987b12-3ffe-432a-ac13-4b06264409ed"

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                try {
                    val status = ApiClient.api.getStatus(userId)
                    battery = status.battery
                    heartRate = status.heart_rate ?: 0
                } catch (e: Exception) {
                    Log.e("DashboardVM", "Failed to fetch status", e)
                }
                delay(3000) // poll every 3 seconds like web app
            }
        }
    }
}



// Retrofit interface
interface SenseWayApi {
    @GET("status")
    suspend fun getStatus(@Query("user_id") userId: String): StatusResponse
}

// Response data class
data class StatusResponse(
    val battery: Int,
    val heart_rate: Int?
    
)




/* ---------- Simple Dark Theme ---------- */

private val DarkBackground = Color(0xFF020617)  // deep navy
private val DarkSurface = Color(0xFF0B1220)     // card background
private val Accent = Color(0xFF22C55E)          // green
private val AccentBlue = Color(0xFF38BDF8)

@Composable
fun SenseWayAppTheme(content: @Composable () -> Unit) {
    val darkColors = androidx.compose.material3.darkColorScheme(
        primary = Accent,
        secondary = AccentBlue,
        background = DarkBackground,
        surface = DarkSurface,
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onBackground = Color(0xFFE5E7EB),
        onSurface = Color(0xFFE5E7EB)
    )
    MaterialTheme(
        colorScheme = darkColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}

/* ---------- Navigation ---------- */

sealed class SenseWayScreen(val label: String, val icon: @Composable () -> Unit) {
    object Dashboard : SenseWayScreen("Dashboard", { androidx.compose.material3.Icon(Icons.Filled.Home, null) })
    object BackendStats : SenseWayScreen("Backend", { androidx.compose.material3.Icon(Icons.Filled.BarChart, null) })
}

@Composable
fun SenseWayApp() {
    var currentScreen by remember { mutableStateOf<SenseWayScreen>(SenseWayScreen.Dashboard) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        containerColor = DarkBackground,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF020617)
            ) {
                listOf(SenseWayScreen.Dashboard, SenseWayScreen.BackendStats).forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen::class == screen::class,
                        onClick = { currentScreen = screen },
                        icon = { screen.icon() },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(DarkBackground)
        ) {
            when (currentScreen) {
                is SenseWayScreen.Dashboard -> DashboardScreen()
                is SenseWayScreen.BackendStats -> BackendStatsScreen()
            }
        }
    }
}

/* ---------- Dashboard Screen ---------- */

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Top bar: logo + welcome
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(Accent, AccentBlue)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("Lo", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Welcome", fontSize = 14.sp, color = Color(0xFF9CA3AF))
                Text("User_ID", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            AssistChip(
                onClick = { /* TODO: user profile / settings */ },
                label = { Text("Status • Online") },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Accent, CircleShape)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF111827)
                )
            )
        }

        // Bluetooth Status card
        LargeStatusCard(
            title = "Bluetooth Status",
            subtitle = "Connected to SenseWay cane",
            icon = {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = AccentBlue
                )
            },
            accentColor = AccentBlue
        )


        // Battery + Heart rate row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallInfoCard(
                modifier = Modifier.weight(1f),
                title = "Battery",
                value = "${viewModel.battery}%",
                icon = { androidx.compose.material3.Icon(Icons.Filled.BatteryFull, null) }
            )
            SmallInfoCard(
                modifier = Modifier.weight(1f),
                title = "HRate",
                value = "${viewModel.heartRate} bpm",
                icon = { androidx.compose.material3.Icon(Icons.Filled.Favorite, null) }
            )
        }

        // Map preview
        BigBlockCard(
            title = "Map",
            description = "Current route overview",
            icon = { androidx.compose.material3.Icon(Icons.Filled.Map, null) }
        )

        // Events / alerts
        BigBlockCard(
            title = "Events",
            description = "No recent events. All clear ✨",
            icon = { androidx.compose.material3.Icon(Icons.Filled.Notifications, null) }
        )
    }
}

@Composable
private fun LargeStatusCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    accentColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp,
        color = DarkSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        accentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Just draw the icon; tint is handled by the caller
                icon()
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 13.sp, color = Color(0xFF9CA3AF))
            }
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF4B5563)
            )
        }
    }
}


@Composable
private fun SmallInfoCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(24.dp),
        color = DarkSurface,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            Color(0xFF111827),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 14.sp, color = Color(0xFF9CA3AF))
            }
            Text(
                value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BigBlockCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        shape = RoundedCornerShape(24.dp),
        color = DarkSurface,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    fontSize = 13.sp,
                    color = Color(0xFF9CA3AF),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF111827), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }
    }
}

/* ---------- Backend Statistics Screen ---------- */

data class BackendStat(
    val label: String,
    val value: String,
    val description: String
)

@Composable
fun BackendStatsScreen() {
    // Replace with your live sensor data later.
    val sampleStats = listOf(
        BackendStat(
            "Accelerometer",
            "x: 0.02  y: -0.15  z: 9.81 m/s²",
            "Linear acceleration on all three axes."
        ),
        BackendStat(
            "Gyroscope",
            "x: 0.01  y: 0.03  z: 0.00 rad/s",
            "Angular velocity (device rotation)."
        ),
        BackendStat(
            "Battery Percentage",
            "25/100",
            "Battery Health 100%"
        ),
        BackendStat(
            "Current Location",
            "48.4284° N, 123.3656° W",
            "Last GPS fix – accuracy 6 m."
        ),
        BackendStat(
            "Altitude",
            "23.4 m",
            "Height above sea level."
        ),
        BackendStat(
            "Bluetooth RSSI",
            "-62 dBm",
            "Signal strength from cane."
        ),
        BackendStat(
            "Heart Rate (sensor)",
            "76 bpm",
            "Most recent reading from HR sensor."
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Column {
                    Text("Backend Statistics", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Live sensor & connection values",
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF)
                    )
                }
            },
            navigationIcon = {
                androidx.compose.material3.Icon(
                    Icons.Filled.BarChart,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 12.dp)
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkBackground,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = Accent
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sampleStats) { stat ->
                BackendStatCard(stat)
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Tip: you can hook these fields directly to your sensor " +
                            "flows / ViewModel for real-time updates.",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun BackendStatCard(stat: BackendStat) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = DarkSurface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stat.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .background(
                            Accent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Live",
                        fontSize = 11.sp,
                        color = Accent,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Text(
                stat.value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = AccentBlue
            )
            Text(
                stat.description,
                fontSize = 12.sp,
                color = Color(0xFF9CA3AF)
            )
        }
    }
}
