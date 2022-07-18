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
import java.math.BigDecimal
import java.util.*

class PaymentController(context: Context) {

    private val bluetoothService: BluetoothService = BluetoothService(context)
    private val retrofitService: RetrofitService = RetrofitCommon.retrofitService

    private var sdkToken: String? = null

    /**
     * Получение токена
     */
    fun setCredentials(
        login: String,
        password: String,
        successHandler: (String) -> Unit,
        errorHandler: (Exception) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = retrofitService.getToken(login, password)
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
     * Начинает выполнение платежа. При неверных параметрах
    платежа или при попытке начать новый платеж/отмену
    платежа до окончания будет сгенерировано исключение
    PaymentException
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
     * Начинает выполнение возврата/отмены платежа. При попытке
    начать новый платеж/отмену платежа до окончания будет
    сгенерировано исключение PaymentException
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
     * Начинает выполнение платежа наличкой
     *
     * @param paymentContext - данные платежа
     * @return PaymentResultData - все данные по транзакции
     */
    @Throws(PaymentException::class)
    fun startCash(
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
     * Начинает выполнение возврата/отмены платежа наличкой
     *
     * @param reverseContext - данные для проведения отмены/возврата
     */
    @Throws(PaymentException::class)
    fun cancelCash(
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
     * Начинает выполнение активации подарочной карты.
     *
     * @param giftCardActivationContext - данные для проведения активации подарочной карты
     */
    fun giftCardActivate(
        giftCardActivationContext: GiftCardActivationContext,
        paymentProductTextData: Map<String, String>? = null,
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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = retrofitService.sendGift(
                    sdkToken.orEmpty(),
                    GiftActivationBody(
                        loyaltyNumber = giftCardActivationContext.loyalty_number,
                        tid = giftCardActivationContext.tid,
                        login = giftCardActivationContext.login,
                        amount = giftCardActivationContext.amount ?: BigDecimal.ZERO,
                        paymentProductTextData = paymentProductTextData
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
                                        giftCardActivationContext.tid,
                                        giftCardActivationContext.loyalty_number,
                                        giftCardActivationContext.amount ?: BigDecimal.ONE
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
     * Начинает выполнение деактивации подарочной карты.
     *
     * @param giftCardActivationContext - данные для проведения деактивации подарочной карты
     */
    fun giftCardDeactivate(
        giftCardActivationContext: GiftCardActivationContext,
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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = retrofitService.cancelGift(
                    sdkToken.orEmpty(),
                    GiftCancelBody(
                        loyalty_number = giftCardActivationContext.loyalty_number,
                        tid = giftCardActivationContext.tid,
                        login = giftCardActivationContext.login,
                        transactionId = giftCardActivationContext.transactionId.orEmpty()
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
        login: String,
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
                                login = login
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
                                                BigDecimal(
                                                    100
                                                )
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