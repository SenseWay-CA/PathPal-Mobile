package com.example.sensewayapp1.di

import com.example.sensewayapp1.data.AuthRepository
import com.example.sensewayapp1.data.DeviceRepository
import com.example.sensewayapp1.data.FakeAuthRepo
import com.example.sensewayapp1.data.FakeDeviceRepo
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun bindAuth(impl: FakeAuthRepo): AuthRepository
    @Binds abstract fun bindDevice(impl: FakeDeviceRepo): DeviceRepository
}
