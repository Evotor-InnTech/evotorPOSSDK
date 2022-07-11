package ru.evotor.sdk.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ru.evotor.sdk.payment.PaymentResultListener
import ru.evotor.sdk.payment.entities.ResultData
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothConnectionService(private val context: Context) {
    private var socket: BluetoothSocket? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var connectionActive: AtomicBoolean = AtomicBoolean(false)
    private val resultBuilder = StringBuilder()
    private var paymentResultListener: PaymentResultListener? = null
    private var bluetoothDeviceAddress: String? = null
    private var bluetoothDevice: BluetoothDevice? = null

    companion object {
        private const val SOCKET_UUID = "8f87f7ce-a064-4123-910f-8a28d221b4c5"
        private const val TAG = "MVMBthConn"
        private const val BUFFER_SIZE = 1024 * 4
    }

    init {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    suspend fun onBluetoothDeviceSelected(deviceAddress: String, device: BluetoothDevice? = null) {
        bluetoothDeviceAddress = deviceAddress
        bluetoothDevice = device

        if (connectionActive.get()) {
            showConnectionStatus()
        } else {
            connect()
        }
    }

    private fun showConnectionStatus() {
        GlobalScope.launch(Dispatchers.Main) {
            val text = if (connectionActive.get()) {
                "Connected"
            } else {
                "Disconnected"
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect() {
        bluetoothAdapter?.cancelDiscovery()

        if (bluetoothDevice != null) {
            socket = bluetoothDevice?.createRfcommSocketToServiceRecord(
                UUID.fromString(
                    SOCKET_UUID
                )
            )
        } else {
            bluetoothDeviceAddress?.let { deviceAddress ->
                socket =
                    getPairedDeviceByAddress(deviceAddress).createRfcommSocketToServiceRecord(
                        UUID.fromString(
                            SOCKET_UUID
                        )
                    )
            }
        }

        socket?.connect()

        socket?.let { clientSocket ->
            //Socket connected
            Log.e(TAG, "Bluetooth socket connected")
            connectionActive.set(true)
            showConnectionStatus()
            // Start endless reading
            readSocketInput(clientSocket)
        }
    }

    private suspend fun readSocketInput(bluetoothSocket: BluetoothSocket) {
        GlobalScope.launch(Dispatchers.IO) {
            val inputStream = bluetoothSocket.inputStream
            val buffer = ByteArray(BUFFER_SIZE)
            var bytes: Int
            while (connectionActive.get()) {
                try {
                    bytes = inputStream.read(buffer)
                    val message = String(buffer, 0, bytes)
                    readPacketData(message)
                } catch (exception: Exception) {
                    Log.e(TAG, exception.message.toString())
                    connectionActive.set(false)
                    break
                }
            }
        }
    }

    private suspend fun readPacketData(data: String) {
        resultBuilder.append(data)
        try {
            val result = Gson().fromJson(resultBuilder.toString(), ResultData::class.java)
            Log.e(TAG, result.toString())
            resultBuilder.clear()
            paymentResultListener?.onResult(result)
        } catch (exception: Exception) {
            return
        }
    }

    fun sendBluetoothData(data: String) {
        try {
            socket?.outputStream?.write(data.toByteArray())
        } catch (e: java.lang.Exception) {
            connectionActive.set(false)
            socket?.close()
        }
    }

    fun setResultListener(paymentResultListener: PaymentResultListener) {
        this.paymentResultListener = paymentResultListener
    }

    @SuppressLint("MissingPermission")
    private suspend fun getPairedDeviceByAddress(address: String): BluetoothDevice =
        bluetoothAdapter?.bondedDevices?.find { it.address == address }
            ?: throw RuntimeException("Has not paired device with this address")

}