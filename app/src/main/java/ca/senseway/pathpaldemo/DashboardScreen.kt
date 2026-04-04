@file:OptIn(ExperimentalMaterial3Api::class)
package ca.senseway.pathpaldemo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.Bitmap as AndroidBitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.drawable.BitmapDrawable
import java.util.Locale
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// ── CartoDB Dark Matter tile source ─────────────────────────────────────────
private val CARTO_DARK_TILES = XYTileSource(
    "CartoDark", 0, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/"
    ),
    "© OpenStreetMap contributors © CARTO"
)

// ── Live GPS dot marker bitmap ───────────────────────────────────────────────
private fun createLiveDotIcon(ctx: Context): BitmapDrawable {
    val dp   = ctx.resources.displayMetrics.density
    val size = (56 * dp).toInt()
    val bmp  = AndroidBitmap.createBitmap(size, size, AndroidBitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val paint  = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    val cx = size / 2f;  val cy = size / 2f

    paint.color = android.graphics.Color.argb(35, 78, 142, 243)
    canvas.drawCircle(cx, cy, cx - 1f, paint)

    paint.style = AndroidPaint.Style.STROKE
    paint.strokeWidth = 1.5f * dp
    paint.color = android.graphics.Color.argb(85, 78, 142, 243)
    canvas.drawCircle(cx, cy, cx - 2f, paint)
    paint.style = AndroidPaint.Style.FILL

    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(cx, cy, 14f * dp, paint)
    paint.color = android.graphics.Color.argb(255, 78, 142, 243)
    canvas.drawCircle(cx, cy, 11f * dp, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(cx, cy, 3.8f * dp, paint)

    return BitmapDrawable(ctx.resources, bmp)
}

// ── Weather helper functions ─────────────────────────────────────────────────
fun weatherDescription(code: Int): String = when {
    code == 0          -> "Clear sky"
    code in 1..2       -> "Partly cloudy"
    code == 3          -> "Overcast"
    code in 45..48     -> "Foggy"
    code in 51..55     -> "Light drizzle"
    code in 56..57     -> "Freezing drizzle"
    code in 61..63     -> "Raining"
    code in 64..67     -> "Heavy rain"
    code in 71..73     -> "Light snow"
    code in 74..77     -> "Snowing"
    code in 80..82     -> "Rain showers"
    code in 85..86     -> "Snow showers"
    code in 95..99     -> "Thunderstorm"
    else               -> "—"
}

fun weatherIcon(code: Int): ImageVector = when {
    code == 0          -> Icons.Default.WbSunny
    code in 1..3       -> Icons.Default.Cloud
    code in 45..48     -> Icons.Default.Visibility
    code in 51..67     -> Icons.Default.Opacity
    code in 71..77     -> Icons.Default.AcUnit
    code in 80..82     -> Icons.Default.Opacity
    code in 85..86     -> Icons.Default.AcUnit
    code in 95..99     -> Icons.Default.FlashOn
    else               -> Icons.Default.WbSunny
}

fun weatherTint(code: Int): Color = when {
    code == 0          -> Color(0xFFFFB830)
    code in 1..3       -> Color(0xFF9BA7B8)
    code in 45..48     -> Color(0xFF9BA7B8)
    code in 51..67     -> Color(0xFF5BA4CF)
    code in 71..77     -> Color(0xFF80CFFF)
    code in 80..82     -> Color(0xFF5BA4CF)
    code in 85..86     -> Color(0xFF80CFFF)
    code in 95..99     -> Color(0xFFFFD060)
    else               -> Color(0xFFFFB830)
}

fun temperatureColor(temp: Double): Color = when {
    temp > 35   -> Color(0xFFFF6B35)
    temp > 25   -> Color(0xFFFFB830)
    temp >= 10  -> GreenOk
    temp >= 0   -> Color(0xFF5BA4CF)
    else        -> Color(0xFF80CFFF)
}

// ── DashboardScreen ──────────────────────────────────────────────────────────
@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    val sheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    BottomSheetScaffold(
        scaffoldState    = sheetState,
        sheetPeekHeight  = 290.dp,
        sheetShape       = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetContainerColor = PanelPurple,
        containerColor   = BgDeep,
        sheetDragHandle = {
            Column(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.width(44.dp).height(4.dp)
                        .background(TextMuted.copy(alpha = 0.4f), CircleShape)
                )
            }
        },
        sheetContent = { DashboardSheetContent(viewModel) }
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            if (viewModel.latitude != 0.0 && viewModel.longitude != 0.0) {
                OsmFullMap(
                    latitude    = viewModel.latitude,
                    longitude   = viewModel.longitude,
                    onMapCreated = { mapViewRef = it }
                )
                LiveLocationOverlay(modifier = Modifier.align(Alignment.Center))
            } else {
                GpsAcquiringScreen()
            }

            // Top vignette
            Box(
                Modifier
                    .fillMaxWidth().height(110.dp)
                    .align(Alignment.TopStart)
                    .background(
                        Brush.verticalGradient(listOf(BgDeep.copy(alpha = 0.65f), Color.Transparent))
                    )
            )

            // Map action buttons
            if (viewModel.latitude != 0.0 && viewModel.longitude != 0.0) {
                val ctx = LocalContext.current
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 16.dp, start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MapFab(Icons.Default.Map, "Open in Google Maps") {
                        val uri = Uri.parse(
                            "geo:${viewModel.latitude},${viewModel.longitude}" +
                            "?q=${viewModel.latitude},${viewModel.longitude}(PathPal+Device)"
                        )
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        if (intent.resolveActivity(ctx.packageManager) != null) {
                            ctx.startActivity(intent)
                        } else {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(
                                    "https://www.google.com/maps/search/?api=1" +
                                    "&query=${viewModel.latitude},${viewModel.longitude}"
                                ))
                            )
                        }
                    }
                    MapFab(Icons.Default.GpsFixed, "Recenter", iconTint = TextGray) {
                        mapViewRef?.controller?.animateTo(
                            GeoPoint(viewModel.latitude, viewModel.longitude)
                        )
                    }
                }
            }
        }
    }
}

