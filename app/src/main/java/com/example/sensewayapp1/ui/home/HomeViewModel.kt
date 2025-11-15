package com.example.sensewayapp1.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sensewayapp1.data.DeviceRepository
import com.example.sensewayapp1.model.DeviceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(repo: DeviceRepository) : ViewModel() {
    val status = repo.deviceStatus.stateIn(viewModelScope, SharingStarted.Eagerly, DeviceStatus("", false, 0, null, null, null))
    val contacts = repo.contacts
    val fences = repo.fences
}
