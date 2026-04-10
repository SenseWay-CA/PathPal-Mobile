@file:OptIn(ExperimentalMaterial3Api::class)
package ca.senseway.pathpaldemo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.graphics.Bitmap as AndroidBitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

// CartoDB Dark Matter tile source
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

// Aim/crosshair marker for the live device position on the map
private fun createAimMarkerIcon(ctx: Context): BitmapDrawable {
    val dp     = ctx.resources.displayMetrics.density
    val size   = (54 * dp).toInt()
    val bmp    = AndroidBitmap.createBitmap(size, size, AndroidBitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val paint  = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    val cx     = size / 2f
    val cy     = size / 2f
    val outer  = cx - 2f * dp

    // Soft outer glow
    paint.color = android.graphics.Color.argb(28, 78, 142, 243)
    canvas.drawCircle(cx, cy, outer, paint)

    // Outer ring
    paint.style = AndroidPaint.Style.STROKE
    paint.strokeWidth = 2f * dp
    paint.color = android.graphics.Color.argb(210, 78, 142, 243)
    canvas.drawCircle(cx, cy, outer * 0.86f, paint)

    // Four crosshair lines with a gap around the center
    paint.strokeCap = android.graphics.Paint.Cap.ROUND
    paint.strokeWidth = 2f * dp
    paint.color = android.graphics.Color.argb(210, 78, 142, 243)
    val lineOuter = outer * 0.68f
    val lineInner = outer * 0.28f
    canvas.drawLine(cx, cy - lineOuter, cx, cy - lineInner, paint)
    canvas.drawLine(cx, cy + lineInner, cx, cy + lineOuter, paint)
    canvas.drawLine(cx - lineOuter, cy, cx - lineInner, cy, paint)
    canvas.drawLine(cx + lineInner, cy, cx + lineOuter, cy, paint)

    // Center dot
    paint.style = AndroidPaint.Style.FILL
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(cx, cy, 5.5f * dp, paint)
    paint.color = android.graphics.Color.argb(255, 78, 142, 243)
    canvas.drawCircle(cx, cy, 4f * dp, paint)

    return BitmapDrawable(ctx.resources, bmp)
}

// Weather helper functions
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
    else               -> "Unknown"
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

// Weather ambient particle effect (snow / rain)
private data class WeatherParticle(
    val x: Float,       // 0..1 normalised start x
    val phase: Float,   // 0..1 timing phase offset
    val size: Float,    // draw radius / stroke width
    val alpha: Float,   // opacity
    val track: Int,     // 0=slow, 1=medium, 2=fast
    val drift: Float    // x drift per progress unit
)

@Composable
fun WeatherParticleEffect(
    weatherCode: Int,
    temperature: Double,
    modifier: Modifier = Modifier
) {
    val isSnow = weatherCode in 71..77 || weatherCode in 85..86 ||
                 (!temperature.isNaN() && temperature < 0.0)
    val isRain = !isSnow && (weatherCode in 51..67 || weatherCode in 80..82)

    if (!isSnow && !isRain) return

    val particles = remember(isSnow) {
        val rng = java.util.Random(77L)
        val count = if (isSnow) 55 else 90
        List(count) {
            WeatherParticle(
                x     = rng.nextFloat(),
                phase = rng.nextFloat(),
                size  = if (isSnow) rng.nextFloat() * 3.5f + 1.5f
                        else rng.nextFloat() * 0.9f + 0.4f,
                alpha = rng.nextFloat() * 0.28f + 0.07f,
                track = rng.nextInt(3),
                drift = rng.nextFloat() * 0.06f - 0.03f
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "wxfx")
    val t1 by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (isSnow) 22_000 else 3_200, easing = LinearEasing)),
        label = "t1"
    )
    val t2 by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (isSnow) 15_000 else 2_100, easing = LinearEasing)),
        label = "t2"
    )
    val t3 by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (isSnow) 10_000 else 1_400, easing = LinearEasing)),
        label = "t3"
    )

    Canvas(modifier = modifier) {
        particles.forEach { p ->
            val t       = when (p.track) { 0 -> t1; 1 -> t2; else -> t3 }
            val progress = (t + p.phase) % 1f

            // Snow drifts side-to-side with a gentle sine wave
            val sineShift = if (isSnow)
                sin((t + p.phase).toDouble() * 6.2831853 * 1.5).toFloat() * p.drift
            else 0f

            val px = ((p.x + sineShift + 1f) % 1f) * size.width
            val py = progress * size.height

            if (isSnow) {
                drawCircle(
                    color  = Color.White.copy(alpha = p.alpha),
                    radius = p.size,
                    center = Offset(px, py)
                )
            } else {
                // Rain: short angled streaks
                drawLine(
                    color       = Color(0xFF8BBCD4).copy(alpha = p.alpha * 0.65f),
                    start       = Offset(px, py),
                    end         = Offset(px + 2.5f, py + 13f),
                    strokeWidth = p.size
                )
            }
        }
    }
}

