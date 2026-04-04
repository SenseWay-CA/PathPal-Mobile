package ca.senseway.pathpaldemo

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ── Senseway device API ─────────────────────────────────────────────────────
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

// ── Open-Meteo (free, no API key — weather forecast + terrain elevation) ────
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
    // Elevation is returned for free alongside the weather response
    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude")        lat:       Double,
        @Query("longitude")       lon:       Double,
        @Query("current")         current:   String,
        @Query("wind_speed_unit") windUnit:  String = "kmh",
        @Query("timezone")        timezone:  String = "auto"
    ): WeatherResponse
}

data class WeatherResponse(
    val elevation: Double?,          // terrain elevation at this coordinate (metres)
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

// ── Open-Meteo Air Quality (free, no API key — AQI, PM2.5, UV) ─────────────
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
