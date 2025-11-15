package com.example.sensewayapp1.model

import androidx.compose.runtime.Immutable
import java.time.Instant

@Immutable
data class DeviceStatus(
    val deviceId: String,
    val connected: Boolean,
    val batteryPct: Int,
    val heartRateBpm: Int?,
    val lastGpsFix: Instant?,
    val rssi: Int?,
    val lastEvent: String? = null
)

@Immutable data class GeoFence(val id: String, val name: String, val center: LatLng, val radiusM: Double)
@Immutable data class Contact(val id: String, val name: String, val phone: String?, val role: String)
@Immutable data class LatLng(val lat: Double, val lng: Double)