// Category accent color for a geofence by its name
private fun fenceCategoryColor(name: String): Int {
    val n = name.lowercase()
    return when {
        n.contains("home") || n.contains("house")                       -> android.graphics.Color.argb(255, 34, 197, 94)
        n.contains("work") || n.contains("office") || n.contains("job") -> android.graphics.Color.argb(255, 165, 85, 244)
        n.contains("school") || n.contains("uni") || n.contains("college") -> android.graphics.Color.argb(255, 245, 158, 11)
        n.contains("gym") || n.contains("fitness") || n.contains("sport") -> android.graphics.Color.argb(255, 239, 68, 68)
        n.contains("shop") || n.contains("store") || n.contains("mall")  -> android.graphics.Color.argb(255, 251, 146, 60)
        n.contains("hospital") || n.contains("clinic") || n.contains("doctor") -> android.graphics.Color.argb(255, 239, 68, 68)
        n.contains("park") || n.contains("garden")                       -> android.graphics.Color.argb(255, 74, 222, 128)
        n.contains("cafe") || n.contains("coffee") || n.contains("restaurant") -> android.graphics.Color.argb(255, 251, 191, 36)
        else -> android.graphics.Color.argb(255, 78, 142, 243)
    }
}

// Clean circular icon marker for a geofence center (tap to reveal name)
private fun createFenceIconMarker(ctx: Context, name: String): BitmapDrawable {
    val dp       = ctx.resources.displayMetrics.density
    val size     = (50 * dp).toInt()
    val bmp      = AndroidBitmap.createBitmap(size, size, AndroidBitmap.Config.ARGB_8888)
    val canvas   = AndroidCanvas(bmp)
    val paint    = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    val cx       = size / 2f
    val cy       = size / 2f
    val circleR  = cx - 3f * dp
    val catColor = fenceCategoryColor(name)
    val cr = android.graphics.Color.red(catColor)
    val cg = android.graphics.Color.green(catColor)
    val cb = android.graphics.Color.blue(catColor)

    // Soft outer glow
    paint.style = AndroidPaint.Style.FILL
    paint.color = android.graphics.Color.argb(28, cr, cg, cb)
    canvas.drawCircle(cx, cy, circleR + 4f * dp, paint)

    // Filled circle background
    paint.color = android.graphics.Color.argb(225, cr, cg, cb)
    canvas.drawCircle(cx, cy, circleR, paint)

    // Inner lighter ring for depth
    paint.style       = AndroidPaint.Style.STROKE
    paint.strokeWidth = 1.5f * dp
    paint.color       = android.graphics.Color.argb(90, 255, 255, 255)
    canvas.drawCircle(cx, cy, circleR - 1.5f * dp, paint)

    // White vector icon
    paint.style       = AndroidPaint.Style.FILL
    paint.strokeWidth = 1.8f * dp
    paint.color       = android.graphics.Color.WHITE
    drawFenceCategoryIcon(canvas, paint, dp, name, cx, cy, circleR * 0.48f)

    return BitmapDrawable(ctx.resources, bmp)
}