// ── Bottom sheet content — tabbed ────────────────────────────────────────────
@Composable
fun DashboardSheetContent(viewModel: AppViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxWidth()) {
        // Segmented tab selector
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(
                "Overview"  to Icons.Default.Dashboard,
                "Alerts"    to Icons.Default.NotificationsActive
            ).forEachIndexed { idx, (label, icon) ->
                val sel = selectedTab == idx
                Surface(
                    onClick = { selectedTab = idx },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (sel) ElectricBlue.copy(alpha = 0.14f) else Color.Transparent,
                    border = BorderStroke(1.dp, if (sel) ElectricBlue.copy(alpha = 0.45f) else CardBorder)
                ) {
                    Row(
                        Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            icon, null,
                            tint = if (sel) ElectricBlue else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            label, fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (sel) ElectricBlue else TextMuted
                        )
                    }
                }
            }
        }

        // Animated tab content
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (targetState > initialState)
                    slideInHorizontally { it / 2 } + fadeIn(tween(200)) togetherWith
                    slideOutHorizontally { -it / 2 } + fadeOut(tween(200))
                else
                    slideInHorizontally { -it / 2 } + fadeIn(tween(200)) togetherWith
                    slideOutHorizontally { it / 2 } + fadeOut(tween(200))
            },
            label = "sheet_tab"
        ) { tab ->
            when (tab) {
                0    -> DashboardOverviewPanel(viewModel)
                else -> DashboardAlertsPanel(viewModel)
            }
        }
    }
}

