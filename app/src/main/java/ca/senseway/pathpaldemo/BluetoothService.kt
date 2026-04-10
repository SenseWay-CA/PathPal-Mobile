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
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val TAG = "BT"
        private const val RECONNECT_DELAY_MS = 4_000L
        private const val MAX_RETRIES        = 8
    }

    private var socket:  BluetoothSocket? = null
    @Volatile private var running  = false
    @Volatile private var retries  = 0
    private var connectThread: Thread? = null

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
        // tear down any existing attempt before starting a new one
        running = false
        runCatching { socket?.close() }
        socket = null
        connectThread?.interrupt()

        retries = 0
        running = true
        connectThread = Thread { connectLoop(mac) }.also { it.start() }
    }

    @SuppressLint("MissingPermission")
    private fun connectLoop(mac: String) {
        while (running && retries <= MAX_RETRIES) {
            try {
                _status.value = "Connecting..."

                // cancelDiscovery() needs BLUETOOTH_SCAN on Android 12+ — wrap it
                runCatching { btAdapter?.cancelDiscovery() }

                btAdapter?.getRemoteDevice(mac)
                    ?: throw IllegalStateException("Adapter unavailable")

                // Try 3 socket strategies in order — each handles different Pi/Android combos:
                // 1. Standard authenticated RFCOMM (most reliable when it works)
                // 2. Insecure RFCOMM (no PIN required — common for Pi serial servers)
                // 3. Reflection on channel 1 (last resort for stubborn hardware)
                socket = createSocket(mac)
                socket?.connect()

                retries = 0
                _status.value = "Connected"
                readLoop()   // blocks until stream ends

                // readLoop returned — connection dropped, attempt reconnect if still wanted
                if (running) {
                    Log.w(TAG, "stream ended — reconnecting in ${RECONNECT_DELAY_MS}ms")
                    _status.value = "Reconnecting..."
                    Thread.sleep(RECONNECT_DELAY_MS)
                    retries++
                }

            } catch (_: InterruptedException) {
                break   // disconnect() was called
            } catch (e: Exception) {
                Log.e(TAG, "connect attempt ${retries + 1} failed: ${e.message}")
                if (!running) break
                retries++
                if (retries > MAX_RETRIES) {
                    _status.value = "Failed"
                    break
                }
                _status.value = "Reconnecting..."
                runCatching { socket?.close() }
                socket = null
                try { Thread.sleep(RECONNECT_DELAY_MS) } catch (_: InterruptedException) { break }
            }
        }

        if (_status.value != "Failed") cleanup()
    }

    @SuppressLint("MissingPermission")
    private fun createSocket(mac: String): BluetoothSocket {
        val device = btAdapter!!.getRemoteDevice(mac)
        // strategy 1: standard SPP
        return try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e1: Exception) {
            Log.w(TAG, "standard RFCOMM failed (${e1.message}), trying insecure")
            // strategy 2: insecure (no authentication) — common for Pi serial servers
            try {
                device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            } catch (e2: Exception) {
                Log.w(TAG, "insecure RFCOMM failed (${e2.message}), trying reflection ch1")
                // strategy 3: reflection on fixed channel 1 — last resort
                @Suppress("DiscouragedPrivateApi")
                device.javaClass
                    .getMethod("createRfcommSocket", Int::class.java)
                    .invoke(device, 1) as BluetoothSocket
            }
        }
    }

    private fun readLoop() {
        val gson = Gson()
        try {
            val reader = BufferedReader(InputStreamReader(socket?.inputStream))
            while (running) {
                val line = reader.readLine() ?: break   // null = stream closed
                if (line.isBlank()) continue
                try {
                    val parsed = gson.fromJson(line, PiSensorData::class.java)
                    // validate — bad JSON can produce zeroed objects with valid structure
                    if (parsed != null) _data.value = parsed
                } catch (_: Exception) {
                    Log.v(TAG, "unparseable line: $line")
                }
            }
        } catch (e: Exception) {
            if (running) Log.e(TAG, "stream error: ${e.message}")
        }
    }

    fun disconnect() {
        running = false
        connectThread?.interrupt()
        connectThread = null
        cleanup()
    }

    private fun cleanup() {
        running = false
        runCatching { socket?.close() }
        socket = null
        _status.value = "Disconnected"
    }
}