// Draws a simple geometric white icon for the geofence category
private fun drawFenceCategoryIcon(
    canvas: AndroidCanvas,
    paint:  AndroidPaint,
    dp:     Float,
    name:   String,
    cx: Float, cy: Float, r: Float
) {
    val n = name.lowercase(Locale.ROOT)
    when {
        // House
        n.contains("home") || n.contains("house") -> {
            val roof = android.graphics.Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + r, cy - r * 0.12f)
                lineTo(cx - r, cy - r * 0.12f)
                close()
            }
            canvas.drawPath(roof, paint)
            canvas.drawRect(cx - r * 0.60f, cy - r * 0.12f, cx + r * 0.60f, cy + r, paint)
        }
        // Office building
        n.contains("work") || n.contains("office") || n.contains("job") -> {
            canvas.drawRect(cx - r * 0.65f, cy - r, cx + r * 0.65f, cy + r, paint)
            // Dark window cut-outs
            paint.color = android.graphics.Color.argb(180, 10, 12, 22)
            val wW = r * 0.22f; val wH = r * 0.24f
            canvas.drawRect(cx - r * 0.38f, cy - r * 0.80f, cx - r * 0.38f + wW, cy - r * 0.80f + wH, paint)
            canvas.drawRect(cx + r * 0.16f, cy - r * 0.80f, cx + r * 0.16f + wW, cy - r * 0.80f + wH, paint)
            canvas.drawRect(cx - r * 0.38f, cy - r * 0.38f, cx - r * 0.38f + wW, cy - r * 0.38f + wH, paint)
            canvas.drawRect(cx + r * 0.16f, cy - r * 0.38f, cx + r * 0.16f + wW, cy - r * 0.38f + wH, paint)
            paint.color = android.graphics.Color.WHITE
        }
        // School / education
        n.contains("school") || n.contains("uni") || n.contains("college") -> {
            // Mortarboard: flat brim + box cap body
            canvas.drawRect(cx - r, cy - r * 0.18f, cx + r, cy + r * 0.18f, paint)
            canvas.drawRect(cx - r * 0.52f, cy - r, cx + r * 0.52f, cy - r * 0.18f, paint)
            // Tassel
            paint.style       = AndroidPaint.Style.STROKE
            paint.strokeWidth = 1.6f * dp
            canvas.drawLine(cx + r * 0.42f, cy + r * 0.18f, cx + r * 0.42f, cy + r * 0.72f, paint)
            paint.style = AndroidPaint.Style.FILL
            canvas.drawCircle(cx + r * 0.42f, cy + r * 0.75f, r * 0.14f, paint)
        }
        // Gym / fitness
        n.contains("gym") || n.contains("fitness") || n.contains("sport") -> {
            // Dumbbell: centre bar + end weights
            canvas.drawRect(cx - r, cy - r * 0.16f, cx + r, cy + r * 0.16f, paint)
            canvas.drawRoundRect(RectF(cx - r, cy - r * 0.58f, cx - r * 0.54f, cy + r * 0.58f), r * 0.14f, r * 0.14f, paint)
            canvas.drawRoundRect(RectF(cx + r * 0.54f, cy - r * 0.58f, cx + r, cy + r * 0.58f), r * 0.14f, r * 0.14f, paint)
        }
        // Medical
        n.contains("hospital") || n.contains("clinic") || n.contains("doctor") -> {
            val arm = r * 0.28f
            canvas.drawRect(cx - arm, cy - r, cx + arm, cy + r, paint)
            canvas.drawRect(cx - r, cy - arm, cx + r, cy + arm, paint)
        }
        // Shopping
        n.contains("shop") || n.contains("store") || n.contains("mall") -> {
            val bag = android.graphics.Path().apply {
                moveTo(cx - r * 0.78f, cy - r * 0.18f)
                lineTo(cx + r * 0.78f, cy - r * 0.18f)
                lineTo(cx + r * 0.58f, cy + r)
                lineTo(cx - r * 0.58f, cy + r)
                close()
            }
            canvas.drawPath(bag, paint)
            // Handle arc
            paint.style       = AndroidPaint.Style.STROKE
            paint.strokeWidth = r * 0.22f
            canvas.drawArc(
                RectF(cx - r * 0.40f, cy - r, cx + r * 0.40f, cy - r * 0.18f + r * 0.12f),
                180f, 180f, false, paint
            )
            paint.style = AndroidPaint.Style.FILL
        }
        // Park / nature
        n.contains("park") || n.contains("garden") -> {
            canvas.drawCircle(cx, cy - r * 0.18f, r * 0.72f, paint)
            canvas.drawRect(cx - r * 0.13f, cy + r * 0.42f, cx + r * 0.13f, cy + r, paint)
        }
        // Cafe / food
        n.contains("cafe") || n.contains("coffee") || n.contains("restaurant") || n.contains("food") -> {
            canvas.drawRoundRect(RectF(cx - r * 0.62f, cy - r * 0.28f, cx + r * 0.62f, cy + r), r * 0.15f, r * 0.15f, paint)
            paint.style       = AndroidPaint.Style.STROKE
            paint.strokeWidth = r * 0.22f
            canvas.drawArc(
                RectF(cx + r * 0.38f, cy - r * 0.12f, cx + r, cy + r * 0.65f),
                -75f, 195f, false, paint
            )
            paint.style = AndroidPaint.Style.FILL
        }
        // Default: location pin (circle + teardrop tip)
        else -> {
            canvas.drawCircle(cx, cy - r * 0.18f, r * 0.68f, paint)
            val tip = android.graphics.Path().apply {
                moveTo(cx - r * 0.28f, cy + r * 0.36f)
                lineTo(cx + r * 0.28f, cy + r * 0.36f)
                lineTo(cx, cy + r)
                close()
            }
            canvas.drawPath(tip, paint)
        }
    }
}

