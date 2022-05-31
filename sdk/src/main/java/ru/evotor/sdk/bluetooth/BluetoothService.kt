package ru.evotor.sdk.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import kotlinx.coroutines.*
import org.json.JSONObject
import ru.evotor.sdk.payment.PaymentControllerListener
import ru.evotor.sdk.payment.entities.PaymentResultContext
import ru.evotor.sdk.payment.entities.ResultData
import ru.evotor.sdk.bluetooth.BluetoothCommand
import ru.evotor.sdk.payment.PaymentResultListener
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class BluetoothService(private val context: Context) : CommandsInterface {

    private companion object {
        const val TAG = "BluetoothUtils"

        const val SOCKET_UUID = "8f87f7ce-a064-4123-910f-8a28d221b4c5"
    }

    private var bluetoothAdapter: BluetoothAdapter? = null

    private var socket: BluetoothSocket? = null

    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null

    private var paymentResultListener: PaymentResultListener? = null

    init {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    fun checkBluetoothPermissions(
        bluetoothPermission: ActivityResultLauncher<String>,
        onSuccess: () -> Unit
    ) {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) -> {
                onSuccess()
            }
            else -> {
                bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }

    fun checkBluetoothOn(
        bluetoothResult: ActivityResultLauncher<Intent>,
        onSuccess: () -> Unit
    ) {
        if (bluetoothAdapter == null) {
            Log.e(BluetoothService::class.java.toString(), "Device not have Bluetooth")
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            bluetoothResult.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            onSuccess()
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> =
        bluetoothAdapter?.bondedDevices?.toList() ?: listOf()

    @SuppressLint("MissingPermission")
    fun getPairedDeviceByAddress(address: String): BluetoothDevice =
        bluetoothAdapter?.bondedDevices?.find { it.address == address }
            ?: throw RuntimeException("Has not paired device with this address")

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                socket = getPairedDeviceByAddress(address).createRfcommSocketToServiceRecord(
                    UUID.fromString(
                        SOCKET_UUID
                    )
                )

                socket?.connect()
            } catch (exception: Exception) {
                Log.e(TAG, exception.message.toString())
                try {
                    socket?.close()
                } catch (exception: Exception) {
                    Log.e(TAG, exception.message.toString())
                }
            }

            if (socket != null) {
                subscribeToData()
            }
        }
    }

    private fun subscribeToData() {
        inStream = socket?.inputStream
        outStream = socket?.outputStream

        val buffer = ByteArray(1024 * 4)
        var bytes: Int

        while (true) {
            try {
                bytes = inStream?.read(buffer) ?: 0
                val message = String(buffer, 0, bytes)
                processBluetoothData(message)
            } catch (exception: Exception) {
                Log.e(TAG, exception.message.toString())
                break
            }
        }
    }

    private fun sendBluetoothData(json: String) {
        try {
            outStream?.write(json.toByteArray())
        } catch (exception: Exception) {
            Log.e(TAG, exception.message.toString())
        }
    }

    override fun startPayment(amount: String?, json: String?) {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.START_PAYMENT)
        jsonObject.put("amount", amount)
        jsonObject.put("data", convertToValidJson(json))
        sendBluetoothData(jsonObject.toString())
    }

    override fun startRefund(amount: String?, json: String?) {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.START_REFUND)
        jsonObject.put("amount", amount)
        jsonObject.put("data", convertToValidJson(json))
        sendBluetoothData(jsonObject.toString())
    }

    override fun startReversal(amount: String?, json: String?) {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.START_REVERSAL)
        jsonObject.put("amount", amount)
        jsonObject.put("data", convertToValidJson(json))
        sendBluetoothData(jsonObject.toString())
    }

    override fun startReconciliation() {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.START_RECONCILIATION)
        sendBluetoothData(jsonObject.toString())
    }

    override fun cashout(cashBack: String?, json: String?) {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.CASHOUT)
        jsonObject.put("cashBack", cashBack)
        jsonObject.put("data", convertToValidJson(json))
        sendBluetoothData(jsonObject.toString())
    }

    override fun purchaseWithCashback(amount: String?, cashBack: String?, json: String?) {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.PURCHASE_WITH_CASHBACK)
        jsonObject.put("amount", amount)
        jsonObject.put("cashBack", cashBack)
        jsonObject.put("data", convertToValidJson(json))
        sendBluetoothData(jsonObject.toString())
    }

    override fun setDefaultTerminal(isDefaultTerminal: Boolean) {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.SET_DEFAULT_TERMINAL)
        jsonObject.put("isDefaultTerminal", isDefaultTerminal)
        sendBluetoothData(jsonObject.toString())
    }

    override fun startServiceMenu() {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.START_SERVICE_MENU)
        sendBluetoothData(jsonObject.toString())
    }

    override fun addTestConfiguration() {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.ADD_TEST_CONFIGURATION)
        sendBluetoothData(jsonObject.toString())
    }

    private val resultBuilder = StringBuilder()

    private fun processBluetoothData(data: String) {
        resultBuilder.append(data)

        try {
            val result = Gson().fromJson(resultBuilder.toString(), ResultData::class.java)
            resultBuilder.clear()
            paymentResultListener?.onResult(result)
        } catch (exception: Exception) {
            return
        }
    }

    fun setResultListener(paymentResultListener: PaymentResultListener) {
        this.paymentResultListener = paymentResultListener
    }

    private fun convertToValidJson(json: String?): JSONObject? =
        if (json.isNullOrEmpty()) {
            null
        } else {
            JSONObject(json)
        }
}