// ── Overview panel (metrics) ─────────────────────────────────────────────────
@Composable
fun DashboardOverviewPanel(viewModel: AppViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("PathPal Device", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingOnlineDot()
                    Spacer(Modifier.width(6.dp))
                    Text("Online  •  Live tracking", fontSize = 13.sp, color = TextMuted)
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = GreenOk.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, GreenOk.copy(alpha = 0.25f))
            ) {
                Text(
                    "Active",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp, color = GreenOk, fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Metrics grid
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashMetricCard(
                modifier   = Modifier.weight(1f),
                icon       = Icons.Default.BatteryFull,
                iconTint   = if (viewModel.battery > 20) GreenOk else YellowWarn,
                label      = "Battery",
                value      = "${viewModel.battery}%",
                valueColor = if (viewModel.battery > 20) GreenOk else YellowWarn,
                anim       = MetricAnim.GLOW
            )
            DashMetricCard(
                modifier   = Modifier.weight(1f),
                icon       = Icons.Default.Favorite,
                iconTint   = Color(0xFFFF6B8A),
                label      = "Heart Rate",
                value      = if (viewModel.heartRate > 0) "${viewModel.heartRate} bpm" else "-- bpm",
                valueColor = Color(0xFFFF6B8A),
                anim       = MetricAnim.HEARTBEAT
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashMetricCard(
                modifier   = Modifier.weight(1f),
                icon       = Icons.Default.Terrain,
                iconTint   = ElectricBlue,
                label      = "Altitude",
                value      = if (!viewModel.elevation.isNaN()) "${viewModel.elevation.toInt()} m" else "-- m",
                valueColor = ElectricBlue,
                anim       = MetricAnim.BOB
            )
            DashMetricCard(
                modifier   = Modifier.weight(1f),
                icon       = weatherIcon(viewModel.weatherCode),
                iconTint   = weatherTint(viewModel.weatherCode),
                label      = "Weather",
                value      = if (!viewModel.temperature.isNaN()) "${viewModel.temperature.toInt()}°C" else "-- °C",
                valueColor = if (!viewModel.temperature.isNaN()) temperatureColor(viewModel.temperature) else TextMuted,
                anim       = MetricAnim.SPIN
            )
        }

        if (viewModel.latitude != 0.0 && viewModel.longitude != 0.0) {
            GpsLocationCard(viewModel.latitude, viewModel.longitude)
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ── Alerts panel ─────────────────────────────────────────────────────────────
@Composable
fun DashboardAlertsPanel(viewModel: AppViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Weather summary card — or loading placeholder
        if (viewModel.weatherCode < 0) {
            WeatherLoadingCard()
        } else {
            WeatherSummaryCard(viewModel)
            // All computed alerts (pavement, wind, temp, visibility, AQI, UV)
            viewModel.weatherAlerts.forEach { alert ->
                WeatherAlertCard(alert)
            }
        }

        // Geofence status card (always visible)
        GeofenceStatusCard(viewModel.isWithinGeofence)

        Spacer(Modifier.height(4.dp))
    }
}

// ── Weather summary card ─────────────────────────────────────────────────────
@Composable
fun WeatherSummaryCard(viewModel: AppViewModel) {
    val tint = weatherTint(viewModel.weatherCode)
    Surface(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(18.dp),
        color  = PanelPurple2,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.25f))
    ) {
        Column(Modifier.padding(16.dp)) {
            // Condition row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(tint.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(weatherIcon(viewModel.weatherCode), null, tint = tint, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        weatherDescription(viewModel.weatherCode),
                        fontWeight = FontWeight.SemiBold, color = TextWhite, fontSize = 15.sp
                    )
                    Text("Current conditions", fontSize = 11.sp, color = TextMuted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${viewModel.temperature.toInt()}°C",
                        fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        color = temperatureColor(viewModel.temperature)
                    )
                    Text(
                        "Feels ${viewModel.apparentTemperature.toInt()}°",
                        fontSize = 11.sp, color = TextMuted
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = CardBorder)
            Spacer(Modifier.height(12.dp))

            // Quick stats row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                WeatherStatChip(Icons.Default.Opacity,      "${viewModel.humidity}%",                Color(0xFF5BA4CF), "Humidity")
                WeatherStatChip(Icons.Default.Air,          "${viewModel.windSpeed.toInt()} km/h",   Color(0xFF8BB4D4), "Wind")
                WeatherStatChip(Icons.Default.Cloud,        "${viewModel.cloudCover}%",              TextGray,          "Cloud")
                if (!viewModel.elevation.isNaN())
                    WeatherStatChip(Icons.Default.Terrain,  "${viewModel.elevation.toInt()} m",      ElectricBlue,      "Elevation")
            }
        }
    }
}

@Composable
fun WeatherStatChip(icon: ImageVector, value: String, color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
        Text(label, fontSize = 10.sp, color = TextMuted)
    }
}

// ── Individual alert card ────────────────────────────────────────────────────
@Composable
fun WeatherAlertCard(alert: WeatherAlert) {
    val (color, icon) = when (alert.severity) {
        AlertSeverity.SAFE    -> GreenOk   to Icons.Default.CheckCircle
        AlertSeverity.CAUTION -> YellowWarn to Icons.Default.Warning
        AlertSeverity.UNSAFE  -> RedAlert   to Icons.Default.Error
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(16.dp),
        color  = color.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    alert.category.uppercase(),
                    fontSize = 9.sp, color = color,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(alert.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
                Spacer(Modifier.height(2.dp))
                Text(alert.description, fontSize = 12.sp, color = TextMuted)
            }
        }
    }
}