// Geofence circle polygon (approximated as 72-point polygon)
private fun createGeofencePolygon(fence: GeofenceDto): Polygon? {
    val lat    = fence.latitude  ?: return null
    val lon    = fence.longitude ?: return null
    val radius = fence.radius    ?: return null
    val steps  = 72
    val points = ArrayList<GeoPoint>(steps)
    for (i in 0 until steps) {
        val angle    = 2.0 * PI * i / steps
        val deltaLat = radius / 111_320.0 * cos(angle)
        val deltaLon = radius / (111_320.0 * cos(Math.toRadians(lat))) * sin(angle)
        points.add(GeoPoint(lat + deltaLat, lon + deltaLon))
    }
    return Polygon().apply {
        setPoints(points)
        fillPaint.color  = android.graphics.Color.argb(38,  78, 142, 243)
        fillPaint.style  = android.graphics.Paint.Style.FILL
        outlinePaint.color       = android.graphics.Color.argb(200, 78, 142, 243)
        outlinePaint.strokeWidth = 4f
        outlinePaint.style       = android.graphics.Paint.Style.STROKE
        title = fence.name ?: "Geofence"
    }
}

// DashboardScreen
@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    val sheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )
    var mapViewRef        by remember { mutableStateOf<MapView?>(null) }
    var selectedFenceName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedFenceName) {
        if (selectedFenceName != null) {
            delay(3_000)
            selectedFenceName = null
        }
    }

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
                    latitude        = viewModel.latitude,
                    longitude       = viewModel.longitude,
                    geofences       = viewModel.geofences,
                    showGeofences   = viewModel.showGeofencesOnMap,
                    onFenceSelected = { selectedFenceName = it },
                    onMapCreated    = { mapViewRef = it }
                )
                LiveLocationOverlay(modifier = Modifier.align(Alignment.Center))
            } else {
                GpsAcquiringScreen()
            }

            // Fence name popup — appears on marker tap, auto-dismisses after 3 s
            AnimatedVisibility(
                visible  = selectedFenceName != null,
                enter    = fadeIn(tween(180)) + slideInVertically { -it / 2 },
                exit     = fadeOut(tween(180)) + slideOutVertically { -it / 2 },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
            ) {
                selectedFenceName?.let { fname ->
                    Surface(
                        shape           = RoundedCornerShape(22.dp),
                        color           = PanelPurple.copy(alpha = 0.96f),
                        border          = BorderStroke(1.dp, CardBorder),
                        shadowElevation = 10.dp
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn, null,
                                tint     = ElectricBlue,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(fname, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
                        }
                    }
                }
            }

            // Ambient weather particles — subtle overlay on top of the map
            if (viewModel.weatherCode >= 0) {
                WeatherParticleEffect(
                    weatherCode = viewModel.weatherCode,
                    temperature = viewModel.temperature,
                    modifier    = Modifier.fillMaxSize()
                )
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
                        try {
                            val uri = Uri.parse(
                                "geo:${viewModel.latitude},${viewModel.longitude}" +
                                "?q=${viewModel.latitude},${viewModel.longitude}(PathPal+Device)"
                            )
                            val gmaps = Intent(Intent.ACTION_VIEW, uri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            if (gmaps.resolveActivity(ctx.packageManager) != null) {
                                ctx.startActivity(gmaps)
                            } else {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(
                                        "https://www.google.com/maps/search/?api=1" +
                                        "&query=${viewModel.latitude},${viewModel.longitude}"
                                    ))
                                )
                            }
                        } catch (_: Exception) { /* no browser / maps installed */ }
                    }
                    MapFab(Icons.Default.GpsFixed, "Recenter", iconTint = TextGray) {
                        mapViewRef?.controller?.animateTo(
                            GeoPoint(viewModel.latitude, viewModel.longitude)
                        )
                    }
                    MapFab(
                        icon        = if (viewModel.showGeofencesOnMap) Icons.Default.Visibility
                                      else Icons.Default.VisibilityOff,
                        contentDesc = if (viewModel.showGeofencesOnMap) "Hide geofences"
                                      else "Show geofences",
                        iconTint    = if (viewModel.showGeofencesOnMap) ElectricBlue else TextMuted
                    ) {
                        viewModel.toggleGeofenceVisibility()
                    }
                }
            }
        }
    }
}

