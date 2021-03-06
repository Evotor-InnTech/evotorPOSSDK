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
import androidx.core.content.ContextCompat
import com.google.gson.Gson
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

class PaymentController(context: Context) {

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
        context: Context,
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
            onSuccessHandler(pairedDevice)
        } else {
            receiver = object : BroadcastReceiver() {
                @SuppressLint("MissingPermission")
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device: BluetoothDevice? =
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            if (device != null && device.name == "CloudPOS") {
                                CoroutineScope(Dispatchers.IO).launch {
                                    bluetoothService.selectBluetoothDevice(device.address, device)
                                    CoroutineScope(Dispatchers.Main).launch {
                                        onSuccessHandler(device)
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
    fun disable(context: Context) {
        context.unregisterReceiver(receiver)
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
        token: String? = null,
        resultHandler: (PaymentResultContext) -> Unit
    ) {
        token?.let {
            sdkToken = it
        }

        if (sdkToken == null) {
            resultHandler(
                PaymentResultContext(
                    success = false,
                    message = "Отсутствует токен",
                    code = TOKEN_ERROR_CODE
                )
            )
        }

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
                    val paymentResult =
                        Gson().fromJson(data, CardPaymentResultContext::class.java)
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

        bluetoothService.setResultDataListener(resultDataListener)
        bluetoothService.startCardPayment(Gson().toJson(paymentContext), sdkToken.orEmpty())
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
                val response = retrofitService.sendCash(
                    sdkToken.orEmpty(),
                    paymentContext
                )
                if (response.isSuccessful) {
                    CoroutineScope(Dispatchers.Main).launch {
                        resultHandler(
                            PaymentResultContext(
                                true,
                                null,
                                null,
                                CashResultData(
                                    paymentContext,
                                    response.body()?.transactionId.orEmpty()
                                )
                            )
                        )
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
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
                val response = retrofitService.sendGift(
                    sdkToken.orEmpty(),
                    GiftActivationBody(
                        loyaltyNumber = paymentContext.loyaltyNumber.orEmpty(),
                        tid = paymentContext.tid.orEmpty(),
                        login = login.orEmpty(),
                        amount = paymentContext.amount ?: BigDecimal.ONE,
                        paymentProductTextData = paymentContext.paymentProductTextData
                    )
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.errorMessage != null) {
                        CoroutineScope(Dispatchers.Main).launch {
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
                            resultHandler(
                                PaymentResultContext(
                                    response.isSuccessful,
                                    null,
                                    null,
                                    GiftResultData(
                                        (response.body()?.transactionId ?: 0L).toString(),
                                        paymentContext.tid.orEmpty(),
                                        paymentContext.loyaltyNumber.orEmpty(),
                                        paymentContext.amount ?: BigDecimal.ONE,
                                    )
                                )
                            )
                        }
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
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
        token: String? = null,
        resultHandler: (PaymentResultContext) -> Unit
    ) {
        token?.let {
            sdkToken = it
        }

        if (sdkToken == null) {
            resultHandler(
                PaymentResultContext(
                    success = false,
                    message = "Отсутствует токен",
                    code = TOKEN_ERROR_CODE
                )
            )
        }

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
                    val paymentResult =
                        Gson().fromJson(data, CardRefundResultContext::class.java)
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

        bluetoothService.setResultDataListener(resultDataListener)
        bluetoothService.startCardRefund(Gson().toJson(reverseContext), sdkToken.orEmpty())
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
                val response = retrofitService.reverseCash(
                    sdkToken.orEmpty(),
                    reverseContext
                )
                if (response.isSuccessful) {
                    CoroutineScope(Dispatchers.Main).launch {
                        resultHandler(
                            PaymentResultContext(
                                true,
                                null,
                                null,
                                ReverseCashResultData(
                                    reverseContext
                                )
                            )
                        )
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
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
                val response = retrofitService.cancelGift(
                    sdkToken.orEmpty(),
                    GiftCancelBody(
                        loyalty_number = reverseContext.loyaltyNumber.orEmpty(),
                        tid = reverseContext.tid.orEmpty(),
                        login = login.orEmpty(),
                        transactionId = reverseContext.transactionID.orEmpty()
                    )
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.errorMessage != null) {
                        CoroutineScope(Dispatchers.Main).launch {
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
        token: String? = null,
        successHandler: (GiftResult) -> Unit,
        errorHandler: (String, Int?) -> Unit
    ) {
        token?.let {
            sdkToken = it
        }

        if (sdkToken == null) {
            errorHandler("Отсутствует токен", TOKEN_ERROR_CODE)
        }

        val resultDataListener = object : ResultDataListener {
            override fun onResult(data: String) {
                CoroutineScope(Dispatchers.IO).launch {
                    val resultData = Gson().fromJson(data, ResultData::class.java)

                    try {
                        val response = retrofitService.getGiftBalance(
                            sdkToken.orEmpty(),
                            GiftBalanceBody(
                                loyaltyNumber = resultData.LOYALTY_NUMBER.orEmpty(),
                                tid = resultData.TID.orEmpty(),
                                login = login.orEmpty()
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
                                    successHandler(
                                        GiftResult(
                                            loyaltyCardTrack = resultData.LOYALTY_NUMBER.orEmpty(),
                                            tid = resultData.TID.orEmpty(),
                                            balance = response.body()?.balance?.divide(
                                                BigDecimal(100)
                                            ) ?: BigDecimal.ZERO
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

    private companion object {
        const val TOKEN_ERROR_CODE = 401
    }
}