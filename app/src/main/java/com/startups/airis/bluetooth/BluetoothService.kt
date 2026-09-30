package com.startups.airis.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.util.UUID

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

class BluetoothService(private val context: Context) {
    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _sensorData = MutableStateFlow<SensorData?>(null)
    val sensorData: StateFlow<SensorData?> = _sensorData

    private var socket: BluetoothSocket? = null
    
    // Standard SerialPortService ID
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val DEVICE_NAME = "ESP32_Navigasi"

    @SuppressLint("MissingPermission")
    suspend fun connectToDevice() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _connectionState.value = ConnectionState.ERROR
            return
        }

        _connectionState.value = ConnectionState.CONNECTING

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        val espDevice = pairedDevices?.find { it.name == DEVICE_NAME }

        if (espDevice == null) {
            Log.e("BluetoothService", "Device $DEVICE_NAME not found among paired devices.")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        withContext(Dispatchers.IO) {
            try {
                socket = espDevice.createRfcommSocketToServiceRecord(MY_UUID)
                bluetoothAdapter.cancelDiscovery()
                socket?.connect()
                _connectionState.value = ConnectionState.CONNECTED
                
                listenForData(socket!!.inputStream)
            } catch (e: Exception) {
                Log.e("BluetoothService", "Connection failed", e)
                _connectionState.value = ConnectionState.ERROR
                closeConnection()
            }
        }
    }

    private suspend fun listenForData(inputStream: InputStream) {
        val buffer = ByteArray(1024)
        var bytes: Int
        var messageBuilder = StringBuilder()

        while (true) {
            try {
                bytes = inputStream.read(buffer)
                val incomingMessage = String(buffer, 0, bytes)
                messageBuilder.append(incomingMessage)

                // The ESP32 sends JSON terminated with '\n'
                var endOfLineIndex = messageBuilder.indexOf("\n")
                while (endOfLineIndex > -1) {
                    val completeMessage = messageBuilder.substring(0, endOfLineIndex)
                    messageBuilder.delete(0, endOfLineIndex + 1)
                    
                    parseJsonData(completeMessage)
                    
                    endOfLineIndex = messageBuilder.indexOf("\n")
                }
            } catch (e: Exception) {
                Log.e("BluetoothService", "Error reading data", e)
                _connectionState.value = ConnectionState.DISCONNECTED
                closeConnection()
                break
            }
        }
    }

    private fun parseJsonData(jsonString: String) {
        try {
            val jsonObject = JSONObject(jsonString)
            val depan = jsonObject.getDouble("depan_m")
            val kanan = jsonObject.getDouble("kanan_m")
            val kiri = jsonObject.getDouble("kiri_m")
            val lat = jsonObject.getDouble("lat")
            val lng = jsonObject.getDouble("lng")

            _sensorData.value = SensorData(depan, kanan, kiri, lat, lng)
        } catch (e: Exception) {
            Log.e("BluetoothService", "Error parsing JSON: $jsonString", e)
        }
    }

    fun closeConnection() {
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e("BluetoothService", "Error closing connection", e)
        }
        socket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
