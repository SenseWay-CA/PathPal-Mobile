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

// Data model matching the JSON your Pi sends
data class PiSensorData(
    val bpm: Int = 0,
    val dist_cm: Double = 0.0,
    val accel: List<Double> = listOf(0.0, 0.0, 0.0),
    val gyro: List<Double> = listOf(0.0, 0.0, 0.0),
    // New field for the image
    val image: String = ""
)

class BluetoothService(private val bluetoothAdapter: BluetoothAdapter?) {
    // Standard UUID for Serial Port Profile (SPP) - Must match the Pi!
    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null

    // Live stream of data for the ViewModel to observe
    private val _sensorData = MutableStateFlow(PiSensorData())
    val sensorData = _sensorData.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus = _connectionStatus.asStateFlow()

    private var isRunning = false

    @SuppressLint("MissingPermission")
    fun connectToPi(macAddress: String) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _connectionStatus.value = "Bluetooth Disabled"
            return
        }

        Thread {
            try {
                _connectionStatus.value = "Connecting..."
                val device = bluetoothAdapter.getRemoteDevice(macAddress)

                // Create socket
                socket = device.createRfcommSocketToServiceRecord(uuid)
                socket?.connect()

                _connectionStatus.value = "Connected"
                isRunning = true
                readDataStream()

            } catch (e: Exception) {
                Log.e("BT", "Connection failed", e)
                _connectionStatus.value = "Connection Failed"
                disconnect()
            }
        }.start()
    }

    private fun readDataStream() {
        val gson = Gson()
        try {
            val inputStream = socket?.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream))

            while (isRunning && socket?.isConnected == true) {
                // Read line sent by Pi (e.g. {"bpm": 72, ...}\n)
                val jsonLine = reader.readLine()
                if (jsonLine != null) {
                    try {
                        // Parse JSON to object
                        val data = gson.fromJson(jsonLine, PiSensorData::class.java)
                        _sensorData.value = data
                    } catch (e: Exception) {
                        Log.e("BT", "Parse error: $jsonLine", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BT", "Stream error", e)
        } finally {
            disconnect()
        }
    }

    fun disconnect() {
        isRunning = false
        try {
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _connectionStatus.value = "Disconnected"
    }
}