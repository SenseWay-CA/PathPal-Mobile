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
import kotlin.math.abs

// ── Notification types ──────────────────────────────────────────────────────
enum class NotifType { INFO, SUCCESS, ALERT }

data class AppNotification(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val message: String,
    val type: NotifType = NotifType.INFO
)

// ── Weather alert model ─────────────────────────────────────────────────────
enum class AlertSeverity { SAFE, CAUTION, UNSAFE }

data class WeatherAlert(
    val category:    String,
    val title:       String,
    val description: String,
    val severity:    AlertSeverity
)

// ── ViewModel ───────────────────────────────────────────────────────────────
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("pathpal_prefs", Context.MODE_PRIVATE)

    // Auth
    var isLoggedIn  by mutableStateOf(false);   private set
    var loginError  by mutableStateOf<String?>(null); private set

    // Device sensor data (polled from Senseway API every 3 s)
    var battery     by mutableStateOf(0);       private set
    var heartRate   by mutableStateOf(0);       private set
    var latitude    by mutableStateOf(0.0);     private set
    var longitude   by mutableStateOf(0.0);     private set

    // Weather (polled from Open-Meteo every 10 min, or on first GPS fix)
    var temperature          by mutableStateOf(Double.NaN);  private set
    var apparentTemperature  by mutableStateOf(Double.NaN);  private set
    var humidity             by mutableStateOf(0);           private set
    var windSpeed            by mutableStateOf(Double.NaN);  private set
    var precipitation        by mutableStateOf(0.0);         private set
    var weatherCode          by mutableStateOf(-1);          private set
    var cloudCover           by mutableStateOf(0);           private set

    // Terrain elevation (comes free in the weather response)
    var elevation  by mutableStateOf(Double.NaN);  private set

    // Air quality (polled alongside weather)
    var airQualityIndex  by mutableStateOf(-1);          private set
    var uvIndex          by mutableStateOf(Double.NaN);  private set
    var pm25             by mutableStateOf(Double.NaN);  private set

    // Geofence — placeholder; future: update from API
    var isWithinGeofence by mutableStateOf(true);  private set

    val userId = "c1987b12-3ffe-432a-ac13-4b06264409ed"
    val notifications = mutableStateListOf<AppNotification>()

    // Coroutine jobs — cancelled on logout so stale loops can't stack on re-login
    private var pollingJob: Job? = null
    private var weatherJob: Job? = null

    private var lastWeatherLat = Double.NaN
    private var lastWeatherLon = Double.NaN
    private var hadFirstGpsFix = false

    init {
        // Restore session so the user stays logged in until they explicitly sign out
        if (prefs.getBoolean("logged_in", false)) {
            isLoggedIn = true
            startPolling()
            startWeatherPolling()
        }
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    fun login(username: String, password: String) {
        if (username.trim().equals("admin", ignoreCase = true) && password == "admin") {
            isLoggedIn = true
            loginError = null
            hadFirstGpsFix = false
            prefs.edit().putBoolean("logged_in", true).apply()
            startPolling()
            startWeatherPolling()
        } else {
            loginError = "Invalid username or password"
        }
    }

    fun logout() {
        isLoggedIn = false
        hadFirstGpsFix = false
        lastWeatherLat = Double.NaN
        lastWeatherLon = Double.NaN
        prefs.edit().remove("logged_in").apply()
        pollingJob?.cancel()
        weatherJob?.cancel()
        pollingJob = null
        weatherJob = null
    }

    fun dismissNotification(id: Long) {
        notifications.removeIf { it.id == id }
    }

    // ── Device polling (3 s) ─────────────────────────────────────────────────

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isLoggedIn) {
                try {
                    val s = ApiClient.api.getStatus(userId)
                    battery   = s.battery
                    heartRate = s.heart_rate ?: 0
                    s.latitude?.let  { latitude  = it }
                    s.longitude?.let { longitude = it }

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
                    }
                } catch (e: Exception) {
                    Log.e("DeviceVM", "Device fetch failed", e)
                }
                delay(3_000)
            }
        }
    }

    // ── Weather polling (every 10 min) ───────────────────────────────────────

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

    // ── API fetch helpers ────────────────────────────────────────────────────

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
        } catch (e: Exception) {
            Log.e("WeatherVM", "Air quality fetch failed", e)
        }
    }

    // ── Computed weather alerts ──────────────────────────────────────────────

    val weatherAlerts: List<WeatherAlert>
        get() = if (weatherCode < 0) emptyList() else buildAlerts()

    private fun buildAlerts(): List<WeatherAlert> {
        val alerts = mutableListOf<WeatherAlert>()
        val tempSafe = !temperature.isNaN()
        val icy = tempSafe && temperature < 2.0 && humidity > 65

        // — Pavement safety (most important, always first)
        when {
            weatherCode in 95..99 -> alerts += WeatherAlert(
                "Pavement Safety", "Seek shelter — Thunderstorm",
                "Active thunderstorm. Avoid all outdoor activity immediately.", AlertSeverity.UNSAFE
            )
            weatherCode in 71..77 || weatherCode in 85..86 -> alerts += WeatherAlert(
                "Pavement Safety", "Slippery — Snow on ground",
                "Snow or sleet detected. High fall risk on all paved surfaces.", AlertSeverity.UNSAFE
            )
            (weatherCode in 61..67 || weatherCode in 80..82) && precipitation > 1.0 -> alerts += WeatherAlert(
                "Pavement Safety", "Slippery — Heavy rain",
                "Heavy rainfall. Pavement is very wet and slippery.", AlertSeverity.UNSAFE
            )
            weatherCode in 51..67 || weatherCode in 80..82 || precipitation > 0.05 -> alerts += WeatherAlert(
                "Pavement Safety", "Wet pavement — Caution",
                "Rainfall detected. Pavement may be slippery; slow down.", AlertSeverity.CAUTION
            )
            icy -> alerts += WeatherAlert(
                "Pavement Safety", "Possible ice — Caution",
                "Near-freezing with high humidity. Black ice may form on surfaces.", AlertSeverity.CAUTION
            )
            else -> alerts += WeatherAlert(
                "Pavement Safety", "Safe to walk",
                "Pavement is dry. Conditions are good for outdoor activity.", AlertSeverity.SAFE
            )
        }

        // — Wind
        if (!windSpeed.isNaN()) {
            when {
                windSpeed > 60 -> alerts += WeatherAlert(
                    "Wind", "Dangerous winds — ${windSpeed.toInt()} km/h",
                    "Severe wind gusts. Stay indoors and away from windows.", AlertSeverity.UNSAFE
                )
                windSpeed > 35 -> alerts += WeatherAlert(
                    "Wind", "Strong winds — ${windSpeed.toInt()} km/h",
                    "Grip handrails and secure loose items outdoors.", AlertSeverity.CAUTION
                )
                else -> {}
            }
        }

        // — Temperature extremes
        if (tempSafe) {
            when {
                temperature < -15 -> alerts += WeatherAlert(
                    "Temperature", "Extreme cold — ${temperature.toInt()}°C",
                    "Frostbite risk within minutes of exposure. Stay indoors.", AlertSeverity.UNSAFE
                )
                temperature < 0 -> alerts += WeatherAlert(
                    "Temperature", "Below freezing — ${temperature.toInt()}°C",
                    "Wear warm layers, cover extremities, and watch for ice.", AlertSeverity.CAUTION
                )
                temperature > 38 -> alerts += WeatherAlert(
                    "Temperature", "Extreme heat — ${temperature.toInt()}°C",
                    "Heat exhaustion risk. Hydrate frequently and seek shade.", AlertSeverity.UNSAFE
                )
                temperature > 32 -> alerts += WeatherAlert(
                    "Temperature", "High heat — ${temperature.toInt()}°C",
                    "Avoid prolonged sun exposure and wear light clothing.", AlertSeverity.CAUTION
                )
                else -> {}
            }
        }

        // — Visibility / fog
        if (weatherCode in 45..48) {
            alerts += WeatherAlert(
                "Visibility", "Foggy conditions",
                "Reduced visibility. Walk carefully near roads and intersections.", AlertSeverity.CAUTION
            )
        }

        // — Air quality
        val aqi = airQualityIndex
        if (aqi >= 0) {
            when {
                aqi > 150 -> alerts += WeatherAlert(
                    "Air Quality", "Very poor air — AQI $aqi",
                    "Limit all outdoor activity. Sensitive groups must stay inside.", AlertSeverity.UNSAFE
                )
                aqi > 100 -> alerts += WeatherAlert(
                    "Air Quality", "Poor air quality — AQI $aqi",
                    "Reduce outdoor exertion. Sensitive individuals avoid exposure.", AlertSeverity.CAUTION
                )
                aqi > 50 -> alerts += WeatherAlert(
                    "Air Quality", "Moderate air quality — AQI $aqi",
                    "Acceptable for most. Unusually sensitive people should take care.", AlertSeverity.CAUTION
                )
                else -> alerts += WeatherAlert(
                    "Air Quality", "Good air quality — AQI $aqi",
                    "Air quality is satisfactory for all activities.", AlertSeverity.SAFE
                )
            }
        }

        // — UV Index
        val uv = uvIndex
        if (!uv.isNaN()) {
            when {
                uv >= 8 -> alerts += WeatherAlert(
                    "UV Index", "Very high UV — ${"%.1f".format(uv)}",
                    "UV is very high. Use SPF 50+, protective clothing and hat.", AlertSeverity.UNSAFE
                )
                uv >= 6 -> alerts += WeatherAlert(
                    "UV Index", "High UV — ${"%.1f".format(uv)}",
                    "Seek shade at midday. Apply broad-spectrum sunscreen.", AlertSeverity.CAUTION
                )
                uv >= 3 -> alerts += WeatherAlert(
                    "UV Index", "Moderate UV — ${"%.1f".format(uv)}",
                    "Sun protection recommended during peak hours (10 am–4 pm).", AlertSeverity.CAUTION
                )
                else -> alerts += WeatherAlert(
                    "UV Index", "Low UV — ${"%.1f".format(uv)}",
                    "Minimal sun protection needed for most outdoor activities.", AlertSeverity.SAFE
                )
            }
        }

        return alerts
    }
}
