package com.example.sensewayapp1.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sensewayapp1.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository
) : ViewModel() {
    data class Ui(val email: String = "", val password: String = "", val remember: Boolean = false)
    private val _ui = MutableStateFlow(Ui())
    val ui = _ui.asStateFlow()

    fun onEmail(s: String) = _ui.update { it.copy(email = s) }
    fun onPassword(s: String) = _ui.update { it.copy(password = s) }
    fun onRemember(b: Boolean) = _ui.update { it.copy(remember = b) }

    fun login(done: () -> Unit) = viewModelScope.launch {
        auth.login(ui.value.email, ui.value.password)
        done()
    }
}