// Bottom sheet content — tabbed
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

// Overview panel (metrics)
@Composable
fun DashboardOverviewPanel(viewModel: AppViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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
                    Text("${viewModel.displayName}  •  Live tracking", fontSize = 13.sp, color = TextMuted)
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

        GeofenceMiniCard(
            isWithin      = viewModel.isWithinGeofence,
            geofenceCount = viewModel.geofences.size
        )

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun GeofenceMiniCard(isWithin: Boolean, geofenceCount: Int) {
    val color = when {
        geofenceCount == 0 -> TextMuted
        isWithin           -> GreenOk
        else               -> RedAlert
    }
    val icon = when {
        geofenceCount == 0 -> Icons.Default.LocationOff
        isWithin           -> Icons.Default.GpsFixed
        else               -> Icons.Default.Warning
    }
    val label = when {
        geofenceCount == 0 -> "No safety zones configured"
        isWithin           -> "Within ${geofenceCount} active zone${if (geofenceCount > 1) "s" else ""}"
        else               -> "Outside defined boundaries"
    }

    Surface(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(16.dp),
        color  = color.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(color.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "SAFETY ZONE",
                    fontSize = 9.sp, color = color,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
                )
                Text(label, fontSize = 13.sp, color = TextWhite, fontWeight = FontWeight.SemiBold)
            }
            if (geofenceCount > 0) {
                Canvas(Modifier.size(9.dp)) {
                    drawCircle(color, size.minDimension / 2)
                }
            }
        }
    }
}

// Alerts panel
@Composable
fun DashboardAlertsPanel(viewModel: AppViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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

        // Geofence status card
        GeofenceStatusCard(
            isWithin      = viewModel.isWithinGeofence,
            geofenceCount = viewModel.geofences.size
        )

        Spacer(Modifier.height(4.dp))
    }
}

// Weather summary card
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

// Individual alert card
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

