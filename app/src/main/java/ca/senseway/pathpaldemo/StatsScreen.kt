@file:OptIn(ExperimentalMaterial3Api::class)
package ca.senseway.pathpaldemo

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject

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
    val infiniteTransition = rememberInfiniteTransition(label = "sensors")

    val accelX by infiniteTransition.animateFloat(
        initialValue = -0.48f, targetValue = 0.52f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "aX"
    )
    val accelY by infiniteTransition.animateFloat(
        initialValue = 0.31f, targetValue = -0.42f,
        animationSpec = infiniteRepeatable(tween(2700, easing = LinearEasing), RepeatMode.Reverse),
        label = "aY"
    )
    val accelZ by infiniteTransition.animateFloat(
        initialValue = 9.74f, targetValue = 9.87f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "aZ"
    )
    val gyroAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "gyroAngle"
    )
    val gyroPitch by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(2900, easing = LinearEasing), RepeatMode.Reverse),
        label = "gyroPitch"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            SensorSectionHeader("Accelerometer", Icons.Default.Speed, ElectricBlue)
        }
        item {
            AccelerometerViz(x = accelX, y = accelY, z = accelZ)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SensorChip("X", "${String.format(Locale.US, "%.2f", accelX)} m/s²", ElectricBlue, Modifier.weight(1f))
                SensorChip("Y", "${String.format(Locale.US, "%.2f", accelY)} m/s²", VioletAccent, Modifier.weight(1f))
                SensorChip("Z", "${String.format(Locale.US, "%.2f", accelZ)} m/s²", GreenOk, Modifier.weight(1f))
            }
        }

        item { Spacer(Modifier.height(6.dp)) }

        item {
            SensorSectionHeader("Gyroscope", Icons.Default.Autorenew, VioletAccent)
        }
        item {
            GyroscopeViz(angle = gyroAngle, pitch = gyroPitch)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SensorChip("Roll", "${String.format(Locale.US, "%.1f", gyroAngle % 360)}°", ElectricBlue, Modifier.weight(1f))
                SensorChip("Pitch", "${String.format(Locale.US, "%.1f", gyroPitch)}°", VioletAccent, Modifier.weight(1f))
                SensorChip("Yaw", "0.0°", GreenOk, Modifier.weight(1f))
            }
        }

        item { Spacer(Modifier.height(6.dp)) }

        item {
            SensorSectionHeader("Device Stats", Icons.Default.BarChart, YellowWarn)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatDataCard(
                    Modifier.weight(1f),
                    label = "Battery",
                    value = "${viewModel.battery}%",
                    sub = "Health 100%",
                    color = GreenOk
                )
                StatDataCard(
                    Modifier.weight(1f),
                    label = "Heart Rate",
                    value = "${viewModel.heartRate} bpm",
                    sub = "Latest reading",
                    color = Color(0xFFFF6B8A)
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatDataCard(
                    Modifier.weight(1f),
                    label = "Signal RSSI",
                    value = "-62 dBm",
                    sub = "RF signal strength",
                    color = ElectricBlue
                )
                StatDataCard(
                    Modifier.weight(1f),
                    label = "Altitude",
                    value = if (!viewModel.elevation.isNaN()) "${viewModel.elevation.toInt()} m" else "-- m",
                    sub = "Above sea level",
                    color = VioletAccent
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatDataCard(
                    Modifier.weight(1f),
                    label = "Latitude",
                    value = String.format(Locale.US, "%.4f", viewModel.latitude),
                    sub = "GPS coordinate",
                    color = YellowWarn
                )
                StatDataCard(
                    Modifier.weight(1f),
                    label = "Longitude",
                    value = String.format(Locale.US, "%.4f", viewModel.longitude),
                    sub = "GPS coordinate",
                    color = YellowWarn
                )
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
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
fun GyroscopeViz(angle: Float, pitch: Float) {
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

// ── Camera Tab ──────────────────────────────────────────────────────────────

private const val STREAM_BASE    = "https://api.senseway.ca"
private const val STREAM_STATUS  = "$STREAM_BASE/stream/status"
private const val STREAM_WS      = "wss://api.senseway.ca/ws/stream"

@Composable
fun CameraTab() {
    var streaming       by remember { mutableStateOf(false) }
    var viewers         by remember { mutableIntStateOf(0) }
    var frameBitmap     by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var connectionLabel by remember { mutableStateOf("Connecting...") }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // One-shot status fetch so the UI isn't blank before the WS handshakes
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

    // WebSocket lifecycle — self-reconnects on close/failure
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

                // Binary frame = raw JPEG
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    if (cancelled) return
                    val arr = bytes.toByteArray()
                    val bmp = BitmapFactory.decodeByteArray(arr, 0, arr.size) ?: return
                    mainHandler.post { if (!cancelled) frameBitmap = bmp }
                }

                // Text frame = JSON status update
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (cancelled) return
                    try {
                        val json = JSONObject(text)
                        val s = json.optBoolean("streaming", false)
                        val c = json.optInt("clients", 0)
                        mainHandler.post {
                            if (!cancelled) {
                                streaming       = s
                                viewers         = c
                                connectionLabel = if (s) "Live" else "Offline"
                            }
                        }
                    } catch (_: Exception) {}
                }

                private fun scheduleReconnect() {
                    if (cancelled) return
                    mainHandler.post { if (!cancelled) connectionLabel = "Reconnecting..." }
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

    // ── UI ──────────────────────────────────────────────────────────────────
    Column(
        Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {

        // Status bar
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
                        .background(if (streaming) GreenOk else RedAlert, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    connectionLabel,
                    fontSize     = 13.sp,
                    fontWeight   = FontWeight.SemiBold,
                    color        = when {
                        streaming                         -> GreenOk
                        connectionLabel == "Reconnecting..." -> YellowWarn
                        else                              -> TextMuted
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

        // Camera frame area
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
                                else              -> "Waiting for stream..."
                            },
                            fontSize = 12.sp,
                            color    = TextMuted
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(96.dp))
    }
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
