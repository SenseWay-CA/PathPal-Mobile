package com.example.sensewayapp1.data

import com.example.sensewayapp1.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeAuthRepo @Inject constructor() : AuthRepository {
    private val _state = MutableStateFlow(false)
    override val isLoggedIn: StateFlow<Boolean> = _state.asStateFlow()
    override suspend fun login(email: String, password: String) { delay(400); _state.value = true }
    override suspend fun logout() { _state.value = false }
}

@Singleton
class FakeDeviceRepo @Inject constructor() : DeviceRepository {
    private val _status = MutableStateFlow(
        DeviceStatus(
            deviceId = "CANE-001",
            connected = true,
            batteryPct = 78,
            heartRateBpm = 72,
            lastGpsFix = Instant.now(),
            rssi = -54,
            lastEvent = "Device Connected"
        )
    )
    private val _contacts = MutableStateFlow(
        listOf(
            Contact("1","Sarah Johnson","+1 555-0101","Daughter"),
            Contact("2","Dr. Michael Chen","+1 555-0123","Physician")
        )
    )
    private val _fences = MutableStateFlow(
        listOf(GeoFence("home","Home", LatLng(45.4215,-75.6972), 250.0))
    )

    override val deviceStatus: StateFlow<DeviceStatus> = _status.asStateFlow()
    override val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()
    override val fences: StateFlow<List<GeoFence>> = _fences.asStateFlow()

    override suspend fun refresh() {
        delay(300)
        _status.update { it.copy(batteryPct = (65..95).random()) }
    }
}
