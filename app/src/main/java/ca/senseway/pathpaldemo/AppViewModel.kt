package ca.senseway.pathpaldemo

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

// Notification types
enum class NotifType { INFO, SUCCESS, ALERT }

data class AppNotification(
    val id:      Long   = System.currentTimeMillis(),
    val title:   String,
    val message: String,
    val type:    NotifType = NotifType.INFO
)

// Weather alert model
enum class AlertSeverity { SAFE, CAUTION, UNSAFE }

data class WeatherAlert(
    val category:    String,
    val title:       String,
    val description: String,
    val severity:    AlertSeverity
)

// ViewModel
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("pathpal_prefs", Context.MODE_PRIVATE)

    // Auth
    var isLoggedIn     by mutableStateOf(false);           private set
    var loginError     by mutableStateOf<String?>(null);   private set
    var isLoginLoading by mutableStateOf(false);           private set
    var currentUser    by mutableStateOf<UserDto?>(null);  private set

    // User display helpers (derived from currentUser state)
    val displayName: String
        get() = currentUser?.name?.takeIf { it.isNotBlank() } ?: "Admin"

    val displayEmail: String
        get() = currentUser?.email?.takeIf { it.isNotBlank() } ?: "admin@senseway.ca"

    val displayInitial: String
        get() {
            val src = currentUser?.name?.takeIf { it.isNotBlank() }
                ?: currentUser?.email?.takeIf { it.isNotBlank() }
            return src?.firstOrNull()?.uppercaseChar()?.toString() ?: "A"
        }

    val displayType: String
        get() = currentUser?.type?.replace("_", " ") ?: "Administrator"

    // User ID — from logged-in user, stored fallback, or hardcoded default
    private var resolvedUserId: String =
        prefs.getString("stored_user_id", null)?.takeIf { it.isNotBlank() }
            ?: "c1987b12-3ffe-432a-ac13-4b06264409ed"

    val userId: String
        get() = currentUser?.user_id?.takeIf { it.isNotBlank() } ?: resolvedUserId

    // Device sensor data (polled from Senseway API every 3 s)
    var battery   by mutableStateOf(0);    private set
    var heartRate by mutableStateOf(0);    private set
    var latitude  by mutableStateOf(0.0);  private set
    var longitude by mutableStateOf(0.0);  private set

    // Weather (polled from Open-Meteo on first GPS fix, then every 10 min)
    var temperature         by mutableStateOf(Double.NaN);  private set
    var apparentTemperature by mutableStateOf(Double.NaN);  private set
    var humidity            by mutableStateOf(0);            private set
    var windSpeed           by mutableStateOf(Double.NaN);  private set
    var precipitation       by mutableStateOf(0.0);          private set
    var weatherCode         by mutableStateOf(-1);           private set
    var cloudCover          by mutableStateOf(0);            private set
    var elevation           by mutableStateOf(Double.NaN);  private set

    // Air quality
    var airQualityIndex by mutableStateOf(-1);           private set
    var uvIndex         by mutableStateOf(Double.NaN);   private set
    var pm25            by mutableStateOf(Double.NaN);   private set

    // Geofences
    var geofences          by mutableStateOf<List<GeofenceDto>>(emptyList());  private set
    var showGeofencesOnMap by mutableStateOf(false);                           private set

    // Computed live: is the current GPS position inside any enabled geofence?
    val isWithinGeofence: Boolean
        get() {
            val enabled = geofences.filter { it.enabled == true }
            if (enabled.isEmpty()) return true
            if (latitude == 0.0 && longitude == 0.0) return true
            return enabled.any { fence ->
                val fLat   = fence.latitude  ?: return@any false
                val fLon   = fence.longitude ?: return@any false
                val radius = fence.radius    ?: return@any false
                haversineDistance(latitude, longitude, fLat, fLon) <= radius
            }
        }

    // API events (shown in Events tab — refreshed every 30 s)
    var events by mutableStateOf<List<EventDto>>(emptyList()); private set

    val notifications = mutableStateListOf<AppNotification>()

    // Coroutine jobs
    private var pollingJob:  Job? = null
    private var weatherJob:  Job? = null
    private var geofenceJob: Job? = null
    private var eventJob:    Job? = null

    private var lastWeatherLat = Double.NaN
    private var lastWeatherLon = Double.NaN
    private var hadFirstGpsFix = false

    // Notification deduplication state
    private var lastNotifiedEventId:          Int       = -1
    // Set of fence IDs the device was inside on the last GPS poll.
    // null = not yet initialized (first poll after login — sets baseline without notifying).
    private var lastInsideFenceIds:           Set<Int>? = null
    // Epoch ms — don't fire another geofence notification until after this timestamp.
    private var geofenceNotifCooldownUntil:   Long      = 0L
    private var lastSevereAlertTitle:         String?   = null

    init {
        NotificationHelper.createChannels(app)
        // Restore admin sessions across restarts (admin uses local auth, no cookie needed).
        // Real-API sessions use in-memory cookies that don't survive process death.
        if (prefs.getBoolean("logged_in", false)) {
            if (prefs.getBoolean("is_admin", false)) {
                isLoggedIn = true
                startPolling()
                startWeatherPolling()
                startGeofencePolling()
                startEventPolling()
            } else {
                prefs.edit().remove("logged_in").apply()
            }
        }
    }

    // Auth

    fun login(email: String, password: String) {
        loginError = null

        // Local admin shortcut (quick testing bypass)
        if (email.trim().equals("admin", ignoreCase = true) && password == "admin") {
            prefs.edit()
                .putBoolean("logged_in", true)
                .putBoolean("is_admin",  true)
                .apply()
            isLoggedIn = true
            hadFirstGpsFix = false
            lastInsideFenceIds = null
            geofenceNotifCooldownUntil = 0L
            startPolling()
            startWeatherPolling()
            startGeofencePolling()
            startEventPolling()
            return
        }

        // Real API login
        isLoginLoading = true
        viewModelScope.launch {
            try {
                val response = SenseWayClient.api.login(LoginRequest(email.trim(), password))
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    currentUser = user
                    resolvedUserId =
                        user.user_id?.takeIf { it.isNotBlank() } ?: resolvedUserId
                    prefs.edit()
                        .putBoolean("logged_in",      true)
                        .putBoolean("is_admin",       false)
                        .putString("stored_user_id",  resolvedUserId)
                        .apply()
                    isLoggedIn = true
                    hadFirstGpsFix = false
                    lastInsideFenceIds = null
                    geofenceNotifCooldownUntil = 0L
                    startPolling()
                    startWeatherPolling()
                    startGeofencePolling()
                    startEventPolling()
                } else {
                    loginError = if (response.code() in 401..403)
                        "Invalid email or password"
                    else
                        "Login failed (${response.code()}). Please try again."
                }
            } catch (e: Exception) {
                loginError = "Network error. Check your connection."
                Log.e("AuthVM", "Login error", e)
            } finally {
                isLoginLoading = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try { SenseWayClient.api.logout() } catch (_: Exception) {}
        }
        isLoggedIn         = false
        currentUser        = null
        geofences          = emptyList()
        events             = emptyList()
        showGeofencesOnMap = false
        hadFirstGpsFix     = false
        lastWeatherLat             = Double.NaN
        lastWeatherLon             = Double.NaN
        lastInsideFenceIds         = null
        geofenceNotifCooldownUntil = 0L
        lastSevereAlertTitle       = null
        lastNotifiedEventId        = -1
        prefs.edit()
            .remove("logged_in")
            .remove("is_admin")
            .remove("stored_user_id")
            .apply()
        pollingJob?.cancel();  pollingJob  = null
        weatherJob?.cancel();  weatherJob  = null
        geofenceJob?.cancel(); geofenceJob = null
        eventJob?.cancel();    eventJob    = null
    }

    fun toggleGeofenceVisibility() {
        showGeofencesOnMap = !showGeofencesOnMap
    }

    fun dismissNotification(id: Long) {
        notifications.removeIf { it.id == id }
    }

    // Device polling (every 3 s)

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isLoggedIn) {
                try {
                    val response = SenseWayClient.api.getStatus(userId)
                    if (response.isSuccessful) {
                        response.body()?.let { s ->
                            battery   = s.battery   ?: 0
                            heartRate = s.heart_rate ?: 0
                            s.latitude?.let  { v -> latitude  = v }
                            s.longitude?.let { v -> longitude = v }

                            if (latitude != 0.0 && longitude != 0.0) {
                                if (!hadFirstGpsFix) {
                                    hadFirstGpsFix = true
                                    lastWeatherLat = latitude
                                    lastWeatherLon = longitude
                                    viewModelScope.launch { fetchWeather(latitude, longitude) }
                                    viewModelScope.launch { fetchAirQuality(latitude, longitude) }
                                } else {
                                    val moved = abs(latitude  - lastWeatherLat) > 0.01 ||
                                                abs(longitude - lastWeatherLon) > 0.01
                                    if (moved) {
                                        lastWeatherLat = latitude
                                        lastWeatherLon = longitude
                                        viewModelScope.launch { fetchWeather(latitude, longitude) }
                                    }
                                }
                                checkGeofenceTransitions()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DeviceVM", "Status fetch failed", e)
                }
                delay(3_000)
            }
        }
    }

    // Weather polling (every 10 min)

    private fun startWeatherPolling() {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            delay(10 * 60 * 1_000L)
            while (isLoggedIn) {
                if (latitude != 0.0 && longitude != 0.0) {
                    fetchWeather(latitude, longitude)
                    fetchAirQuality(latitude, longitude)
                }
                delay(10 * 60 * 1_000L)
            }
        }
    }

    // Geofence polling (every 60 s)

    private fun startGeofencePolling() {
        geofenceJob?.cancel()
        geofenceJob = viewModelScope.launch {
            while (isLoggedIn) {
                fetchGeofences()
                delay(5_000)
            }
        }
    }

    // Event polling (every 30 s)

    private fun startEventPolling() {
        eventJob?.cancel()
        eventJob = viewModelScope.launch {
            while (isLoggedIn) {
                fetchEvents()
                delay(30_000)
            }
        }
    }

    // Geofence fetch — only updates state when data actually changed to prevent
    // unnecessary Compose recomposition on every 5-second poll tick.

    private suspend fun fetchGeofences() {
        try {
            val response = SenseWayClient.api.listFences(userId)
            if (response.isSuccessful) {
                val fresh = response.body() ?: emptyList()
                if (fresh != geofences) geofences = fresh
            }
        } catch (e: Exception) {
            Log.e("GeofenceVM", "Geofence fetch failed", e)
        }
    }

    // Geofence transition detection — called every 3 s after each GPS update.
    //
    // Uses fence ID sets rather than a single boolean so that:
    //  - We always know WHICH specific fence was entered or exited.
    //  - When a fence is deleted from the backend the ID disappears from the
    //    current list; we intersect with that list before comparing, so the
    //    deletion silently drops the ID without firing a spurious exit alert.
    //  - A 30-second cooldown prevents GPS-jitter from flooding notifications.
    //  - The first call after login sets a baseline without notifying.

    private fun checkGeofenceTransitions() {
        val enabled = geofences.filter { it.enabled == true && it.id != null }
        val currentIds = enabled.mapNotNull { it.id }.toSet()

        // IDs the device is inside right now
        val nowInsideIds = enabled
            .filter { fence ->
                val fLat = fence.latitude  ?: return@filter false
                val fLon = fence.longitude ?: return@filter false
                val rad  = fence.radius    ?: return@filter false
                haversineDistance(latitude, longitude, fLat, fLon) <= rad
            }
            .mapNotNull { it.id }
            .toSet()

        // First call after login — baseline only, no notification
        if (lastInsideFenceIds == null) {
            lastInsideFenceIds = nowInsideIds
            return
        }

        // Remove any fence IDs that were deleted from the backend without notifying
        val prevInside = lastInsideFenceIds!! intersect currentIds

        val exited  = prevInside   - nowInsideIds   // was inside, GPS moved out
        val entered = nowInsideIds - prevInside      // was outside, GPS moved in

        val now = System.currentTimeMillis()

        if (exited.isNotEmpty() && now >= geofenceNotifCooldownUntil) {
            val fence = enabled.firstOrNull { it.id in exited }
            if (fence != null) {
                geofenceNotifCooldownUntil = now + 30_000L
                val name = fence.name ?: "Safety Zone"
                val ctx  = getApplication<Application>()
                NotificationHelper.postGeofenceExit(ctx, name)
                notifications.add(0, AppNotification(
                    title   = "Outside Safety Zone",
                    message = "Device has left \"$name\"",
                    type    = NotifType.ALERT
                ))
            }
        }

        if (entered.isNotEmpty() && now >= geofenceNotifCooldownUntil) {
            val fence = enabled.firstOrNull { it.id in entered }
            if (fence != null) {
                geofenceNotifCooldownUntil = now + 30_000L
                val name = fence.name ?: "Safety Zone"
                val ctx  = getApplication<Application>()
                NotificationHelper.postGeofenceEnter(ctx, name)
                notifications.add(0, AppNotification(
                    title   = "Back in Safety Zone",
                    message = "Device has returned to \"$name\"",
                    type    = NotifType.SUCCESS
                ))
            }
        }

        lastInsideFenceIds = nowInsideIds
    }

    // Event fetch — notifies about new events via system notification

    private suspend fun fetchEvents() {
        try {
            val response = SenseWayClient.api.getEvents(userId)
            if (response.isSuccessful) {
                val fetched = response.body() ?: emptyList()
                events = fetched

                val maxId = fetched.maxOfOrNull { it.id ?: 0 } ?: 0

                if (lastNotifiedEventId < 0) {
                    // First fetch — baseline only, no notification spam for historical events
                    lastNotifiedEventId = maxId
                } else if (maxId > lastNotifiedEventId) {
                    // New events arrived — notify for each (up to 3 to avoid spam)
                    val ctx = getApplication<Application>()
                    fetched
                        .filter { (it.id ?: 0) > lastNotifiedEventId }
                        .sortedBy  { it.id ?: 0 }
                        .takeLast(3)
                        .forEachIndexed { idx, evt ->
                            val rawType = evt.type ?: "event"
                            val evtTitle = rawType
                                .replace("_", " ")
                                .split(" ")
                                .joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercaseChar() } }
                            val evtMsg = evt.message ?: "New event from device"
                            NotificationHelper.postEvent(ctx, evtTitle, evtMsg, 30_000 + idx)
                            notifications.add(0, AppNotification(
                                title   = evtTitle,
                                message = evtMsg,
                                type    = when {
                                    rawType.contains("alert") || rawType.contains("fall")
                                            || rawType.contains("sos") || rawType.contains("fence") -> NotifType.ALERT
                                    rawType.contains("ok") || rawType.contains("enter")             -> NotifType.SUCCESS
                                    else                                                             -> NotifType.INFO
                                }
                            ))
                        }
                    lastNotifiedEventId = maxId
                }
            }
        } catch (e: Exception) {
            Log.e("EventsVM", "Events fetch failed", e)
        }
    }

    // Weather + AQI helpers

    private suspend fun fetchWeather(lat: Double, lon: Double) {
        try {
            val r = OpenMeteoClient.api.getWeather(
                lat     = lat,
                lon     = lon,
                current = "temperature_2m,apparent_temperature,relative_humidity_2m," +
                          "wind_speed_10m,precipitation,weather_code,cloud_cover"
            )
            temperature         = r.current.temperature_2m
            apparentTemperature = r.current.apparent_temperature
            humidity            = r.current.relative_humidity_2m
            windSpeed           = r.current.wind_speed_10m
            precipitation       = r.current.precipitation
            weatherCode         = r.current.weather_code
            cloudCover          = r.current.cloud_cover
            r.elevation?.let { elevation = it }
            checkWeatherNotification()
        } catch (e: Exception) {
            Log.e("WeatherVM", "Weather fetch failed", e)
        }
    }

    private suspend fun fetchAirQuality(lat: Double, lon: Double) {
        try {
            val r = AirQualityClient.api.getAirQuality(lat, lon)
            airQualityIndex = r.current.european_aqi ?: -1
            uvIndex         = r.current.uv_index     ?: Double.NaN
            pm25            = r.current.pm2_5        ?: Double.NaN
            checkWeatherNotification()
        } catch (e: Exception) {
            Log.e("WeatherVM", "Air quality fetch failed", e)
        }
    }

    // Fire a system notification when a new UNSAFE weather condition is detected.
    // Resets when conditions improve so subsequent worsening triggers again.
    private fun checkWeatherNotification() {
        if (weatherCode < 0) return
        val severeAlert = buildAlerts().firstOrNull { it.severity == AlertSeverity.UNSAFE }
        if (severeAlert == null) {
            lastSevereAlertTitle = null
            return
        }
        if (severeAlert.title == lastSevereAlertTitle) return
        lastSevereAlertTitle = severeAlert.title
        val ctx = getApplication<Application>()
        NotificationHelper.postWeather(ctx, severeAlert.title, severeAlert.description)
        notifications.add(0, AppNotification(
            title   = severeAlert.title,
            message = severeAlert.description,
            type    = NotifType.ALERT
        ))
    }

    // Haversine distance (metres)

    private fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val R    = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val a = sinLat * sinLat +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sinLon * sinLon
        return R * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    // Computed weather alerts

    val weatherAlerts: List<WeatherAlert>
        get() = if (weatherCode < 0) emptyList() else buildAlerts()

    private fun buildAlerts(): List<WeatherAlert> {
        val alerts   = mutableListOf<WeatherAlert>()
        val tempSafe = !temperature.isNaN()
        val icy      = tempSafe && temperature < 2.0 && humidity > 65

        // Pavement safety (highest priority, always first)
        when {
            weatherCode in 95..99 -> alerts += WeatherAlert(
                "Pavement Safety", "Seek shelter — Thunderstorm",
                "Active thunderstorm. Avoid all outdoor activity immediately.",
                AlertSeverity.UNSAFE
            )
            weatherCode in 71..77 || weatherCode in 85..86 -> alerts += WeatherAlert(
                "Pavement Safety", "Slippery — Snow on ground",
                "Snow or sleet detected. High fall risk on all paved surfaces.",
                AlertSeverity.UNSAFE
            )
            (weatherCode in 61..67 || weatherCode in 80..82) && precipitation > 1.0 -> alerts += WeatherAlert(
                "Pavement Safety", "Slippery — Heavy rain",
                "Heavy rainfall. Pavement is very wet and slippery.",
                AlertSeverity.UNSAFE
            )
            weatherCode in 51..67 || weatherCode in 80..82 || precipitation > 0.05 -> alerts += WeatherAlert(
                "Pavement Safety", "Wet pavement — Caution",
                "Rainfall detected. Pavement may be slippery; slow down.",
                AlertSeverity.CAUTION
            )
            icy -> alerts += WeatherAlert(
                "Pavement Safety", "Possible ice — Caution",
                "Near-freezing with high humidity. Black ice may form on surfaces.",
                AlertSeverity.CAUTION
            )
            else -> alerts += WeatherAlert(
                "Pavement Safety", "Safe to walk",
                "Pavement is dry. Conditions are good for outdoor activity.",
                AlertSeverity.SAFE
            )
        }

        // Wind
        if (!windSpeed.isNaN()) when {
            windSpeed > 60 -> alerts += WeatherAlert(
                "Wind", "Dangerous winds — ${windSpeed.toInt()} km/h",
                "Severe wind gusts. Stay indoors and away from windows.",
                AlertSeverity.UNSAFE
            )
            windSpeed > 35 -> alerts += WeatherAlert(
                "Wind", "Strong winds — ${windSpeed.toInt()} km/h",
                "Grip handrails and secure loose items outdoors.",
                AlertSeverity.CAUTION
            )
            else -> {}
        }

        // Temperature
        if (tempSafe) when {
            temperature < -15 -> alerts += WeatherAlert(
                "Temperature", "Extreme cold — ${temperature.toInt()}°C",
                "Frostbite risk within minutes of exposure. Stay indoors.",
                AlertSeverity.UNSAFE
            )
            temperature < 0 -> alerts += WeatherAlert(
                "Temperature", "Below freezing — ${temperature.toInt()}°C",
                "Wear warm layers, cover extremities, and watch for ice.",
                AlertSeverity.CAUTION
            )
            temperature > 38 -> alerts += WeatherAlert(
                "Temperature", "Extreme heat — ${temperature.toInt()}°C",
                "Heat exhaustion risk. Hydrate frequently and seek shade.",
                AlertSeverity.UNSAFE
            )
            temperature > 32 -> alerts += WeatherAlert(
                "Temperature", "High heat — ${temperature.toInt()}°C",
                "Avoid prolonged sun exposure and wear light clothing.",
                AlertSeverity.CAUTION
            )
            else -> {}
        }

        // Visibility / fog
        if (weatherCode in 45..48) alerts += WeatherAlert(
            "Visibility", "Foggy conditions",
            "Reduced visibility. Walk carefully near roads and intersections.",
            AlertSeverity.CAUTION
        )

        // Air quality
        val aqi = airQualityIndex
        if (aqi >= 0) when {
            aqi > 150 -> alerts += WeatherAlert(
                "Air Quality", "Very poor air — AQI $aqi",
                "Limit all outdoor activity. Sensitive groups must stay inside.",
                AlertSeverity.UNSAFE
            )
            aqi > 100 -> alerts += WeatherAlert(
                "Air Quality", "Poor air quality — AQI $aqi",
                "Reduce outdoor exertion. Sensitive individuals avoid exposure.",
                AlertSeverity.CAUTION
            )
            aqi > 50 -> alerts += WeatherAlert(
                "Air Quality", "Moderate air quality — AQI $aqi",
                "Acceptable for most. Unusually sensitive people should take care.",
                AlertSeverity.CAUTION
            )
            else -> alerts += WeatherAlert(
                "Air Quality", "Good air quality — AQI $aqi",
                "Air quality is satisfactory for all activities.",
                AlertSeverity.SAFE
            )
        }

        // UV Index
        val uv = uvIndex
        if (!uv.isNaN()) when {
            uv >= 8 -> alerts += WeatherAlert(
                "UV Index", "Very high UV — ${"%.1f".format(uv)}",
                "UV is very high. Use SPF 50+, protective clothing and hat.",
                AlertSeverity.UNSAFE
            )
            uv >= 6 -> alerts += WeatherAlert(
                "UV Index", "High UV — ${"%.1f".format(uv)}",
                "Seek shade at midday. Apply broad-spectrum sunscreen.",
                AlertSeverity.CAUTION
            )
            uv >= 3 -> alerts += WeatherAlert(
                "UV Index", "Moderate UV — ${"%.1f".format(uv)}",
                "Sun protection recommended during peak hours (10 am–4 pm).",
                AlertSeverity.CAUTION
            )
            else -> alerts += WeatherAlert(
                "UV Index", "Low UV — ${"%.1f".format(uv)}",
                "Minimal sun protection needed for most outdoor activities.",
                AlertSeverity.SAFE
            )
        }

        return alerts
    }
}