// Geofence status card
@Composable
fun GeofenceStatusCard(isWithin: Boolean, geofenceCount: Int) {
    // No geofences configured yet — show an informational card
    if (geofenceCount == 0) {
        Surface(
            Modifier.fillMaxWidth(),
            shape  = RoundedCornerShape(16.dp),
            color  = TextMuted.copy(alpha = 0.06f),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(TextMuted.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocationOff, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "GEOFENCE",
                        fontSize = 9.sp, color = TextMuted,
                        fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
                    )
                    Text("No geofences configured", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
                    Spacer(Modifier.height(2.dp))
                    Text("Add geofences via the Senseway web portal.", fontSize = 12.sp, color = TextMuted)
                }
            }
        }
        return
    }

    val color = if (isWithin) GreenOk else RedAlert
    val title = if (isWithin) "Within geofenced limits" else "Outside geofenced limits"
    val desc  = if (isWithin)
        "$geofenceCount active zone${if (geofenceCount > 1) "s" else ""}. Device is within boundaries."
    else
        "Device is outside defined boundaries. Caregiver has been notified."

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
                    fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
                )
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
                Spacer(Modifier.height(2.dp))
                Text(desc, fontSize = 12.sp, color = TextMuted)
            }
            PulsingOnlineDot()
        }
    }
}

// Weather loading placeholder
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

// Map composables
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
            Text("Waiting for location permission or GPS fix", color = TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
fun OsmFullMap(
    latitude:        Double,
    longitude:       Double,
    geofences:       List<GeofenceDto>,
    showGeofences:   Boolean,
    onFenceSelected: (String) -> Unit = {},
    onMapCreated:    (MapView) -> Unit = {}
) {
    val mapRef            = remember { mutableStateOf<MapView?>(null) }
    val geofencePolygons  = remember { mutableListOf<Polygon>() }
    val geofenceMarkers   = remember { mutableListOf<Marker>() }
    val context           = LocalContext.current

    // Pause/resume the osmdroid MapView with the Activity lifecycle so tile
    // downloads stop when the app is backgrounded.
    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapRef.value?.onResume()
                Lifecycle.Event.ON_PAUSE  -> mapRef.value?.onPause()
                else -> {}
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
            mapRef.value?.onPause()
        }
    }

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
                overlays.add(
                    Marker(this).apply {
                        position = GeoPoint(latitude, longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon  = createAimMarkerIcon(ctx)
                        title = null
                    }
                )
                mapRef.value = this
                onMapCreated(this)
            }
        },
        update = { mapView ->
            // Move aim marker to latest GPS position
            mapView.controller.animateTo(GeoPoint(latitude, longitude))
            mapView.overlays.filterIsInstance<Marker>()
                .firstOrNull { it.title == null }
                ?.position = GeoPoint(latitude, longitude)

            // Remove previous geofence overlays
            geofencePolygons.forEach { mapView.overlays.remove(it) }
            geofenceMarkers.forEach  { mapView.overlays.remove(it) }
            geofencePolygons.clear()
            geofenceMarkers.clear()

            // Redraw geofence circles and name labels when visibility is on
            if (showGeofences) {
                geofences.filter { it.enabled == true }.forEach { fence ->
                    val fLat = fence.latitude  ?: return@forEach
                    val fLon = fence.longitude ?: return@forEach
                    val name = fence.name      ?: "Zone"

                    createGeofencePolygon(fence)?.let { poly ->
                        geofencePolygons.add(poly)
                        mapView.overlays.add(0, poly)
                    }

                    // Category icon at fence center — tap shows name
                    val labelMarker = Marker(mapView).apply {
                        position = GeoPoint(fLat, fLon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon  = createFenceIconMarker(mapView.context, name)
                        title = null
                        setOnMarkerClickListener { _, _ ->
                            onFenceSelected(name)
                            true
                        }
                    }
                    geofenceMarkers.add(labelMarker)
                    mapView.overlays.add(labelMarker)
                }
            }

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

// Shared small composables
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
                AnimatedContent(
                    targetState  = value,
                    transitionSpec = {
                        slideInVertically { it } + fadeIn(tween(180)) togetherWith
                        slideOutVertically { -it } + fadeOut(tween(180))
                    },
                    label = "mv_$label"
                ) { v ->
                    Text(v, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = valueColor)
                }
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
