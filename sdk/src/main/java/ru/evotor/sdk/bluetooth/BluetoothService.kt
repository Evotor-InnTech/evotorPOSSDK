package ru.evotor.sdk.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import org.json.JSONObject
import ru.evotor.sdk.payment.ResultDataListener

class BluetoothService(private val context: Context) : CommandsInterface {

    private var bluetoothAdapter: BluetoothAdapter? = null

    private val bluetoothConnectionService = BluetoothConnectionService(context)

    init {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
    }

    suspend fun selectBluetoothDevice(bluetoothDeviceAddress: String, device: BluetoothDevice? = null) {
        bluetoothConnectionService.onBluetoothDeviceSelected(bluetoothDeviceAddress, device)
    }

    fun checkBluetoothPermissions(
        bluetoothPermission: ActivityResultLauncher<Array<String>>,
        onSuccess: () -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onSuccess()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothPermission.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            } else {
                bluetoothPermission.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
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
    fun startDiscovery() {
        if (bluetoothAdapter?.isDiscovering == true) {
            bluetoothAdapter?.cancelDiscovery()
        }
        bluetoothAdapter?.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> =
        bluetoothAdapter?.bondedDevices?.toList() ?: listOf()

    override fun startCardPayment(json: String?, token: String) {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.START_CARD_PAYMENT)
        jsonObject.put("token", token)
        jsonObject.put("data", convertToValidJson(json))
        bluetoothConnectionService.sendBluetoothData(jsonObject.toString())
    }

    override fun startCardRefund(json: String?, token: String) {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.START_CARD_REFUND)
        jsonObject.put("token", token)
        jsonObject.put("data", convertToValidJson(json))
        bluetoothConnectionService.sendBluetoothData(jsonObject.toString())
    }

    override fun startPayment(amount: String?, json: String?) {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.START_PAYMENT)
        jsonObject.put("amount", amount)
        jsonObject.put("data", convertToValidJson(json))
        bluetoothConnectionService.sendBluetoothData(jsonObject.toString())
    }

    override fun startRefund(amount: String?, json: String?) {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.START_REFUND)
        jsonObject.put("amount", amount)
        jsonObject.put("data", convertToValidJson(json))
        bluetoothConnectionService.sendBluetoothData(jsonObject.toString())
    }

    override fun startReversal(amount: String?, json: String?) {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.START_REVERSAL)
        jsonObject.put("amount", amount)
        jsonObject.put("data", convertToValidJson(json))
        bluetoothConnectionService.sendBluetoothData(jsonObject.toString())
    }

    override fun startReconciliation() {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.START_RECONCILIATION)
        bluetoothConnectionService.sendBluetoothData(jsonObject.toString())
    }

    override fun cashout(cashBack: String?, json: String?) {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.CASHOUT)
        jsonObject.put("cashBack", cashBack)
        jsonObject.put("data", convertToValidJson(json))
        bluetoothConnectionService.sendBluetoothData(jsonObject.toString())
    }

    override fun purchaseWithCashback(amount: String?, cashBack: String?, json: String?) {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.PURCHASE_WITH_CASHBACK)
        jsonObject.put("amount", amount)
        jsonObject.put("cashBack", cashBack)
        jsonObject.put("data", convertToValidJson(json))
        bluetoothConnectionService.sendBluetoothData(jsonObject.toString())
    }

    override fun setDefaultTerminal(isDefaultTerminal: Boolean) {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.SET_DEFAULT_TERMINAL)
        jsonObject.put("isDefaultTerminal", isDefaultTerminal)
        bluetoothConnectionService.sendBluetoothData(jsonObject.toString())
    }

    override fun startServiceMenu() {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.START_SERVICE_MENU)
        bluetoothConnectionService.sendBluetoothData(jsonObject.toString())
    }

    override fun addTestConfiguration() {
        val jsonObject = JSONObject()
        jsonObject.put("command", BluetoothCommand.ADD_TEST_CONFIGURATION)
        bluetoothConnectionService.sendBluetoothData(jsonObject.toString())
    }

    fun setResultDataListener(resultDataListener: ResultDataListener) {
        bluetoothConnectionService.setResultDataListener(resultDataListener)
    }

    private fun convertToValidJson(json: String?): JSONObject? =
        if (json.isNullOrEmpty()) {
            null
        } else {
            JSONObject(json)
        }
}