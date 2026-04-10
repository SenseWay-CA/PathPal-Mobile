package ca.senseway.pathpaldemo

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

data class PiSensorData(
    val bpm:     Int          = 0,
    val dist_cm: Double       = 0.0,
    val accel:   List<Double> = listOf(0.0, 0.0, 0.0),
    val gyro:    List<Double> = listOf(0.0, 0.0, 0.0)
)

class BluetoothService(private val btAdapter: BluetoothAdapter?) {

    companion object {
        // spp uuid — must match pi rfcomm server
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var running = false

    private val _data   = MutableStateFlow(PiSensorData())
    val data = _data.asStateFlow()

    private val _status = MutableStateFlow("Disconnected")
    val status = _status.asStateFlow()

    @SuppressLint("MissingPermission")
    fun connect(mac: String) {
        if (btAdapter == null || !btAdapter.isEnabled) {
            _status.value = "Bluetooth Off"
            return
        }
        Thread {
            try {
                _status.value = "Connecting..."
                btAdapter.cancelDiscovery()
                val device = btAdapter.getRemoteDevice(mac)
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket?.connect()
                _status.value = "Connected"
                running = true
                readLoop()
            } catch (e: Exception) {
                Log.e("BT", "connect failed: ${e.message}")
                _status.value = "Failed"
                cleanup()
            }
        }.start()
    }

    private fun readLoop() {
        val gson = Gson()
        try {
            val reader = BufferedReader(InputStreamReader(socket?.inputStream))
            while (running && socket?.isConnected == true) {
                val line = reader.readLine() ?: break
                try { _data.value = gson.fromJson(line, PiSensorData::class.java) }
                catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("BT", "stream error: ${e.message}")
        } finally {
            cleanup()
        }
    }

    fun disconnect() {
        running = false
        cleanup()
    }

    private fun cleanup() {
        running = false
        runCatching { socket?.close() }
        socket = null
        _status.value = "Disconnected"
    }
}