// ── Geofence status card ─────────────────────────────────────────────────────
@Composable
fun GeofenceStatusCard(isWithin: Boolean) {
    val color = if (isWithin) GreenOk else RedAlert
    val title = if (isWithin) "Within geofenced limits" else "Outside geofenced limits"
    val desc  = if (isWithin)
        "User is within geofenced limits. All monitored zones are clear."
    else
        "User is outside defined boundaries. Alert has been dispatched."

    Surface(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(16.dp),
        color  = color.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.GpsFixed, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "GEOFENCE",
                    fontSize = 9.sp, color = color,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
                Spacer(Modifier.height(2.dp))
                Text(desc, fontSize = 12.sp, color = TextMuted)
            }
            // Live status dot
            PulsingOnlineDot()
        }
    }
}

// ── Weather loading placeholder ───────────────────────────────────────────────
@Composable
fun WeatherLoadingCard() {
    val t = rememberInfiniteTransition(label = "wl")
    val pulse by t.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p"
    )
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PanelPurple2,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                color       = ElectricBlue.copy(alpha = pulse),
                strokeWidth = 2.dp,
                modifier    = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Fetching weather data…", fontSize = 14.sp, color = TextWhite, fontWeight = FontWeight.SemiBold)
                Text("Requires active GPS signal", fontSize = 12.sp, color = TextMuted)
            }
        }
    }
}

// ── Map composables ───────────────────────────────────────────────────────────
@Composable
fun MapFab(
    icon:        ImageVector,
    contentDesc: String,
    iconTint:    Color = ElectricBlue,
    onClick:     () -> Unit
) {
    SmallFloatingActionButton(
        onClick          = onClick,
        containerColor   = PanelPurple.copy(alpha = 0.92f),
        contentColor     = iconTint,
        elevation        = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
        shape            = RoundedCornerShape(14.dp)
    ) {
        Icon(icon, contentDesc, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun GpsAcquiringScreen() {
    val t = rememberInfiniteTransition(label = "gps_load")
    val sweep by t.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "sweep"
    )
    val ringPulse by t.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rp"
    )
    Box(Modifier.fillMaxSize().background(BgDeep), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2, size.height / 2)
                    val r = size.minDimension / 2
                    drawCircle(ElectricBlue.copy(alpha = 0.07f), r, c)
                    drawCircle(ElectricBlue.copy(alpha = 0.12f), r * 0.68f, c)
                    drawArc(
                        color      = ElectricBlue.copy(alpha = ringPulse),
                        startAngle = sweep,
                        sweepAngle = 260f,
                        useCenter  = false,
                        style      = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                        topLeft    = Offset(c.x - r * 0.82f, c.y - r * 0.82f),
                        size       = Size(r * 1.64f, r * 1.64f)
                    )
                }
                Icon(Icons.Default.GpsFixed, null, tint = ElectricBlue, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("Acquiring GPS signal", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Connecting to Senseway network…", color = TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun OsmFullMap(
    latitude:     Double,
    longitude:    Double,
    onMapCreated: (MapView) -> Unit = {}
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory  = { ctx ->
            Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", 0))
            MapView(ctx).apply {
                setTileSource(CARTO_DARK_TILES)
                setMultiTouchControls(true)
                controller.setZoom(16.0)
                controller.setCenter(GeoPoint(latitude, longitude))
                isTilesScaledToDpi = true
                setBackgroundColor(android.graphics.Color.parseColor("#06060F"))
                val marker = Marker(this).apply {
                    position = GeoPoint(latitude, longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon     = createLiveDotIcon(ctx)
                    title    = null
                }
                overlays.add(marker)
                onMapCreated(this)
            }
        },
        update = { mapView ->
            mapView.controller.animateTo(GeoPoint(latitude, longitude))
            mapView.overlays.filterIsInstance<Marker>().firstOrNull()
                ?.position = GeoPoint(latitude, longitude)
            mapView.invalidate()
        }
    )
}

@Composable
fun LiveLocationOverlay(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "live_dot")
    val r1Scale by t.animateFloat(
        initialValue = 1f, targetValue = 3.2f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "r1s"
    )
    val r1Alpha by t.animateFloat(
        initialValue = 0.55f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "r1a"
    )
    val r2Scale by t.animateFloat(
        initialValue = 1f, targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            tween(2200, delayMillis = 700, easing = FastOutSlowInEasing), RepeatMode.Restart
        ), label = "r2s"
    )
    val r2Alpha by t.animateFloat(
        initialValue = 0.45f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(2200, delayMillis = 700, easing = FastOutSlowInEasing), RepeatMode.Restart
        ), label = "r2a"
    )
    val glowAlpha by t.animateFloat(
        initialValue = 0.18f, targetValue = 0.38f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ga"
    )
    Canvas(modifier = modifier.size(88.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(ElectricBlue.copy(alpha = r1Alpha * 0.35f), 20.dp.toPx() * r1Scale, center)
        drawCircle(ElectricBlue.copy(alpha = r2Alpha * 0.45f), 20.dp.toPx() * r2Scale, center)
        drawCircle(ElectricBlue.copy(alpha = glowAlpha), 22.dp.toPx(), center)
        drawCircle(Color.White,    11.dp.toPx(), center)
        drawCircle(ElectricBlue,    9.dp.toPx(), center)
        drawCircle(Color.White,   3.2.dp.toPx(), center)
    }
}

