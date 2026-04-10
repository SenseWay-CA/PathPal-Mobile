@file:OptIn(ExperimentalMaterial3Api::class)
package ca.senseway.pathpaldemo

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import android.speech.tts.TextToSpeech
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

@Composable
fun StatsScreen(viewModel: AppViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Live Sensors", fontSize = 24.sp, fontWeight = FontWeight.Black, color = TextWhite)
                Text("Real-time sensor data", fontSize = 13.sp, color = TextMuted)
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ElectricBlue.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.25f))
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).background(ElectricBlue, CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Text("Live", fontSize = 12.sp, color = ElectricBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = BgDeep,
            contentColor = ElectricBlue,
            divider = { HorizontalDivider(color = CardBorder) }
        ) {
            listOf("Sensors", "Events", "Camera").forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            color = if (selectedTab == index) ElectricBlue else TextMuted,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> SensorsTab(viewModel)
            1 -> EventsTab(viewModel)
            2 -> CameraTab()
        }
    }
}

@Composable
fun SensorsTab(viewModel: AppViewModel) {
    val accelX = viewModel.accelerometerX.toFloat()
    val accelY = viewModel.accelerometerY.toFloat()
    val accelZ = viewModel.accelerometerZ.toFloat()
    val gX     = viewModel.gyroX.toFloat()
    val gY     = viewModel.gyroY.toFloat()
    val gZ     = viewModel.gyroZ.toFloat()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // bluetooth status card
        item { BluetoothStatusCard(viewModel) }

        item { Spacer(Modifier.height(2.dp)) }

        // accelerometer section
        item { SensorSectionHeader("Accelerometer", Icons.Default.Speed, ElectricBlue) }
        item { AccelerometerViz(x = accelX, y = accelY, z = accelZ) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SensorChip("X", String.format(Locale.US, "%.2f", accelX), ElectricBlue, Modifier.weight(1f))
                SensorChip("Y", String.format(Locale.US, "%.2f", accelY), VioletAccent, Modifier.weight(1f))
                SensorChip("Z", String.format(Locale.US, "%.2f", accelZ), GreenOk,      Modifier.weight(1f))
            }
        }
        item {
            val mag = kotlin.math.sqrt((accelX * accelX + accelY * accelY + accelZ * accelZ).toDouble())
            AccelMagnitudeBar(magnitude = mag.toFloat())
        }

        item { Spacer(Modifier.height(4.dp)) }

        // gyroscope section
        item { SensorSectionHeader("Gyroscope", Icons.Default.Autorenew, VioletAccent) }
        item { GyroscopeViz(gx = gX, gy = gY, gz = gZ) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SensorChip("X", String.format(Locale.US, "%.2f", gX), ElectricBlue, Modifier.weight(1f))
                SensorChip("Y", String.format(Locale.US, "%.2f", gY), VioletAccent, Modifier.weight(1f))
                SensorChip("Z", String.format(Locale.US, "%.2f", gZ), GreenOk,      Modifier.weight(1f))
            }
        }

        item { Spacer(Modifier.height(4.dp)) }

        // lidar section
        item { SensorSectionHeader("LiDAR Distance", Icons.Default.Radar, YellowWarn) }
        item { LidarCard(distanceCm = viewModel.lidarDistance) }

        item { Spacer(Modifier.height(4.dp)) }

        // device stats
        item { SensorSectionHeader("Device Stats", Icons.Default.BarChart, GreenOk) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatDataCard(Modifier.weight(1f), "Battery",    "${viewModel.battery}%", "Device power", GreenOk)
                StatDataCard(Modifier.weight(1f), "Heart Rate",
                    if (viewModel.heartRate > 0) "${viewModel.heartRate} bpm" else "-- bpm",
                    "From Bluetooth", Color(0xFFFF6B8A))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatDataCard(Modifier.weight(1f), "Latitude",
                    String.format(Locale.US, "%.4f°", viewModel.latitude), "GPS", YellowWarn)
                StatDataCard(Modifier.weight(1f), "Longitude",
                    String.format(Locale.US, "%.4f°", viewModel.longitude), "GPS", YellowWarn)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatDataCard(Modifier.weight(1f), "Altitude",
                    if (!viewModel.elevation.isNaN()) "${viewModel.elevation.toInt()} m" else "-- m",
                    "Above sea level", ElectricBlue)
                StatDataCard(Modifier.weight(1f), "Temperature",
                    if (!viewModel.temperature.isNaN()) "${viewModel.temperature.toInt()}°C" else "-- °C",
                    "Current weather", Color(0xFFFFB830))
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun BluetoothStatusCard(viewModel: AppViewModel) {
    val connected = viewModel.btStatus == "Connected"
    val color = when (viewModel.btStatus) {
        "Connected"    -> GreenOk
        "Connecting..."-> YellowWarn
        "Failed"       -> RedAlert
        else           -> TextMuted
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(18.dp),
        color  = color.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Bluetooth, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("BLUETOOTH", fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
                Text(viewModel.btStatus, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
                Text("PathPal Pi  •  ${viewModel.btMacAddress}", fontSize = 11.sp, color = TextMuted)
            }
            if (connected) {
                PulsingOnlineDot()
            } else if (viewModel.btStatus != "Connecting...") {
                TextButton(
                    onClick = { viewModel.connectBluetooth() },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Connect", fontSize = 12.sp, color = ElectricBlue, fontWeight = FontWeight.SemiBold)
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = YellowWarn,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
fun AccelMagnitudeBar(magnitude: Float) {
    val maxG = 20f
    val frac = (magnitude / maxG).coerceIn(0f, 1f)
    val color = when {
        magnitude > 15f -> RedAlert
        magnitude > 10f -> YellowWarn
        else            -> GreenOk
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(12.dp),
        color  = PanelPurple,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("G-Force", fontSize = 11.sp, color = TextMuted)
                Text(String.format(Locale.US, "%.2f m/s²", magnitude), fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(6.dp).background(CardBorder, RoundedCornerShape(3.dp))
            ) {
                Box(
                    Modifier.fillMaxWidth(frac).fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(listOf(ElectricBlue, color)),
                            RoundedCornerShape(3.dp)
                        )
                )
            }
        }
    }
}

@Composable
fun LidarCard(distanceCm: Double) {
    val maxCm = 400.0
    val frac  = (distanceCm / maxCm).coerceIn(0.0, 1.0).toFloat()
    val color = when {
        distanceCm < 50  -> RedAlert
        distanceCm < 120 -> YellowWarn
        else             -> GreenOk
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(20.dp),
        color  = PanelPurple2,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // sonar arc indicator
            Canvas(Modifier.size(90.dp)) {
                val cx = size.width / 2
                val cy = size.height / 2
                val r  = size.minDimension / 2 - 4.dp.toPx()
                // grid rings
                for (i in 1..3) {
                    drawCircle(CardBorder.copy(alpha = 0.5f), r * i / 3, Offset(cx, cy),
                        style = Stroke(1f))
                }
                // filled arc showing distance
                drawArc(
                    color      = color.copy(alpha = 0.18f),
                    startAngle = 150f,
                    sweepAngle = 240f * frac,
                    useCenter  = true,
                    topLeft    = Offset(cx - r, cy - r),
                    size       = Size(r * 2, r * 2)
                )
                drawArc(
                    color      = color.copy(alpha = 0.8f),
                    startAngle = 150f,
                    sweepAngle = 240f * frac,
                    useCenter  = false,
                    topLeft    = Offset(cx - r, cy - r),
                    size       = Size(r * 2, r * 2),
                    style      = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
                drawCircle(color, 6.dp.toPx(), Offset(cx, cy))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Distance", fontSize = 11.sp, color = TextMuted)
                if (distanceCm > 0) {
                    Text(
                        String.format(Locale.US, "%.1f cm", distanceCm),
                        fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color
                    )
                    Text(
                        when {
                            distanceCm < 50  -> "Obstacle very close"
                            distanceCm < 120 -> "Obstacle nearby"
                            else             -> "Path clear"
                        },
                        fontSize = 12.sp, color = TextMuted
                    )
                } else {
                    Text("-- cm", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Text("No reading", fontSize = 12.sp, color = TextMuted)
                }
            }
        }
    }
}

@Composable
fun SensorSectionHeader(title: String, icon: ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(32.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
    }
}

@Composable
fun AccelerometerViz(x: Float, y: Float, @Suppress("UNUSED_PARAMETER") z: Float) {
    Surface(
        Modifier.fillMaxWidth().height(190.dp),
        shape = RoundedCornerShape(20.dp),
        color = PanelPurple2,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val r = minOf(cx, cy) * 0.78f

            // Grid rings
            for (i in 1..3) {
                drawCircle(
                    color = CardBorder.copy(alpha = 0.6f),
                    radius = r * i / 3,
                    center = Offset(cx, cy),
                    style = Stroke(1f)
                )
            }
            // Cross lines
            drawLine(CardBorder.copy(alpha = 0.5f), Offset(8f, cy), Offset(size.width - 8f, cy), 1f)
            drawLine(CardBorder.copy(alpha = 0.5f), Offset(cx, 8f), Offset(cx, size.height - 8f), 1f)

            // Trail gradient effect
            val ballX = (cx + (x / 1.2f) * r).coerceIn(cx - r, cx + r)
            val ballY = (cy - (y / 1.2f) * r).coerceIn(cy - r, cy + r)

            // Outer glow rings
            drawCircle(ElectricBlue.copy(alpha = 0.07f), 55f, Offset(ballX, ballY))
            drawCircle(ElectricBlue.copy(alpha = 0.15f), 38f, Offset(ballX, ballY))
            drawCircle(ElectricBlue.copy(alpha = 0.28f), 24f, Offset(ballX, ballY))
            // Ball
            drawCircle(ElectricBlue, 16f, Offset(ballX, ballY))
            drawCircle(Color.White, 6f, Offset(ballX, ballY))
        }
    }
}

@Composable
fun GyroscopeViz(gx: Float, gy: Float, gz: Float) {
    val angle = Math.toDegrees(kotlin.math.atan2(gx.toDouble(), gy.toDouble())).toFloat()
    val pitch = (gz / 10f).coerceIn(-90f, 90f)
    Surface(
        Modifier.fillMaxWidth().height(190.dp),
        shape = RoundedCornerShape(20.dp),
        color = PanelPurple2,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val r = minOf(cx, cy) * 0.75f

            // Outer ring
            drawCircle(CardBorder, r, Offset(cx, cy), style = Stroke(2f))

            // Subtle inner rings
            drawCircle(CardBorder.copy(alpha = 0.3f), r * 0.66f, Offset(cx, cy), style = Stroke(1f))
            drawCircle(CardBorder.copy(alpha = 0.2f), r * 0.33f, Offset(cx, cy), style = Stroke(1f))

            // Tick marks
            for (i in 0 until 36) {
                val a = Math.toRadians((i * 10).toDouble())
                val isMajor = i % 9 == 0
                val inner = r * if (isMajor) 0.86f else 0.92f
                drawLine(
                    color = if (isMajor) VioletAccent.copy(alpha = 0.7f) else CardBorder.copy(alpha = 0.5f),
                    start = Offset((cx + inner * cos(a)).toFloat(), (cy + inner * sin(a)).toFloat()),
                    end = Offset((cx + r * cos(a)).toFloat(), (cy + r * sin(a)).toFloat()),
                    strokeWidth = if (isMajor) 2.5f else 1f
                )
            }

            // Inner glow
            drawCircle(VioletAccent.copy(alpha = 0.06f), r * 0.55f, Offset(cx, cy))

            // Needle
            val rad = Math.toRadians(angle.toDouble())
            val needleLen = r * 0.72f
            // Trailing needle
            drawLine(
                VioletAccent.copy(alpha = 0.2f),
                Offset(cx, cy),
                Offset((cx - needleLen * 0.35f * sin(rad)).toFloat(), (cy + needleLen * 0.35f * cos(rad)).toFloat()),
                strokeWidth = 3f
            )
            // Main needle
            drawLine(
                VioletAccent,
                Offset(cx, cy),
                Offset((cx + needleLen * sin(rad)).toFloat(), (cy - needleLen * cos(rad)).toFloat()),
                strokeWidth = 3.5f
            )
            // Pitch indicator (horizontal line)
            val pitchOffset = (pitch / 90f) * r * 0.4f
            drawLine(
                GreenOk.copy(alpha = 0.6f),
                Offset(cx - r * 0.5f, cy + pitchOffset),
                Offset(cx + r * 0.5f, cy + pitchOffset),
                strokeWidth = 2f
            )
            // Center hub
            drawCircle(VioletAccent.copy(alpha = 0.3f), 14f, Offset(cx, cy))
            drawCircle(VioletAccent, 9f, Offset(cx, cy))
            drawCircle(Color.White, 4f, Offset(cx, cy))
        }
    }
}

@Composable
fun SensorChip(axis: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(12.dp),
        color = PanelPurple,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(axis, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 12.sp, color = TextGray, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun StatDataCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    sub: String,
    color: Color
) {
    Surface(
        modifier.height(88.dp),
        shape = RoundedCornerShape(16.dp),
        color = PanelPurple,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(color, CircleShape))
                Spacer(Modifier.width(5.dp))
                Text("Live", fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
            }
            Column {
                Text(
                    value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(label, fontSize = 11.sp, color = TextMuted)
                Text(sub, fontSize = 10.sp, color = TextMuted.copy(alpha = 0.6f))
            }
        }
    }
}

// Maps an API event type string to icon, color, and display label
private fun eventTypeInfo(type: String?): Triple<ImageVector, Color, String> {
    val t = type?.lowercase(Locale.ROOT) ?: ""
    return when {
        t.contains("battery") || t.contains("low_bat")
            -> Triple(Icons.Default.BatteryAlert,    YellowWarn,          "Battery")
        t.contains("fence") || t.contains("geo") || t.contains("zone")
            -> Triple(Icons.Default.LocationOff,     RedAlert,            "Geofence")
        t.contains("fall") || t.contains("sos") || t.contains("emergency")
            -> Triple(Icons.Default.Warning,         RedAlert,            "Emergency")
        t.contains("heart") || t.contains("pulse")
            -> Triple(Icons.Default.Favorite,        Color(0xFFFF6B8A),   "Heart Rate")
        t.contains("gps") || t.contains("location") || t.contains("position")
            -> Triple(Icons.Default.GpsFixed,        ElectricBlue,        "Location")
        t.contains("step") || t.contains("walk") || t.contains("activity")
            -> Triple(Icons.Default.DirectionsWalk,  GreenOk,             "Activity")
        t.contains("enter") || t.contains("return") || t.contains("ok")
            -> Triple(Icons.Default.CheckCircle,     GreenOk,             "Status")
        t.contains("temp") || t.contains("weather")
            -> Triple(Icons.Default.Thermostat,      Color(0xFFFFB830),   "Weather")
        else
            -> Triple(Icons.Default.Info,            ElectricBlue,        "Event")
    }
}

// Formats an ISO-8601 timestamp into a human-readable relative time string
private fun formatEventTime(createdAt: String?): String {
    if (createdAt == null) return ""
    return try {
        val odt  = OffsetDateTime.parse(createdAt)
        val now  = OffsetDateTime.now(ZoneOffset.UTC)
        val mins = Duration.between(odt.toInstant(), now.toInstant()).toMinutes()
        when {
            mins < 1    -> "Just now"
            mins < 60   -> "${mins}m ago"
            mins < 1440 -> "${mins / 60}h ago"
            else        -> odt.format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
        }
    } catch (_: Exception) {
        try {
            // Fallback for timestamps without timezone
            createdAt.replace("T", " ").take(16)
        } catch (_: Exception) { "" }
    }
}

@Composable
fun EventsTab(viewModel: AppViewModel) {
    if (viewModel.events.isEmpty()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(80.dp)
                        .background(PanelPurple2, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Notifications, null,
                        tint = TextMuted,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "No events yet",
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextGray
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Device events will appear here in real time",
                    fontSize = 13.sp, color = TextMuted
                )
            }
        }
    } else {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${viewModel.events.size} events",
                        fontSize = 12.sp, color = TextMuted
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ElectricBlue.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.20f))
                    ) {
                        Text(
                            "Live",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp, color = ElectricBlue, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            items(viewModel.events, key = { it.id ?: System.nanoTime() }) { evt ->
                ApiEventCard(evt)
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// Camera Tab

private const val STREAM_BASE    = "https://api.senseway.ca"
private const val STREAM_STATUS  = "$STREAM_BASE/stream/status"
private const val STREAM_WS      = "wss://api.senseway.ca/ws/stream"

@Composable
fun CameraTab() {
    // stream state
    var streaming         by remember { mutableStateOf(false) }
    var viewers           by remember { mutableIntStateOf(0) }
    var frameBitmap       by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var connectionLabel   by remember { mutableStateOf("Connecting...") }
    val mainHandler       = remember { Handler(Looper.getMainLooper()) }
    var lastFrameReceivedAt by remember { mutableLongStateOf(0L) }
    var frameStagnant     by remember { mutableStateOf(false) }

    // pathsense detection state
    var detectionLabel     by remember { mutableStateOf<String?>(null) }
    var detectionType      by remember { mutableStateOf("") }
    var lastDetectionTime  by remember { mutableLongStateOf(0L) }
    var isBlurry           by remember { mutableStateOf(false) }
    var isBlocked          by remember { mutableStateOf(false) }
    var ttsReady           by remember { mutableStateOf(false) }
    var activeChips        by remember { mutableStateOf<List<Pair<String, Color>>>(emptyList()) }
    var frameCounter       by remember { mutableIntStateOf(0) }
    // soft-decay counters: missed frame decrements by 1 instead of zeroing, so a single
    // dropped frame won't kill a valid detection streak
    var consecutiveWalk    by remember { mutableIntStateOf(0) }
    var consecutiveStop    by remember { mutableIntStateOf(0) }
    var blurConsecutive    by remember { mutableIntStateOf(0) }
    var blockedConsecutive by remember { mutableIntStateOf(0) }
    val lastSpokenAt       = remember { mutableStateMapOf<String, Long>() }
    val context            = LocalContext.current
    var ttsInstance        by remember { mutableStateOf<TextToSpeech?>(null) }

    // cache yolo detector — loaded lazily on first inference
    val detectorRef   = remember { Array<YoloDetector?>(1) { null } }
    // pathsense bounding boxes for visual overlay
    var pathsenseBoxes by remember { mutableStateOf<List<YoloDetector.BoundingBox>>(emptyList()) }

    // walk signal model state
    val wsDetectorRef = remember { Array<YoloDetector?>(1) { null } }
    var walkBoxes     by remember { mutableStateOf<List<YoloDetector.BoundingBox>>(emptyList()) }
    var consecWsWalk  by remember { mutableIntStateOf(0) }
    var consecWsWait  by remember { mutableIntStateOf(0) }
    var wsWalkCooldownUntil by remember { mutableLongStateOf(0L) }
    var wsWaitCooldownUntil by remember { mutableLongStateOf(0L) }
    val soundPlayer   = remember { WalkSignalSoundPlayer(context) }

    // one-shot status fetch before websocket connects
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(STREAM_STATUS).build()
                sensewayHttpClient.newCall(req).execute().use { resp ->
                    resp.body?.string()?.let { body ->
                        val json = JSONObject(body)
                        val s = json.optBoolean("streaming", false)
                        val c = json.optInt("clients", 0)
                        withContext(Dispatchers.Main) {
                            streaming = s
                            viewers   = c
                            if (connectionLabel == "Connecting...") {
                                connectionLabel = if (s) "Live" else "Offline"
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // websocket — binary = jpeg frame, text = json status
    DisposableEffect(Unit) {
        var cancelled = false
        var currentWs: WebSocket? = null
        var reconnectRunnable: Runnable? = null

        fun connect() {
            if (cancelled) return
            val request = Request.Builder().url(STREAM_WS).build()
            currentWs = sensewayHttpClient.newWebSocket(request, object : WebSocketListener() {

                override fun onOpen(webSocket: WebSocket, response: Response) {
                    mainHandler.post {
                        if (!cancelled && connectionLabel == "Connecting...")
                            connectionLabel = "Connected"
                    }
                }

                // raw jpeg bytes → decode → update displayed frame
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    if (cancelled) return
                    val arr = bytes.toByteArray()
                    val bmp = BitmapFactory.decodeByteArray(arr, 0, arr.size) ?: return
                    mainHandler.post {
                        if (!cancelled) {
                            frameBitmap = bmp
                            lastFrameReceivedAt = System.currentTimeMillis()
                            if (frameStagnant) {
                                frameStagnant = false
                                if (connectionLabel == "No Signal") connectionLabel = "Live"
                            }
                        }
                    }
                }

                // json status → update streaming flag + viewer count
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (cancelled) return
                    try {
                        val json = JSONObject(text)
                        val s = json.optBoolean("streaming", false)
                        val c = json.optInt("clients", 0)
                        mainHandler.post {
                            if (!cancelled) {
                                streaming = s
                                viewers   = c
                                if (!s) {
                                    connectionLabel = "Offline"
                                    frameBitmap     = null
                                    frameStagnant   = false
                                    lastFrameReceivedAt = 0L
                                } else if (connectionLabel != "No Signal") {
                                    connectionLabel = "Live"
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                private fun scheduleReconnect() {
                    if (cancelled) return
                    mainHandler.post { if (!cancelled) connectionLabel = "Reconnecting..." }
                    ttsInstance?.speak(
                        "Stream disconnected. Reconnecting.",
                        TextToSpeech.QUEUE_ADD, null, "disconnect"
                    )
                    val r = Runnable { connect() }
                    reconnectRunnable = r
                    mainHandler.postDelayed(r, 3000L)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
                    scheduleReconnect()

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                    scheduleReconnect()
            })
        }

        connect()

        onDispose {
            cancelled = true
            reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
            currentWs?.cancel()
        }
    }

    // tts init — speaks startup phrase when engine is ready
    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts ?: return@TextToSpeech
                t.language = Locale.CANADA
                t.setPitch(1.0f)
                t.setSpeechRate(0.92f)
                ttsReady = true
                ttsInstance = t
                t.speak(
                    "PathSense is active. Ready to assist.",
                    TextToSpeech.QUEUE_ADD, null, "startup"
                )
            }
        }
        onDispose {
            ttsInstance = null
            tts?.let { it.stop(); it.shutdown() }
        }
    }

    // release sound player when composable leaves
    DisposableEffect(Unit) { onDispose { soundPlayer.stopAll() } }

    // stagnant frame detection — if no frame arrives for 5s while "connected", mark as no signal
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            val t = lastFrameReceivedAt
            val stagnant = t > 0 && (System.currentTimeMillis() - t) > 5_000
            if (stagnant && !frameStagnant) {
                frameStagnant = true
                frameBitmap   = null
                if (connectionLabel == "Live" || connectionLabel == "Connected") {
                    connectionLabel = "No Signal"
                }
            }
        }
    }

    // auto clear detection banner 5s after last detection
    LaunchedEffect(lastDetectionTime) {
        if (lastDetectionTime == 0L) return@LaunchedEffect
        delay(5_000)
        if (System.currentTimeMillis() - lastDetectionTime >= 5_000) {
            detectionLabel = null
            detectionType  = ""
            activeChips    = emptyList()
        }
    }

    // blur + blocked check — throttled to every 5th frame (expensive pixel iteration)
    val blurFrameRef = remember { intArrayOf(0) }
    LaunchedEffect(frameBitmap) {
        val bmp = frameBitmap ?: return@LaunchedEffect
        blurFrameRef[0]++
        if (blurFrameRef[0] % 5 != 0) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val small = android.graphics.Bitmap.createScaledBitmap(bmp, 160, 120, false)

            // mean luminance — low = blocked/covered
            var totalLum = 0L
            for (py in 0 until small.height) {
                for (px in 0 until small.width) {
                    val p = small.getPixel(px, py)
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8)  and 0xFF
                    val b =  p         and 0xFF
                    totalLum += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                }
            }
            val meanLum = totalLum / (small.width * small.height)

            // laplacian variance — low = blurry
            val gray = IntArray(small.width * small.height)
            for (py in 0 until small.height) {
                for (px in 0 until small.width) {
                    val p = small.getPixel(px, py)
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8)  and 0xFF
                    val b =  p         and 0xFF
                    gray[py * small.width + px] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                }
            }
            var lapSum = 0.0; var lapCount = 0
            for (py in 1 until small.height - 1) {
                for (px in 1 until small.width - 1) {
                    val lap = (
                        -gray[(py - 1) * small.width + px]
                        - gray[(py + 1) * small.width + px]
                        - gray[py * small.width + (px - 1)]
                        - gray[py * small.width + (px + 1)]
                        + 4 * gray[py * small.width + px]
                    ).toDouble()
                    lapSum += lap * lap; lapCount++
                }
            }
            val variance = if (lapCount > 0) lapSum / lapCount else 0.0

            withContext(Dispatchers.Main) {
                // blur — fire only after 3 consecutive blurry frames
                if (variance < 80.0) {
                    blurConsecutive++
                    if (blurConsecutive >= 3 && !isBlurry) {
                        isBlurry = true
                        speakIfCooldown(ttsInstance, ttsReady, lastSpokenAt, "blur",
                            "Please clean the camera lens.", urgent = false, cooldownMs = 60_000L)
                    }
                } else if (variance > 120.0) {
                    blurConsecutive = 0
                    if (isBlurry) {
                        isBlurry = false
                        speakIfCooldown(ttsInstance, ttsReady, lastSpokenAt, "blur_clear",
                            "Camera is clear. PathSense is active.",
                            urgent = false, cooldownMs = 10_000L)
                    }
                }

                // blocked — fire only after 3 consecutive dark frames
                if (meanLum < 30) {
                    blockedConsecutive++
                    if (blockedConsecutive >= 3 && !isBlocked) {
                        isBlocked = true
                        speakIfCooldown(ttsInstance, ttsReady, lastSpokenAt, "blocked",
                            "Camera view is blocked.", urgent = false, cooldownMs = 60_000L)
                    }
                } else if (meanLum > 40) {
                    blockedConsecutive = 0
                    if (isBlocked) {
                        isBlocked = false
                        speakIfCooldown(ttsInstance, ttsReady, lastSpokenAt, "blocked_clear",
                            "Camera is operational. PathSense is active to help you.",
                            urgent = false, cooldownMs = 10_000L)
                    }
                }
            }
        }
    }

    // auto-clear bounding boxes 5s after last detection (so they don't linger)
    LaunchedEffect(walkBoxes) {
        if (walkBoxes.isNotEmpty()) {
            delay(5_000)
            walkBoxes = emptyList()
        }
    }
    LaunchedEffect(pathsenseBoxes) {
        if (pathsenseBoxes.isNotEmpty()) {
            delay(5_000)
            pathsenseBoxes = emptyList()
        }
    }

    // ml inference — both models run every 3rd frame for fast detection
    LaunchedEffect(frameBitmap) {
        val bmp = frameBitmap ?: return@LaunchedEffect
        frameCounter++
        val runPathsense  = frameCounter % 3 == 0
        val runWalksignal = frameCounter % 3 == 0
        if (!runPathsense && !runWalksignal) return@LaunchedEffect

        withContext(Dispatchers.Default) {
            try {
                // ── pathsense model — 1 class only: crosswalk ───────────────
                // model output is [1, 5, 8400] = 4 bbox + 1 class, so only class 0 exists
                if (runPathsense) {
                    if (detectorRef[0] == null)
                        synchronized(detectorRef) {
                            if (detectorRef[0] == null)
                                detectorRef[0] = YoloDetector(
                                    context, "pathsense_pedestrian.tflite",
                                    labels = listOf("crosswalk"),
                                    confidenceThreshold = 0.40f
                                )
                        }
                    val detector = detectorRef[0] ?: return@withContext
                    val rawBoxes = detector.detect(bmp)
                    // reject tiny blips — real crosswalks must occupy some portion of frame
                    val boxes = rawBoxes.filter { box ->
                        (box.x2 - box.x1) > bmp.width  * 0.04f &&
                        (box.y2 - box.y1) > bmp.height * 0.04f
                    }
                    val crosswalkHit = boxes.isNotEmpty()

                    withContext(Dispatchers.Main) {
                        pathsenseBoxes = boxes

                        // soft-decay counter for crosswalk confirmation
                        if (crosswalkHit) consecutiveWalk = minOf(consecutiveWalk + 1, 10)
                        else              consecutiveWalk = maxOf(consecutiveWalk - 1, 0)

                        if (consecutiveWalk >= 4) {
                            triggerDetection("crosswalk",
                                "Crosswalk detected. Cross slowly, check both ways.", "caution",
                                "Crosswalk ahead. Check both ways.",
                                urgent = false, cooldownMs = 12_000L,
                                tts = ttsInstance, ttsReady = ttsReady, lastSpokenAt = lastSpokenAt,
                                setLabel = { detectionLabel = it },
                                setType  = { detectionType = it },
                                setTime  = { lastDetectionTime = it })
                            activeChips = listOf("Crosswalk" to YellowWarn)
                        }
                    }
                }

                // ── walk signal model ────────────────────────────────────────
                if (runWalksignal) {
                    if (wsDetectorRef[0] == null)
                        synchronized(wsDetectorRef) {
                            if (wsDetectorRef[0] == null)
                                wsDetectorRef[0] = YoloDetector(
                                    context, "walksignal.tflite",
                                    labels = listOf("wait", "walk"),
                                    confidenceThreshold = 0.55f
                                )
                        }
                    val wsBoxesRaw = wsDetectorRef[0]!!.detect(bmp)
                    val wsBoxes = wsBoxesRaw.filter { box ->
                        (box.x2 - box.x1) > bmp.width  * 0.04f &&
                        (box.y2 - box.y1) > bmp.height * 0.04f
                    }
                    val wsWalkHit = wsBoxes.any { it.label == "walk" }
                    val wsWaitHit = wsBoxes.any { it.label == "wait" }

                    withContext(Dispatchers.Main) {
                        if (wsWalkHit) consecWsWalk = minOf(consecWsWalk + 1, 10)
                        else           consecWsWalk = maxOf(consecWsWalk - 1, 0)
                        if (wsWaitHit) consecWsWait = minOf(consecWsWait + 1, 10)
                        else           consecWsWait = maxOf(consecWsWait - 1, 0)
                        walkBoxes = wsBoxes

                        val now = System.currentTimeMillis()
                        // walk and wait have independent cooldowns — changing signal always gets through
                        if (consecWsWalk >= 4 && now > wsWalkCooldownUntil) {
                            wsWalkCooldownUntil = now + 30_000L  // 30s cooldown for walk
                            consecWsWalk        = 0
                            consecWsWait        = 0
                            soundPlayer.playWalk()
                            detectionLabel    = "Walk sign is on. Safe to cross."
                            detectionType     = "safe"
                            lastDetectionTime = now
                            activeChips = activeChips.toMutableList()
                                .also { it.add(0, "Walk Signal" to GreenOk) }
                        }
                        if (consecWsWait >= 4 && now > wsWaitCooldownUntil) {
                            wsWaitCooldownUntil = now + 15_000L  // 15s cooldown for wait (urgent)
                            consecWsWait        = 0
                            consecWsWalk        = 0
                            soundPlayer.playWait()
                            detectionLabel    = "Wait. Do not cross."
                            detectionType     = "warning"
                            lastDetectionTime = now
                            activeChips = activeChips.toMutableList()
                                .also { it.add(0, "Wait" to RedAlert) }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Camera UI
    Column(
        Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {

        // stream connection status bar
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape  = RoundedCornerShape(14.dp),
            color  = PanelPurple,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            when {
                                frameStagnant -> YellowWarn
                                streaming     -> GreenOk
                                else          -> RedAlert
                            },
                            CircleShape
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    connectionLabel,
                    fontSize     = 13.sp,
                    fontWeight   = FontWeight.SemiBold,
                    color        = when {
                        connectionLabel == "Live"           -> GreenOk
                        connectionLabel == "Reconnecting..." -> YellowWarn
                        connectionLabel == "No Signal"      -> YellowWarn
                        else                                -> TextMuted
                    }
                )
                Spacer(Modifier.weight(1f))
                if (viewers > 0) {
                    Icon(
                        Icons.Default.Visibility, null,
                        tint     = TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("$viewers watching", fontSize = 11.sp, color = TextMuted)
                }
            }
        }

        // live camera frame — fills all available space
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .weight(1f),
            shape  = RoundedCornerShape(16.dp),
            color  = Color.Black,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (frameBitmap != null) {
                    Image(
                        bitmap             = frameBitmap!!.asImageBitmap(),
                        contentDescription = "Cane camera live feed",
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Fit
                    )
                    // detection bounding box overlay — walksignal + pathsense boxes
                    val allBoxVisible = walkBoxes.isNotEmpty() || pathsenseBoxes.isNotEmpty()
                    if (allBoxVisible) {
                        val bmp = frameBitmap!!
                        Canvas(Modifier.fillMaxSize()) {
                            val scaleX = size.width  / bmp.width
                            val scaleY = size.height / bmp.height
                            val scale  = minOf(scaleX, scaleY)
                            val imgW   = bmp.width  * scale
                            val imgH   = bmp.height * scale
                            val dx     = (size.width  - imgW) / 2f
                            val dy     = (size.height - imgH) / 2f

                            val labelPaint = android.graphics.Paint().apply {
                                color       = android.graphics.Color.WHITE
                                textSize    = 34f
                                typeface    = android.graphics.Typeface.DEFAULT_BOLD
                                isAntiAlias = true
                            }

                            fun drawBox(
                                box: YoloDetector.BoundingBox,
                                accent: Color,
                                nativeBg: Int,
                                labelText: String
                            ) {
                                val l = dx + box.x1 / bmp.width  * imgW
                                val t = dy + box.y1 / bmp.height * imgH
                                val r = dx + box.x2 / bmp.width  * imgW
                                val b = dy + box.y2 / bmp.height * imgH
                                // tinted fill
                                drawRect(accent.copy(alpha = 0.13f), Offset(l, t), Size(r - l, b - t))
                                // border
                                drawRect(accent, Offset(l, t), Size(r - l, b - t),
                                    style = Stroke(width = 3f, cap = StrokeCap.Round))
                                // corner brackets
                                val ca = 16f; val cw = 4f
                                drawLine(accent, Offset(l, t),      Offset(l + ca, t), cw)
                                drawLine(accent, Offset(l, t),      Offset(l, t + ca), cw)
                                drawLine(accent, Offset(r - ca, t), Offset(r, t),      cw)
                                drawLine(accent, Offset(r, t),      Offset(r, t + ca), cw)
                                drawLine(accent, Offset(l, b - ca), Offset(l, b),      cw)
                                drawLine(accent, Offset(l, b),      Offset(l + ca, b), cw)
                                drawLine(accent, Offset(r - ca, b), Offset(r, b),      cw)
                                drawLine(accent, Offset(r, b - ca), Offset(r, b),      cw)
                                // label pill
                                val textW = labelPaint.measureText(labelText)
                                val pillPaint = android.graphics.Paint().apply {
                                    color = nativeBg; isAntiAlias = true
                                }
                                drawIntoCanvas { c ->
                                    c.nativeCanvas.drawRoundRect(
                                        android.graphics.RectF(l, t - 46f, l + textW + 18f, t - 4f),
                                        8f, 8f, pillPaint
                                    )
                                    c.nativeCanvas.drawText(labelText, l + 9f, t - 15f, labelPaint)
                                }
                            }

                            // ── walksignal model boxes (high confidence, solid colours) ──
                            for (box in walkBoxes) {
                                val isWalk = box.label == "walk"
                                drawBox(
                                    box       = box,
                                    accent    = if (isWalk) GreenOk else RedAlert,
                                    nativeBg  = if (isWalk)
                                        android.graphics.Color.argb(210, 34, 197, 94)
                                    else
                                        android.graphics.Color.argb(210, 239, 68, 68),
                                    labelText = "${if (isWalk) "WALK" else "WAIT"} ${(box.score * 100).toInt()}%"
                                )
                            }

                            // ── pathsense model boxes (dashed style to distinguish from above) ──
                            for (box in pathsenseBoxes) {
                                val (accent, nativeBg, tag) = when (box.label) {
                                    "walk_signal" -> Triple(
                                        GreenOk,
                                        android.graphics.Color.argb(170, 34, 197, 94),
                                        "WALK SIG"
                                    )
                                    "stop_signal" -> Triple(
                                        RedAlert,
                                        android.graphics.Color.argb(170, 239, 68, 68),
                                        "STOP SIG"
                                    )
                                    else -> Triple(          // crosswalk
                                        YellowWarn,
                                        android.graphics.Color.argb(170, 245, 158, 11),
                                        "CROSSWALK"
                                    )
                                }
                                drawBox(
                                    box       = box,
                                    accent    = accent,
                                    nativeBg  = nativeBg,
                                    labelText = "$tag ${(box.score * 100).toInt()}%"
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            Modifier
                                .size(72.dp)
                                .background(PanelPurple2, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Videocam, null,
                                tint     = TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            when (connectionLabel) {
                                "Reconnecting..." -> "Reconnecting..."
                                "Offline"         -> "Camera Offline"
                                "No Signal"       -> "Camera Not Transmitting"
                                else              -> "No Live Feed"
                            },
                            fontSize   = 16.sp,
                            color      = TextGray,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when (connectionLabel) {
                                "Reconnecting..." -> "Attempting to reconnect..."
                                "Offline"         -> "Pi camera is not streaming"
                                "No Signal"       -> "Connected but no frames received"
                                else              -> "Waiting for stream..."
                            },
                            fontSize = 12.sp,
                            color    = TextMuted
                        )
                    }
                }
            }
        }

        // detection info panel — color + icon driven by detection type
        val (panelBg, panelBorder, panelIcon) = when {
            isBlocked -> Triple(RedAlert.copy(alpha = 0.08f),     RedAlert,     Icons.Default.Block)
            isBlurry  -> Triple(YellowWarn.copy(alpha = 0.08f),   YellowWarn,   Icons.Default.BlurOn)
            detectionType == "safe"    -> Triple(GreenOk.copy(alpha = 0.08f),      GreenOk,      Icons.Default.CheckCircle)
            detectionType == "warning" -> Triple(RedAlert.copy(alpha = 0.08f),     RedAlert,     Icons.Default.Warning)
            detectionType == "caution" -> Triple(YellowWarn.copy(alpha = 0.08f),   YellowWarn,   Icons.Default.ReportProblem)
            detectionType == "info"    -> Triple(ElectricBlue.copy(alpha = 0.08f), ElectricBlue, Icons.Default.Info)
            else                       -> Triple(PanelPurple, CardBorder, Icons.Default.Visibility)
        }
        val panelText = when {
            isBlocked              -> "Camera view blocked. Please clear the lens."
            isBlurry               -> "Image blurry. Please clean the camera lens."
            detectionLabel != null -> detectionLabel!!
            else                   -> "No pedestrian alerts detected"
        }
        val panelTextColor = when {
            isBlocked              -> RedAlert
            isBlurry               -> YellowWarn
            detectionLabel != null -> panelBorder
            else                   -> TextMuted
        }

        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape  = RoundedCornerShape(14.dp),
            color  = panelBg,
            border = BorderStroke(1.dp, panelBorder.copy(alpha = 0.45f))
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(panelBorder.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(panelIcon, null, tint = panelBorder, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    panelText,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = panelTextColor,
                    modifier   = Modifier.weight(1f)
                )
            }
        }

        // detected class chips — horizontally scrollable
        if (activeChips.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeChips.forEach { (label, color) ->
                    Surface(
                        shape  = RoundedCornerShape(20.dp),
                        color  = color.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
                    ) {
                        Text(
                            label,
                            modifier   = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = color
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // pathsense disclaimer footer — always visible
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(ElectricBlue.copy(alpha = 0.55f), CircleShape)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "PathSense",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = ElectricBlue.copy(alpha = 0.65f)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Assistive tool only. Always verify safety yourself.",
                fontSize = 10.sp,
                color    = TextMuted.copy(alpha = 0.45f)
            )
        }

        Spacer(Modifier.height(96.dp))
    }
}

// speak phrase only if cooldown has elapsed for this key
private fun speakIfCooldown(
    tts: TextToSpeech?,
    ttsReady: Boolean,
    lastSpokenAt: MutableMap<String, Long>,
    key: String,
    phrase: String,
    urgent: Boolean,
    cooldownMs: Long
) {
    if (!ttsReady || tts == null) return
    val now = System.currentTimeMillis()
    if (now - (lastSpokenAt[key] ?: 0L) < cooldownMs) return
    lastSpokenAt[key] = now
    tts.speak(phrase, if (urgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, key)
}

// update detection state + announce if cooldown allows
private fun triggerDetection(
    key: String, label: String, type: String,
    phrase: String, urgent: Boolean, cooldownMs: Long,
    tts: TextToSpeech?, ttsReady: Boolean,
    lastSpokenAt: MutableMap<String, Long>,
    setLabel: (String) -> Unit,
    setType:  (String) -> Unit,
    setTime:  (Long)   -> Unit
) {
    val now = System.currentTimeMillis()
    if (now - (lastSpokenAt[key] ?: 0L) < cooldownMs) return
    lastSpokenAt[key] = now
    setLabel(label); setType(type); setTime(now)
    speakIfCooldown(tts, ttsReady, lastSpokenAt, "${key}_tts", phrase, urgent, 0L)
}

@Composable
fun ApiEventCard(event: EventDto) {
    val (icon, color, label) = eventTypeInfo(event.type)
    val timeStr = formatEventTime(event.created_at)
    val displayType = event.type
        ?.replace("_", " ")
        ?.split(" ")
        ?.joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercaseChar() } }
        ?: label

    Surface(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(16.dp),
        color  = PanelPurple,
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        displayType,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextWhite
                    )
                    if (timeStr.isNotEmpty()) {
                        Text(timeStr, fontSize = 11.sp, color = TextMuted)
                    }
                }
                if (!event.message.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(event.message, fontSize = 12.sp, color = TextMuted)
                }
                if (event.id != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "#${event.id}",
                        fontSize = 10.sp, color = TextMuted.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
