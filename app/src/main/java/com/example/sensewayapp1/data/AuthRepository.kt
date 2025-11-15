package com.example.sensewayapp1.data

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val isLoggedIn: Flow<Boolean>
    suspend fun login(email: String, password: String)
    suspend fun logout()
}