// ── Shared small composables ──────────────────────────────────────────────────
enum class MetricAnim { HEARTBEAT, SPIN, BOB, GLOW }

@Composable
fun DashMetricCard(
    modifier:   Modifier = Modifier,
    icon:       ImageVector,
    iconTint:   Color,
    label:      String,
    value:      String,
    valueColor: Color = TextWhite,
    anim:       MetricAnim? = null
) {
    val transition = rememberInfiniteTransition(label = "metric_$label")

    val heartScale by transition.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 1500
            1.0f  at 0   using FastOutSlowInEasing
            1.35f at 120 using FastOutSlowInEasing
            1.0f  at 280 using FastOutSlowInEasing
            1.20f at 400 using FastOutSlowInEasing
            1.0f  at 560
            1.0f  at 1500
        }), label = "hs"
    )
    val sunAngle  by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "sa"
    )
    val sunBright by transition.animateFloat(
        0.75f, 1f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sb"
    )
    val bobY      by transition.animateFloat(
        0f, -4f,
        infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "by"
    )
    val glowAlpha by transition.animateFloat(
        0.12f, 0.45f,
        infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ga"
    )

    val iconMod = when (anim) {
        MetricAnim.HEARTBEAT -> Modifier.size(18.dp).scale(heartScale)
        MetricAnim.SPIN      -> Modifier.size(20.dp).rotate(sunAngle)
        MetricAnim.BOB       -> Modifier.size(18.dp).offset(y = bobY.dp)
        else                 -> Modifier.size(18.dp)
    }
    val effectiveTint = if (anim == MetricAnim.SPIN) iconTint.copy(alpha = sunBright) else iconTint
    val bgAlpha       = if (anim == MetricAnim.GLOW) glowAlpha else 0.15f

    Surface(
        modifier = modifier.height(100.dp),
        shape    = RoundedCornerShape(18.dp),
        color    = PanelPurple2,
        border   = BorderStroke(
            1.dp,
            if (anim == MetricAnim.HEARTBEAT) iconTint.copy(alpha = heartScale * 0.15f) else CardBorder
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                Modifier.size(32.dp)
                    .background(iconTint.copy(alpha = bgAlpha), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = effectiveTint, modifier = iconMod)
            }
            Column {
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = valueColor)
                Text(label, fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

@Composable
fun PulsingOnlineDot() {
    val t = rememberInfiniteTransition(label = "online")
    val ringScale by t.animateFloat(
        1f, 2.2f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "rs"
    )
    val ringAlpha by t.animateFloat(
        0.6f, 0f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "ra"
    )
    Canvas(Modifier.size(14.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(GreenOk.copy(alpha = ringAlpha), 5.dp.toPx() * ringScale, c)
        drawCircle(GreenOk, 4.dp.toPx(), c)
    }
}

@Composable
fun GpsLocationCard(latitude: Double, longitude: Double) {
    val t = rememberInfiniteTransition(label = "gps")
    val pingScale by t.animateFloat(
        1f, 1.9f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "ps"
    )
    val pingAlpha by t.animateFloat(
        0.5f, 0f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "pa"
    )
    Surface(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(16.dp),
        color  = PanelPurple2,
        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.18f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(36.dp)) {
                    val c = Offset(size.width / 2, size.height / 2)
                    drawCircle(ElectricBlue.copy(alpha = pingAlpha * 0.4f), 18.dp.toPx() * pingScale, c)
                    drawCircle(ElectricBlue.copy(alpha = 0.15f), 18.dp.toPx(), c)
                }
                Icon(Icons.Default.LocationOn, null, tint = ElectricBlue, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GPS Location", fontSize = 11.sp, color = TextMuted)
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = ElectricBlue.copy(alpha = 0.15f)) {
                        Text(
                            "LIVE",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ElectricBlue
                        )
                    }
                }
                Text(
                    "${String.format(Locale.US, "%.5f", latitude)}°   ${String.format(Locale.US, "%.5f", longitude)}°",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextGray
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextMuted.copy(alpha = 0.5f))
        }
    }
}
