package ca.senseway.pathpaldemo

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// In-memory cookie store — persists the session cookie across all API calls.
// Uses java.net.URI for host extraction to stay compatible with all OkHttp versions.
private val sessionCookieJar = object : CookieJar {
    private val store = HashMap<String, List<Cookie>>()

    private fun host(url: HttpUrl): String =
        runCatching { java.net.URI(url.toString()).host }.getOrNull() ?: url.toString()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        store[host(url)] = cookies
    }
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store[host(url)] ?: emptyList()
}

// Single OkHttpClient shared by the Senseway Retrofit instance
// pingInterval keeps WebSocket alive through firewalls/idle periods — without it,
// connections can silently die with no onFailure callback, meaning no auto-reconnect
val sensewayHttpClient: OkHttpClient = OkHttpClient.Builder()
    .cookieJar(sessionCookieJar)
    .pingInterval(20, TimeUnit.SECONDS)
    .build()

// Senseway API (cookie-based session auth, mirrors web app credentials:include)
object SenseWayClient {
    private const val BASE_URL = "https://api.senseway.ca/"
    val api: SenseWayApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(sensewayHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SenseWayApi::class.java)
    }
}

// Request / response DTOs

data class LoginRequest(
    val email:    String,
    val password: String
)

data class UserDto(
    val user_id:    String?,
    val email:      String?,
    val name:       String?,
    val type:       String?,
    val birth_date: String?,
    val home_long:  Double?,
    val home_lat:   Double?,
    val avatar_url: String?
)

data class StatusDto(
    val id:         Int?,
    val user_id:    String?,
    val longitude:  Double?,
    val latitude:   Double?,
    val battery:    Int?,
    val heart_rate: Int?,
    val created_at: String?
)

data class StatusUpdateRequest(
    val user_id:    String,
    val latitude:   Double,
    val longitude:  Double,
    val heart_rate: Int? = null
)

data class GeofenceDto(
    val id:          Int?,
    val user_id:     String?,
    val name:        String?,
    val enabled:     Boolean?,
    val latitude:    Double?,
    val longitude:   Double?,
    val radius:      Double?,      // metres
    val starts_at:   String?,
    val ends_at:     String?,
    val timed_title: String?,
    val set_timed:   Boolean?
)

data class EventDto(
    val id:         Int?,
    val user_id:    String?,
    val type:       String?,
    val message:    String?,
    val created_at: String?
)

data class AppointmentDto(
    val id:          Int?,
    val user_id:     String?,
    val title:       String?,
    val location:    String?,
    val description: String?,
    val start_at:    String?,
    val end_at:      String?,
    val fence_id:    Int?
)

// API interface — after login the app only calls GET endpoints
interface SenseWayApi {

    // Auth
    @POST("login")
    suspend fun login(@Body body: LoginRequest): Response<UserDto>

    @GET("session")
    suspend fun checkSession(): Response<UserDto>

    @DELETE("session")
    suspend fun logout(): Response<Unit>

    // User (read-only post-login)
    @GET("user")
    suspend fun getUser(@Query("user_id") userId: String): Response<UserDto>

    // Device status
    @GET("status")
    suspend fun getStatus(@Query("user_id") userId: String): Response<StatusDto>

    @POST("status")
    suspend fun updateStatus(@Body body: StatusUpdateRequest): Response<StatusDto>

    // Events (read-only)
    @GET("events")
    suspend fun getEvents(
        @Query("user_id")  userId:   String,
        @Query("quantity") quantity: Int = 100
    ): Response<List<EventDto>>

    // Geofences (read-only)
    @GET("fences")
    suspend fun listFences(@Query("user_id") userId: String): Response<List<GeofenceDto>>

    // Appointments (read-only)
    @GET("appointments")
    suspend fun listAppointments(@Query("user_id") userId: String): Response<List<AppointmentDto>>
}

// Open-Meteo weather + terrain elevation (free, no API key)
object OpenMeteoClient {
    private const val BASE_URL = "https://api.open-meteo.com/"
    val api: OpenMeteoApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoApi::class.java)
    }
}

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude")        lat:      Double,
        @Query("longitude")       lon:      Double,
        @Query("current")         current:  String,
        @Query("wind_speed_unit") windUnit: String = "kmh",
        @Query("timezone")        timezone: String = "auto"
    ): WeatherResponse
}

data class WeatherResponse(
    val elevation: Double?,
    val current:   CurrentWeather
)

data class CurrentWeather(
    val temperature_2m:       Double,
    val apparent_temperature: Double,
    val relative_humidity_2m: Int,
    val wind_speed_10m:       Double,
    val precipitation:        Double,
    val weather_code:         Int,
    val cloud_cover:          Int
)

// Open-Meteo Air Quality (free, no API key)
object AirQualityClient {
    private const val BASE_URL = "https://air-quality-api.open-meteo.com/"
    val api: AirQualityApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AirQualityApi::class.java)
    }
}

interface AirQualityApi {
    @GET("v1/air-quality")
    suspend fun getAirQuality(
        @Query("latitude")  lat:     Double,
        @Query("longitude") lon:     Double,
        @Query("current")   current: String = "european_aqi,pm10,pm2_5,uv_index"
    ): AirQualityResponse
}

data class AirQualityResponse(val current: AirQualityCurrent)

data class AirQualityCurrent(
    val european_aqi: Int?,
    val pm10:         Double?,
    val pm2_5:        Double?,
    val uv_index:     Double?
)
