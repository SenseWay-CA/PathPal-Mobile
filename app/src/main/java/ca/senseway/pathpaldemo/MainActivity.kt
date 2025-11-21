@file:OptIn(ExperimentalMaterial3Api::class)

package ca.senseway.pathpaldemo

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

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

// ---------------- VIEW MODEL FACTORY ----------------
class DashboardViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class DashboardViewModel(private val context: Context) : ViewModel() {
    var battery by mutableStateOf(0)
        private set

    var heartRate by mutableStateOf(0)
        private set

    var latitude by mutableStateOf(0.0)
        private set

    var longitude by mutableStateOf(0.0)
        private set

    var connectionState by mutableStateOf("Disconnected")
        private set

    // --- SENSOR DATA ---
    var accelerometer by mutableStateOf(listOf(0.0, 0.0, 0.0))
        private set

    var gyroscope by mutableStateOf(listOf(0.0, 0.0, 0.0))
        private set

    var lidarDistance by mutableStateOf(0.0)
        private set
    // -----------------------

    val userId = "c1987b12-3ffe-432a-ac13-4b06264409ed"

    // Bluetooth Service Initialization
    private val btAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val bluetoothService = BluetoothService(btAdapter)

    // GPS Initialization
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                latitude = location.latitude
                longitude = location.longitude
            }
        }
    }

    // *** REPLACE THIS WITH YOUR PI'S ACTUAL MAC ADDRESS ***
    private val PI_MAC_ADDRESS = "B8:27:EB:7D:E7:FE"

    init {
        // 1. Start listening to Bluetooth data
        viewModelScope.launch {
            bluetoothService.sensorData.collect { data ->
                if (data.bpm > 0) heartRate = data.bpm

                // Update new sensor states
                accelerometer = data.accel
                gyroscope = data.gyro
                lidarDistance = data.dist_cm
            }
        }

        // 2. Start listening to Connection Status
        viewModelScope.launch {
            bluetoothService.connectionStatus.collect { status ->
                connectionState = status
            }
        }

        // 3. Attempt connection
        connectBluetooth()

        // 4. Start Server Polling (Only for Battery now)
        startPolling()
    }

    fun connectBluetooth() {
        bluetoothService.connectToPi(PI_MAC_ADDRESS)
    }

    fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(2000)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                try {
                    val status = ApiClient.api.getStatus(userId)
                    battery = status.battery

                    // We no longer fetch lat/long from server
                    // status.latitude?.let { latitude = it }
                    // status.longitude?.let { longitude = it }
                } catch (e: Exception) {
                    Log.e("DashboardVM", "Failed to fetch status", e)
                }
                delay(3000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

// Retrofit interface
interface SenseWayApi {
    @GET("status")
    suspend fun getStatus(@Query("user_id") userId: String): StatusResponse
}

data class StatusResponse(
    val battery: Int,
    val heart_rate: Int?,
    val latitude: Double?,
    val longitude: Double?
)

// ---------------- MAP COMPONENT ----------------

@Composable
fun LiveMap(latitude: Double, longitude: Double) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(24.dp),
        color = DarkSurface,
        tonalElevation = 6.dp
    ) {
        if (latitude != 0.0 && longitude != 0.0) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", 0))
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(18.0) // Zoomed in closer for GPS tracking
                        controller.setCenter(GeoPoint(latitude, longitude))

                        val marker = Marker(this)
                        marker.position = GeoPoint(latitude, longitude)
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        marker.title = "Current Location"
                        marker.icon = ctx.getDrawable(org.osmdroid.library.R.drawable.person) // Optional: specific icon
                        overlays.add(marker)
                    }
                },
                update = { mapView ->
                    mapView.controller.animateTo(GeoPoint(latitude, longitude))
                    if (mapView.overlays.isNotEmpty()) {
                        val marker = mapView.overlays[0] as? Marker
                        marker?.position = GeoPoint(latitude, longitude)
                    }
                    mapView.invalidate() // Force refresh
                }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Waiting for GPS...", color = Color(0xFF9CA3AF))
            }
        }
    }
}

/* ---------- THEME & NAVIGATION ---------- */

private val DarkBackground = Color(0xFF29292C)
private val DarkSurface = Color(0xFF222223)
private val Accent = Color(0xFF22C55E)
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

