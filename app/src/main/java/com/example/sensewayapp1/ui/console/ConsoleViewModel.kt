package com.example.sensewayapp1.ui.console

import androidx.lifecycle.ViewModel
import com.example.sensewayapp1.data.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class ConsoleViewModel @Inject constructor(repo: DeviceRepository) : ViewModel() {
    val status = repo.deviceStatus
    val contacts = repo.contacts
    val txHealthy = MutableStateFlow(true)

    fun scanAndPair() { /* TODO: BLE pairing */ }
    fun troubleshoot() { /* TODO: open help */ }
}
