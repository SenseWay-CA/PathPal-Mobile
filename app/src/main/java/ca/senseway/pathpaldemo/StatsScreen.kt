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
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.FileInputStream
import java.nio.channels.FileChannel

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
    // stream state
    var streaming       by remember { mutableStateOf(false) }
    var viewers         by remember { mutableIntStateOf(0) }
    var frameBitmap     by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var connectionLabel by remember { mutableStateOf("Connecting...") }
    val mainHandler     = remember { Handler(Looper.getMainLooper()) }

    // pathsense detection state
    var detectionLabel     by remember { mutableStateOf<String?>(null) }
    var detectionType      by remember { mutableStateOf("") }
    var lastDetectionTime  by remember { mutableLongStateOf(0L) }
    var isBlurry           by remember { mutableStateOf(false) }
    var isBlocked          by remember { mutableStateOf(false) }
    var ttsReady           by remember { mutableStateOf(false) }
    var activeChips        by remember { mutableStateOf<List<Pair<String, Color>>>(emptyList()) }
    var frameCounter       by remember { mutableIntStateOf(0) }
    var consecutiveWalk    by remember { mutableIntStateOf(0) }
    var consecutiveStop    by remember { mutableIntStateOf(0) }
    var consecutiveTlState by remember { mutableStateOf("") }
    var consecutiveTlCount by remember { mutableIntStateOf(0) }
    var blurConsecutive    by remember { mutableIntStateOf(0) }
    var blockedConsecutive by remember { mutableIntStateOf(0) }
    val lastSpokenAt       = remember { mutableStateMapOf<String, Long>() }
    val context            = LocalContext.current
    var ttsInstance        by remember { mutableStateOf<TextToSpeech?>(null) }

    // cache interpreters so we only load from assets once
    val interpHolder = remember { arrayOfNulls<Interpreter>(2) }

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
                            frameBitmap?.recycle()  // free old frame memory
                            frameBitmap = bmp
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
                tts?.language = java.util.Locale.CANADA
                tts?.setPitch(1.0f)
                tts?.setSpeechRate(0.92f)
                ttsReady = true
                ttsInstance = tts
                tts?.speak(
                    "PathSense is active. Ready to assist.",
                    TextToSpeech.QUEUE_ADD, null, "startup"
                )
            }
        }
        onDispose {
            ttsInstance = null
            tts?.stop()
            tts?.shutdown()
        }
    }

    // auto clear detection banner 10s after last detection
    LaunchedEffect(lastDetectionTime) {
        if (lastDetectionTime == 0L) return@LaunchedEffect
        kotlinx.coroutines.delay(10_000)
        if (System.currentTimeMillis() - lastDetectionTime >= 10_000) {
            detectionLabel = null
            detectionType  = ""
            activeChips    = emptyList()
        }
    }

    // blur + blocked check on every new frame
    LaunchedEffect(frameBitmap) {
        val bmp = frameBitmap ?: return@LaunchedEffect
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

    // ml inference — every 3rd frame on background thread
    LaunchedEffect(frameBitmap) {
        val bmp = frameBitmap ?: return@LaunchedEffect
        frameCounter++
        if (frameCounter % 3 != 0) return@LaunchedEffect

        withContext(Dispatchers.Default) {
            try {
                // load models lazily — cached after first load
                if (interpHolder[0] == null)
                    interpHolder[0] = loadInterpreter(context, "pathsense_pedestrian.tflite")
                val interpreter = interpHolder[0] ?: return@withContext
                if (interpHolder[1] == null)
                    interpHolder[1] = loadInterpreter(context, "pathsense_traffic_light_state.tflite")
                val tlInterpreter = interpHolder[1]

                // resize to 640x640 and normalize pixels 0-1
                val resized = android.graphics.Bitmap.createScaledBitmap(bmp, 640, 640, true)
                val inputBuffer = ByteBuffer.allocateDirect(1 * 640 * 640 * 3 * 4)
                inputBuffer.order(ByteOrder.nativeOrder())
                for (py in 0 until 640) {
                    for (px in 0 until 640) {
                        val pixel = resized.getPixel(px, py)
                        inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
                        inputBuffer.putFloat(((pixel shr 8)  and 0xFF) / 255.0f)
                        inputBuffer.putFloat(( pixel         and 0xFF) / 255.0f)
                    }
                }

                // run yolo pedestrian + sign detection
                val output = Array(1) { Array(8400) { FloatArray(10) } }
                interpreter.run(inputBuffer, output)

                val classThresholds = mapOf(
                    0 to 0.85f,   // crosswalk
                    1 to 0.88f,   // walk_signal
                    2 to 0.88f,   // stop_signal
                    3 to 0.82f,   // pedestrian_crossing_sign
                    4 to 0.82f,   // school_crossing_sign
                    5 to 0.80f    // traffic_light
                )
                val detections = parseYoloOutput(output[0], confThreshold = 0.80f, nmsThreshold = 0.45f)
                val detectedClasses = detections
                    .filter { it.confidence >= (classThresholds[it.classId] ?: 0.82f) }
                    .map { it.classId }
                    .toSet()

                // traffic light classifier on cropped bounding box
                var tlState = ""
                if (5 in detectedClasses && tlInterpreter != null) {
                    val tlDet = detections.firstOrNull { it.classId == 5 } ?: return@withContext
                    val cropX = (tlDet.cx - tlDet.w / 2).coerceIn(0f, 1f)
                    val cropY = (tlDet.cy - tlDet.h / 2).coerceIn(0f, 1f)
                    val cropW = tlDet.w.coerceIn(0f, 1f - cropX)
                    val cropH = tlDet.h.coerceIn(0f, 1f - cropY)
                    val cropBmp = android.graphics.Bitmap.createBitmap(
                        bmp,
                        (cropX * bmp.width).toInt(),
                        (cropY * bmp.height).toInt(),
                        (cropW * bmp.width).toInt().coerceAtLeast(1),
                        (cropH * bmp.height).toInt().coerceAtLeast(1)
                    )
                    val tlResized = android.graphics.Bitmap.createScaledBitmap(cropBmp, 96, 96, true)
                    val tlBuffer = ByteBuffer.allocateDirect(1 * 96 * 96 * 3 * 4)
                    tlBuffer.order(ByteOrder.nativeOrder())
                    for (py in 0 until 96) {
                        for (px in 0 until 96) {
                            val pixel = tlResized.getPixel(px, py)
                            tlBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
                            tlBuffer.putFloat(((pixel shr 8)  and 0xFF) / 255.0f)
                            tlBuffer.putFloat(( pixel         and 0xFF) / 255.0f)
                        }
                    }
                    val tlOutput = Array(1) { FloatArray(3) }
                    tlInterpreter.run(tlBuffer, tlOutput)
                    val states = listOf("red", "yellow", "green")
                    val maxIdx = tlOutput[0].indices.maxByOrNull { tlOutput[0][it] } ?: -1
                    if (maxIdx >= 0 && tlOutput[0][maxIdx] >= 0.90f) {
                        // needs 3 consecutive frames agreeing on same state
                        val stateStr = states[maxIdx]
                        if (stateStr == consecutiveTlState) consecutiveTlCount++
                        else { consecutiveTlState = stateStr; consecutiveTlCount = 1 }
                        if (consecutiveTlCount >= 3) tlState = stateStr
                    }
                }

                // track consecutive walk/stop frames for confirmation
                if (1 in detectedClasses) consecutiveWalk++ else consecutiveWalk = 0
                if (2 in detectedClasses) consecutiveStop++ else consecutiveStop = 0
                val confirmedWalk = consecutiveWalk >= 4
                val confirmedStop = consecutiveStop >= 4
                val chips = mutableListOf<Pair<String, Color>>()

                withContext(Dispatchers.Main) {
                    // priority 1: stop signal — do not cross
                    if (confirmedStop) {
                        triggerDetection("stop_signal",
                            "Stop — Do not cross. Wait for the walk signal.", "warning",
                            "Stop. Do not cross.", urgent = true, cooldownMs = 8_000L,
                            tts = ttsInstance, ttsReady = ttsReady, lastSpokenAt = lastSpokenAt,
                            setLabel = { detectionLabel = it },
                            setType  = { detectionType = it },
                            setTime  = { lastDetectionTime = it })
                        chips.add("Stop Signal" to RedAlert)
                    }
                    // priority 2: walk signal — safe to cross
                    if (confirmedWalk && !confirmedStop) {
                        triggerDetection("walk_signal",
                            "Walk sign is on — Safe to cross", "safe",
                            "Walk sign is on. Safe to cross.", urgent = true, cooldownMs = 8_000L,
                            tts = ttsInstance, ttsReady = ttsReady, lastSpokenAt = lastSpokenAt,
                            setLabel = { detectionLabel = it },
                            setType  = { detectionType = it },
                            setTime  = { lastDetectionTime = it })
                        chips.add("Walk Signal" to GreenOk)
                    }
                    // priority 3: crosswalk with no signal present
                    if (0 in detectedClasses && !confirmedWalk && !confirmedStop) {
                        triggerDetection("crosswalk",
                            "Crosswalk detected — Cross slowly and check both ways", "caution",
                            "Crosswalk ahead. Check traffic both ways.",
                            urgent = false, cooldownMs = 12_000L,
                            tts = ttsInstance, ttsReady = ttsReady, lastSpokenAt = lastSpokenAt,
                            setLabel = { detectionLabel = it },
                            setType  = { detectionType = it },
                            setTime  = { lastDetectionTime = it })
                        chips.add("Crosswalk" to YellowWarn)
                    }
                    // priority 4: pedestrian crossing sign
                    if (3 in detectedClasses) {
                        triggerDetection("ped_sign",
                            "Pedestrian crossing ahead", "info",
                            "Pedestrian crossing ahead.",
                            urgent = false, cooldownMs = 15_000L,
                            tts = ttsInstance, ttsReady = ttsReady, lastSpokenAt = lastSpokenAt,
                            setLabel = { detectionLabel = it },
                            setType  = { detectionType = it },
                            setTime  = { lastDetectionTime = it })
                        chips.add("Crossing Sign" to ElectricBlue)
                    }
                    // priority 5: school crossing sign
                    if (4 in detectedClasses) {
                        triggerDetection("school_sign",
                            "School crossing zone — Reduced speed area", "info",
                            "School crossing zone ahead.",
                            urgent = false, cooldownMs = 15_000L,
                            tts = ttsInstance, ttsReady = ttsReady, lastSpokenAt = lastSpokenAt,
                            setLabel = { detectionLabel = it },
                            setType  = { detectionType = it },
                            setTime  = { lastDetectionTime = it })
                        chips.add("School Zone" to YellowWarn)
                    }
                    // priority 6: traffic light state
                    when (tlState) {
                        "red" -> {
                            triggerDetection("tl_red",
                                "Red light — Do not cross", "warning",
                                "Red light.", urgent = true, cooldownMs = 6_000L,
                                tts = ttsInstance, ttsReady = ttsReady, lastSpokenAt = lastSpokenAt,
                                setLabel = { detectionLabel = it },
                                setType  = { detectionType = it },
                                setTime  = { lastDetectionTime = it })
                            chips.add("Red Light" to RedAlert)
                        }
                        "green" -> {
                            triggerDetection("tl_green",
                                "Green light", "safe",
                                "Green light.", urgent = false, cooldownMs = 6_000L,
                                tts = ttsInstance, ttsReady = ttsReady, lastSpokenAt = lastSpokenAt,
                                setLabel = { detectionLabel = it },
                                setType  = { detectionType = it },
                                setTime  = { lastDetectionTime = it })
                            chips.add("Green Light" to GreenOk)
                        }
                        "yellow" -> {
                            triggerDetection("tl_yellow",
                                "Light is changing — Caution", "caution",
                                "Light is changing. Caution.", urgent = false, cooldownMs = 6_000L,
                                tts = ttsInstance, ttsReady = ttsReady, lastSpokenAt = lastSpokenAt,
                                setLabel = { detectionLabel = it },
                                setType  = { detectionType = it },
                                setTime  = { lastDetectionTime = it })
                            chips.add("Yellow Light" to YellowWarn)
                        }
                    }
                    if (chips.isNotEmpty()) activeChips = chips
                }
            } catch (_: Exception) {}
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────────
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
                        .background(if (streaming) GreenOk else RedAlert, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    connectionLabel,
                    fontSize     = 13.sp,
                    fontWeight   = FontWeight.SemiBold,
                    color        = when {
                        streaming                            -> GreenOk
                        connectionLabel == "Reconnecting..." -> YellowWarn
                        else                                 -> TextMuted
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
            isBlocked              -> "Camera view blocked — Please clear the lens"
            isBlurry               -> "Image blurry — Please clean the camera lens"
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
                "Assistive tool only — always verify safety yourself",
                fontSize = 10.sp,
                color    = TextMuted.copy(alpha = 0.45f)
            )
        }

        Spacer(Modifier.height(96.dp))
    }
}

// bounding box from yolo output row
data class Detection(
    val classId: Int,
    val confidence: Float,
    val cx: Float, val cy: Float,
    val w: Float,  val h: Float
)

// parse raw yolo rows → detections, then nms filter
private fun parseYoloOutput(
    output: Array<FloatArray>,
    confThreshold: Float,
    nmsThreshold: Float
): List<Detection> {
    val raw = mutableListOf<Detection>()
    for (row in output) {
        val conf = row[4]
        if (conf < confThreshold) continue
        val classScores = row.drop(5)
        val classId = classScores.indices.maxByOrNull { classScores[it] } ?: continue
        val classConf = conf * classScores[classId]
        if (classConf < confThreshold) continue
        raw.add(Detection(classId, classConf, row[0], row[1], row[2], row[3]))
    }
    return applyNms(raw, nmsThreshold)
}

// keep highest confidence boxes, suppress overlapping ones
private fun applyNms(detections: List<Detection>, iouThreshold: Float): List<Detection> {
    val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
    val result = mutableListOf<Detection>()
    while (sorted.isNotEmpty()) {
        val best = sorted.removeAt(0)
        result.add(best)
        sorted.removeAll { iou(best, it) > iouThreshold }
    }
    return result
}

// intersection over union between two boxes
private fun iou(a: Detection, b: Detection): Float {
    val ax1 = a.cx - a.w / 2; val ay1 = a.cy - a.h / 2
    val ax2 = a.cx + a.w / 2; val ay2 = a.cy + a.h / 2
    val bx1 = b.cx - b.w / 2; val by1 = b.cy - b.h / 2
    val bx2 = b.cx + b.w / 2; val by2 = b.cy + b.h / 2
    val ix1 = maxOf(ax1, bx1); val iy1 = maxOf(ay1, by1)
    val ix2 = minOf(ax2, bx2); val iy2 = minOf(ay2, by2)
    val inter = maxOf(0f, ix2 - ix1) * maxOf(0f, iy2 - iy1)
    val union = a.w * a.h + b.w * b.h - inter
    return if (union <= 0f) 0f else inter / union
}

// memory-map tflite model from assets — null if file missing
private fun loadInterpreter(context: Context, modelName: String): Interpreter? {
    return try {
        val afd = context.assets.openFd(modelName)
        val fis = FileInputStream(afd.fileDescriptor)
        val buffer = fis.channel.map(
            FileChannel.MapMode.READ_ONLY,
            afd.startOffset,
            afd.declaredLength
        )
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            setUseXNNPACK(true)
        }
        Interpreter(buffer, options)
    } catch (_: Exception) { null }
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