sealed class SenseWayScreen(val label: String, val icon: @Composable () -> Unit) {
    object Dashboard : SenseWayScreen("Dashboard", { Icon(Icons.Filled.Home, null) })
    object BackendStats : SenseWayScreen("Backend", { Icon(Icons.Filled.BarChart, null) })
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
            NavigationBar(containerColor = Color(0xFF020617)) {
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

/* ---------- DASHBOARD SCREEN ---------- */

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    // --- PERMISSION HANDLING ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                viewModel.startLocationUpdates()
            }
        }
    )

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        // Attempt start in case already granted
        viewModel.startLocationUpdates()
    }
    // --------------------------

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(Accent, AccentBlue)),
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("Lo", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Welcome", fontSize = 14.sp, color = Color(0xFF9CA3AF))
                Text("John Doe", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            AssistChip(
                onClick = { },
                label = { Text("Status • Online") },
                leadingIcon = {
                    Box(modifier = Modifier.size(8.dp).background(Accent, CircleShape))
                },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF111827))
            )
        }

        // Bluetooth Status Card
        LargeStatusCard(
            title = "Bluetooth Status",
            subtitle = viewModel.connectionState,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = if (viewModel.connectionState == "Connected") Color.Green else AccentBlue
                )
            },
            accentColor = AccentBlue
        )

        // Battery + Heart Rate
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallInfoCard(
                modifier = Modifier.weight(1f),
                title = "Battery",
                value = "${viewModel.battery}%",
                icon = { Icon(Icons.Filled.BatteryFull, null) }
            )
            SmallInfoCard(
                modifier = Modifier.weight(1f),
                title = "HRate",
                value = "${viewModel.heartRate} bpm",
                icon = { Icon(Icons.Filled.Favorite, null) }
            )
        }

        // Map
        LiveMap(latitude = viewModel.latitude, longitude = viewModel.longitude)

        // Events
        BigBlockCard(
            title = "Events",
            description = "No recent events. All clear ✨",
            icon = { Icon(Icons.Filled.Notifications, null) }
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
                    .background(accentColor.copy(alpha = 0.15f), shape = RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 13.sp, color = Color(0xFF9CA3AF))
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFF4B5563))
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
                        .background(Color(0xFF111827), shape = RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 14.sp, color = Color(0xFF9CA3AF))
            }
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                modifier = Modifier.size(40.dp).background(Color(0xFF111827), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }
    }
}

/* ---------- BACKEND STATS SCREEN ---------- */

data class BackendStat(val label: String, val value: String, val description: String)

@Composable
fun BackendStatsScreen(
    viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    // Helper to format list of doubles
    fun formatVector(vec: List<Double>): String {
        return if (vec.size >= 3) {
            "x: %.2f  y: %.2f  z: %.2f".format(vec[0], vec[1], vec[2])
        } else {
            "Loading..."
        }
    }

    val sampleStats = listOf(
        BackendStat("Bluetooth Status", viewModel.connectionState, "Live connection status"),
        BackendStat("Heart Rate", "${viewModel.heartRate} bpm", "Sensor reading"),

        // --- NEW CARDS ---
        BackendStat(
            "Accelerometer",
            formatVector(viewModel.accelerometer),
            "Linear acceleration (m/s²)"
        ),
        BackendStat(
            "Gyroscope",
            formatVector(viewModel.gyroscope),
            "Angular velocity (rad/s)"
        ),
        BackendStat(
            "LiDAR Distance",
            "%.1f cm".format(viewModel.lidarDistance),
            "Obstacle distance"
        ),
        // -----------------

        BackendStat("Battery Percentage", "${viewModel.battery}%", "Battery Health 100%"),
        BackendStat("Current Location", "${String.format("%.4f", viewModel.latitude)}° N, ${String.format("%.4f", viewModel.longitude)}° W", "GPS")
    )

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        TopAppBar(
            title = {
                Column {
                    Text("Backend Statistics", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Live sensor & connection values", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                }
            },
            navigationIcon = { Icon(Icons.Filled.BarChart, null, modifier = Modifier.padding(start = 12.dp)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkBackground,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = Accent
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sampleStats) { stat ->
                BackendStatCard(stat)
            }
        }
    }
}

@Composable
private fun BackendStatCard(stat: BackendStat) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = DarkSurface,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stat.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .background(Accent.copy(alpha = 0.12f), shape = RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Live", fontSize = 11.sp, color = Accent, fontWeight = FontWeight.Medium)
                }
            }
            Text(stat.value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AccentBlue)
            Text(stat.description, fontSize = 12.sp, color = Color(0xFF9CA3AF))
        }
    }
}