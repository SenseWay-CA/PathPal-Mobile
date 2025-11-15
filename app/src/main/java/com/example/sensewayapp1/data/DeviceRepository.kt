package com.example.sensewayapp1.data

import com.example.sensewayapp1.model.Contact
import com.example.sensewayapp1.model.DeviceStatus
import com.example.sensewayapp1.model.GeoFence
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    val deviceStatus: Flow<DeviceStatus>
    val contacts: Flow<List<Contact>>
    val fences: Flow<List<GeoFence>>
    suspend fun refresh()
}
