package ru.evotor.sdk.payment

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.evotor.sdk.api.RetrofitCommon
import ru.evotor.sdk.api.RetrofitService
import ru.evotor.sdk.bluetooth.BluetoothService
import ru.evotor.sdk.payment.entities.*
import ru.evotor.sdk.payment.enums.Currency
import ru.evotor.sdk.payment.enums.PaymentMethod
import java.math.BigDecimal
import java.util.*
import kotlin.math.log

class PaymentController(private val context: Context) {

    private val bluetoothService: BluetoothService = BluetoothService(context)
    private val retrofitService: RetrofitService = RetrofitCommon.retrofitService

    private var sdkToken: String? = null
    private var login: String? = null
    private var password: String? = null

    /**
     * Сохранение логина и пароля
     */
    fun setCredentials(
        login: String,
        password: String
    ) {
        if (!this.login.equals(login) || !this.password.equals(password)) {
            this.sdkToken = null
        }

        this.login = login
        this.password = password
    }

    /**
     * Получение токена
     */
    fun auth(
        successHandler: (String) -> Unit,
        errorHandler: (Exception) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = retrofitService.getToken(login.orEmpty(), password.orEmpty())
                if (response.isSuccessful) {
                    sdkToken = response.body()?.string()
                    successHandler(sdkToken.orEmpty())
                } else {
                    throw RuntimeException(response.code().toString())
                }
            } catch (exception: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    errorHandler(exception)
                }
            }
        }
    }

    private var receiver: BroadcastReceiver? = null

    /**
     * Начинает работу со считывателем карт
     */
    fun enable(
        onSuccessHandler: (BluetoothDevice) -> Unit,
        errorHandler: (String) -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            errorHandler("Необходим Manifest.permission.BLUETOOTH_CONNECT permission")
            return
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            errorHandler("Необходим Manifest.permission.BLUETOOTH_SCAN permission")
            return
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            errorHandler("Необходим Manifest.permission.ACCESS_COARSE_LOCATION permission")
            return
        }

        val pairedDevice = bluetoothService.getPairedDevices().find { it.name == "CloudPOS" }
        if (pairedDevice != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    bluetoothService.selectBluetoothDevice(pairedDevice.address, pairedDevice)
                    CoroutineScope(Dispatchers.Main).launch {
                        onSuccessHandler(pairedDevice)
                    }
                } catch (exception: Exception) {
                    CoroutineScope(Dispatchers.Main).launch {
                        errorHandler(exception.message.toString())
                    }
                }
            }
        } else {
            receiver = object : BroadcastReceiver() {
                @SuppressLint("MissingPermission")
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device: BluetoothDevice? =
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            if (device != null &&
                                (device.name.equals("CloudPOS", ignoreCase = true) ||
                                        device.name.equals("WIZARPOS_Q3", ignoreCase = true))
                            ) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        bluetoothService.selectBluetoothDevice(
                                            device.address,
                                            device
                                        )
                                        CoroutineScope(Dispatchers.Main).launch {
                                            onSuccessHandler(device)
                                        }
                                    } catch (exception: Exception) {
                                        CoroutineScope(Dispatchers.Main).launch {
                                            errorHandler(exception.message.toString())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            bluetoothService.startDiscovery()

            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            context.registerReceiver(receiver, filter)
        }
    }

    /**
     * Завершает работу со считывателем карт
     */
    fun disable() {
        try {
            context.unregisterReceiver(receiver)
        } catch (exception: Exception) {
        }
    }

    /**
     * Начинает выполнение платежа по карте, наличке и ПК
     *
     * @param paymentContext - данные платежа
     * @return PaymentResultData - все данные по транзакции
     */
    @Throws(PaymentException::class)
    fun startPayment(
        paymentContext: PaymentContext,
        resultHandler: (PaymentResultContext) -> Unit
    ) {
        if (sdkToken == null) {
            auth({
                paymentProcess(paymentContext, resultHandler)
            }, {
                resultHandler(PaymentResultContext(false, it.message.toString(), null))
            })
        } else {
            paymentProcess(paymentContext, resultHandler)
        }
    }

    private fun paymentProcess(
        paymentContext: PaymentContext,
        resultHandler: (PaymentResultContext) -> Unit
    ) {
        val currentPaymentContext = paymentContext.copy(
            login = login,
            password = password
        )

        when (paymentContext.method) {
            PaymentMethod.CARD -> {
                cardPaymentProcess(currentPaymentContext, resultHandler)
            }
            PaymentMethod.CASH -> {
                cashPaymentProcess(currentPaymentContext, resultHandler)
            }
            PaymentMethod.LINKED_CARD -> {
                giftPaymentProcess(currentPaymentContext, resultHandler)
            }
        }
    }

    /**
     * Оплата картой
     */
    private fun cardPaymentProcess(
        paymentContext: PaymentContext,
        resultHandler: (PaymentResultContext) -> Unit
    ) {
        val resultDataListener = object : ResultDataListener {
            override fun onResult(data: String) {
                CoroutineScope(Dispatchers.Main).launch {
                    var paymentResult: CardPaymentResultContext
                    try {
                        Log.d("PaymentController", data)
                        paymentResult = Gson().fromJson(data, CardPaymentResultContext::class.java)
                    } catch (exception: Exception) {
                        Log.e("PaymentController", exception.message.toString())
                        resultHandler(
                            PaymentResultContext(
                                success = false,
                                message = exception.message.toString(),
                                code = null,
                                data = null
                            )
                        )
                        return@launch
                    }
                    paymentResult = paymentResult.copy(
                        data = paymentResult.data?.copy(
                            data = paymentResult.data?.data?.copy(
                                amount = BigDecimal(paymentResult.data?.data?.amount ?: "0").divide(
                                    BigDecimal(100)
                                ).toPlainString(),
                                amountClear = BigDecimal(
                                    paymentResult.data?.data?.amountClear ?: "0"
                                ).divide(BigDecimal(100)).toPlainString()
                            ) ?: throw RuntimeException("data is null")
                        )
                    )
                    Log.d(
                        "PaymentController", "cardPaymentProcess: " + Gson().toJson(
                            PaymentResultContext(
                                success = paymentResult.success,
                                message = paymentResult.message,
                                code = paymentResult.code,
                                data = paymentResult.data
                            )
                        )
                    )
                    resultHandler(
                        PaymentResultContext(
                            success = paymentResult.success,
                            message = paymentResult.message,
                            code = paymentResult.code,
                            data = paymentResult.data
                        )
                    )
                }
            }
        }

        var currentPaymentContext = paymentContext
        currentPaymentContext = currentPaymentContext.copy(
            deviceAppBuild = "1.0.0",
            deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ),
            deviceModel = Build.MANUFACTURER,
            deviceName = Build.MODEL
        )

        bluetoothService.setResultDataListener(resultDataListener)
        bluetoothService.startCardPayment(Gson().toJson(currentPaymentContext), sdkToken.orEmpty())
    }

    /**
     * Оплата наличкой
     */
    private fun cashPaymentProcess(
        paymentContext: PaymentContext,
        resultHandler: (PaymentResultContext) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var currentPaymentContext = paymentContext
                currentPaymentContext = currentPaymentContext.copy(
                    deviceAppBuild = "1.0.0",
                    deviceId = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ANDROID_ID
                    ),
                    deviceModel = Build.MANUFACTURER,
                    deviceName = Build.MODEL
                )
                val response = retrofitService.sendCash(
                    sdkToken.orEmpty(),
                    convertToBody(
                        currentPaymentContext.copy(
                            amount = currentPaymentContext.amount?.multiply(
                                BigDecimal(100)
                            )
                        )
                    )
                )
                if (response.isSuccessful) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Log.d(
                            "PaymentController", "cashPaymentProcess_success: " + Gson().toJson(
                                PaymentResultContext(
                                    true,
                                    null,
                                    null,
                                    CashResultData(
                                        currentPaymentContext,
                                        response.body()?.transactionId.orEmpty()
                                    )
                                )
                            )
                        )
                        resultHandler(
                            PaymentResultContext(
                                true,
                                null,
                                null,
                                CashResultData(
                                    currentPaymentContext,
                                    response.body()?.transactionId.orEmpty()
                                )
                            )
                        )
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        Log.d(
                            "PaymentController", "cashPaymentProcess_error: " + Gson().toJson(
                                PaymentResultContext(
                                    false,
                                    response.message().orEmpty(),
                                    response.code()
                                )
                            )
                        )
                        resultHandler(
                            PaymentResultContext(
                                false,
                                response.message().orEmpty(),
                                response.code()
                            )
                        )
                    }
                }
            } catch (exception: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    Log.d(
                        "PaymentController", "cashPaymentProcess_error: " + Gson().toJson(
                            PaymentResultContext(
                                false,
                                (exception.localizedMessage ?: exception.message).toString(),
                                null
                            )
                        )
                    )
                    resultHandler(
                        PaymentResultContext(
                            false,
                            (exception.localizedMessage ?: exception.message).toString(),
                            null
                        )
                    )
                }
            }
        }
    }

    /**
     * Оплата ПК
     */
    private fun giftPaymentProcess(
        paymentContext: PaymentContext,
        resultHandler: (PaymentResultContext) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var currentPaymentContext = paymentContext
                currentPaymentContext = currentPaymentContext.copy(
                    deviceAppBuild = "1.0.0",
                    deviceId = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ANDROID_ID
                    ),
                    deviceModel = Build.MANUFACTURER,
                    deviceName = Build.MODEL
                )
                val response = retrofitService.sendGift(
                    sdkToken.orEmpty(),
                    GiftActivationBody(
                        loyaltyNumber = currentPaymentContext.loyaltyNumber.orEmpty(),
                        tid = currentPaymentContext.tid.orEmpty(),
                        login = login.orEmpty(),
                        amount = currentPaymentContext.amount ?: BigDecimal.ONE,
                        paymentProductTextData = currentPaymentContext.paymentProductTextData,
                        deviceAppBuild = currentPaymentContext.deviceAppBuild.orEmpty(),
                        device = DeviceBody(
                            deviceId = currentPaymentContext.deviceId.orEmpty(),
                            deviceModel = currentPaymentContext.deviceModel.orEmpty(),
                            deviceName = currentPaymentContext.deviceName.orEmpty()
                        )
                    )
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.errorMessage != null) {
                        CoroutineScope(Dispatchers.Main).launch {
                            Log.d(
                                "PaymentController", "giftPaymentProcess_error: " + Gson().toJson(
                                    PaymentResultContext(
                                        false,
                                        body.errorMessage.toString(),
                                        response.code()
                                    )
                                )
                            )
                            resultHandler(
                                PaymentResultContext(
                                    false,
                                    body.errorMessage.toString(),
                                    response.code()
                                )
                            )
                        }
                    } else {
                        CoroutineScope(Dispatchers.Main).launch {
                            Log.d(
                                "PaymentController", "giftPaymentProcess_success: " + Gson().toJson(
                                    GiftResultData(
                                        (response.body()?.transactionId ?: 0L).toString(),
                                        currentPaymentContext.tid.orEmpty(),
                                        currentPaymentContext.loyaltyNumber.orEmpty(),
                                        currentPaymentContext.amount ?: BigDecimal.ONE,
                                    )
                                )
                            )
                            resultHandler(
                                PaymentResultContext(
                                    response.isSuccessful,
                                    null,
                                    null,
                                    GiftResultData(
                                        (response.body()?.transactionId ?: 0L).toString(),
                                        currentPaymentContext.tid.orEmpty(),
                                        currentPaymentContext.loyaltyNumber.orEmpty(),
                                        currentPaymentContext.amount ?: BigDecimal.ONE,
                                    )
                                )
                            )
                        }
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        Log.d(
                            "PaymentController", "giftPaymentProcess_error: " + Gson().toJson(
                                PaymentResultContext(
                                    false,
                                    response.message().orEmpty(),
                                    response.code()
                                )
                            )
                        )
                        resultHandler(
                            PaymentResultContext(
                                false,
                                response.message().orEmpty(),
                                response.code()
                            )
                        )
                    }
                }
            } catch (exception: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    Log.d(
                        "PaymentController", "giftPaymentProcess_error: " + Gson().toJson(
                            PaymentResultContext(
                                false,
                                (exception.localizedMessage ?: exception.message).toString(),
                                null
                            )
                        )
                    )
                    resultHandler(
                        PaymentResultContext(
                            false,
                            (exception.localizedMessage ?: exception.message).toString(),
                            null
                        )
                    )
                }
            }
        }
    }

    /**
     * Начинает выполнение возврата/отмены платежа
     *
     * @param reverseContext - данные для проведения отмены/возврата
     */
    @Throws(PaymentException::class)
    fun cancelPayment(
        reverseContext: ReverseContext,
        resultHandler: (PaymentResultContext) -> Unit
    ) {
        if (sdkToken == null) {
            auth({
                cancelProcess(reverseContext, resultHandler)
            }, {
                resultHandler(PaymentResultContext(false, it.message.toString(), null))
            })
        } else {
            cancelProcess(reverseContext, resultHandler)
        }
    }

    private fun cancelProcess(
        reverseContext: ReverseContext,
        resultHandler: (PaymentResultContext) -> Unit
    ) {
        val currentReverseContext = reverseContext.copy(
            login = login,
            password = password
        )

        when (currentReverseContext.method) {
            PaymentMethod.CARD -> {
                cancelCardPayment(currentReverseContext, resultHandler)
            }
            PaymentMethod.CASH -> {
                cancelCashPayment(currentReverseContext, resultHandler)
            }
            PaymentMethod.LINKED_CARD -> {
                cancelGiftPayment(currentReverseContext, resultHandler)
            }
        }
    }

    /**
     * Отмена платежа по карте
     */
    private fun cancelCardPayment(
        reverseContext: ReverseContext,
        resultHandler: (PaymentResultContext) -> Unit
    ) {
        val resultDataListener = object : ResultDataListener {
            override fun onResult(data: String) {
                CoroutineScope(Dispatchers.Main).launch {
                    var paymentResult: CardRefundResultContext
                    try {
                        Log.d("PaymentController", data)
                        paymentResult = Gson().fromJson(data, CardRefundResultContext::class.java)
                    } catch (exception: Exception) {
                        Log.e("PaymentController", exception.message.toString())
                        resultHandler(
                            PaymentResultContext(
                                success = false,
                                message = exception.message.toString(),
                                code = null,
                                data = null
                            )
                        )
                        return@launch
                    }
                    paymentResult = paymentResult.copy(
                        data = paymentResult.data?.copy(
                            data = paymentResult.data?.data?.copy(
                                amount = BigDecimal(paymentResult.data?.data?.amount ?: "0").divide(
                                    BigDecimal(100)
                                ).toPlainString(),
                                amountClear = BigDecimal(
                                    paymentResult.data?.data?.amountClear ?: "0"
                                ).divide(BigDecimal(100)).toPlainString()
                            ) ?: throw RuntimeException("data is null")
                        )
                    )
                    Log.d(
                        "PaymentController", "cancelCardPayment_success: " + Gson().toJson(
                            PaymentResultContext(
                                success = paymentResult.success,
                                message = paymentResult.message,
                                code = paymentResult.code,
                                data = paymentResult.data
                            )
                        )
                    )
                    resultHandler(
                        PaymentResultContext(
                            success = paymentResult.success,
                            message = paymentResult.message,
                            code = paymentResult.code,
                            data = paymentResult.data
                        )
                    )
                }
            }
        }

        var currentReverseContext = reverseContext
        currentReverseContext = currentReverseContext.copy(
            deviceAppBuild = "1.0.0",
            deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ),
            deviceModel = Build.MANUFACTURER,
            deviceName = Build.MODEL
        )

        bluetoothService.setResultDataListener(resultDataListener)
        bluetoothService.startCardRefund(Gson().toJson(currentReverseContext), sdkToken.orEmpty())
    }

    /**
     * Отмена платежа по наличке
     */
    private fun cancelCashPayment(
        reverseContext: ReverseContext,
        resultHandler: (PaymentResultContext) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var currentReverseContext = reverseContext
                currentReverseContext = currentReverseContext.copy(
                    deviceAppBuild = "1.0.0",
                    deviceId = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ANDROID_ID
                    ),
                    deviceModel = Build.MANUFACTURER,
                    deviceName = Build.MODEL
                )
                val response = retrofitService.reverseCash(
                    sdkToken.orEmpty(),
                    convertToBody(
                        currentReverseContext.copy(
                            returnAmount = currentReverseContext.returnAmount?.multiply(
                                BigDecimal(100)
                            )
                        )
                    )
                )
                if (response.isSuccessful) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Log.d(
                            "PaymentController", "cancelCashPayment_success: " + Gson().toJson(
                                PaymentResultContext(
                                    true,
                                    null,
                                    null,
                                    ReverseCashResultData(
                                        currentReverseContext
                                    )
                                )
                            )
                        )
                        resultHandler(
                            PaymentResultContext(
                                true,
                                null,
                                null,
                                ReverseCashResultData(
                                    currentReverseContext
                                )
                            )
                        )
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        Log.d(
                            "PaymentController", "cancelCashPayment_error: " + Gson().toJson(
                                PaymentResultContext(
                                    false,
                                    response.message().orEmpty(),
                                    response.code()
                                )
                            )
                        )
                        resultHandler(
                            PaymentResultContext(
                                false,
                                response.message().orEmpty(),
                                response.code()
                            )
                        )
                    }
                }
            } catch (exception: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    Log.d(
                        "PaymentController", "cancelCashPayment_error: " + Gson().toJson(
                            PaymentResultContext(
                                false,
                                (exception.localizedMessage ?: exception.message).toString(),
                                null
                            )
                        )
                    )
                    resultHandler(
                        PaymentResultContext(
                            false,
                            (exception.localizedMessage ?: exception.message).toString(),
                            null
                        )
                    )
                }
            }
        }
    }

    /**
     * Отмена платежа по ПК
     */
    private fun cancelGiftPayment(
        reverseContext: ReverseContext,
        resultHandler: (PaymentResultContext) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var currentReverseContext = reverseContext
                currentReverseContext = currentReverseContext.copy(
                    deviceAppBuild = "1.0.0",
                    deviceId = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ANDROID_ID
                    ),
                    deviceModel = Build.MANUFACTURER,
                    deviceName = Build.MODEL
                )
                val response = retrofitService.cancelGift(
                    sdkToken.orEmpty(),
                    GiftCancelBody(
                        loyalty_number = currentReverseContext.loyaltyNumber.orEmpty(),
                        tid = currentReverseContext.tid.orEmpty(),
                        login = login.orEmpty(),
                        transactionId = currentReverseContext.transactionID.orEmpty(),
                        deviceAppBuild = currentReverseContext.deviceAppBuild.orEmpty(),
                        device = DeviceBody(
                            deviceId = currentReverseContext.deviceId.orEmpty(),
                            deviceModel = currentReverseContext.deviceModel.orEmpty(),
                            deviceName = currentReverseContext.deviceName.orEmpty()
                        )
                    )
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.errorMessage != null) {
                        CoroutineScope(Dispatchers.Main).launch {
                            Log.d(
                                "PaymentController", "cancelGiftPayment_error: " + Gson().toJson(
                                    PaymentResultContext(
                                        false,
                                        body.errorMessage.toString(),
                                        response.code()
                                    )
                                )
                            )
                            resultHandler(
                                PaymentResultContext(
                                    false,
                                    body.errorMessage.toString(),
                                    response.code()
                                )
                            )
                        }
                    } else {
                        CoroutineScope(Dispatchers.Main).launch {
                            Log.d(
                                "PaymentController", "cancelGiftPayment_success: " + Gson().toJson(
                                    PaymentResultContext(
                                        response.isSuccessful,
                                        null,
                                        null
                                    )
                                )
                            )
                            resultHandler(
                                PaymentResultContext(
                                    response.isSuccessful,
                                    null,
                                    null
                                )
                            )
                        }
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        Log.d(
                            "PaymentController", "cancelGiftPayment_error: " + Gson().toJson(
                                PaymentResultContext(
                                    false,
                                    response.message().orEmpty(),
                                    response.code()
                                )
                            )
                        )
                        resultHandler(
                            PaymentResultContext(
                                false,
                                response.message().orEmpty(),
                                response.code()
                            )
                        )
                    }
                }
            } catch (exception: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    Log.d(
                        "PaymentController", "cancelGiftPayment_error: " + Gson().toJson(
                            PaymentResultContext(
                                false,
                                (exception.localizedMessage ?: exception.message).toString(),
                                null
                            )
                        )
                    )
                    resultHandler(
                        PaymentResultContext(
                            false,
                            (exception.localizedMessage ?: exception.message).toString(),
                            null
                        )
                    )
                }
            }
        }
    }

    /**
     * Начинает выполнение процедуры запроса баланса карты.
    При попытке проверить баланс до окончания
    выполняющегося платежа(отмены) будет сгенерировано
    исключение PaymentException. Асинхронный, см.
    PaymentControllerListener
     *
     * @param currency - валюта карты
     * @param acquirerCode - код банка
     */
    @Throws(PaymentException::class)
    fun balanceInquiry(
        successHandler: (GiftResult) -> Unit,
        errorHandler: (String, Int?) -> Unit
    ) {
        if (sdkToken == null) {
            auth({
                balanceProcess(successHandler, errorHandler)
            }, {
                errorHandler(it.message.toString(), null)
            })
        } else {
            balanceProcess(successHandler, errorHandler)
        }
    }

    private fun balanceProcess(
        successHandler: (GiftResult) -> Unit,
        errorHandler: (String, Int?) -> Unit
    ) {
        val resultDataListener = object : ResultDataListener {
            override fun onResult(data: String) {
                CoroutineScope(Dispatchers.IO).launch {
                    val resultData: ResultData
                    try {
                        Log.d("PaymentController", data)
                        resultData = Gson().fromJson(data, ResultData::class.java)
                    } catch (exception: Exception) {
                        CoroutineScope(Dispatchers.Main).launch {
                            Log.e("PaymentController", exception.message.toString())
                            errorHandler(
                                (exception.localizedMessage ?: exception.message).toString(),
                                null
                            )
                        }
                        return@launch
                    }

                    try {
                        val response = retrofitService.getGiftBalance(
                            sdkToken.orEmpty(),
                            GiftBalanceBody(
                                loyaltyNumber = resultData.LOYALTY_NUMBER.orEmpty(),
                                tid = resultData.TID.orEmpty(),
                                login = login.orEmpty(),
                                deviceAppBuild = "1.0.0",
                                device = DeviceBody(
                                    deviceId = Settings.Secure.getString(
                                        context.contentResolver,
                                        Settings.Secure.ANDROID_ID
                                    ),
                                    deviceModel = Build.MANUFACTURER,
                                    deviceName = Build.MODEL
                                )
                            )
                        )
                        if (response.isSuccessful) {
                            val body = response.body()
                            if (body?.errorMessage != null) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    errorHandler(body.errorMessage.toString(), response.code())
                                }
                            } else {
                                CoroutineScope(Dispatchers.Main).launch {
                                    Log.d(
                                        "PaymentController",
                                        "balanceProcess_success: " + Gson().toJson(
                                            GiftResult(
                                                loyaltyCardTrack = resultData.LOYALTY_NUMBER.orEmpty(),
                                                tid = resultData.TID.orEmpty(),
                                                balance = response.body()?.balance
                                                    ?: BigDecimal.ZERO
                                            )
                                        )
                                    )
                                    successHandler(
                                        GiftResult(
                                            loyaltyCardTrack = resultData.LOYALTY_NUMBER.orEmpty(),
                                            tid = resultData.TID.orEmpty(),
                                            balance = response.body()?.balance ?: BigDecimal.ZERO
                                        )
                                    )
                                }
                            }
                        } else {
                            CoroutineScope(Dispatchers.Main).launch {
                                errorHandler(response.message().orEmpty(), response.code())
                            }
                        }
                    } catch (exception: Exception) {
                        CoroutineScope(Dispatchers.Main).launch {
                            errorHandler(
                                (exception.localizedMessage ?: exception.message).toString(),
                                null
                            )
                        }
                    }
                }
            }
        }

        bluetoothService.setResultDataListener(resultDataListener)
        bluetoothService.startPayment("1", null)
    }

    fun getBluetoothService() = bluetoothService

    private fun convertToBody(paymentContext: PaymentContext) = CashPaymentBody(
        amount = (paymentContext.amount ?: BigDecimal.ZERO).toLong(),
        description = paymentContext.description,
        currency = paymentContext.currency?.name ?: Currency.RUB.name,
        suppressSignatureWaiting = paymentContext.suppressSignatureWaiting,
        paymentProductTextData = paymentContext.paymentProductTextData,
        paymentProductCode = paymentContext.paymentProductCode,
        extID = paymentContext.extID,
        method = paymentContext.method,
        acquirerCode = paymentContext.acquirerCode,
        tid = paymentContext.tid,
        login = paymentContext.login,
        password = paymentContext.password,
        deviceAppBuild = paymentContext.deviceAppBuild,
        device = DeviceBody(
            deviceId = paymentContext.deviceId.orEmpty(),
            deviceName = paymentContext.deviceName.orEmpty(),
            deviceModel = paymentContext.deviceModel.orEmpty()
        )
    )

    private fun convertToBody(reverseContext: ReverseContext) = CashReverseBody(
        transactionID = reverseContext.transactionID,
        returnAmount = (reverseContext.returnAmount ?: BigDecimal.ZERO).toLong(),
        currency = reverseContext.currency?.name ?: Currency.RUB.name,
        suppressSignatureWaiting = reverseContext.suppressSignatureWaiting,
        extID = reverseContext.extID,
        acquirerCode = reverseContext.acquirerCode,
        tid = reverseContext.tid,
        login = reverseContext.login,
        password = reverseContext.password,
        deviceAppBuild = reverseContext.deviceAppBuild,
        device = DeviceBody(
            deviceId = reverseContext.deviceId.orEmpty(),
            deviceName = reverseContext.deviceName.orEmpty(),
            deviceModel = reverseContext.deviceModel.orEmpty()
        )
    )
}